package services

import java.util.concurrent.atomic.AtomicLong
import javax.inject._
import ch.bfh.unicrypt.helper.hash.HashAlgorithm
import ch.bfh.unicrypt.helper.hash.HashMethod
import ch.bfh.unicrypt.helper.converter.classes.bytearray.StringToByteArray
import ch.bfh.unicrypt.helper.converter.classes.biginteger.ByteArrayToBigInteger
import ch.bfh.unicrypt.helper.converter.classes.string.ByteArrayToString
import ch.bfh.unicrypt.helper.array.classes.ByteArray;
import ch.bfh.unicrypt.crypto.schemes.encryption.classes.ElGamalEncryptionScheme
import ch.bfh.unicrypt.math.algebra.multiplicative.classes.GStarModSafePrime
import ch.bfh.unicrypt.math.algebra.general.interfaces.Element

import ch.bfh.unicrypt.crypto.schemes.signature.classes.SchnorrSignatureScheme;
import ch.bfh.unicrypt.helper.math.Alphabet;
import ch.bfh.unicrypt.math.algebra.concatenative.classes.StringElement;
import ch.bfh.unicrypt.math.algebra.concatenative.classes.StringMonoid;
import ch.bfh.unicrypt.math.algebra.general.classes.BooleanElement;
import ch.bfh.unicrypt.math.algebra.general.classes.Pair;
import ch.bfh.unicrypt.math.algebra.general.classes.Tuple;
import ch.bfh.unicrypt.math.algebra.multiplicative.classes.GStarModElement;
import java.security.interfaces.DSAPrivateKey;
import ch.bfh.unicrypt.math.algebra.multiplicative.classes.GStarModPrime;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.DSAParams;
import java.security.interfaces.DSAPrivateKey;
import java.security.interfaces.DSAPublicKey;
import java.security.spec.DSAPrivateKeySpec;
import java.security.spec.DSAPublicKeySpec;
import java.security.spec.KeySpec;
import java.security.KeyFactory;
import java.util.Base64
import java.nio.charset.StandardCharsets
import ch.bfh.unicrypt.math.algebra.dualistic.classes.ZModElement;
import ch.bfh.unicrypt.math.algebra.dualistic.classes.ZModPrime;
import java.math.BigInteger;

import play.api.libs.ws._
import play.api.libs.json._
import play.api.libs.json.Reads._
import play.api.libs.functional.syntax._
import play.api.Logger
import models._
import scala.util.{Try, Success, Failure}
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
                     (configuration: services.Config) 
                     extends BoardBackend 
                     with BoardJSONFormatter
                     with FiwareJSONFormatter
                     with Subscription
                     with ErrorProcessing
{  
  // Use DSA keys
  private val keyGen = KeyPairGenerator.getInstance("DSA")
  keyGen.initialize(2048)
  // public/private key pair
  private val keyPair = keyGen.genKeyPair()
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
  private def fiwareParsePostAnswer
  (response: WSResponse, 
   promise: Promise[BoardAttributes], 
   post: models.Post)
  {
    Try(response.json) match {
      case Success(json) => 
        val jsonStr = Json.stringify(json)
        json.validate[SuccessfulGetPost] match {
          case s: JsSuccess[SuccessfulGetPost] => 
            Logger.info(s"Future success:\n${jsonStr}\n")
            // commit the last post to the hash service
            HashService.commit(post) onComplete {
              case Success(commited) =>
                promise.success(post.board_attributes)
              case Failure(err) =>
                promise.failure(err)
            }
            // only for testing, normally all post calls should increment
            //index.incrementAndGet()
            //promise.success(post.board_attributes)
          case e: JsError => 
            Logger.info(s"Future failure:\n${jsonStr}\n")
            promise.failure(new Error(s"${jsonStr}"))
        }
      case Failure(error) =>
        Logger.info(s"Future failure:\n${response.body}\n")
        promise.failure(new Error(s"${response.body}"))
    }
  }
   
   def signPost(post: models.Post): models.Post = {
       Logger.info(s"signPost 0")
     val message = new Base64Message(Json.stringify(Json.toJson(post)))
       Logger.info(s"signPost 1")
     val signature = SchnorrSigningDevice.signString(keyPair, message)
       Logger.info(s"signPost 2")
     val verified = signature.verify(message)
       Logger.info(s"signPost 3")
     Logger.info(s"Post Verification ${verified}")
       Logger.info(s"signPost 4")
     val signatureStr = signature.toSignatureString()
     models.Post(
         post.message, 
         post.user_attributes, 
         models.BoardAttributes(
             post.board_attributes.index, 
             post.board_attributes.timestamp, 
             post.board_attributes.hash, 
             Some(signatureStr)))
   }
   
   private def verifyPostRequest(request: PostRequest): Boolean = {
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
         val base64message = new Base64Message(Json.stringify(Json.toJson(leanRequest)))
         DSASignature.fromSignatureString(signatureStr) match {
           case Some(signature) => signature.verify(base64message)
           case None => false
         }
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
       Logger.info(s"PostRequest 0 - $postIndex")
       val timeStamp = System.currentTimeMillis()
       Logger.info(s"PostRequest 1 - $postIndex")
       // fill in the Post object
       // the message will be encoded with Base64
       val b64 = new Base64Message(request.message)
       Logger.info(s"PostRequest 2 - $postIndex")
       // for some reason Fiware doesn't like the '=' character on a String (or \")
       val msg = b64.toString().replace('=', '.')
       Logger.info(s"PostRequest 3 - $postIndex")
       val postNoHash = 
         models.Post(
             msg,
             request.user_attributes, 
             // add board attributes, including index and timestamp
             BoardAttributes(s"$postIndex",s"$timeStamp","",None))
       Logger.info(s"PostRequest 4 - $postIndex")
       // get hash                      
       val hashFuture = HashService.createHash(postNoHash)
       Logger.info(s"PostRequest 5 - $postIndex")
       
       hashFuture onFailure {
         case error => 
           Logger.info(s"hashing error:\n$error\n")
           promise.failure(new Error(s"Hashing error : $error"))
       }
       
       hashFuture onSuccess {
         case hash =>
           Logger.info(s"PostRequest 6 - $postIndex")
           val postNotSigned = 
             models.Post(
               msg, 
               request.user_attributes, 
               // add hash
               BoardAttributes(
                   s"$postIndex",
                   s"$timeStamp",
                   hash.toString(),
                   None))
           Logger.info(s"PostRequest 7 - $postIndex")
           // add signature
           val post = signPost(postNotSigned)
           Logger.info(s"PostRequest 8 - $postIndex")
           val data = fiwarePostQuery(post)
           Logger.info(s"PostRequest 9 - $postIndex")
           Logger.info(s"POST data:\n$data\n")
           // send HTTP POST message to Fiware-Orion backend
           val futureResponse: Future[WSResponse] = 
           ws.url(s"http://${configuration.fiware.addressPort}/v1/updateContext")
           .withHeaders(
               "Content-Type" -> "application/json",
               "Accept" -> "application/json",
               "Fiware-ServicePath" -> 
                 s"/${post.user_attributes.section}/${post.user_attributes.group}")
           .post(data)
           // Interpret HTTP POST answer
           futureResponse onComplete {
             case Success(response) => 
               // this resolves the promise with either success or failure
               Logger.info(s"PostRequest 10 - $postIndex")
               fiwareParsePostAnswer(response, promise, post)
             case Failure(e) => 
               Logger.info(s"Future failure:\n$e\n")
               promise.failure(e)
           }
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
   private def fiwareParseGetAnswer
   (response: WSResponse, 
    promise: Promise[Seq[Post]]) 
   {
     Try(response.json) match {
       case Success(json) => 
         json.validate[SuccessfulGetPost] match {
          case s: JsSuccess[SuccessfulGetPost] => 
            var hasMapError : Option[String] = None
            // Map attribute.value to Post
            val postList: Seq[Post] = s.get.contextResponses.flatMap(
                _.contextElement.attributes(0).value.validate[Post] match {
                    case sp: JsSuccess[Post] =>
                      Try {
                        val post = sp.get
                        // for some reason Fiware doesn't like the '=' character on a String (or \")
                        val messageB64 = post.message.replace('.', '=')
                        // the message was Base64 encoded so it has to be decoded
                        val msg =new String(Base64.getDecoder.decode(messageB64), StandardCharsets.UTF_8)
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
                Logger.info(s"Future failure:\n${json}\nError: $err")
                promise.failure(new Error(err))
            // Otherwise return the Get results as a list of Post messages
              case None =>
                Logger.info(s"Future success:\n${json}\n")
                promise.success(postList)
            }
          // The Fiware-Orion backend returned an error message
          case e: JsError =>
            val responseStr = Json.prettyPrint(json)
            Logger.info(s"Future failure:\n${responseStr}\n")
            promise.failure(new Error(responseStr))
        }
       case Failure(error) => 
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
       .withHeaders("Content-Type" -> "application/json",
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
   private def fiwareParseSubscribeAnswer
   (
       response: WSResponse, 
       promise: Promise[SuccessfulSubscribe], 
       reference: String
   ) 
   {
     Try(response.json) match {
       case Success(json) => 
         json.validate[SuccessfulSubscribe] match {
           case s: JsSuccess[SuccessfulSubscribe] => 
             Logger.info(s"Subscribe: adding subscription Id: ${s.get.subscribeResponse.subscriptionId} with reference: ${reference}")
             addSubscription(s.get.subscribeResponse.subscriptionId, reference)
             promise.success(s.get)
           // The Fiware-Orion backend returned an error message with json format
           case e: JsError => 
             val responseStr = Json.prettyPrint(json)
             Logger.info(s"Future failure:\n${responseStr}\n")
             promise.failure(new Error(responseStr))
         }
       // The Fiware-Orion backend returned an error message
       case Failure(error) =>
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
      .withHeaders("Content-Type" -> "application/json",
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
                    case Some(err) =>
                      Some(err +"\n" + str)
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
        AccumulateRequest(request.originator, request.subscriptionId, contextResponses)
    }
  }
  
  
  override def Accumulate(request: AccumulateRequest): Future[JsValue] = {
    val promise = Promise[JsValue]()
    Future {
      getSubscription(request.subscriptionId) match {
        case Some(reference) =>  
        Logger.info(s"subscriptionId: ${request.subscriptionId}, " +
                 s"reference: ${reference}")
          Logger.info(s"ACCUMULATE data:\n${request}\n")
          val futureDecode = decodeAccumulate(request)
          
          futureDecode onComplete {
            case Success(decoded) => 
              // send HTTP POST message to the reference
              val futureResponse: Future[WSResponse] = ws.url(reference)
              .withHeaders("Content-Type" -> "application/json",
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
  
  
}
