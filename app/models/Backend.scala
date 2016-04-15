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
}