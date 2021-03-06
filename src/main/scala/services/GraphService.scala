package services

import main.Requests.{Author, PaperImportRequest, PaperSearchResult, Reference}
import com.twitter.util.Future
import dao.PaperGraphDao
import services.GraphService._

trait GraphService {
  def createAuthor(name: String): Future[Unit]

  def createPaper(paper: Paper): Future[Unit]

  def createWroteRelation(authorName: String, paperTitle: String): Future[Unit]

  def getPaper(title: String): Future[Paper]

  def getResearchFields(): Future[List[String]]

  def search(authorName: Option[String], paperTitle: Option[String], researchField: Option[String], journalName: Option[String], year: Option[Int], isWithSelfCitation: Boolean): Future[Seq[PaperSearchResult]]

  def importGraph(data: PaperImportRequest): Future[Unit]

  def findReferenceCycles(isWithSelfCitation: Boolean): Future[List[Loop]]
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

  def getPaper(paperTitle: String): Future[Paper] = {
    dao.getPaper(paperTitle)
  }

  def findReferenceCycles(isWithSelfCitation: Boolean): Future[List[Loop]] = {
    dao.findReferenceCycles().map {
      cycles =>
      if (isWithSelfCitation) {
        cycles
      } else {
        cycles.filter(!_.sliding(2).exists(x => x.head.author_name == x.last.author_name))
      }
    }
  }

  def getResearchFields(): Future[List[String]] = {
    dao.getResearchFields()
  }

  def search(authorName: Option[String], paperTitle: Option[String], researchField: Option[String], journalName: Option[String], year: Option[Int], isWithSelfCitation: Boolean): Future[Seq[PaperSearchResult]] = {
    dao
      .search(authorName, paperTitle, researchField, journalName, year, isWithSelfCitation)
      .map {
      _.groupBy(_.paper).map {
        case (p, resultList) =>
          PaperSearchResult(
            resultList.map(_.author).toSet,
            p.title,
            p.journalName,
            p.researchField,
            p.year,
            p.link,
            resultList.flatMap(_.reference).toSet
          )
      }.toList
    }
  }

  def importGraph(data: PaperImportRequest): Future[Unit] = {
    val (as, ps, rs) =
    data.importPapers.foldLeft((Seq.empty[(Author, Paper)], Seq.empty[Paper], Seq.empty[Reference])) {
      case ((authors, papers, references), elem) =>
        val newAuthors = elem.authors
        val paper = Paper(elem.title, elem.journalName, elem.researchField, elem.year, elem.link)
        val refs = elem.references
        (authors ++ newAuthors.map((_, paper)), papers :+ paper, references ++ refs)
    }
    dao.importGraph(as, ps, rs)
  }
}

object GraphService {
  type Loop = List[LoopEntity]

  case class LoopEntity(author_name: String, paper_title: String)

  case class Citation(author: String, title: String)

  case class Paper(title: String, journalName: String, researchField: String, year: Int, link: String)
}