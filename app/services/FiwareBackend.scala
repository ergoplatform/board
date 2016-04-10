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
  
   def  signString(strMessage: String) : models.Signature  = {
     // Use DSA keys
     val keyGen = KeyPairGenerator.getInstance("DSA")
     keyGen.initialize(2048)
     // public/private key pair
     val keypair = keyGen.genKeyPair()
     // private key
     val dsaPrivateKey = keypair.getPrivate() match {
       case r: DSAPrivateKey => r
       case _ => throw new ClassCastException
     }
     // public key
     val dsaPublicKey = keypair.getPublic match {
       case r: DSAPublicKey => r
       case _ => throw new ClassCastException
     }
     // group
     val g_q = GStarModPrime.getInstance(dsaPrivateKey.getParams().getP(), dsaPrivateKey.getParams().getQ())
     val g = g_q.getElement(dsaPrivateKey.getParams().getG())
     // Schnorr signature scheme
     val schnorr = SchnorrSignatureScheme.getInstance(StringMonoid.getInstance(Alphabet.BASE64), g);
     // Encode UTF-8 string to Base64
     val base64message = Base64.getEncoder.encodeToString(strMessage.getBytes(StandardCharsets.UTF_8))
     // message in Element format
     val message = schnorr.getMessageSpace().getElementFrom(base64message)
     
		 val keyPair = schnorr.getKeyPairGenerator().generateKeyPair()
		 val privateKey = keyPair.getFirst()
		 val publicKey = keyPair.getSecond()
     Logger.info(s"g:${publicKey.convertToString()}")
		 
     Logger.info(s"g:${g.toString()}")
     Logger.info(s"g_g:${g_q.toString()}")
		 val signature = schnorr.sign(privateKey, message)
     Logger.info(s"signature:${signature.convertToString()}")
		 Logger.info(s"Signature: ${signature}")
     
		 val result = schnorr.verify(publicKey, message, signature)
		 Logger.info(s"1 Verification ${result}")

		 val falseResult = schnorr.verify(publicKey, message, signature.invert())
		 Logger.info(s"2 Verification inverse test: ${falseResult}")
		 
		 // Let's verify the signature using only the signer public key
     {
  		 
  		 // signer PK reconstruction
  		 val dsaParams : DSAParams = dsaPublicKey.getParams()
  		 val dsaPublicKeySpec : DSAPublicKeySpec = new DSAPublicKeySpec(dsaPublicKey.getY(), 
  		                                            dsaParams.getP(), 
  		                                            dsaParams.getQ(), 
  		                                            dsaParams.getG())
       val keyFactory : KeyFactory = KeyFactory.getInstance("DSA")
       val dsaPublicKey2  = keyFactory.generatePublic(dsaPublicKeySpec).asInstanceOf[DSAPublicKey]
                                   
       // group
       val g_q2 = GStarModPrime.getInstance(dsaPublicKey2.getParams().getP(), dsaPublicKey2.getParams().getQ())
       val g2 = g_q2.getElement(dsaPublicKey2.getParams().getG())
       
       // signature PK reconstruction: GStarModElement
       val publicKey2 = g_q2.getElementFrom(publicKey.convertToString()).asInstanceOf[GStarModElement]
       // signature reconstruction: Pair[ZModElement]
  		 val s1 = signature.getFirst().getSet()
  		 val s2 = signature.getSecond().getValue().toString()
       val signature2 = Pair.getInstance(signature.getFirst(),//g_q2.getElementFrom(s1).asInstanceOf[ZModElement], 
                                      signature.getSecond())//g_q2.getElementFrom(s2).asInstanceOf[ZModElement])
       // Schnorr signature scheme
       val schnorr2 = SchnorrSignatureScheme.getInstance(StringMonoid.getInstance(Alphabet.BASE64), g2)
       // Verify signature
  		 val result2 = schnorr2.verify(publicKey2, message, signature2)
  		 Logger.info(s"3 Verification ${result2}  ${signature2.getClass} ${signature2.getFirst().getClass}  ${signature2.getSecond().getClass}")
  		 Logger.info(s"4 publicKey ${publicKey2.getClass}")
     }
		 models.Signature(dsaPublicKey.toString(), publicKey.convertToString(), signature.convertToString())
   }
   
   def signPost(post: models.Post): models.Post = {
     val message = Json.prettyPrint(Json.toJson(post))
     signString(message)
     models.Post(post.message, 
                 post.user_attributes, 
                 models.BoardAttributes(post.board_attributes.index, 
                                       post.board_attributes.timestamp, 
                                       post.board_attributes.hash, 
                                       signString(message)))
   }
   
   override def Post(request: PostRequest): Future[BoardAttributes] = {
     val promise: Promise[BoardAttributes] = Promise[BoardAttributes]()
     // hash
     val hashAlgorithm: HashAlgorithm = HashAlgorithm.SHA512
     val hashMethod = HashMethod.getInstance(hashAlgorithm)
     val converter = StringToByteArray.getInstance()
     val byteArray = converter.convert("holacaracola")
     val byteHash = hashAlgorithm.getHashValue(byteArray)
     val stringConverter = ByteArrayToString.getInstance()
     val hash = stringConverter.convert(byteHash)
     // fill in the Post object
     var postNotSigned = models.Post(request.message, 
                           request.user_attributes, 
                           // add board attributes, including index and timestamp
                           BoardAttributes(s"${index.getAndIncrement()}",s"${System.currentTimeMillis()}",hash,models.Signature("","","")))
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
