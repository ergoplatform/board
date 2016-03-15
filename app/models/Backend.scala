package models

trait Backend {
  def Post(request: PostRequest): BoardAttributes
}