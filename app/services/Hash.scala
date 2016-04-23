package services

import play.api.libs.json._
import models._
import ch.bfh.unicrypt.helper.hash.HashAlgorithm
import ch.bfh.unicrypt.helper.hash.HashMethod
import ch.bfh.unicrypt.helper.converter.classes.bytearray.StringToByteArray
import ch.bfh.unicrypt.helper.converter.classes.biginteger.ByteArrayToBigInteger
import ch.bfh.unicrypt.helper.converter.classes.string.ByteArrayToString
import ch.bfh.unicrypt.helper.array.classes.ByteArray;
import scala.concurrent.{Future, Promise}
import scala.collection.mutable


class Hash (message: Base64Message) {
  private val hashValue = calculateHash(message)
  
  override def toString(): String = {
    hashValue
  }
  
  private def calculateHash(msg: Base64Message): String = {
     val hashAlgorithm: HashAlgorithm = HashAlgorithm.SHA512
     val hashMethod = HashMethod.getInstance(hashAlgorithm)
     val converter = StringToByteArray.getInstance()
     val byteArray = converter.convert(msg.toString())
     val byteHash = hashAlgorithm.getHashValue(byteArray)
     val stringConverter = ByteArrayToString.getInstance()
     stringConverter.convert(byteHash)
  }
}

object HashService extends BoardJSONFormatter {
  private var postMap = Map[Int, Tuple2[Base64Message,Promise[Hash]]]()
  private var lastCommitedIndex: Int = -1
  private var lastPostB64: Base64Message = new Base64Message(JsNull)
  
  def createHash(post: Post) : Future[Hash] = {
    val promise = Promise[Hash]()
    postMap.synchronized {
      val index = post.board_attributes.index.toInt
      // we already received the previous post, we can calculate the hash
      if(lastCommitedIndex + 1 == index) {
        val messageB64 = new Base64Message(Json.toJson(post))
        promise.success(new Hash(lastPostB64 + messageB64))
      }
      // the post is out of order, wait till we receive the previous post
      else if(lastCommitedIndex + 1 < index) {
        if(postMap.contains(index)) {
          promise.failure(new Error(s"Post index collision with index: ${index}"))
        } else {
          postMap += (index -> (new Base64Message(Json.toJson(post)), promise))
        }
      } 
      // the post received is outdated
      else {
        promise.failure(new Error(s"Post index ${index} is outdated. " + 
                      s"Last post had index ${lastCommitedIndex}"))
      }
    }
    promise.future
  }
  // the post has been committed to the immutable log, synchronize futures
  def commit(post: Post) {
    var ret = false
    postMap.synchronized {
      lastPostB64 = new Base64Message(Json.toJson(post))
      lastCommitedIndex = lastCommitedIndex +1
      val index = post.board_attributes.index.toInt
      if(index == lastCommitedIndex + 1) {
        postMap.get(index) map { r =>
               r._2.success(new Hash(lastPostB64 + r._1))
               postMap -= (index)
               ret = true
            }
      }
    }
    ret
  }
  
}