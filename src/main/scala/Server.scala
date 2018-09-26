import com.twitter.finagle.http.Request
import com.twitter.finatra.http.routing.HttpRouter
import com.twitter.finatra.http.{Controller, HttpServer}
import com.twitter.finatra.request.QueryParam
import com.twitter.util.Future
import dao.PaperGraphDao
import services.GraphFillService

object FinatraMain extends FinatraServer {
  case class CreateAuthorRequest(@QueryParam name: String)
  case class CreatePaperRequest(@QueryParam title: String)
  case class CreateRelationRequest(@QueryParam name: String,
                                   @QueryParam title: String)


  val paperGraphDao = new PaperGraphDao()
  val graphFillService = new GraphFillService(paperGraphDao)
}
class FinatraServer extends HttpServer {
  override def configureHttp(router: HttpRouter): Unit = {
    router.add(new MainController)
  }
}

class MainController extends Controller {
  import FinatraMain._

  get("/hello") { request: Request =>
    "Hello, World!"
  }

  prefix("/graph") {
    prefix("/authors") {
      get("/get") { request: Request =>
        response.ok("NOT IMPLEMENTED YET IM REALLY SORRY LOL")
      }
      get("/create") { request: CreateAuthorRequest =>
         graphFillService
           .createAuthor(request.name)
           .map(_ => response.ok("OK"))
           .rescue { case e => Future.value(response.ok(s"NOT OK: $e")) }
      }
    }

    prefix("/papers") {
      get("/create") { request: CreatePaperRequest =>
        graphFillService
          .createPaper(request.title)
          .map(_ => response.ok("OK"))
          .rescue { case e => Future.value(response.ok(s"NOT OK: $e")) }
      }
    }

    prefix("/relation") {
      get("/create") { request: CreateRelationRequest =>
        graphFillService
          .createWroteRelation(request.name, request.title)
          .map(_ => response.ok("OK"))
          .rescue { case e => Future.value(response.ok(s"NOT OK: $e")) }
      }
    }
  }
  prefix("/queries") {

  }
}