package es.care.sf.scraper.controller

import scala.collection.JavaConversions._

import org.jsoup.Jsoup

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.Props
import akka.routing.SmallestMailboxRouter
import es.care.sf.scraper.main.Scraper._
import es.care.sf.scraper.utils.ParserUtil
import es.care.sf.scraper.worker.BusinessListWorker

object BusinessCollector {
  case object StartScraper
  case class AddRegion(link: Link)
  case class AddArea(link: Link, region: Link)  
  case class RemoveBusinessList(businessList: String)
  case class SaveBusinesses(businesses: List[Link], businessListLink: Link)
  case class AddBusinessList(businessList: Link)
}

class BusinessCollector(rootUrl: String) extends Actor with ActorLogging with ParserUtil {

  import BusinessCollector._
  import es.care.sf.scraper.worker.BusinessListWorker._

  val lists = context.actorOf(Props(new BusinessListWorker(self, rootUrl)).withRouter(SmallestMailboxRouter(2)), name = "BusinessList")

  var businessListUrls = List[String]()

  var businessUrls = Map[String, Set[String]]()

  def receive = {
    
    case StartScraper => {
    	val doc = Jsoup.connect(rootUrl).timeout(ConnectionTimeout).get()

    	val regions = doc.select("div#rootZones a[title~=.+]").map(extractLink).toList
    	
    	regions.foreach(println)
    	
    	regions.take(2).foreach {
    		region => self ! AddRegion(region)
    	}
      
    }
    case AddRegion(region) => {
      val doc = Jsoup.connect(rootUrl+"/"+region.url).timeout(ConnectionTimeout).get()
      

      val defaultAreaURL = doc.select("div#js-zone-changer form").attr("action").substring(1)
      val defaultAreaName = doc.select("div#view h1.gl-zone__title span").text()
      val defaultArea = Link(defaultAreaURL, defaultAreaName)

      val otherAreas = doc.select("div#luClosestZones a[href]").map(extractLink).toList

      val allAreas = defaultArea :: otherAreas

      println(s"*********Area: ${region}*********")
      allAreas.foreach(println)

      allAreas.take(2).foreach {
        area => self ! AddArea(area, region)
      }

    }

    case RemoveBusinessList(businessListUrl) => {
      businessListUrls = businessListUrls.filter(_ != businessListUrl)
      log.debug(s"businessListUrls size is ${businessListUrls.size}: After removing ${businessListUrl}")

      if (businessListUrls.isEmpty) {
        println(s"***********RESULTS START(${businessUrls.size})************")
        businessUrls.foreach{
          entry=> println(s"${entry._2.size} businesses were found for category ${entry._1}")
        }
        
        println(s"***********RESULTS END(${businessUrls.size})**************")
        context.system.shutdown()
      }

    }

    case SaveBusinesses(businesses, businessListLink) => {
      val regex = """(?:\w*/)*([^:]*)(?:/.*:.*)?""".r

      businessListLink.url match {
        case regex(gr) => {
          log.debug(s"Added ${businesses.size} businesses to category ${gr}")
          businessUrls= businessUrls.updatedWith(gr, Set.empty){
            businesses.map(_.name).toSet ++ _
          }
        }
      }

    }

    case AddArea(area, region) => {
      log.info(s"Added area ${area.name} in region $region")
      Thread.sleep(throttle)

      /*CATEGORIES*/
      val doc = Jsoup.connect(rootUrl + "/" + area.url).timeout(ConnectionTimeout).get()
      val categories = doc.select("ul#categoriesList li a[href]").map(extractLink)

      categories.take(2).foreach {
        category =>
          log.info(s"Added category ${category.name} in area ${area.name}")
          self ! AddBusinessList(category)
      }

    }

    case AddBusinessList(link) => {
      businessListUrls = link.url :: businessListUrls
      log.debug(s"businessListUrls size is ${businessListUrls.size}")
      lists ! StartBusinessListParser(link)
    }

  }
}