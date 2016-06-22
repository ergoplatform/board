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

import com.google.inject.AbstractModule
import java.time.Clock
import models.BoardBackend
import services.FiwareBackend
import services.Config

/**
 * This class is a Guice module that tells Guice how to bind several
 * different types. This Guice module is created when the Play
 * application starts.

 * Play will automatically use any class called `Module` that is in
 * the root package. You can create modules in other locations by
 * adding `play.modules.enabled` settings to the `application.conf`
 * configuration file.
 */
class Module extends AbstractModule {

  override def configure() = {
    // Set FiwareBackend as the implementation for BoardBackend.
    bind(classOf[BoardBackend]).to(classOf[FiwareBackend])
    // Ask Guice to create an instance of FiwareBackend when the
    // application starts, reducing latency on the first Backend call
    bind(classOf[FiwareBackend]).asEagerSingleton
    bind(classOf[Config]).asEagerSingleton
  }

}
