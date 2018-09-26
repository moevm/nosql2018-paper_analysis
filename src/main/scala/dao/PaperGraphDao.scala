package dao

import com.twitter.util.{Future, FuturePool}
import org.neo4j.driver.v1._
import org.neo4j.driver.v1.Values.parameters


class PaperGraphDao {
  import PaperGraphDao._

  def createAuthor(name: String): Future[Unit] = {
      doQuery(
        _.run(
          "MERGE (a:Author { name: $name })",
          parameters("name", name)
        )
      ).map(_ => ())
    }

  def createPaper(title: String): Future[Unit] = {
    doQuery(
      _.run(
        "MERGE (p:Paper { title: $title })",
        parameters("title", title)
      )
    ).map(_ => ())
  }

  def createWroteRelation(authorName: String, paperTitle: String): Future[Unit] = {
    doQuery(
      _.run(
        "MATCH (a:Author { name: $name }), (p:Paper { title: $title })" +
        "MERGE (a)-[:WROTE]->(p)",
        parameters(
          "name", authorName,
          "title", paperTitle
        )
      )
    ).map(_ => ())
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
}

object PaperGraphDao {
  private val uri = "bolt://localhost:7687"
  private val user = "neo4j"
  private val password = "123"
  private val driver = GraphDatabase.driver(uri, AuthTokens.basic(user, password))
}
