// This file is part of agora-board.
// Copyright (C) 2016  Agora Voting SL <agora@agoravoting.com>

// agora-board is free software: you can redistribute it and/or modify
// it under the terms of the GNU Lesser General Public License as published by
// the Free Software Foundation, either version 3 of the License.

// agora-board  is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU Lesser General Public License for more details.

// You should have received a copy of the GNU Lesser General Public License
// along with agora-board.  If not, see <http://www.gnu.org/licenses/>.

name := """agora-board"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.7"

resolvers += Resolver.jcenterRepo

libraryDependencies ++= Seq(
  jdbc,
  cache,
  ws,
  "org.scalatestplus.play" %% "scalatestplus-play" % "1.5.0-RC1" % Test,
  "com.iheart" %% "play-swagger" % "0.2.2-PLAY2.5",
  "org.webjars" % "swagger-ui" % "2.1.4"
)

resolvers += "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases"

PlayKeys.devSettings := Seq("play.server.http.port" -> "9258", "play.server.http.address" -> "0:0:0:0:0:0:0:0")

fork in run := false
