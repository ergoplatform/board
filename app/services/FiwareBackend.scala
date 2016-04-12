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
import scala.util.{Success, Failure}
import scala.concurrent.{Future, Promise}
import scala.concurrent.ExecutionContext.Implicits.global

case class CryptoSettings(group: GStarModSafePrime, generator: Element[_])

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
  private def fiwareParsePostAnswer(response: WSResponse, promise: Promise[BoardAttributes], post: models.Post) {
    response.json.validate[SuccessfulGetPost] match {
      case s: JsSuccess[SuccessfulGetPost] => Logger.info(s"Future success:\n${response.json}\n")
                                              // commit the last post to the hash service
                                              HashService.commit(post)
                                              index.incrementAndGet()
                                              promise.success(post.board_attributes)
      case e: JsError => Logger.info(s"Future failure:\n${response.json}\n")
                         promise.failure(new Error(s"${response.json}"))
    }
  }
   
   def signPost(post: models.Post): models.Post = {
     val message = new Base64Message(Json.toJson(post))
     val signature = SchnorrSigningDevice.signString(keyPair, message)
     Logger.info(s"Post Verification ${signature.verify(message)}")
     val signatureStr = signature.toSignatureString()
     models.Post(post.message, 
                 post.user_attributes, 
                 models.BoardAttributes(post.board_attributes.index, 
                                       post.board_attributes.timestamp, 
                                       post.board_attributes.hash, 
                                       Some(signatureStr)))
   }
   
   private def verifyPostRequest(request: PostRequest): Boolean = {
     request.user_attributes.signature match {
       case None => false
       case Some(signatureStr) => // strip the signature from the Post Request
                                  val leanRequest = PostRequest(request.message,
                                                                 UserAttributes(
                                                                     request.user_attributes.group,
                                                                     request.user_attributes.section,
                                                                     request.user_attributes.pk,
                                                                     None))
                                  val base64message = new Base64Message(Json.toJson(leanRequest))
                                  val signature = DSASignature.fromSignatureString(signatureStr)
                                  signature.verify(base64message)
     }
   }
  
  /**
   * Implements the `Post` operation. Send the Post to the Fiware backend and interpret the result
   */
   override def Post(request: PostRequest): Future[BoardAttributes] = {
     val promise: Promise[BoardAttributes] = Promise[BoardAttributes]()
     Logger.info(s"PostRequest Verification ${verifyPostRequest(request)}")
     // index and timestamp
     val postIndex = index.get()
     val timeStamp = System.currentTimeMillis()
     // fill in the Post object
     val postNoHash = models.Post(request.message, 
                           request.user_attributes, 
                           // add board attributes, including index and timestamp
                           BoardAttributes(s"$postIndex",s"$timeStamp","",None))
     // get hash                      
     val hashFuture = HashService.createHash(postNoHash)
     
     hashFuture onFailure {
       case error => Logger.info(s"hashing error:\n$error\n")
                     promise.failure(new Error(s"Hashing error : $error"))
     }
     
     hashFuture onSuccess {
       case hash =>  
         val postNotSigned = models.Post(request.message, 
         request.user_attributes, 
         // add hash
         BoardAttributes(
             s"$postIndex",
             s"$timeStamp",
             hash.toString(),
             None))
         // add signature
         val post = signPost(postNotSigned)
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
