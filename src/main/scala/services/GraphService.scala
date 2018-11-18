package services

import com.twitter.util.Future
import dao.PaperGraphDao
import services.GraphService.{CyberleninkaPageData, Cycle, Paper}

trait GraphService {
  def createAuthor(name: String): Future[Unit]
  def createPaper(paper: Paper): Future[Unit]
  def createWroteRelation(authorName: String, paperTitle: String): Future[Unit]
  def persistParsedPaper(data: CyberleninkaPageData): Future[Unit]
  def getPaper(title: String): Future[Paper]
  def findReferenceCycles(): Future[List[Cycle]]
}

class GraphServiceImpl(dao: PaperGraphDao) extends GraphService {
  import GraphService._

  def createAuthor(name: String): Future[Unit] = {
    dao.createAuthor(name)
  }

  def createPaper(paper: Paper): Future[Unit] = {
    dao.createPaper(paper)
  }

  def createWroteRelation(authorName: String, paperTitle: String): Future[Unit] = {
    dao.createWroteRelation(authorName, paperTitle)
  }

  def persistParsedPaper(data: CyberleninkaPageData): Future[Unit] = {
    Future.join(
      Seq(
        createAuthor(data.authorName),
        createPaper(data.paper)
      )
    ).before {
      createWroteRelation(data.authorName, data.paper.title)
    }
  }

  override def getPaper(paperTitle: String): Future[Paper] = {
    dao.getPaper(paperTitle)
  }

  override def findReferenceCycles(): Future[List[Cycle]] = {
    dao.findReferenceCycles()
  }
}

object GraphService {
  type Cycle = List[Paper]
  case class CyberleninkaPageData(authorName: String, paper: Paper, citations: Seq[Citation])

  case class Citation(author: String, title: String)

  case class Paper(title: String, journalName: String, researchField: String, year: Int, link: String)
}