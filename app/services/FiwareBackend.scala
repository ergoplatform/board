/**
 * This file is part of agora-board.
 * Copyright (C) 2016  Agora Voting SL <agora@agoravoting.com>

 * agora-board is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License.

 * agora-board is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.

 * You should have received a copy of the GNU Affero General Public License
 * along with agora-board.  If not, see <http://www.gnu.org/licenses/>.
**/

package services

import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.atomic.AtomicLong
import javax.inject.{Inject, Singleton}

import models._
import play.api.Logger
import play.api.libs.json.{JsError, JsSuccess, JsValue, Json}
import play.api.libs.ws.{WSClient, WSResponse}
import scorex.crypto.signatures.{Curve25519, Signature}

import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success, Try}

/** This class implements the BoardBackend and connects to the Fiware-orion
 * context-broker backend.
 * 
 * This class has a `Singleton` annotation because we need to make
 * sure we only use one counter per application. Without this
 * annotation we would get a new instance every time a [[FiwareBackend]] is
 * injected.
 */
@Singleton
class FiwareBackend @Inject()(ws: WSClient)(configuration: services.Config) extends BoardBackend
    with BoardJSONFormatter
    with FiwareJSONFormatter
    with Subscription
    with ErrorProcessing {

  import scorex.utils.{Random => RandomBytes}
  val randomSeed = RandomBytes.randomBytes(64)
  val (privateKey, publicKey) = Curve25519.createKeyPair(randomSeed)

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
    Try(response.json) match {
      case Success(json) => 
        val jsonStr = Json.stringify(json)
        json.validate[SuccessfulGetPost] match {
          case _: JsSuccess[SuccessfulGetPost] =>
            Logger.info(s"Future success:\n$jsonStr\n")
            // commit the last post to the hash service
            HashService.commit(post) onComplete {
              case Success(_) =>
                promise.success(post.board_attributes)
              case Failure(err) =>
                promise.failure(err)
            }
            // only for testing, normally all post calls should increment
            //index.incrementAndGet()
            //promise.success(post.board_attributes)
          case _: JsError =>
            Logger.info(s"Future failure:\n$jsonStr\n")
            promise.failure(new Error(s"$jsonStr"))
        }
      case Failure(_) =>
        Logger.info(s"Future failure:\n${response.body}\n")
        promise.failure(new Error(s"${response.body}"))
    }
  }
   
   def signPost(post: models.Post, hash: Hash): models.Post = {
     import java.nio.charset.StandardCharsets._

     val signature = Curve25519.sign(privateKey, hash.value)
     val verified = Curve25519.verify(signature, hash.value, publicKey)
     Logger.info(s"Post Verification $verified")
     val signatureString = SignatureString(new String(publicKey, UTF_8), new String(signature, UTF_8))

     models.Post(
         post.message, 
         post.user_attributes, 
         models.BoardAttributes(
             post.board_attributes.index, 
             post.board_attributes.timestamp,
             new String(hash.value),
             Some(signatureString)))
   }
   
   private def verifyPostRequest(request: PostRequest): Boolean = {
     import java.nio.charset.StandardCharsets._

     request.user_attributes.signature match {
       case None => false
       case Some(signatureStr) => 
         // strip the signature from the Post Request
         val leanRequest = PostRequest(
             request.message,
             UserAttributes(
                 request.user_attributes.group,
                 request.user_attributes.section,
                 request.user_attributes.pk,
                 None))
         val b64 = new Base64Message(request.message)
         val signatureBytes = Signature @@ signatureStr.signature.getBytes(UTF_8)
         val messageBytes = b64.toString().replace('=', '.').getBytes(UTF_8)
         Curve25519.verify(signatureBytes, messageBytes, publicKey)
     }
   }
  
  /**
   * Implements the `Post` operation. Send the Post to the Fiware backend and interpret the result
   */
   override def Post(request: PostRequest): Future[BoardAttributes] = {
     val promise: Promise[BoardAttributes] = Promise[BoardAttributes]()
     Future {
       Logger.info(s"PostRequest Verification ${verifyPostRequest(request)}")
       // index and timestamp
       val postIndex = index.getAndIncrement()
       val timeStamp = System.currentTimeMillis()
       Logger.info(s"POST data:\n${Json.stringify(Json.toJson(request))}\n")
       // fill in the Post object
       // the message will be encoded with Base64
       val b64 = new Base64Message(request.message)
       // for some reason Fiware doesn't like the '=' character on a String (or \")
       val msg = b64.toString().replace('=', '.')
       val postNoHash = 
         models.Post(
             msg,
             request.user_attributes, 
             // add board attributes, including index and timestamp
             BoardAttributes(s"$postIndex",s"$timeStamp","",None))
       // get hash                      
       val hashFuture = HashService.createHash(postNoHash)

       hashFuture onComplete {
         case Success(hash) => {
           val postNotSigned =
             models.Post(
               msg,
               request.user_attributes,
               // add hash
               BoardAttributes(
                 s"$postIndex",
                 s"$timeStamp",
                 "",
                 None))
           // add signature
           val post = signPost(postNotSigned, hash)
           val data = fiwarePostQuery(post)
           //Logger.info(s"POST data:\n$data\n")
           // send HTTP POST message to Fiware-Orion backend
           val futureResponse: Future[WSResponse] =
           ws.url(s"http://${configuration.fiware.addressPort}/v1/updateContext")
             .addHttpHeaders(
               "Content-Type" -> "application/json",
               "Accept" -> "application/json",
               "Fiware-ServicePath" ->
                 s"/${post.user_attributes.section}/${post.user_attributes.group}")
             .post(data)
           // Interpret HTTP POST answer
           futureResponse onComplete {
             case Success(response) =>
               // this resolves the promise with either success or failure
               fiwareParsePostAnswer(response, promise, post)
             case Failure(e) =>
               Logger.info(s"Future failure:\n$e\n")
               promise.failure(e)
           }
         }
         case Failure(error) =>
           Logger.info(s"hashing error:\n$error\n")
           promise.failure(new Error(s"Hashing error : $error"))
       }
     }
     promise.future
   }
   
  // Parse a `Get` into a JSON query that Fiware understands
  private def fiwareGetQuery(post: models.GetRequest): JsValue = {
     Json.obj(
         "entities" -> Json.arr(Json.obj(
             "type" -> "Post",
             "isPattern" -> "false",
             "id" -> post.index
         ))
     )
   }
   
  /**
   * Interpret the answer to a Get message sent to Fiware-Orion.
   * If successful, it will resolve the promise with the list of Post messages
   */
   private def fiwareParseGetAnswer(response: WSResponse, promise: Promise[Seq[Post]]) {
     Try(response.json) match {
       case Success(json) => 
         json.validate[SuccessfulGetPost] match {
          case s: JsSuccess[SuccessfulGetPost] =>
            var hasMapError : Option[String] = None
            // Map attribute.value to Post
            val postList: Seq[Post] = s.get.contextResponses.flatMap(
                _.contextElement.attributes.head.value.validate[Post] match {
                    case sp: JsSuccess[Post] =>
                      Try {
                        val post = sp.get
                        // for some reason Fiware doesn't like the '=' character on a String (or \")
                        val messageB64 = post.message.replace('.', '=')
                        // the message was Base64 encoded so it has to be decoded
                        val msg = new String(Base64.getDecoder.decode(messageB64), StandardCharsets.UTF_8)
                        Some(models.Post(msg, post.user_attributes, post.board_attributes))
                      } match {
                        case Success(some) => 
                          some
                        case Failure(err) =>
                          val strError = getMessageFromThrowable(err)
                          hasMapError = hasMapError match {
                            case Some(s) =>
                              Some(s + "\n" + strError)
                            case None =>
                              Some(strError)
                          }
                          None
                      }
                    case e: JsError => 
                      hasMapError = hasMapError match {
                        case Some(s) =>
                          Some(s + s"\n$e")
                        case None =>
                          Some(s"$e")
                      }
                      None
            })
            hasMapError match {
            // If there was any error, resolve the promise with a failure
              case Some(err) =>
                Logger.info(s"Future failure:\n$json\nError: $err")
                promise.failure(new Error(err))
            // Otherwise return the Get results as a list of Post messages
              case None =>
                Logger.info(s"Future success:\n$json\n")
                promise.success(postList)
            }
          // The Fiware-Orion backend returned an error message
          case _: JsError =>
            val responseStr = Json.prettyPrint(json)
            Logger.info(s"Future failure:\n$responseStr\n")
            promise.failure(new Error(responseStr))
        }
       case Failure(_) =>
         Logger.info(s"Future failure:\n${response.body}\n")
         promise.failure(new Error(response.body))
     }
   }
   
  /**
   * Implements the `Get` operation. Send the GetRequest to the Fiware backend and interpret the result
   */
   override def Get(post: models.GetRequest): Future[Seq[Post]] = {
     val promise: Promise[Seq[Post]] = Promise[Seq[Post]]()
     Future {
       // fill in the Get query
       val data = fiwareGetQuery(post)
       Logger.info(s"GET data:\n$data\n")
       // send HTTP POST message to Fiware-Orion backend
       val futureResponse: Future[WSResponse] = 
       ws.url(s"http://${configuration.fiware.addressPort}/v1/queryContext")
       .addHttpHeaders(
         "Content-Type" -> "application/json",
         "Accept" -> "application/json",
         "Fiware-ServicePath" -> s"/${post.section}/${post.group}")
       .post(data)
       // Interpret HTTP POST answer
       futureResponse onComplete {
         case Success(response) => 
           fiwareParseGetAnswer(response, promise)
         case Failure(e) => 
           Logger.info(s"Future failure:\n$e\n")
           promise.failure(e)
       }
     }
     promise.future
   }
  
  // Parse a `Subscribe` into a JSON query that Fiware understands
   private def fiwareSubscribeQuery(post: models.SubscribeRequest): JsValue = {
     Json.obj(
         "entities" -> Json.arr(Json.obj(
             "type" -> "Post",
             "isPattern" -> "true",
             "id" -> ".*"
         )),
         "attributes" -> Json.arr(),
         "reference" -> (s"http://${configuration.server.dockerAddress}:" +
                       s"${configuration.server.port}/bulletin_accumulate"), 
         "duration" -> "P1M",
         "notifyConditions" -> Json.arr(Json.obj(
             "type" -> "ONCHANGE"
         ))/*,
         "throttling" -> "PT1S"*/
     )
   }
   
   
  /**
   * Interpret the answer to a Subscribe message sent to Fiware-Orion.
   * If successful, it will resolve the promise with the list of Post messages
   */
   private def fiwareParseSubscribeAnswer(response: WSResponse,
                                          promise: Promise[SuccessfulSubscribe],
                                          reference: String) {
     Try(response.json) match {
       case Success(json) => 
         json.validate[SuccessfulSubscribe] match {
           case s: JsSuccess[SuccessfulSubscribe] => 
             Logger.info(s"Subscribe: adding subscription Id: ${s.get.subscribeResponse.subscriptionId} with reference: $reference")
             addSubscription(s.get.subscribeResponse.subscriptionId, reference)
             promise.success(s.get)
           // The Fiware-Orion backend returned an error message with json format
           case _: JsError =>
             val responseStr = Json.prettyPrint(json)
             Logger.info(s"Future failure:\n$responseStr\n")
             promise.failure(new Error(responseStr))
         }
       // The Fiware-Orion backend returned an error message
       case Failure(_) =>
         Logger.info(s"Future failure:\n${response.body}\n")
         promise.failure(new Error(response.body))
     }
   }
   
  def Subscribe(request: SubscribeRequest): Future[SuccessfulSubscribe] = {
    val promise = Promise[SuccessfulSubscribe]()
    Future {
      // fill in the Subscribe query
      val data = fiwareSubscribeQuery(request)
      val path ="/" + (if(request.section.length() > 0) {
        request.section + (if(request.group.length() > 0) {
          "/" + request.group
        } else "")
      } else "")
      Logger.info(s"Subscribe path: $path data:\n$data\n")
      // send HTTP POST message to Fiware-Orion backend
      val futureResponse: Future[WSResponse] = 
      ws.url(s"http://${configuration.fiware.addressPort}/v1/subscribeContext")
      .addHttpHeaders(
        "Content-Type" -> "application/json",
        "Accept" -> "application/json",
        "Fiware-ServicePath" -> path)
      .post(data)
      // Interpret HTTP POST answer
      futureResponse onComplete {
        case Success(response) =>
          fiwareParseSubscribeAnswer(response, promise, request.reference)
        case Failure(e) => 
          Logger.info(s"Future failure:\n$e\n")
          promise.failure(e)
      }
    }
    promise.future
  }
  
  def decodeAccumulate(request: AccumulateRequest): Future[AccumulateRequest] = 
  Future {
    var parseError: Option[String] = None
    val contextResponses = request.contextResponses map { x =>
      val attributes= x.contextElement.attributes flatMap { y =>
          y.value.validate[Post] match {
            case postSuccess: JsSuccess[Post] =>
              Try {
                val post = postSuccess.get
                // for some reason Fiware doesn't like the '=' character on a String (or \")
                val messageB64 = post.message.replace('.', '=')
                // the message was Base64 encoded so it has to be decoded
                val msg = new String(
                    Base64.getDecoder.decode(messageB64), 
                    StandardCharsets.UTF_8)
                Some(
                    Attribute(
                        y.name, 
                        y._type, 
                        Json.toJson(
                            models.Post(
                                msg, 
                                post.user_attributes, 
                                post.board_attributes
                ))))
              } match {
                case Success(some) =>
                  some
                case Failure(err) =>
                  val str = "Decoding  Base64 string error: this is not " +
                          s"a valid Post: ${y.value}!\nError: $err"
                  Logger.info(str)
                  parseError = parseError match {
                    case Some(error) =>
                      Some(error +"\n" + str)
                    case None =>
                      Some(str)
                  }
                  None
              }
            case e: JsError =>
              val str = "Decoding error: this is not " +
                      s"a valid Post: ${y.value}!\nError: $e"
              Logger.info(str)
              parseError = parseError match {
                case Some(err) =>
                  Some(err +"\n" + str)
                case None =>
                  Some(str)
              }
              None
        }
      }
      ContextResponse(ContextElement(x.contextElement.id, x.contextElement.isPattern ,x.contextElement._type, attributes), x.statusCode)
    }
    parseError match {
      case Some(err) =>
        throw new Error(err)
      case None =>
        AccumulateRequest(request.subscriptionId, request.originator, contextResponses)
    }
  }
  
  
  override def Accumulate(request: AccumulateRequest): Future[JsValue] = {
    val promise = Promise[JsValue]()
    Future {
      getSubscription(request.subscriptionId) match {
        case Some(reference) =>  
        Logger.info(s"subscriptionId: ${request.subscriptionId}, " +
                 s"reference: $reference")
          //Logger.info(s"ACCUMULATE data:\n${request}\n")
          val futureDecode = decodeAccumulate(request)
          
          futureDecode onComplete {
            case Success(decoded) => 
              // send HTTP POST message to the reference
              val futureResponse: Future[WSResponse] = ws.url(reference)
              .addHttpHeaders(
                "Content-Type" -> "application/json",
                "Accept" -> "application/json")
              .post(Json.toJson(decoded))
               
              // Interpret HTTP POST answer
              futureResponse onComplete {
                case Success(response) => 
                  Try (response.json) match {
                     case Success(json) => 
                       Logger.info("ACCUMULATE reference response: " + 
                                  Json.stringify(json))
                       promise.success(json)
                     case Failure(fail) => 
                       Logger.info("ACCUMULATE reference response ERROR: " + 
                                  response.body)
                       promise.failure(fail)
                   }
                case Failure(e) => 
                  Logger.info(s"ACCUMULATE to reference: $reference:\n$e\n")
                  promise.failure(e)
              }
            case Failure(err) =>
              Logger.info(s"ACCUMULATE decoding error: $reference:\n$err\n")
              promise.failure(err)
          }
          
        case None => 
          Logger.info("Future failure: subscriptionId not found")
          promise.failure(new Error("Future failure: subscriptionId not found:"
                                  +s" ${request.subscriptionId}"))
      }
    }
    promise.future
  }
  
  
   /**
   * `Unsubscribe` operation
   */
  def Unsubscribe(request: UnsubscribeRequest) : Future[Unit] = {
    removeSubscription(request.subscriptionId, request.reference)
  }
  
}
