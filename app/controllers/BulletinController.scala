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
import scala.concurrent._

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
  with BoardJSONFormatter
  with FiwareJSONFormatter
  with ErrorProcessing
{
  /**
   * Create asynchronous `Action` to send a Post operation to the backend
   */
  def post = Action.async { 
    request => process_post_request(request)
  }
  
  /**
   * Check the Post request body is in JSON format and pass it to `json_to_backend_post`
   */
  private def process_post_request
  (request : Request[AnyContent])
  : Future[Result] = 
  {
    val promise = Promise[Result]()
    Future {
      promise.completeWith {
        Logger.info(s"action: Board POST")
        request.body.asJson match {
          case Some(json_data) => 
            json_to_backend_post(json_data)
          case None => 
            Future { BadRequest(s"Bad request: not a json or json format error:\n" + request.body) }
        }
      }
    }
    promise.future
  }

  /**
   * Validate JSON, convert it to a PostRequest, and send it to the `BoardBackend` Post interface
   */
  private def json_to_backend_post
  (json : libs.json.JsValue)  
  : Future[Result] = 
  {
    val promise: Promise[Result] = Promise[Result]()
    Future {
      json.validate[PostRequest] match {
          case s: JsSuccess[PostRequest] => 
            backend.Post(s.get) onComplete {
              case Success(p) => 
                promise.success( Ok(Json.toJson(p)) )
              case Failure(e) => 
                promise.success( BadRequest(s"${getMessageFromThrowable(e)}") )
            }
          case e: JsError => 
            promise.success(BadRequest(s"$e"))
      }
    }
    promise.future
  }
  
  /**
   * Create asynchronous `Action` to send a Get operation to the backend
   */
  def get = Action.async { 
    request => process_get_request(request)
  }
  
  /**
   * Check the Get request body is in JSON format and pass it to `json_to_backend_get`
   */
  private def process_get_request
  (request : Request[AnyContent])  
  : Future[Result] = 
  {
    Logger.info(s"action: Board GET")
    request.body.asJson match {
      case Some(json_data) => 
        json_to_backend_get(json_data)
      case None => 
        Future { BadRequest("Bad request: not a json or json format error:\n" + 
                             s"${request.body.asRaw}\n") }
    }
  }
  
  /**
   * Validate JSON, convert it to a Post, and send it to the `BoardBackend` Get interface
   */
  private def json_to_backend_get
  (json : libs.json.JsValue)  
  : Future[Result] = 
  {
    val promise: Promise[Result] = Promise[Result]()
    Future {
      // check that the basic elements are there
      // instead of using json.validate[Post], as many elements are optional
      json.validate[GetRequest] match { 
        case s: JsSuccess[GetRequest] => 
          backend.Get(s.get) onComplete {
            case Success(p) => 
              promise.success( Ok(Json.prettyPrint(Json.toJson(p))) )
            case Failure(e) => 
              promise.success( BadRequest(getMessageFromThrowable(e)) )
          }
        case e: JsError => promise.success( BadRequest(s"$e") )
      }
    }
    promise.future
  }
  
  /**
   * Create asynchronous `Action` to send a Post operation to the backend
   */
  def subscribe = Action.async { 
    request => process_subscribe_request(request)
  }
  
  
  /**
   * Check the subscribe request body is in JSON format and pass it to `json_to_backend_get`
   */
  private def process_subscribe_request
  (request : Request[AnyContent])  
  : Future[Result] = 
  {
    Logger.info(s"action: Board SUBSCRIBE, remoteAddress: " + request.remoteAddress)
    request.body.asJson match {
      case Some(json_data) => 
        json_to_backend_subscribe(json_data)
      case None => 
        Future { BadRequest("Bad request: not a json or json format error:\n" + request.body)  }
    }
  }
  
  /**
   * Validate JSON, convert it to a Post, and send it to the `BoardBackend` Get interface
   */
  private def json_to_backend_subscribe
  (json : libs.json.JsValue)
  : Future[Result] = 
  {
    val promise: Promise[Result] = Promise[Result]()
    Future {
      json.validate[SubscribeRequest] match { 
        case s: JsSuccess[SubscribeRequest] => 
          backend.Subscribe(s.get) onComplete {
            case Success(p) => 
              Logger.info(Json.prettyPrint(Json.toJson(p)))
              promise.success( Ok(p.subscribeResponse.subscriptionId) )
            case Failure(e) => 
              Logger.info(getMessageFromThrowable(e))
              promise.success( BadRequest(getMessageFromThrowable(e)) )
          }
        case e: JsError => 
          Logger.info(s"$e")
          promise.success( BadRequest(s"$e") )
      }
    }
    promise.future
  }

  /**
   * Create asynchronous `Action` to send a Consume operation to the backend
   */
  def accumulate = Action.async { 
    request => process_accumulate_request(request)
  }
  
  /**
   * Check the subscribe request body is in JSON format and pass it to `json_to_backend_get`
   */
  private def process_accumulate_request(request : Request[AnyContent])  : Future[Result] = {
    val promise = Promise[Result]()
    Future {
      promise.completeWith {
        Logger.info(s"action: Board ACCUMULATOR")
        request.body.asJson match {
          case Some(json_data) =>  json_to_backend_accumulate(json_data)
          case None => Future {
            Logger.info(s"Bad request: not a json or json format error:\n${request.body.asRaw}\n")
            BadRequest(s"Bad request: not a json or json format error:\n${request.body.asRaw}\n")
          }
        }
      }
    }
    promise.future
  }
  
  /**
   * Validate JSON, convert it to a Post, and send it to the `BoardBackend` Get interface
   */
  private def json_to_backend_accumulate(json : libs.json.JsValue)  : Future[Result] = {
    val promise: Promise[Result] = Promise[Result]()
    Future {
      json.validate[AccumulateRequest] match { 
        case s: JsSuccess[AccumulateRequest] =>
          backend.Accumulate(s.get) onComplete {
            case Success(any) => 
              Logger.info("Ok()")
              promise.success( Ok("") )
            case Failure(e) => 
              Logger.info("ACCUMULATE Failure " + getMessageFromThrowable(e))
              promise.success( BadRequest(getMessageFromThrowable(e)) )
          }
        case e: JsError => 
          Logger.info("ACCUMULATE JsError " + s"$e")
          promise.success( BadRequest(s"$e") )
      }
    }
    promise.future
  }
  
  /**
   * Create asynchronous `Action` to send a Unsubscribe operation to the backend
   */
  def unsubscribe = Action.async { 
    request => process_unsubscribe_request(request)
  }
  
  /**
   * Check the unsubscribe request body is in JSON format and call the backend's unsubscribe
   */
  private def process_unsubscribe_request(request : Request[AnyContent])  : Future[Result] = {
    val promise = Promise[Result]()
    Future {
      Logger.info(s"action: Board UNSUBSCRIBE")
      request.body.asJson match {
        case Some(json_data) =>  
          json_data.validate[UnsubscribeRequest] match { 
            case s: JsSuccess[UnsubscribeRequest] =>
              backend.Unsubscribe(s.get) onComplete {
                case Success(any) => 
                  Logger.info("Ok()")
                  promise.success(Ok(""))
                case Failure(e) => 
                  Logger.info("UNSUBSCRIBE Failure " + getMessageFromThrowable(e))
                  promise.success(BadRequest(getMessageFromThrowable(e)))
              }
            case e: JsError => 
              Logger.info("UNSUBSCRIBE JsError " + s"$e")
              promise.success(BadRequest(s"$e"))
          }
        case None => Future {
          Logger.info(s"Bad request: not a json or json format error:\n${request.body.asRaw}\n")
          promise.success(BadRequest(s"Bad request: not a json or json format error:\n${request.body.asRaw}\n"))
        }
      }
    }
    promise.future
  }
}
