package models

import play.api.libs.json._
import java.util.Base64
import java.nio.charset.StandardCharsets

class Base64Message(js: JsValue) {
   // Encode UTF-8 string to Base64
   private var encoded =  Base64.getEncoder.encodeToString(Json.prettyPrint(js).getBytes(StandardCharsets.UTF_8))
   // B64 encoded
   override def toString(): String = {
     encoded
   }
   
   def +(that: Base64Message) : Base64Message = {
     var ret = new Base64Message(JsNull)
     encoded = encoded + that.encoded
     ret
   }
}