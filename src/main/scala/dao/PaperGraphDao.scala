package dao

import com.twitter.util.{Future, FuturePool}
import dao.PaperGraphDao.SearchRowResult
import main.Requests.{Author, Reference}
import org.neo4j.driver.v1._
import org.neo4j.driver.v1.Values.parameters
import org.neo4j.driver.v1.types.Entity
import services.GraphService._

import scala.collection.JavaConverters._


trait PaperGraphDao {
  def findReferenceCycles(): Future[List[Loop]]
  def createAuthor(name: String): Future[Unit]
  def createPaper(paper: Paper): Future[Unit]
  def createReferenceRelation(sourcePaperTitle: String, targetPaperTitle: String): Future[Unit]
  def createWroteRelation(authorName: String, paperTitle: String): Future[Unit]
  def getPaper(paperTitle: String): Future[Paper]
  def searchPapers(researchField: String): Future[List[String]]
  def getResearchFields(): Future[List[String]]
  def search(authorName: Option[String], paperTitle: Option[String], researchField: Option[String], journalName: Option[String], year: Option[Int]): Future[List[SearchRowResult]]
  def importGraph(authors: Seq[(Author, Paper)], papers: Seq[Paper], references: Seq[Reference]): Future[Unit]
}

class PaperGraphDaoImpl extends PaperGraphDao {
  import PaperGraphDao._

  def findReferenceCycles(): Future[List[Loop]] = {
    doQuery[List[Loop]] { tx =>
      val result = tx.run(
        """MATCH (n:Paper)
          |RETURN [x IN (n)-[*]->(n) |
          | [z IN  nodes(x) | z {
          |                       .title,
          |                       author : [(a)-[:WROTE]->(z) | a.name][0]
          |                     }
          | ]
          |]""".stripMargin,
        parameters()
      )

      val aaa = new org.neo4j.driver.v1.util.Function[Record, List[LoopEntity]] {
        override def apply(record: Record): List[LoopEntity] = {
          record
            .values()
            .get(0)
            .asList(_.asList(z => mapToLoopEntity(z.asMap(_.asString()).asScala.toMap)).asScala.toList)
            .get(0)
        }
      }
      result.list(aaa).asScala.toList
    }
  }

  def createAuthor(name: String): Future[Unit] = {
    doQuery(
      _.run(
        "MERGE (a:Author { name: $name })",
        parameters("name", name)
      )
    ).map(_ => Unit)
    }

  def createPaper(paper: Paper): Future[Unit] = {
    doQuery(
      _.run(
        """MERGE (
          | p:Paper {
          |   title: $title,
          |   journal_name: $journal_name,
          |   research_field: $research_field
          |   year: $year
          |   link: $link
          |})"""
          .stripMargin,
        parameters(
          "title", paper.title,
          "journal_name", paper.journalName,
          "research_field", paper.researchField,
          "year", paper.year.asInstanceOf[Object],
          "link", paper.link
        )
      )
    ).map(_ => ())
  }

  def createReferenceRelation(sourcePaperTitle: String, targetPaperTitle: String): Future[Unit] = {
    doQuery(
      _.run(
        """MATCH
          | (a:Paper { title: $title1 }),
          | (p:Paper { title: $title2 })
          | MERGE (p1)-[:REFERENCES]->(p2)"""
          .stripMargin,
        parameters(
          "title1", sourcePaperTitle,
          "title2", targetPaperTitle
        )
      )
    ).map(_ => ())
  }

  def createWroteRelation(authorName: String, paperTitle: String): Future[Unit] = {
    doQuery(
      _.run(
        """MATCH
          | (a:Author { name: $name }),
          | (p:Paper { title: $title })
          | MERGE (a)-[:WROTE]->(p)"""
          .stripMargin,
        parameters(
          "name", authorName,
          "title", paperTitle
        )
      )
    ).map(_ => ())
  }

  def getPaper(paperTitle: String): Future[Paper] = {
    doQuery(tx => {
      entityToPaper(
        tx.run(
          """MATCH
            | (p:Paper { title: $title })
            | RETURN p"""
            .stripMargin,
          parameters(
            "title", paperTitle
          )
        ).single().get("p").asEntity()
      )
    })
  }

  def doQuery[T](transactionWork: TransactionWork[T]): Future[T] = {
    FuturePool.unboundedPool {
      try {
        val session = driver.session
        try {
          session.writeTransaction(transactionWork)
        } catch {
          case e: Exception => println(e.toString); throw e
        } finally {
          if (session != null) session.close()
        }
      }
    }
  }

  override def searchPapers(researchField: String): Future[List[String]] = {
    doQuery { tx =>
      recordToPapersList(
        tx.run(
          """MATCH
            | (p:Paper { research_field: $researchField })
            | RETURN p"""
            .stripMargin,
          parameters(
            "researchField", researchField
          )
        ).single()
      ).map(_.title)
    }
  }

  override def getResearchFields(): Future[List[String]] = {
    doQuery {
      _.run(
        """MATCH (p:Paper) WITH DISTINCT p.research_field as rf RETURN rf""",
      )
        .list()
        .asScala
        .toList
        .map(_.get(0).asString)
    }
  }

  override def search(authorName: Option[String], paperTitle: Option[String], researchField: Option[String], journalName: Option[String], year: Option[Int]): Future[List[SearchRowResult]] = {
    val buildPaperQuery =
      Seq(
        paperTitle.map(x => s"title: '$x'"),
        researchField.map(x => s"research_field: '$x'"),
        journalName.map(x => s"journal_name: '$x'"),
        year.map(x => s"year: $x")
      ).flatten.mkString(", ")
    doQuery {
      _.run(
        ("""MATCH (p1: Paper {""" + buildPaperQuery + """ } )-[x:REFERENCES]->(p2),
           |      (a: Author { """ + authorName.map(x => s"name: '$x'").getOrElse("") + """ } )-[:WROTE]->(p1)
           |RETURN a as author, p1 as paper, p1.title as source, p2.title as target
           |UNION
           |MATCH (p: Paper {""" + buildPaperQuery + """ } ), (a: Author { """ + authorName.map(x => s"name: '$x'").getOrElse("") + """ })
           |WHERE (a: Author)-[:WROTE]->(p)
           |AND
           |NOT (p: Paper)-[:REFERENCES]->(:Paper)
           |RETURN a as author, p as paper, null as source, null as target""").stripMargin
      ).list()
        .asScala
        .toList
        .map {
          x =>
            val src = x.get("source").asString
            val tgt = x.get("target").asString

            val reference = for {
              source <- if (src == "null") None else Some(src)
              target <- if (tgt == "null") None else Some(tgt)
            } yield Reference(source, target)

            PaperGraphDao.SearchRowResult(
              entityToAuthor(x.get("author").asEntity()),
              entityToPaper(x.get("paper").asEntity()),
              reference,
            )
        }
    }
  }

  override def importGraph(authors: Seq[(Author, Paper)], papers: Seq[Paper], references: Seq[Reference]): Future[Unit] = {
    val buildCreatePapersQuery = "MERGE " + papers.map {
      p => s"(:Paper { link: '${p.link}', journal_name: '${p.link}', title: '${p.title}', year: ${p.year}, research_field: '${p.researchField}'})"
    }.mkString(", \n")

    val buildCreateAuthorsQuery = "MERGE " + authors.map {
      case (a, _) => s"(:Author { name: '${a.name}' })"
    }.mkString(", \n")

    val buildCreateWroteQuery = authors.map {
      case (a, p) =>
        s"""MATCH (a:Author),(p:Paper)
           |WHERE a.name = '${a.name}'
           |AND p.title = '${p.title}'
           |MERGE (a)-[:WROTE]->(p)""".stripMargin
    }.mkString("\nWITH 1 as dummy\n")

    val buildCreateReferencesQuery = references.map {
      case Reference(src, tgt) =>
        s"""MATCH (p1:Paper),(p2:Paper)
           |WHERE p1.title = '$src'
           |AND p2.title = '$tgt'
           |MERGE (p1)-[:REFERENCES]->(p2)""".stripMargin
    }.mkString("\nWITH 1 as dummy\n")

    val query =
      Seq(buildCreatePapersQuery, buildCreateAuthorsQuery, buildCreateWroteQuery, buildCreateReferencesQuery)
        .mkString("\nWITH 1 as dummy\n")

    doQuery {
      _.run(query)
    }.map(_ => ())
  }
}

object PaperGraphDao {
  val uri = "bolt://localhost:7687"
  val driver = GraphDatabase.driver(uri, AuthTokens.none())

  case class SearchRowResult(author: Author, paper: Paper, reference: Option[Reference])

  val recordToPapersList = new org.neo4j.driver.v1.util.Function[Record, List[Paper]] {
    override def apply(record: Record): List[Paper] = {
      record
        .values()
        .asScala
        .toList
        .map { paperNode =>
          Paper(
            paperNode.get("title").asString(),
            paperNode.get("journal_name").asString(),
            paperNode.get("research_field").asString(),
            paperNode.get("year").asInt(),
            paperNode.get("link").asString()
          )
        }
    }
  }

  val recordToPaper = new org.neo4j.driver.v1.util.Function[Record, Paper] {
    override def apply(record: Record): Paper = {
      Paper(
        record.get("title").asString(),
        record.get("journal_name").asString(),
        record.get("research_field").asString(),
        record.get("year").asInt(),
        record.get("link").asString()
      )
    }
  }

  val entityToPaper = new org.neo4j.driver.v1.util.Function[Entity, Paper] {
    override def apply(record: Entity): Paper = {
      Paper(
        record.get("title").asString(),
        record.get("journal_name").asString(),
        record.get("research_field").asString(),
        record.get("year").asInt(),
        record.get("link").asString()
      )
    }
  }

  val entityToAuthor = new org.neo4j.driver.v1.util.Function[Entity, Author] {
    override def apply(record: Entity): Author = {
      Author(
        record.get("name").asString()
      )
    }
  }

  val mapToLoopEntity = new org.neo4j.driver.v1.util.Function[Map[String, String], LoopEntity] {
    override def apply(record: Map[String, String]): LoopEntity = {
      LoopEntity(
        record.get("author").orNull,
        record.get("title").orNull
      )
    }
  }
}
