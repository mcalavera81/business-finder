package es.care.sf.scraper.utils


trait ParserUtil {
    val ConnectionTimeout = 10000 // millis
    val throttle = 6000 // millis
    
    implicit class MapExtensions[K, V](val map: Map[K, V]) {
        def updatedWith(key: K, default: V)(f: V => V) = {
            map.updated(key, f(map.getOrElse(key, default)))
        }
    }
}

