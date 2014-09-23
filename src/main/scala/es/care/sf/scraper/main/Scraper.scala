package es.care.sf.scraper.main


import com.typesafe.config.ConfigFactory
import collection.JavaConversions._
import collection.JavaConverters._
import akka.actor.ActorSystem
import com.typesafe.config.ConfigObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import akka.actor.Props
import es.care.sf.scraper.controller.BusinessCollector


object Scraper extends App {

  	import es.care.sf.scraper.controller.BusinessCollector._
  	
  	case class Link(url: String, name: String)
  
  	def extractLink(element: Element):Link={
  	  Link(element.attr("href").substring(1),element.text()) 
  	}
  
    val config = ConfigFactory.load()
    val system = ActorSystem("businesses-scraper-system")

    val rootUrl = config.getString("url")
    
    val businessCollector = system.actorOf(Props(new BusinessCollector(rootUrl)), "CollectorService")
    businessCollector ! StartScraper
      
    /*Left to do:
    	1) Pagination x
    	2) Add category to the business x
    	3) Add regions x
    	4) Throttle, implicit class
    	5) Output
    */
}


