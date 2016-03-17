package models

import play.api.libs.json._
import play.api.libs.json.Reads._
import play.api.libs.functional.syntax._

case class GetAttribute(name: String, _type: String, value: JsValue)
case class GetContextElement(id: String, isPattern: String, _type: String, attributes: Seq[GetAttribute])
case class GetStatusCode(code: String, reasonPhrase: String)
case class GetContextResponse(contextElement: GetContextElement, statusCode: GetStatusCode)
case class SuccessfulGetPost(contextResponses: Seq[GetContextResponse])

case class GetErrorCode(code: String, reasonPhrase: String)
case class FailedGetPost(errorCode: GetErrorCode)

trait FiwareQueryReadValidator {
  
  // Get success validators
  
  implicit val validateGetAttribute: Reads[GetAttribute] = (
      (JsPath \ "name").read[String] and
      (JsPath \ "type").read[String] and
      (JsPath \ "value").read[JsValue]
  )(GetAttribute.apply _)
  
  implicit val validateGetContextElement: Reads[GetContextElement] = (
      (JsPath \ "id").read[String] and
      (JsPath \ "isPattern").read[String] and
      (JsPath \ "type").read[String] and
      (JsPath \ "attributes").read[Seq[GetAttribute]](minLength[Seq[GetAttribute]](1) keepAnd maxLength[Seq[GetAttribute]](1))
  )(GetContextElement.apply _)
  
  implicit val validateGetStatusCode: Reads[GetStatusCode] = (
      (JsPath \ "code").read[String] and
      (JsPath \ "reasonPhrase").read[String]
  )(GetStatusCode.apply _)
  
  implicit val validateGetContextResponse: Reads[GetContextResponse] = (
      (JsPath \ "contextElement").read[GetContextElement] and
      (JsPath \ "statusCode").read[GetStatusCode]
  )(GetContextResponse.apply _)
  
  implicit val validateSuccessfulGetPost: Reads[SuccessfulGetPost] = 
      (JsPath \ "contextResponses").read[Seq[GetContextResponse]].map{ contextResponses => SuccessfulGetPost(contextResponses)}
  
  // Get failure validators
  
  implicit val validateGetErrorCode: Reads[GetErrorCode] = (
      (JsPath \ "code").read[String] and
      (JsPath \ "reasonPhrase").read[String]
  )(GetErrorCode.apply _)
  
  implicit val validateFailedGet: Reads[FailedGetPost] = 
      (JsPath \ "errorCode").read[GetErrorCode].map{ errorCode => FailedGetPost(errorCode)}
}