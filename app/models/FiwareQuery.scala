package models

import play.api.libs.json._
import play.api.libs.json.Reads._
import play.api.libs.functional.syntax._

// Classes used for validating and parsing JSON

case class GetAttribute(name: String, _type: String, value: JsValue)
case class GetContextElement(id: String, isPattern: String, _type: String, attributes: Seq[GetAttribute])
case class GetStatusCode(code: String, reasonPhrase: String)
case class GetContextResponse(contextElement: GetContextElement, statusCode: GetStatusCode)
case class SuccessfulGetPost(contextResponses: Seq[GetContextResponse])

case class SubscribeResponse(subscriptionId: String, duration: String, throttling: String)
case class SuccessfulSubscribe(subscribeResponse: SubscribeResponse)

case class GetErrorCode(code: String, reasonPhrase: String)
case class FailedGetPost(errorCode: GetErrorCode)

trait FiwareQueryReadValidator {
  
  // Subscribe success validators
  
  implicit val validateSubscribeResponseRead: Reads[SubscribeResponse] = (
      (JsPath \ "subscriptionId").read[String] and
      (JsPath \ "duration").read[String] and
      (JsPath \ "throttling").read[String]
  )(SubscribeResponse.apply _)
  
  implicit val validateSuccessfulSubscribeRead: Reads[SuccessfulSubscribe] = 
      (JsPath \ "subscribeResponse").read[SubscribeResponse].map{ errorCode => SuccessfulSubscribe(errorCode)}
  
  // Get success validators
  
  implicit val validateGetAttributeRead: Reads[GetAttribute] = (
      (JsPath \ "name").read[String] and
      (JsPath \ "type").read[String] and
      (JsPath \ "value").read[JsValue]
  )(GetAttribute.apply _)
  
  implicit val validateGetContextElementRead: Reads[GetContextElement] = (
      (JsPath \ "id").read[String] and
      (JsPath \ "isPattern").read[String] and
      (JsPath \ "type").read[String] and
      (JsPath \ "attributes").read[Seq[GetAttribute]](minLength[Seq[GetAttribute]](1) keepAnd maxLength[Seq[GetAttribute]](1))
  )(GetContextElement.apply _)
  
  implicit val validateGetStatusCodeRead: Reads[GetStatusCode] = (
      (JsPath \ "code").read[String] and
      (JsPath \ "reasonPhrase").read[String]
  )(GetStatusCode.apply _)
  
  implicit val validateGetContextResponseRead: Reads[GetContextResponse] = (
      (JsPath \ "contextElement").read[GetContextElement] and
      (JsPath \ "statusCode").read[GetStatusCode]
  )(GetContextResponse.apply _)
  
  implicit val validateSuccessfulGetPostRead: Reads[SuccessfulGetPost] = 
      (JsPath \ "contextResponses").read[Seq[GetContextResponse]].map{ contextResponses => SuccessfulGetPost(contextResponses)}
  
  // Get failure validators
  
  implicit val validateGetErrorCodeRead: Reads[GetErrorCode] = (
      (JsPath \ "code").read[String] and
      (JsPath \ "reasonPhrase").read[String]
  )(GetErrorCode.apply _)
    
  // see http://stackoverflow.com/questions/14754092/how-to-turn-json-to-case-class-when-case-class-has-only-one-field
  implicit val validateFailedGetRead: Reads[FailedGetPost] = 
      (JsPath \ "errorCode").read[GetErrorCode].map{ errorCode => FailedGetPost(errorCode)}
}

trait FiwareQueryWriteValidator {
  
  implicit val validateSubscribeResponseWrite: Writes[SubscribeResponse] = (
      (JsPath \ "subscriptionId").write[String] and
      (JsPath \ "duration").write[String] and
      (JsPath \ "throttling").write[String]
  )(unlift(SubscribeResponse.unapply))
  
  // see http://stackoverflow.com/questions/14754092/how-to-turn-json-to-case-class-when-case-class-has-only-one-field
  implicit val validateSuccessfulSubscribeWrite: Writes[SuccessfulSubscribe] = 
      (JsPath \ "subscribeResponse").write[SubscribeResponse].contramap { a: SuccessfulSubscribe => a.subscribeResponse }
  
}