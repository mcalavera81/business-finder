package es.care.sf.scraper.worker

import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import akka.pattern.pipe

import org.jsoup.Jsoup

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import es.care.sf.scraper.main.Scraper._
import es.care.sf.scraper.utils.ParserUtil

object BusinessListWorker {

  case class BusinessListResult(listUrl: Link, businesses: List[Link], nextPage: Option[Link] = None)
  case class StartBusinessListParser(category: Link)
  
}
class BusinessListWorker(businessCollector: ActorRef, rootUrl: String) extends Actor with ActorLogging with ParserUtil {

  import es.care.sf.scraper.controller.BusinessCollector._
  import BusinessListWorker._

  
  def receive = {
    case StartBusinessListParser(category) => {

      val future = parseBusinessList(category)

      future onFailure {
        case e: Exception => {
          log.warning(s"Can't process ${category.url}, cause: ${e.getMessage}")
          businessCollector ! RemoveBusinessList(category.url)
        }
      }

      future pipeTo self

    }
    case BusinessListResult(businessListLink, businesses, Some(nextPage)) =>{
    	log.debug(s"Adding a new page: ${nextPage}")
    	businessCollector ! AddBusinessList(nextPage)
    	self ! BusinessListResult(businessListLink, businesses)
    }
    
    case BusinessListResult(businessListLink, businesses, None) => {
      log.debug(s"Adding new businesses: ${businesses}")
      businessCollector ! SaveBusinesses(businesses, businessListLink)
      businessCollector ! RemoveBusinessList(businessListLink.url)
       
    }

    case _ =>
  }

  def parseBusinessList(businessListLink: Link): Future[BusinessListResult] = Future {
    
    val Link(businessListUrl,_)= businessListLink

    Thread.sleep(throttle)
    val emptyResult = BusinessListResult(businessListLink, List.empty, None)

    try {

      val doc = Jsoup.connect(rootUrl + "/" + businessListUrl).timeout(ConnectionTimeout).get()

      val links = doc.select("ul li.clearfix:not(.featured) div.info-title a[href]").map(extractLink)

      
      if (links.isEmpty){
        emptyResult 
      }else{
        val nextPageSelector = doc.select("div.paging.mtm.right > span.next > a")
        
        if(!nextPageSelector.isEmpty()){
        	BusinessListResult(businessListLink, links.toList, Some(extractLink(nextPageSelector.first())))
        }else{
        	BusinessListResult(businessListLink, links.toList, None)  
        }
        
      } 
      

    } catch {
      case e: Exception => {
        log.warning(s"Can't parse list page: ${e.getMessage} ${rootUrl}/${businessListUrl}")
        emptyResult
      }
    }
  }

}