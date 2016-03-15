package services

import javax.inject._
import play.api.libs.ws._
import play.api.libs.json._
import play.api.Logger
import models._
import scala.util.{Success, Failure}
import scala.concurrent.{Future, Promise}
import scala.concurrent.ExecutionContext.Implicits.global

/**This class has a `Singleton` annotation because we need to make
 * sure we only use one counter per application. Without this
 * annotation we would get a new instance every time a [[Counter]] is
 * injected.
 */
@Singleton
class FiwareBackend @Inject() (ws: WSClient) extends Backend {  
   override def Post(request: PostRequest): BoardAttributes = {
     val attr = BoardAttributes("","","","")
     val data = Json.parse("""
      {
          "entities": [
              {
                  "type": "Room",
                  "isPattern": "true",
                  "id": "Roo."
              }
          ]
      }
      """)
     Logger.info(s"POST data:\n$data\n")
     val futureResponse: Future[WSResponse] = ws.url("http://localhost:1026/v1/queryContext")
     .withHeaders("Content-Type" -> "application/json",
                 "Accept" -> "application/json")
     .post(data)
     futureResponse onComplete {
       case Success(response) => Logger.info(s"Future success:\n${response.json}\n")
       case Failure(e) => Logger.info(s"Future failure:\n$e\n")
     }
     attr
   }
}
