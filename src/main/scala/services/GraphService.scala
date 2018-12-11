package services

import main.Requests.{Author, PaperImportRequest, PaperSearchResult, Reference}
import com.twitter.util.Future
import dao.PaperGraphDao
import services.GraphService._

trait GraphService {
  def createAuthor(name: String): Future[Unit]

  def getPaper(title: String): Future[Paper]

  def createPaper(paper: Paper): Future[Unit]

  def createWroteRelation(authorName: String, paperTitle: String): Future[Unit]

  def searchPapers(researchField: String): Future[List[String]]

  def persistParsedPaper(data: CyberleninkaPageData): Future[Unit]

  def findReferenceCycles(): Future[List[Loop]]

  def getResearchFields(): Future[List[String]]

  def search(authorName: Option[String], paperTitle: Option[String], researchField: Option[String]): Future[Seq[PaperSearchResult]]

  def importGraph(data: PaperImportRequest): Future[Unit]
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

  def getPaper(paperTitle: String): Future[Paper] = {
    dao.getPaper(paperTitle)
  }

  def findReferenceCycles(): Future[List[Loop]] = {
    dao.findReferenceCycles()
  }

  def searchPapers(researchField: String): Future[List[String]] = {
    dao.searchPapers(researchField)
  }

  def getResearchFields(): Future[List[String]] = {
    dao.getResearchFields()
  }

  def search(authorName: Option[String], paperTitle: Option[String], researchField: Option[String]): Future[Seq[PaperSearchResult]] = {
    dao.search(authorName, paperTitle, researchField).map {
      _.groupBy(_.paper).map {
        case (p, resultList) =>
          PaperSearchResult(
            resultList.head.author,
            p.title,
            p.journalName,
            p.researchField,
            p.year,
            p.link,
            resultList.flatMap(_.reference)
          )
      }.toList
    }
  }

  def importGraph(data: PaperImportRequest): Future[Unit] = {
    val (as, ps, rs) =
    data.importPapers.foldLeft((Seq.empty[(Author, Paper)], Seq.empty[Paper], Seq.empty[Reference])) {
      case ((authors, papers, references), elem) =>
        val author = elem.author
        val paper = Paper(elem.title, elem.journalName, elem.researchField, elem.year, elem.link)
        val refs = elem.references
        (authors :+ ((author, paper)), papers :+ paper, references ++ refs)
    }
    dao.importGraph(as, ps, rs)
  }
}

object GraphService {
  type Loop = List[LoopEntity]
  case class CyberleninkaPageData(authorName: String, paper: Paper, citations: Seq[Citation])

  case class Citation(author: String, title: String)


  case class Paper(title: String, journalName: String, researchField: String, year: Int, link: String)


  case class LoopEntity(author_name: String, paper_title: String)
}