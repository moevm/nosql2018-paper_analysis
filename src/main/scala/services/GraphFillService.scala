package services

import com.twitter.util.Future
import dao.PaperGraphDao

class GraphFillService(dao: PaperGraphDao) {
  def createAuthor(name: String): Future[Unit] = {
    dao.createAuthor(name)
  }

  def createPaper(title: String): Future[Unit] = {
    dao.createPaper(title)
  }

  def createWroteRelation(authorName: String, paperTitle: String): Future[Unit] = {
    dao.createWroteRelation(authorName, paperTitle)
  }
}
