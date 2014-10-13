package es.care.sf.scraper.controller

import java.io.File
import java.util.concurrent.TimeUnit
import scala.collection.JavaConversions._
import scala.concurrent.duration.Duration
import org.jsoup.Jsoup
import com.github.tototoshi.csv.CSVWriter
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Props
import akka.contrib.throttle.Throttler.Rate
import akka.contrib.throttle.Throttler.SetTarget
import akka.contrib.throttle.TimerBasedThrottler
import akka.pattern.ask
import akka.pattern.pipe
import akka.routing.SmallestMailboxRouter
import akka.util.Timeout
import es.care.sf.scraper.main.Scraper._
import es.care.sf.scraper.utils.CommonUtil
import es.care.sf.scraper.worker.BusinessListWorker
import es.care.sf.scraper.worker.BusinessWorker._
import es.care.sf.scraper.worker.HttpRequestWorker._
import es.care.sf.scraper.worker.HttpRequestWorker
import scala.concurrent.duration._
import org.jsoup.nodes.Document
import scala.concurrent.ExecutionContext.Implicits.global
import es.care.sf.scraper.utils.CommonUtil._

object BusinessCollector {
  case class StartScraper
  case class AddRegion(link: Link)
  case class AddArea(link: Link, region: Link)
  case class RemoveBusinessList(businessList: String)
  case class SaveBusinesses(businesses: List[Link], businessListLink: Link)
  case class AddBusinessList(businessList: Link)
  case class BusinessesResults(results: List[BusinessResult], businessListUrl: String)
}

class BusinessCollector(rootUrl: String) extends Actor with ActorLogging with CommonUtil {

  import BusinessCollector._
  import es.care.sf.scraper.worker.BusinessListWorker._

  var lists = context.system.deadLetters

  var businessListUrls = List[String]()

  var businessUrls = Map[String, Set[BusinessResult]]()

  var throttler: ActorRef = context.system.deadLetters

  override def preStart(): Unit = {

    val httpReqWorker = system.actorOf(Props(new HttpRequestWorker(rootUrl)), "httpRequestWorker")

    throttler = system.actorOf(Props(new TimerBasedThrottler(new Rate(throttleRate, Duration(500, TimeUnit.MILLISECONDS)))), "throttler")

    throttler ! SetTarget(Some(httpReqWorker))

    lists = context.actorOf(Props(new BusinessListWorker(self, rootUrl)).withRouter(SmallestMailboxRouter(3)), name = "BusinessList")
  }

  def receive = {

    case StartScraper => {

      (throttler ? GetDocument("")).mapTo[Document].onSuccess {
        case doc => {
          val regions = doc.select("div#rootZones a[title~=.+]").map(extractLink).toList

          regions.foreach(println)

          regions.find(_.url == "regionmetropolitana") foreach {
            region => self ! AddRegion(region)
          }
        }
      }

    }

    case AddRegion(region) => {

      (throttler ? GetDocument(region.url)).mapTo[Document].onSuccess {
        case doc => {
          val allAreas = doc.select("div#closestZones a[href]").map(extractLink).toList

          println(s"*********Area: ${region}*********")
          allAreas.foreach(println)

          allAreas.foreach {
            area => self ! AddArea(area, region)
          }

        }
      }

    }

    case RemoveBusinessList(businessListUrl) => {
      businessListUrls = businessListUrls.filter(_ != businessListUrl)
      log.debug(s"businessListUrls size is ${businessListUrls.size}: After removing ${businessListUrl}")

      if (businessListUrls.isEmpty) {
        println(s"***********RESULTS START(${businessUrls.size})************")

        val file = new File(s"aaa.csv")
        if (file.exists()) file.delete()
        file.createNewFile()
        val writer = CSVWriter.open(file)

        implicit class OptionalListToString(optList: Option[List[String]]) {

          def toStr() = {
            optList match { case None => ""; case Some(list) => list.mkString("|") }
          }
        }

        businessUrls.keySet.foreach {
          catId =>
            businessUrls(catId).foreach(business => writer.writeRow(List(
              catId,
              business.name,
              business.url,
              business.categorias.toStr,
              business.rubros.toStr,
              business.productos.toStr,
              business.marcas.toStr)))
        }

        println(s"***********RESULTS END(${businessUrls.size})**************")
        context.system.shutdown()
      }

    }

    case BusinessesResults(businesses, businessListUrl) => {
      val regex = """(?:\w*/)*([^:]*)(?:/.*:.*)?""".r

      businessListUrl match {
        case regex(gr) => {
          log.debug(s"Added ${businesses.size} businesses to category ${gr}")
          businessUrls = businessUrls.updatedWith(gr, Set.empty) {
            businesses.toSet ++ _
          }
        }
      }

    }

    case AddArea(area, region) => {
      log.info(s"Added area ${area.name} in region $region")

      (throttler ? GetDocument(area.url)).mapTo[Document].onSuccess {
        case doc => {
          val categories = doc.select("ul#categoriesList li a[href]").map(extractLink)

          categories.foreach {
            category =>
              {
                log.info(s"Added category ${category.name} in area ${area.name}")
                self ! AddBusinessList(category)
              }
          }

        }
      }

    }

    case AddBusinessList(link) => {
      businessListUrls = link.url :: businessListUrls
      log.debug(s"businessListUrls size is ${businessListUrls.size}")
      lists ! StartBusinessListParser(link)
    }

  }
}