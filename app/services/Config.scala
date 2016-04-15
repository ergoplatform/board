package services

import javax.inject._

case class Server(address: String, port: String, dockerAddress: String)
case class Fiware(addressPort: String)

@Singleton
class Config @Inject() 
                     (configuration: play.api.Configuration)  
{
  val fiware = Fiware(configuration.getString("play.fiware.address_port").getOrElse("localhost:1026"))
  val server = Server(configuration.getString("play.server.http.address").getOrElse("0:0:0:0:0:0:0:0"), 
                   configuration.getString("play.server.http.port").getOrElse("9000"),
                   configuration.getString("play.server.docker.address").getOrElse("172.17.0.1"))
}