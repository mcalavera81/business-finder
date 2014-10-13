package es.care.sf.scraper.worker

import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try
import org.jsoup.nodes.Document
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Props
import akka.pattern.AskTimeoutException
import akka.pattern.ask
import akka.pattern.pipe
import akka.routing.SmallestMailboxRouter
import akka.util.Timeout
import es.care.sf.scraper.main.Scraper._
import es.care.sf.scraper.utils.CommonUtil
import es.care.sf.scraper.worker.BusinessWorker._
import es.care.sf.scraper.worker.HttpRequestWorker._
import es.care.sf.scraper.utils.CommonUtil._
import scala.util.Success
import scala.util.Failure
import scala.concurrent.duration._

object BusinessListWorker extends CommonUtil {

  case class BusinessListResult(listUrl: Link, businesses: List[Link], nextPage: Option[Link] = None)
  case class StartBusinessListParser(category: Link, retries: Int = retries)
  case class AddBusinessUrl(listUrl: String, url: String)
  case class RemoveBusinessUrl(listUrl: String, url: String)
  case class SaveBusinessResult(listUrl: String, result: Option[BusinessResult])

}
class BusinessListWorker(businessCollector: ActorRef, rootUrl: String) extends Actor with ActorLogging with CommonUtil {

  import es.care.sf.scraper.controller.BusinessCollector._
  import BusinessListWorker._

  val pages = context.actorOf(Props(new BusinessWorker(self, rootUrl)).withRouter(SmallestMailboxRouter(2)), name = "Business")

  var pageUrls = Map[String, List[String]]()

  var businessResults = Map[String, List[Option[BusinessResult]]]()

  var throttler: ActorRef = context.system.deadLetters

  override def preStart(): Unit = {
    throttler = system.actorFor("/user/throttler")
  }

  def receive = {
    case StartBusinessListParser(businessList, retriesLeft) => {

      val future = parseBusinessList(businessList)

      future onComplete {
        case Success(result) => self ! result
        case Failure(e) => e match {
          case askTimeout: AskTimeoutException if retriesLeft > 0 => {
            log.warning(s"Retrying on business list ${businessList} with ${retriesLeft} retries left.")
            context.system.scheduler.scheduleOnce(10 * (retries + 1 - retriesLeft) second, self, StartBusinessListParser(businessList, retriesLeft - 1))
          }   
          case _ => {
            log.warning(s"Can't process business list ${businessList}, cause: ${e.getMessage}")
            businessCollector ! RemoveBusinessList(businessList.url)
          }

        }
      }

    }

    case SaveBusinessResult(businessListUrl, businessResult) => {
      businessResults = businessResults.updatedWith(businessListUrl, List.empty) { businessResult :: _ }
    }

    case RemoveBusinessUrl(businessListUrl, businessUrl) => {
      pageUrls = pageUrls.updatedWith(businessListUrl, List.empty) { urls =>
        val updatedUrls = urls.filter(_ != businessUrl)

        if (updatedUrls.isEmpty) {
          businessResults.get(businessListUrl).map { results =>
            businessCollector ! BusinessesResults(results.flatten.toList, businessListUrl)
            businessCollector ! RemoveBusinessList(businessListUrl)
          }

        }

        updatedUrls
      }
      
    }
    case AddBusinessUrl(listUrl, url) => {
      pageUrls = pageUrls.updatedWith(listUrl, List.empty) { url :: _ }
      pages ! StartBusinessWorker(listUrl, url)
    }

    case BusinessListResult(businessListLink, businesses, Some(nextPage)) => {
      
      businessCollector ! AddBusinessList(nextPage)
      self ! BusinessListResult(businessListLink, businesses)
    }

    case BusinessListResult(businessListLink, businesses, None) => {

      if (businesses.isEmpty) businessCollector ! RemoveBusinessList(businessListLink.url)

      businesses foreach { business =>
        self ! AddBusinessUrl(businessListLink.url, business.url)
      }

    }

    case _ =>
  }

  def parseBusinessList(businessListLink: Link): Future[BusinessListResult] = {

    val Link(businessListUrl, _) = businessListLink

    val emptyResult = BusinessListResult(businessListLink, List.empty, None)

    (throttler ? GetDocument(businessListUrl)).mapTo[Document].map {
      doc =>
        {

          val result = Try {
            val links = doc.select("ul li.clearfix:not(.featured) div.info-title a[href]").map(extractLink)

            if (links.isEmpty) {
              emptyResult
            } else {
              val nextPageSelector = doc.select("div.paging.mtm.right > span.next > a")

              if (!nextPageSelector.isEmpty()) {
                BusinessListResult(businessListLink, links.toList, Some(extractLink(nextPageSelector.first())))
              } else {
                BusinessListResult(businessListLink, links.toList, None)
              }
            }

          }
          result.getOrElse(emptyResult)
        }

    }
  }

}