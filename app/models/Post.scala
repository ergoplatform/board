package models

import javax.inject._
import play.api.libs.json._
import play.api.libs.functional.syntax._

case class UserAttributes(section: String, group: String, pk: String, signature: String)
case class BoardAttributes(index: String, timestamp: String, hash: String, signature: String)
case class PostRequest(message: String, user_attributes: UserAttributes)
case class Post(message: String, user_attributes: UserAttributes, board_attributes: BoardAttributes)  