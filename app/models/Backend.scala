package models

import scala.collection.Seq
import scala.concurrent.{Future, Promise}

trait Backend {
  def Post(request: PostRequest): Future[BoardAttributes]
  def Get(request: Post): Seq[Post]
}