package services

import java.util.concurrent.atomic.AtomicLong
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
class FiwareBackend @Inject() (ws: WSClient) extends Backend with PostWrites {  
  
  private val index = new AtomicLong(0)
  
  private def fiwarePost(post: models.Post): JsValue = {
    Json.obj(
        "contextElements" -> Json.arr(Json.obj(
            "type" -> "Post",
            "isPattern" -> "false",
            "id" -> s"${post.board_attributes.index}",
            "attributes" -> Json.arr(Json.obj(
                "name" -> "post",
                "type" -> "Post",
                "value" -> Json.toJson(post)
            ))
        )),
        "updateAction" -> "APPEND"
    )
  }
  
   override def Post(request: PostRequest): Future[BoardAttributes] = {
     val promise: Promise[BoardAttributes] = Promise[BoardAttributes]()
     val post = models.Post(request.message, 
                           request.user_attributes, 
                           BoardAttributes(s"${index.getAndIncrement()}",s"${System.currentTimeMillis()}","",""))
     val data = fiwarePost(post)
     Logger.info(s"POST data:\n$data\n")
     val futureResponse: Future[WSResponse] = ws.url("http://localhost:1026/v1/updateContext")
     .withHeaders("Content-Type" -> "application/json",
                 "Accept" -> "application/json",
                 "Fiware-ServicePath" -> s"/${post.user_attributes.section}/${post.user_attributes.group}")
     .post(data)
     futureResponse onComplete {
       case Success(response) => Logger.info(s"Future success:\n${response.json}\n")
                                  promise.success(post.board_attributes)
       case Failure(e) => Logger.info(s"Future failure:\n$e\n")
                         promise.failure(e)
     }
     
     promise.future
   }
   
   override def Get(request: models.Post): Seq[Post] = {
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
     Logger.info(s"GET data:\n$data\n")
     val futureResponse: Future[WSResponse] = ws.url("http://localhost:1026/v1/queryContext")
     .withHeaders("Content-Type" -> "application/json",
                 "Accept" -> "application/json",
                 "Fiware-ServicePath" -> "/#")
     .post(data)
     futureResponse onComplete {
       case Success(response) => Logger.info(s"Future success:\n${response.json}\n")
       case Failure(e) => Logger.info(s"Future failure:\n$e\n")
     }
     Seq(request)
   }
}
