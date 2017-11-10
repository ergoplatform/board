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

import play.api.libs.json._

// Classes used for reading and writing JSON structures of a Post
case class SignatureString(signaturePK: String, signature: String)
case class UserAttributes(section: String, group: String, pk: Option[String] = None, signature: Option[SignatureString] = None)
case class BoardAttributes(index: String, timestamp: String, hash: String, signature: Option[SignatureString] = None)
case class PostRequest(message: String, user_attributes: UserAttributes)
case class Post(message: String, user_attributes: UserAttributes, board_attributes: BoardAttributes)
case class GetRequest(section: String, group: String, index: String)
case class SubscribeRequest(section: String, group: String, reference: String)

// This trait enables easily reading a Json into a Post
trait BoardJSONFormatter {
  implicit val SubscribeRequestF = Json.format[SubscribeRequest]
  implicit val SignatureStringF = Json.format[SignatureString]
  implicit val UserAttributesF = Json.format[UserAttributes]
  implicit val PostRequestF = Json.format[PostRequest]
  implicit val BoardAttributesF = Json.format[BoardAttributes]
  implicit val PostF = Json.format[Post]
  implicit val GetRequestF = Json.format[GetRequest]
}