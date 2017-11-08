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

package controllers.swagger

import javax.inject._

import scala.concurrent.{ExecutionContext, Future}
import play.api.mvc._
import play.api.cache._
import com.iheart.playSwagger.SwaggerSpecGenerator

class ApiSpecs @Inject()(cached: Cached)(implicit ec: ExecutionContext) extends InjectedController {
  implicit val cl = getClass.getClassLoader

  // The root package of your domain classes, play-swagger will automatically generate definitions when it encounters class references in this package.
  // In our case it would be "com.iheart", play-swagger supports multiple domain package names
  val domainPackage = "com.agora-board"  
  val secondDomainPackage = "YOUR.OtherDOMAIN.PACKAGE"
  private lazy val generator = SwaggerSpecGenerator(domainPackage, secondDomainPackage)

  def specs = cached("swaggerDef") {  //it would be beneficial to cache this endpoint as we do here, but it's not required if you don't expect much traffic.   
     Action.async { _ =>
        Future.fromTry(generator.generate()).map(Ok(_)) //generate() can also taking in an optional arg of the route file name. 
      }           
  }

}