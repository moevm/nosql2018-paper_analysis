package main

  import com.twitter.finagle.http.Request
  import com.twitter.finagle.http.filter.Cors
  import com.twitter.finagle.http.filter.Cors.HttpFilter
  import com.twitter.finatra.http.response.ResponseBuilder
  import com.twitter.finatra.http.routing.HttpRouter
  import com.twitter.finatra.http.{Controller, HttpServer}
  import com.twitter.finatra.request.QueryParam
  import com.twitter.util.{Future, Return, Throw}
  import dao.{PaperGraphDao, PaperGraphDaoImpl}
  import main.Requests._
  import services.{GraphService, GraphServiceImpl}

object FinatraMain extends FinatraServer {
  val paperGraphDao: PaperGraphDao = new PaperGraphDaoImpl()
  val graphService: GraphService = new GraphServiceImpl(paperGraphDao)
}

class FinatraServer extends HttpServer {
  override def configureHttp(router: HttpRouter): Unit = {
    router
      .filter(new HttpFilter(Cors.UnsafePermissivePolicy))
      .add(new CorsController)
      .add(new MainController)
  }
}

class MainController extends Controller {
  import FinatraMain._

  implicit class EnrichResponse[T](future: Future[T]) {
    def toTextStatusResponse: Future[ResponseBuilder#EnrichedResponse] = {
      future
          .transform {
            case Return(_) => Future.value(response.ok("OK"))
            case Throw(e) => Future.value(response.ok(s"NOT OK: $e"))
          }
    }
  }

  get("/hello") {
    _: Request => "Hello, World!"
  }

  prefix("/graph") {
    post("/import") {
      request: PaperImportRequest =>
        graphService.importGraph(request)
    }
    get("/search") {
      request: SearchRequest =>
        graphService.search(
          request.authorName,
          request.paperTitle,
          request.researchField,
          request.journalName,
          request.year,
          request.isWithSelfCitation
        )
    }
    get("/find_reference_cycles") {
      req: FindCyclesRequest =>
        graphService
          .findReferenceCycles(req.isWithSelfCitation)
          .map { papersCycle => FindReferenceCyclesResponse(papersCycle) }
    }

    prefix("/research_fields") {
      get("/list") {
        _: Request =>
          graphService.getResearchFields()
      }
    }

    prefix("/authors") {
      get("/create") {
        request: CreateAuthorRequest =>
          graphService
            .createAuthor(request.name)
            .toTextStatusResponse
      }
    }

    prefix("/papers") {
      get("/get") {
        request: PaperGetRequest =>
          graphService.getPaper(request.title)
      }
      get("/create") {
        request: CreatePaperRequest =>
          graphService
            .createPaper(request.toPaper)
            .toTextStatusResponse
      }
    }

    prefix("/references") {
      get("/create") {
        request: CreateRelationRequest =>
          graphService
            .createWroteRelation(request.name, request.title)
            .toTextStatusResponse
      }
    }
  }
}

class CorsController extends Controller {
  prefix("/graph") {
    options("/import") {
      _: Request => response.ok
    }
  }
}

  object Requests {
    import GraphService._

    case class FindCyclesRequest(@QueryParam isWithSelfCitation: Boolean)

    case class CreateAuthorRequest(@QueryParam name: String)

    case class CreatePaperRequest(@QueryParam
                                  title: String,
                                  @QueryParam
                                  journalName: String,
                                  @QueryParam
                                  researchField: String,
                                  @QueryParam
                                  year: Int,
                                  @QueryParam
                                  link: String) {
      def toPaper: Paper = {
        Paper(title, journalName, researchField, year, link)
      }
    }

    case class CreateRelationRequest(@QueryParam name: String,
                                     @QueryParam title: String)

    case class PaperGetRequest(@QueryParam
                               title: String)

    case class SearchRequest(@QueryParam
                             authorName: Option[String],
                             @QueryParam
                             paperTitle: Option[String],
                             @QueryParam
                             researchField: Option[String],
                             @QueryParam
                             journalName: Option[String],
                             @QueryParam
                             year: Option[Int],
                             @QueryParam
                             isWithSelfCitation: Boolean)

    case class FindReferenceCyclesResponse(papers: List[List[LoopEntity]])

    case class Author(name: String)

    case class Reference(sourcePaperTitle: String, targetPaperTitle: String)

    case class PaperSearchResult(authors: Set[Author], title: String, journalName: String, researchField: String, year: Int, link: String, references: Set[Reference])

    case class PaperImportRequest(importPapers: Seq[PaperSearchResult])
  }