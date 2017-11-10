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
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

import scala.concurrent.{Future, Promise}

trait Subscription {
  implicit val system = ActorSystem()
  implicit val executor = system.dispatchers.lookup("my-other-dispatcher")
  implicit val materializer = ActorMaterializer()
  // the index is the subscriptionId
  // the value is the reference
  private var subscriptionMap = Map[String, String]()
  
  def addSubscription(subscriptionId: String, reference: String) = {
    subscriptionMap.synchronized {
      subscriptionMap += (subscriptionId -> reference)
    }
  }
  
  def getSubscription(subscriptionId: String) : Option[String] = {
    subscriptionMap.synchronized {
      subscriptionMap.get(subscriptionId)
    }
  }
  
  def removeSubscription(subscriptionId: String, reference: String) : Future[Unit] = {
    val promise = Promise[Unit]()
    Future {
      subscriptionMap.synchronized {
        subscriptionMap.get(subscriptionId) match {
          case Some(ref) =>
            if(ref == reference) {
              subscriptionMap -= subscriptionId
              promise.success({})
            } else {
              promise.failure(new Error(s"error: reference ${reference} does not match ${ref}"))
            }
          case None =>
            promise.failure(new Error(s"Error: subscription id not found: $subscriptionId"))
        }
      }
    }
    promise.future
  }
}