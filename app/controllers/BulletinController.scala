package controllers

import javax.inject._
import play.api._
import play.api.mvc._


/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
@Singleton
class BulletinController @Inject() extends Controller {

  case class UserAttributes(section: String, group: String, pk: String, signature: String)
  case class BoardPost(message: String, user_attributes: UserAttributes)
  
  import play.api.libs.json._
  import play.api.libs.functional.syntax._
  
  implicit val userAttributesReads: Reads[UserAttributes] = (
      (JsPath \ "section").read[String] and
      (JsPath \ "group").read[String] and
      (JsPath \ "pk").read[String] and
      (JsPath \ "signature").read[String] 
  )(UserAttributes.apply _)
  
  implicit val boardPostReads: Reads[BoardPost] = (
      (JsPath \ "message").read[String] and
      (JsPath \ "user_attributes").read[UserAttributes]
  )(BoardPost.apply _)
  
  /**
   * {
   *     message: m,
   *     user_attributes: 
   *     {
   *         section: s,
   *         group: g,
   *         pk: public_key,
   *         signature: S
   *     }
   * }
   */
  
  def post = Action { request =>
    Logger.info(s"action: Board POST")
    request.body.asJson match {
      case Some(json_data) => process_post(json_data)
      case None => BadRequest("Bad request: not a json\n")
    }
  }
  
  private def process_post(json : libs.json.JsValue)  : Result = {
    json.validate[BoardPost] match {
      case s: JsSuccess[BoardPost] => Ok("json:\n" + s +"\n")
      case e: JsError => BadRequest("json:\n" + e +"\n")
    }
  }

}
