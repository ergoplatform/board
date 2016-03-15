package controllers

import akka.actor.ActorSystem
import javax.inject._
import play.api._
import play.api.mvc._
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.concurrent.duration._
import models._
import services.FiwareBackend


/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 * 
 * @param actorSystem We need the `ActorSystem`'s `Scheduler` to
 * run code after a delay.
 * @param exec We need an `ExecutionContext` to execute our
 * asynchronous code.
 */
@Singleton
class BulletinController @Inject() (actorSystem: ActorSystem)(backend: FiwareBackend)(implicit exec: ExecutionContext)extends Controller {
  
  import play.api.libs.json._
  import play.api.libs.functional.syntax._
  
  implicit val userAttributesReads: Reads[UserAttributes] = (
      (JsPath \ "section").read[String] and
      (JsPath \ "group").read[String] and
      (JsPath \ "pk").read[String] and
      (JsPath \ "signature").read[String] 
  )(UserAttributes.apply _)
  
  implicit val boardPostReads: Reads[PostRequest] = (
      (JsPath \ "message").read[String] and
      (JsPath \ "user_attributes").read[UserAttributes]
  )(PostRequest.apply _)
  
  /**
   * {
   *     message: m,
   *     user_attributes: 
   *     {
   *         section: s,
   *         group: g,
   *         pk: public_key,
   *         signature: S
   *     }
   * }
   */
  
  def post = Action.async { request =>
    getFuturePost(request)
  }
  
  private def getFuturePost(request : Request[AnyContent]): Future[Result] = {
    val promise: Promise[Result] = Promise[Result]()
    actorSystem.scheduler.scheduleOnce(0.second) { promise.success(process_post_request(request)) }
    promise.future
  }
  
  private def process_post_request(request : Request[AnyContent])  : Result = {
    Logger.info(s"action: Board POST")
    request.body.asJson match {
      case Some(json_data) => process_post(json_data)
      case None => BadRequest("Bad request: not a json\n")
    }
  }
  
  private def process_post(json : libs.json.JsValue)  : Result = {
    json.validate[PostRequest] match {
      case s: JsSuccess[PostRequest] => { 
                                          backend.Post(s.get)
                                          Ok(s"json:\n$s\n") 
                                         }
      case e: JsError => BadRequest(s"json:\n$e\n")
    }
  }

}
