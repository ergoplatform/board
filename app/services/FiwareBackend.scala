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
 * annotation we would get a new instance every time a [[Counter]] is
 * injected.
 */

case class GetAttribute(name: String, _type: String, value: JsValue)
case class GetContextElement(id: String, isPattern: String, _type: String, attributes: Seq[GetAttribute])
case class GetStatusCode(code: String, reasonPhrase: String)
case class GetContextResponse(contextElement: GetContextElement, statusCode: GetStatusCode)
case class SuccessfulGetPost(contextResponses: Seq[GetContextResponse])

case class GetErrorCode(code: String, reasonPhrase: String)
case class FailedGetPost(errorCode: GetErrorCode)

trait FiwareGetValidator {
  
  // Get success validators
  
  implicit val validateGetAttribute: Reads[GetAttribute] = (
      (JsPath \ "name").read[String] and
      (JsPath \ "type").read[String] and
      (JsPath \ "value").read[JsValue]
  )(GetAttribute.apply _)
  
  implicit val validateGetContextElement: Reads[GetContextElement] = (
      (JsPath \ "id").read[String] and
      (JsPath \ "isPattern").read[String] and
      (JsPath \ "type").read[String] and
      (JsPath \ "attributes").read[Seq[GetAttribute]]
  )(GetContextElement.apply _)
  
  implicit val validateGetStatusCode: Reads[GetStatusCode] = (
      (JsPath \ "code").read[String] and
      (JsPath \ "reasonPhrase").read[String]
  )(GetStatusCode.apply _)
  
  implicit val validateGetContextResponse: Reads[GetContextResponse] = (
      (JsPath \ "contextElement").read[GetContextElement] and
      (JsPath \ "statusCode").read[GetStatusCode]
  )(GetContextResponse.apply _)
  
  implicit val validateSuccessfulGetPost: Reads[SuccessfulGetPost] = 
      (JsPath \ "contextResponses").read[Seq[GetContextResponse]].map{ contextResponses => SuccessfulGetPost(contextResponses)}
  
  // Get failure validators
  
  implicit val validateGetErrorCode: Reads[GetErrorCode] = (
      (JsPath \ "code").read[String] and
      (JsPath \ "reasonPhrase").read[String]
  )(GetErrorCode.apply _)
  
  implicit val validateFailedGet: Reads[FailedGetPost] = 
      (JsPath \ "errorCode").read[GetErrorCode].map{ errorCode => FailedGetPost(errorCode)}
}

@Singleton
class FiwareBackend @Inject() (ws: WSClient) extends Backend with PostWrites with PostReads with FiwareGetValidator {  
  
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
                         promise.failure(new Exception(s"$response.json"))
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
      case s: JsSuccess[SuccessfulGetPost] => Logger.info(s"Future success:\n${response.json}\n")
                                              var hasMapError = false
                                              val jsOpt: Seq[Option[Post]] = s.get.contextResponses.map(
                                                  _.contextElement.attributes(0).value.validate[Post] match {
                                                      case sp: JsSuccess[Post] => Some(sp.get)
                                                      case e: JsError => hasMapError = true
                                                                         None
                                              })
                                              if(hasMapError) {
                                                promise.failure(new Exception(s"$response.json"))
                                              } else {
                                                promise.success(jsOpt.map(_.get))
                                              }
      case e: JsError => Logger.info(s"Future failure:\n${response.json}\n")
                         promise.failure(new Exception(s"$response.json"))
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
       case Success(response) => Logger.info(s"Future success:\n${response.json}\n")
                                  fiwareParseGetAnswer(response, promise)
       case Failure(e) => Logger.info(s"Future failure:\n$e\n")
                         promise.failure(e)
     }     
     promise.future
   }
}
