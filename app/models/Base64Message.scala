package models

import play.api.libs.json._
import java.util.Base64
import java.nio.charset.StandardCharsets
import java.math.BigInteger

class Base64Message(js: JsValue = JsNull) {
  // decoded bytes
  private var decodedBytes = Json.prettyPrint(js).getBytes(StandardCharsets.UTF_8)
  // Encode UTF-8 string to Base64
  private var encoded =  Base64.getEncoder.encodeToString(decodedBytes)
  // B64 encoded
  override def toString(): String = {
    encoded
  }
   
  def +(that: Base64Message) : Base64Message = {
    var ret = new Base64Message
    ret.decodedBytes = decodedBytes ++ that.decodedBytes
    ret.encoded = Base64.getEncoder.encodeToString(ret.decodedBytes)
    ret
  }
  
  def getBigInteger(): BigInteger = {
    new BigInteger(decodedBytes)
  }

  def decode() : String= {
    new String(decodedBytes)
  }
}