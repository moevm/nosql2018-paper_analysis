package dao

import com.twitter.util.{Future, FuturePool}
import org.neo4j.driver.v1._
import org.neo4j.driver.v1.Values.parameters
import org.neo4j.driver.v1.types.Entity
import services.GraphService.{Citation, Paper, Reference}

import scala.collection.JavaConverters._


trait PaperGraphDao {
  def findReferenceCycles(): Future[List[List[Paper]]]
  def createAuthor(name: String): Future[Unit]
  def createPaper(paper: Paper): Future[Unit]
  def createReferenceRelation(sourcePaperTitle: String, targetPaperTitle: String): Future[Unit]
  def createWroteRelation(authorName: String, paperTitle: String): Future[Unit]
  def getPaper(paperTitle: String): Future[Paper]
  def searchPapers(researchField: String): Future[List[String]]
  def getResearchFields(): Future[List[String]]
  def getReferencesByAuthorName(authorName: String): Future[List[Reference]]
  def getReferencesByPaperTitle(paperTitle: String): Future[List[Reference]]
}

class PaperGraphDaoImpl extends PaperGraphDao {
  import PaperGraphDao._

  def findReferenceCycles(): Future[List[List[Paper]]] = {
    doQuery[List[List[Paper]]] { tx =>
      val result = tx.run(
        """MATCH p = (n:Paper)-[*]->(n:Paper) RETURN nodes(p)""".stripMargin,
        parameters()
      )

      val aaa = new org.neo4j.driver.v1.util.Function[Record, List[Paper]] {
        override def apply(record: Record): List[Paper] = {
          record
            .values()
            .get(0)
            .asList(z => entityToPaper(z.asEntity()))
            .asScala
            .toList
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

  override def getReferencesByAuthorName(authorName: String): Future[List[Reference]] = {
//    doQuery {
//      _.run(
//        """MATCH (p1: Paper {} )-[:REFERENCES]->(p2) WITH DISTINCT p.research_field as rf RETURN rf""",
//      )
//    }
    ???
  }

  override def getReferencesByPaperTitle(paperTitle: String): Future[List[Reference]] = {
    doQuery {
      _.run(
        """MATCH (p1: Paper { title: $paper_title } )-[x:REFERENCES]->(p2) RETURN p1.title as source, p2.title as target""",
        parameters("paper_title", paperTitle)
      ).list().asScala.toList.map(x => Reference(x.get("source").asString, x.get("target").asString))
    }
  }
}

object PaperGraphDao {
  val uri = "bolt://localhost:7687"
  val driver = GraphDatabase.driver(uri, AuthTokens.none())

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
}
