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

package models

import play.api.libs.json.JsValue
import scala.collection.Seq
import scala.concurrent.{Future, Promise}

/**
 * This trait defines the Public Bulletin Board `Backend` operations interface.
 */
trait BoardBackend {
  /**
   * `Post` operation, add a post to the board
   */
  def Post(request: PostRequest): Future[BoardAttributes]
  /**
   * `Get` operation, query the board to get a set of posts
   */
  def Get(request: GetRequest): Future[Seq[Post]]
  /**
   * `Subscribe` operation
   */
  def Subscribe(request: SubscribeRequest): Future[SuccessfulSubscribe]
  /**
   * `Accumulate` operation
   */
  def Accumulate(request: AccumulateRequest): Future[JsValue]
  /**
   * `Unsubscribe` operation
   */
  def Unsubscribe(request: UnsubscribeRequest): Future[Unit]
}