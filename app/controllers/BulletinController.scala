package controllers

import akka.actor.ActorSystem
import javax.inject._
import play.api._
import play.api.mvc._ 
import play.api.libs.json._
import play.api.mvc.Results._
import play.api.libs.functional.syntax._
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.concurrent.duration._
import scala.util.{Success, Failure}
import models._
import services.FiwareBackend


/**
 * This controller creates an `Action` to handle bulletin post and get operations.
 * 
 * @param actorSystem We need the `ActorSystem`'s `Scheduler` to
 * run code after a delay.
 * @param backend We need a `BoardBackend` to implement the Public Bulletin Board
 * @param exec We need an `ExecutionContext` to execute our
 * asynchronous code.
 */
@Singleton
class BulletinController @Inject() 
                      (actorSystem: ActorSystem)
                      (backend: BoardBackend)
                      (implicit exec: ExecutionContext) 
                      extends Controller 
                      with PostReadValidator
                      with PostWriteValidator 
{
  /**
   * Create asynchronous `Action` to send a Post operation to the backend
   */
  def post = Action.async { request =>
    process_post_request(request)
  }
  
  /**
   * Check the Post request body is in JSON format and pass it to `json_to_backend_post`
   */
  private def process_post_request(request : Request[AnyContent])  : Future[Result] = {
    Logger.info(s"action: Board POST")
    request.body.asJson match {
      case Some(json_data) => json_to_backend_post(json_data)
      case None => Future { BadRequest(s"Bad request: not a json or json format error:\n${request.body}\n") }
    }
  }
  /**
   * Get the message safely from a `Throwable`
   */
  private def getMessageFromThrowable(t: Throwable): String = {
    if (null == t.getCause) {
        t.toString
     } else {
        t.getCause.getMessage
     }
  }
  /**
   * Validate JSON, convert it to a PostRequest, and send it to the `BoardBackend` Post interface
   */
  private def json_to_backend_post(json : libs.json.JsValue)  : Future[Result] = {
    val promise: Promise[Result] = Promise[Result]()
    json.validate[PostRequest] match {
        case s: JsSuccess[PostRequest] => { 
                                              backend.Post(s.get) onComplete {
                                                case Success(p) => promise.success( Ok(Json.prettyPrint(Json.toJson(p))) );
                                                case Failure(e) => promise.success( BadRequest(s"${getMessageFromThrowable(e)}") )
                                              }
                                           }
        case e: JsError => promise.success(BadRequest(s"$e"))
    }
    promise.future
  }
  
  /**
   * Create asynchronous `Action` to send a Get operation to the backend
   */
  def get = Action.async { request =>
    process_get_request(request)
  }
  
  /**
   * Check the Get request body is in JSON format and pass it to `json_to_backend_get`
   */
  private def process_get_request(request : Request[AnyContent])  : Future[Result] = {
    Logger.info(s"action: Board GET")
    request.body.asJson match {
      case Some(json_data) => json_to_backend_get(json_data)
      case None => Future { BadRequest(s"Bad request: not a json or json format error:\n${request.body.asRaw}\n") }
    }
  }
  /**
   * Validate Get Body, which contains a JSON with some of the fields of the post.
   * For now only the section, group and index are required, which is why this ad-hoc method is here
   */
  /*private def jsonValidatePost(json : libs.json.JsValue): JsResult[Post] = {
    val section = (json \ "user_attributes" \ "section" ).asOpt[String]
    val group = (json \ "user_attributes" \ "group" ).asOpt[String]
    val index = (json \ "board_attributes" \ "index" ).asOpt[String]
    if(section.isDefined && group.isDefined && index.isDefined) {
      val p = Post("",
                   UserAttributes(section.get,group.get,"",None), 
                   BoardAttributes(index.get,"","",None))
      return JsSuccess(p)
    }
    return JsError()
  }*/
  
  /**
   * Validate JSON, convert it to a Post, and send it to the `BoardBackend` Get interface
   */
  private def json_to_backend_get(json : libs.json.JsValue)  : Future[Result] = {
    val promise: Promise[Result] = Promise[Result]()
    // check that the basic elements are there
    // instead of using json.validate[Post], as many elements are optional
    json.validate[GetRequest] match { 
      case s: JsSuccess[GetRequest] => {
                                      backend.Get(s.get) onComplete {
                                                case Success(p) => promise.success( Ok(Json.prettyPrint(Json.toJson(p))) )
                                                case Failure(e) => promise.success( BadRequest(s"${getMessageFromThrowable(e)}") )
                                      }
                                 }
      case e: JsError => promise.success( BadRequest(s"$e") )
    }
    promise.future
  }

}
