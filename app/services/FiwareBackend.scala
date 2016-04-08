package services

import java.util.concurrent.atomic.AtomicLong
import javax.inject._
import play.api.libs.ws._
import play.api.libs.json._
import play.api.libs.json.Reads._
import play.api.libs.functional.syntax._
import play.api.Logger
import models._
import scala.util.{Success, Failure}
import scala.concurrent.{Future, Promise}
import scala.concurrent.ExecutionContext.Implicits.global

/** This class implements the BoardBackend and connects to the Fiware-orion
 * context-broker backend.
 * 
 * This class has a `Singleton` annotation because we need to make
 * sure we only use one counter per application. Without this
 * annotation we would get a new instance every time a [[FiwareBackend]] is
 * injected.
 */
@Singleton
class FiwareBackend @Inject() 
                     (ws: WSClient) 
                     extends BoardBackend 
                     with PostWriteValidator 
                     with PostReadValidator 
                     with FiwareQueryReadValidator 
{  
  // Atomic post index counter
  private val index = new AtomicLong(0)
  
  // Parse a `Post` into a JSON query that Fiware understands
  private def fiwarePostQuery(post: models.Post): JsValue = {
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
  
  /**
   * Interpret the answer to a Post message sent to Fiware-Orion.
   * If successful, it will resolve the promise with the BoardAttributes
   */
  private def fiwareParsePostAnswer(response: WSResponse, promise: Promise[BoardAttributes], post: models.Post) {
    response.json.validate[SuccessfulGetPost] match {
      case s: JsSuccess[SuccessfulGetPost] => Logger.info(s"Future success:\n${response.json}\n")
                                              promise.success(post.board_attributes)
      case e: JsError => Logger.info(s"Future failure:\n${response.json}\n")
                         promise.failure(new Error(s"${response.json}"))
    }
  }
  /**
   * Implements the `Post` operation. Send the Post to the Fiware backend and interpret the result
   */
   override def Post(request: PostRequest): Future[BoardAttributes] = {
     val promise: Promise[BoardAttributes] = Promise[BoardAttributes]()
     // fill in the Post object
     val post = models.Post(request.message, 
                           request.user_attributes, 
                           // add board attributes, including index and timestamp
                           BoardAttributes(s"${index.getAndIncrement()}",s"${System.currentTimeMillis()}","",""))
     val data = fiwarePostQuery(post)
     Logger.info(s"POST data:\n$data\n")
     // send HTTP POST message to Fiware-Orion backend
     val futureResponse: Future[WSResponse] = ws.url("http://localhost:1026/v1/updateContext")
     .withHeaders("Content-Type" -> "application/json",
                 "Accept" -> "application/json",
                 "Fiware-ServicePath" -> s"/${post.user_attributes.section}/${post.user_attributes.group}")
     .post(data)
     // Interpret HTTP POST answer
     futureResponse onComplete {
       case Success(response) => // this resolves the promise with either success or failure
                                  fiwareParsePostAnswer(response, promise, post) 
       case Failure(e) => Logger.info(s"Future failure:\n$e\n")
                         promise.failure(e)
     }
     promise.future
   }
   
  // Parse a `Get` into a JSON query that Fiware understands
   private def fiwareGetQuery(post: models.GetRequest): JsValue = {
     Json.obj(
         "entities" -> Json.arr(Json.obj(
             "type" -> "Post",
             "isPattern" -> "false",
             "id" -> s"${post.index}"
         ))
     )
   }
   
  /**
   * Interpret the answer to a Get message sent to Fiware-Orion.
   * If successful, it will resolve the promise with the list of Post messages
   */
   private def fiwareParseGetAnswer(response: WSResponse, promise: Promise[Seq[Post]]) {
      response.json.validate[SuccessfulGetPost] match {
        case s: JsSuccess[SuccessfulGetPost] => var hasMapError = false
                                                // Map attribute.value to Post
                                                val jsOpt: Seq[Option[Post]] = s.get.contextResponses.map(
                                                    _.contextElement.attributes(0).value.validate[Post] match {
                                                        case sp: JsSuccess[Post] => Some(sp.get)
                                                        case e: JsError => hasMapError = true
                                                                           None
                                                })
                                                // If there was any error, resolve the promise with a failure
                                                if(hasMapError) {
                                                  Logger.info(s"Future failure:\n${response.json}\n")
                                                  promise.failure(new Error(s"${response.json}"))
                                                } else {
                                                  // Otherwise return the Get results as a list of Post messages
                                                  Logger.info(s"Future success:\n${response.json}\n")
                                                  promise.success(jsOpt.map(_.get))
                                                }
        // The Fiware-Orion backend returned an error message
        case e: JsError => Logger.info(s"Future failure:\n${response.json}\n")
                           promise.failure(new Error(s"${response.json}"))
      }
   }
   
  /**
   * Implements the `Get` operation. Send the GetRequest to the Fiware backend and interpret the result
   */
   override def Get(post: models.GetRequest): Future[Seq[Post]] = {
     val promise: Promise[Seq[Post]] = Promise[Seq[Post]]()
     // fill in the Get query
     val data = fiwareGetQuery(post)
     Logger.info(s"GET data:\n$data\n")
     // send HTTP POST message to Fiware-Orion backend
     val futureResponse: Future[WSResponse] = ws.url("http://localhost:1026/v1/queryContext")
     .withHeaders("Content-Type" -> "application/json",
                 "Accept" -> "application/json",
                 "Fiware-ServicePath" -> s"/${post.section}/${post.group}")
     .post(data)
     // Interpret HTTP POST answer
     futureResponse onComplete {
       case Success(response) => fiwareParseGetAnswer(response, promise)
       case Failure(e) => Logger.info(s"Future failure:\n$e\n")
                         promise.failure(e)
     }     
     promise.future
   }
}
