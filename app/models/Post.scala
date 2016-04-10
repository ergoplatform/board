package models

import javax.inject._
import play.api.libs.json._
import play.api.libs.functional.syntax._

// Classes used for reading and writing JSON structures of a Post

case class Signature(signerPK: String, signaturePK: String, signature: String)
case class UserAttributes(section: String, group: String, pk: String, signature: String)
case class BoardAttributes(index: String, timestamp: String, hash: String, signature: Signature)
case class PostRequest(message: String, user_attributes: UserAttributes)
case class Post(message: String, user_attributes: UserAttributes, board_attributes: BoardAttributes)  
case class GetRequest(section: String, group: String, index: String)

// This trait enables easily reading a Json into a Post
trait PostReadValidator {
  implicit val signatureReads: Reads[Signature] = (
      (JsPath \ "signerPK").read[String] and
      (JsPath \ "signaturePK").read[String] and
      (JsPath \ "signature").read[String]
  )(Signature.apply _)
  
  implicit val userAttributesReads: Reads[UserAttributes] = (
      (JsPath \ "section").read[String] and
      (JsPath \ "group").read[String] and
      (JsPath \ "pk").read[String] and
      (JsPath \ "signature").read[String] 
  )(UserAttributes.apply _)
  
  implicit val postRequestReads: Reads[PostRequest] = (
      (JsPath \ "message").read[String] and
      (JsPath \ "user_attributes").read[UserAttributes]
  )(PostRequest.apply _)
  
  implicit val boardAttributesReads: Reads[BoardAttributes] = (
      (JsPath \ "index").read[String] and
      (JsPath \ "timestamp").read[String] and
      (JsPath \ "hash").read[String] and
      (JsPath \ "signature").read[Signature] 
  )(BoardAttributes.apply _)
  
  implicit val postReads: Reads[Post] = (
      (JsPath \ "message").read[String] and
      (JsPath \ "user_attributes").read[UserAttributes] and
      (JsPath \ "board_attributes").read[BoardAttributes]
  )(Post.apply _)
  
  implicit val getRequestReads: Reads[GetRequest] = (
      (JsPath \ "section").read[String] and
      (JsPath \ "group").read[String] and
      (JsPath \ "index").read[String]
  )(GetRequest.apply _)
}

// This trait enables easily writing a Post into a Json
trait PostWriteValidator {
  implicit val signatureWrites: Writes[Signature] = (
      (JsPath \ "signerPK").write[String] and
      (JsPath \ "signaturePK").write[String] and
      (JsPath \ "signature").write[String]
  )(unlift(Signature.unapply))
  
  implicit val userAttributesWrites: Writes[UserAttributes] = (
      (JsPath \ "section").write[String] and
      (JsPath \ "group").write[String] and
      (JsPath \ "pk").write[String] and
      (JsPath \ "signature").write[String] 
  )(unlift(UserAttributes.unapply))
  
  implicit val postRequestWrites: Writes[PostRequest] = (
      (JsPath \ "message").write[String] and
      (JsPath \ "user_attributes").write[UserAttributes]
  )(unlift(PostRequest.unapply))
  
  implicit val boardAttributesWrites: Writes[BoardAttributes] = (
      (JsPath \ "index").write[String] and
      (JsPath \ "timestamp").write[String] and
      (JsPath \ "hash").write[String] and
      (JsPath \ "signature").write[Signature] 
  )(unlift(BoardAttributes.unapply))
  
  implicit val postWrites: Writes[Post] = (
      (JsPath \ "message").write[String] and
      (JsPath \ "user_attributes").write[UserAttributes] and
      (JsPath \ "board_attributes").write[BoardAttributes]
  )(unlift(Post.unapply))
  
  implicit val getRequestWrites: Writes[GetRequest] = (
      (JsPath \ "section").write[String] and
      (JsPath \ "group").write[String] and
      (JsPath \ "index").write[String] 
  )(unlift(GetRequest.unapply))
}