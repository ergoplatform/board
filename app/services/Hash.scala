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

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import models._
import play.api.libs.json.Json

import scala.concurrent.{Future, Promise, _}
import scala.util.{Failure, Success, Try}


class Hash(message: Base64Message) {

  override def toString(): String = new String(value)

  lazy val value = calculateHash(message)

  private def calculateHash(msg: Base64Message) = {
    import scorex.crypto.hash._
    val bytes = msg.toString().getBytes(StandardCharsets.UTF_8)
    Blake2b256.hash(bytes)
  }
}

object HashService extends BoardJSONFormatter {
  implicit val system = ActorSystem()
  implicit val executor = system.dispatchers.lookup("my-other-dispatcher")
  implicit val materializer = ActorMaterializer()
  private var postMap = Map[Int, (Base64Message,Promise[Hash])]()
  private var lastCommitedIndex: Int = -1
  private var lastPostB64: Base64Message = new Base64Message("")

  def createHash(post: Post): Future[Hash] = {
    val promise = Promise[Hash]()
    Future {
      blocking {
        postMap.synchronized {
          Try {
            post.board_attributes.index.toInt
          } match {
            case Success(index) =>
              // we already received the previous post, we can calculate the hash
              if (lastCommitedIndex + 1 == index) {
                val messageB64 = new Base64Message(Json.stringify(Json.toJson(post)))
                promise.success(new Hash(lastPostB64 + messageB64))
              }
              // the post is out of order, wait till we receive the previous post
              else if (lastCommitedIndex + 1 < index) {
                if (postMap.contains(index)) {
                  promise.failure(new Error(s"Post index collision with index: $index"))
                } else {
                  postMap += (index -> (new Base64Message(Json.stringify(Json.toJson(post))), promise))
                }
              }
              // the post received is outdated
              else {
                promise.failure(new Error(s"Post index $index is outdated. Last post had index $lastCommitedIndex"))
              }
            case Failure(err) =>
              promise.failure(err)
          }
        }
      }
    }
    promise.future
  }

  // the post has been committed to the immutable log, synchronize futures
  def commit(post: Post): Future[Unit] = {
    val promise = Promise[Unit]
    Future {
      blocking {
        postMap.synchronized {
          Try {
            post.board_attributes.index.toInt
          } match {
            case Success(index) =>
              if (index == lastCommitedIndex + 1) {
                lastPostB64 = new Base64Message(Json.stringify(Json.toJson(post)))
                lastCommitedIndex = index
                postMap.get(index + 1) map { data =>
                  data._2.success(new Hash(lastPostB64 + data._1))
                  //postMap -= (index)  // maybe remove this line?
                }
                promise.success({})
              } else {
                promise.failure(new Error(s"Hash Service Error: committing message out of order. Last committed post index was: $lastCommitedIndex and the index to commit is $index"))
              }
            case Failure(err) =>
              promise.failure(err)
          }
        }
      }
    }
    promise.future
  }
  
}