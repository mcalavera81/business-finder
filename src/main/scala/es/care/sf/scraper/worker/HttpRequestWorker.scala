package es.care.sf.scraper.worker

import akka.actor.ActorLogging
import akka.actor.Actor
import es.care.sf.scraper.utils.CommonUtil
import org.jsoup.Jsoup
import scala.util.Try
import scala.util.Success
import scala.util.Failure

object HttpRequestWorker{
  case class GetDocument(url: String)
}

class HttpRequestWorker(rootUrl: String) extends Actor with ActorLogging with CommonUtil {

  import HttpRequestWorker._
  
  def receive={
    
    case GetDocument(url) =>{
    	log.debug(s"Handling request to ${rootUrl}/${url}")
    	val wrapper=Try{
    	  Jsoup.connect(rootUrl + "/" + url).timeout(ConnectionTimeout).get()
    	}
    	
    	wrapper match {
    		case  	Success(doc)=>sender ! doc
    		case    Failure(e) => {
    		  log.warning(s"Error Handling request ${url}")
    		  Thread.sleep(HttpRequestSleep)
    		  self ! GetDocument(url)
    		}
    	}
    	
    }  
  }
  
}