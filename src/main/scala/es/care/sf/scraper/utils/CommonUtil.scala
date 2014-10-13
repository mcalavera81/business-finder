package es.care.sf.scraper.utils

import org.jsoup.select.Elements
import org.jsoup.nodes.Element
import akka.util.Timeout
import scala.concurrent.duration._


object CommonUtil{
	case class Link(url: String, name: String)
}


trait CommonUtil {
  import CommonUtil._
  
  val AskTimeout = 10 // minutes
  val ConnectionTimeout = 10000 // millis
  val throttleRate = 1 
  val retries = 3
  val HttpRequestSleep= 5000 //millis

  implicit val timeout = Timeout(AskTimeout day)

  implicit class MapExtensions[K, V](val map: Map[K, V]) {
    def updatedWith(key: K, default: V)(f: V => V) = {
      map.updated(key, f(map.getOrElse(key, default)))
    }
  }

  def extractLink(element: Element): Link = {
    Link(element.attr("href").substring(1), element.text())
  }

  implicit class JsoupElementsUtils(elements: Elements) {

    def getFirst(): Option[Element] = {
      if (elements.isEmpty()) None else Some(elements.first())
    }

    def map[B](f: Element => B): List[B] = {

      val a = elements.iterator()
      var list = List[B]()
      val x = while (a.hasNext()) {
        list = f(a.next()) :: list
      }
      list
    }

  }
}

