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

package services

import javax.inject._

case class Server(address: String, port: String, dockerAddress: String)
case class Fiware(addressPort: String)

@Singleton
class Config @Inject()(configuration: play.api.Configuration) {
  val fiware = Fiware(
    configuration.getOptional[String]("play.fiware.address_port").getOrElse("localhost:1026")
  )
  val server = Server(
    configuration.getOptional[String]("play.server.http.address").getOrElse("0:0:0:0:0:0:0:0"),
    configuration.getOptional[String]("play.server.http.port").getOrElse("9258"),
    configuration.getOptional[String]("play.server.docker.address").getOrElse("172.17.0.1")
  )
}