  import Requests._
  import com.twitter.finagle.http.Request
  import com.twitter.finatra.http.response.ResponseBuilder
  import com.twitter.finatra.http.routing.HttpRouter
  import com.twitter.finatra.http.{Controller, HttpServer}
  import com.twitter.finatra.request.QueryParam
  import com.twitter.util.{Future, Return, Throw}
  import dao.{PaperGraphDao, PaperGraphDaoImpl}
  import services.{GraphService, GraphServiceImpl}
  import services.GraphService._

object FinatraMain extends FinatraServer {
  val paperGraphDao: PaperGraphDao = new PaperGraphDaoImpl()
  val graphService: GraphService = new GraphServiceImpl(paperGraphDao)
}

class FinatraServer extends HttpServer {
  override def configureHttp(router: HttpRouter): Unit = {
    router.add(new MainController)
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

  get("/hello") { _: Request =>
    "Hello, World!"
  }

  prefix("/graph") {
    post("/import") { request: Request => ()

    }
    get("/search") { request: Request =>

    }
    get("/find_reference_cycles") { request: Request =>
      graphService
        .findReferenceCycles()
        .map { papersCycle =>
          FindReferenceCyclesResponse(papersCycle)
        }
    }
    get("/persist_parsed_paper") { request: PersistParsedPageRequest =>
      graphService
        .persistParsedPaper(request.toPageData)
        .toTextStatusResponse
    }

    prefix("/research_fields") {
      get("/list") { request: Request =>
        graphService
          .getResearchFields()
      }
    }

    prefix("/authors") {
      get("/create") { request: CreateAuthorRequest =>
        graphService
          .createAuthor(request.name)
          .toTextStatusResponse
      }
    }

    prefix("/papers") {
      get("/get") { request: PaperGetRequest =>
        graphService.getPaper(request.title)
      }
      get("/search") { request: PapersSearchRequest =>
        graphService.searchPapers(request.researchField)
      }
      get("/create") { request: CreatePaperRequest =>
        graphService
          .createPaper(request.toPaper)
          .toTextStatusResponse
      }
    }

    prefix("/references") {
      get("/get") { request: ReferencesGetRequest =>
        graphService.getReferences(request.authorName, request.paperTitle)
      }
      get("/create") { request: CreateRelationRequest =>
        graphService
          .createWroteRelation(request.name, request.title)
          .toTextStatusResponse
      }
    }
  }
}

  object Requests {
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

    case class PersistParsedPageRequest(@QueryParam
                                        authorName: String,
                                        @QueryParam
                                        paper: Paper,
                                        @QueryParam
                                        citations: Seq[Citation]) {

      def toPageData: CyberleninkaPageData = {
        CyberleninkaPageData(
          authorName = authorName,
          paper = paper,
          citations = citations
        )
      }
    }

    case class PaperGetRequest(@QueryParam
                               title: String)

    case class FindReferenceCyclesResponse(@QueryParam
                                           papers: List[List[LoopEntity]])

    case class PapersSearchRequest(@QueryParam researchField: String)

    case class ReferencesGetRequest(@QueryParam
                                    authorName: Option[String],
                                    @QueryParam
                                    paperTitle: Option[String])

  }