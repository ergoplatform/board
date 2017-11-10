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

lazy val root = (project in file(".")).enablePlugins(PlayScala, SwaggerPlugin)

swaggerDomainNameSpaces := Seq("com.agora-board")

scalaVersion := "2.12.4"

resolvers += Resolver.jcenterRepo

resolvers += Resolver.sonatypeRepo("releases")

resolvers += Resolver.sonatypeRepo("snapshots")

libraryDependencies ++= Seq(
  jdbc,
  ehcache,
  guice,
  ws,
  "com.typesafe.play" %% "play-json" % "2.6.7",
  "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.2" % Test,
  "org.webjars" % "swagger-ui" % "3.2.0",
  "org.scorexfoundation" %% "scrypto" % "2.0.3"
)

PlayKeys.devSettings := Seq("play.server.http.port" -> "9258", "play.server.http.address" -> "0:0:0:0:0:0:0:0")

fork in run := true
