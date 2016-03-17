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
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 * 
 * @param actorSystem We need the `ActorSystem`'s `Scheduler` to
 * run code after a delay.
 * @param exec We need an `ExecutionContext` to execute our
 * asynchronous code.
 */

@Singleton
class BulletinController @Inject() (actorSystem: ActorSystem)(backend: Backend)(implicit exec: ExecutionContext) extends Controller with PostReads{
  
  def post = Action.async { request =>
    process_post_request(request)
  }
  // Retrieve JSON
  private def process_post_request(request : Request[AnyContent])  : Future[Result] = {
    Logger.info(s"action: Board POST")
    request.body.asJson match {
      case Some(json_data) => json_to_backend_post(json_data)
      case None => Future { BadRequest(s"Bad request: not a json or json format error:\n${request.body}\n") }
    }
  }
  /**
   * Validate JSON, convert it to a PostRequest, and send to the backend
   */
  private def json_to_backend_post(json : libs.json.JsValue)  : Future[Result] = {
    val promise: Promise[Result] = Promise[Result]()
    json.validate[PostRequest] match {
        case s: JsSuccess[PostRequest] => { 
                                              backend.Post(s.get) onComplete {
                                                case Success(p) => promise.success( Ok(s"$p") )
                                                case e: Failure[ BoardAttributes ] => promise.success( BadRequest(s"$e") )
                                              }
                                           }
        case e: JsError => promise.success(BadRequest(s"$e"))
    }
    promise.future
  }
  
  def get = Action.async { request =>
    process_get_request(request)
  }
  
  private def process_get_request(request : Request[AnyContent])  : Future[Result] = {
    Logger.info(s"action: Board GET")
    request.body.asJson match {
      case Some(json_data) => json_to_backend_get(json_data)
      case None => Future { BadRequest(s"Bad request: not a json or json format error:\n${request.body.asRaw}\n") }
    }
  }
  
  private def json_to_backend_get(json : libs.json.JsValue)  : Future[Result] = {
    val promise: Promise[Result] = Promise[Result]()
    json.validate[Post] match {
      case s: JsSuccess[Post] => { 
                                      backend.Get(s.get) onComplete {
                                                case Success(p) => promise.success( Ok(s"json:\n$p\n") )
                                                case e: Failure[ Seq[Post] ] => promise.success( BadRequest(s"json:\n$s\n") )
                                      }
                                 }
      case e: JsError => promise.success( BadRequest(s"json:\n$e\n") )
    }
    promise.future
  }

}
