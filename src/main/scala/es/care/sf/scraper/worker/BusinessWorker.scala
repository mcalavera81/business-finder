package es.care.sf.scraper.worker

import akka.actor.ActorLogging
import akka.actor.Actor
import akka.actor.ActorRef
import es.care.sf.scraper.utils.CommonUtil
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Success
import scala.util.Failure
import org.jsoup.Jsoup
import akka.pattern.ask
import es.care.sf.scraper.worker.HttpRequestWorker._
import akka.util.Timeout
import scala.concurrent.duration._
import org.jsoup.nodes.Document
import scala.util.Try
import akka.pattern.AskTimeoutException

object BusinessWorker extends CommonUtil {
  case class StartBusinessWorker(businessListUrl: String, businessUrl: String, retries: Int = retries)
  case class BusinessResult(
    url: String,
    name: String,
    categorias: Option[List[String]],
    rubros: Option[List[String]],
    marcas: Option[List[String]],
    productos: Option[List[String]])
}

class BusinessWorker(businessListWorker: ActorRef, rootUrl: String) extends Actor with ActorLogging with CommonUtil {

  import BusinessWorker._
  import es.care.sf.scraper.worker.BusinessListWorker._

  var throttler: ActorRef = context.system.deadLetters

  override def preStart(): Unit = {
    throttler = context.system.actorFor("/user/throttler")
  }

  def receive = {

    /*
    	 * For each page to be processed this case is invoked.
    	 * Whether the parsing is successful or not the page url is removed from the map pageUrls of the parent actor.
    	 * In case the parsing is a success we save the result in the pageResults map of the parent actor.
    	 */
    case StartBusinessWorker(listUrl, businessUrl, retriesLeft) => {

      val future = parseBusiness(businessUrl) //.mapTo[Option[BusinessResult]]

      future onComplete {
        case Success(result) => {
          businessListWorker ! SaveBusinessResult(listUrl, result)
          businessListWorker ! RemoveBusinessUrl(listUrl, businessUrl)
        }
        case Failure(e) => {
          e match {
            case askTimeout: AskTimeoutException if retriesLeft > 0 => {
              log.warning(s"Retrying on page ${businessUrl} wth ${retriesLeft} retries left.")
              context.system.scheduler.scheduleOnce(10*(retries+1-retriesLeft) second, self, StartBusinessWorker(listUrl, businessUrl, retriesLeft - 1))
            }
            case _ => {
              log.warning(s"Can't process page ${businessUrl}, cause: ${e.getMessage}")
              businessListWorker ! RemoveBusinessUrl(listUrl, businessUrl)
            }
          }

        }
      }
    }
  }

  def parseBusiness(businessUrl: String): Future[Option[BusinessResult]] = {

    (throttler ? GetDocument(businessUrl)).mapTo[Document].map {
      doc =>
        {
          Try {
            val title = doc.select("div#view  h1.gl-wrapper__title").first().text()

            val selectors = Map(
              "categories" -> "div#view  h4:contains(Categor)",
              "rubros" -> "div#view  h4:contains(Rubros)",
              "marcas" -> "div#view  h4:contains(Marcas)",
              "productos" -> "div#view  h4:contains(Productos)")

            val results = selectors.map(selector => selector._1 -> doc.select(selector._2).getFirst.map(_.siblingElements().map(_.text())))
            Some(BusinessResult(businessUrl, title, results("categories"), results("rubros"), results("marcas"), results("productos")))
          }.getOrElse(None)

        }
    }

  }

}