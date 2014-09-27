package es.care.sf.scraper.utils

import org.jsoup.select.Elements
import org.jsoup.nodes.Element
import akka.util.Timeout
import scala.concurrent.duration._

trait ParserUtil {
  val AskTimeout = 120 // sec
  val ConnectionTimeout = 10000 // millis
  val throttleRate = 4// msgs/Sec
  val retries = 3

  implicit val timeout = Timeout(AskTimeout second)
    
  implicit class MapExtensions[K, V](val map: Map[K, V]) {
    def updatedWith(key: K, default: V)(f: V => V) = {
      map.updated(key, f(map.getOrElse(key, default)))
    }
  }

  implicit class AAA(elements: Elements) {

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

