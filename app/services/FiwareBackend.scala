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

/**This class has a `Singleton` annotation because we need to make
 * sure we only use one counter per application. Without this
 * annotation we would get a new instance every time a [[FiwareBackend]] is
 * injected.
 */
@Singleton
class FiwareBackend @Inject() 
                     (ws: WSClient) 
                     extends Backend 
                     with PostWriteValidator 
                     with PostReadValidator 
                     with FiwareQueryReadValidator 
{  
  
  private val index = new AtomicLong(0)
  
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
  
  private def fiwareParsePostAnswer(response: WSResponse, promise: Promise[BoardAttributes], post: models.Post) {
    response.json.validate[SuccessfulGetPost] match {
      case s: JsSuccess[SuccessfulGetPost] => Logger.info(s"Future success:\n${response.json}\n")
                                              promise.success(post.board_attributes)
      case e: JsError => Logger.info(s"Future failure:\n${response.json}\n")
                         promise.failure(new Exception(s"${response.json}"))
    }
  }
  
   override def Post(request: PostRequest): Future[BoardAttributes] = {
     val promise: Promise[BoardAttributes] = Promise[BoardAttributes]()
     val post = models.Post(request.message, 
                           request.user_attributes, 
                           BoardAttributes(s"${index.getAndIncrement()}",s"${System.currentTimeMillis()}","",""))
     val data = fiwarePostQuery(post)
     Logger.info(s"POST data:\n$data\n")
     val futureResponse: Future[WSResponse] = ws.url("http://localhost:1026/v1/updateContext")
     .withHeaders("Content-Type" -> "application/json",
                 "Accept" -> "application/json",
                 "Fiware-ServicePath" -> s"/${post.user_attributes.section}/${post.user_attributes.group}")
     .post(data)
     futureResponse onComplete {
       case Success(response) => fiwareParsePostAnswer(response, promise, post)
       case Failure(e) => Logger.info(s"Future failure:\n$e\n")
                         promise.failure(e)
     }
     promise.future
   }
   
   private def fiwareGetQuery(post: models.Post): JsValue = {
     Json.obj(
         "entities" -> Json.arr(Json.obj(
             "type" -> "Post",
             "isPattern" -> "false",
             "id" -> s"${post.board_attributes.index}"
         ))
     )
   }
   
   private def fiwareParseGetAnswer(response: WSResponse, promise: Promise[Seq[Post]]) {
    response.json.validate[SuccessfulGetPost] match {
      case s: JsSuccess[SuccessfulGetPost] => var hasMapError = false
                                              val jsOpt: Seq[Option[Post]] = s.get.contextResponses.map(
                                                  _.contextElement.attributes(0).value.validate[Post] match {
                                                      case sp: JsSuccess[Post] => Some(sp.get)
                                                      case e: JsError => hasMapError = true
                                                                         None
                                              })
                                              if(hasMapError) {
                                                Logger.info(s"0Future failure:\n${response.json}\n")
                                                promise.failure(new Exception(s"$response.json"))
                                              } else {
                                                Logger.info(s"1Future success:\n${response.json}\n")
                                                promise.success(jsOpt.map(_.get))
                                              }
      case e: JsError => val exception = new Error(s"${response.json}")
                         Logger.info(s"2Future failure:\n${exception}\n")
                         promise.failure(exception)
    }
   }
   
   override def Get(post: models.Post): Future[Seq[Post]] = {
     val promise: Promise[Seq[Post]] = Promise[Seq[Post]]()
     val data = fiwareGetQuery(post)
     Logger.info(s"GET data:\n$data\n")
     val futureResponse: Future[WSResponse] = ws.url("http://localhost:1026/v1/queryContext")
     .withHeaders("Content-Type" -> "application/json",
                 "Accept" -> "application/json",
                 "Fiware-ServicePath" -> s"/${post.user_attributes.section}/${post.user_attributes.group}")
     .post(data)
     futureResponse onComplete {
       case Success(response) => fiwareParseGetAnswer(response, promise)
       case Failure(e) => Logger.info(s"3Future failure:\n$e\n")
                         promise.failure(e)
     }     
     promise.future
   }
}
