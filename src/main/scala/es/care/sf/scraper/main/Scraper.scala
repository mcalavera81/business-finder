package es.care.sf.scraper.main

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import com.typesafe.config.ConfigFactory
import akka.actor.ActorSystem
import akka.actor.Props
import es.care.sf.scraper.controller.BusinessCollector


object Scraper extends App {

  	import es.care.sf.scraper.controller.BusinessCollector._
  	
  	case class Link(url: String, name: String)
  
  	def extractLink(element: Element):Link={
  	  Link(element.attr("href").substring(1),element.text()) 
  	}
  
    val config = ConfigFactory.load()
    val rootUrl = config.getString("url")
    val system = ActorSystem("businesses-scraper-system")
    val businessCollector = system.actorOf(Props(new BusinessCollector(rootUrl)), "CollectorService")
    businessCollector ! StartScraper
  	
}


