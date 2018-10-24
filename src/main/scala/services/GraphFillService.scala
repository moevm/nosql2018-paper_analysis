package services

import com.twitter.util.Future
import dao.PaperGraphDao
import services.GraphFillService.CyberleninkaPageData

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

  def persistParsedPaper(data: CyberleninkaPageData): Future[Unit] = {
    Future.join(
      Seq(
        createAuthor(data.authorName),
        createPaper(data.paperTitle)
      )
    ).before(
      createWroteRelation(data.authorName, data.paperTitle)
    )
  }
}

object GraphFillService {
  case class CyberleninkaPageData(authorName: String,
                                  paperTitle: String,
                                  field: String = null,
                                  tags: Seq[String] = null,
                                  citations: Seq[Citation] = null,
                                  journal: String = null)

  case class Citation(author: String, title: String)

}