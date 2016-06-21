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
import java.util.Base64
import java.nio.charset.StandardCharsets
import java.math.BigInteger

class Base64Message(str: String) {
  // decoded bytes
  private var decodedBytes = str.getBytes(StandardCharsets.UTF_8)
  // Encode UTF-8 string to Base64
  private var encoded =  Base64.getEncoder.encodeToString(decodedBytes)
  // B64 encoded
  override def toString(): String = {
    encoded
  }
   
  def +(that: Base64Message) : Base64Message = {
    var ret = new Base64Message("")
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