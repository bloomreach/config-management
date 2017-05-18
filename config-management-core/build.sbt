organization := "com.bloomreach.config-management-system"

name := "config-management-core"

version := "1.0-SNAPSHOT"

scalaVersion := "2.11.8"

crossPaths := false

exportJars := true

isSnapshot := true

resolvers += Resolver.mavenLocal

libraryDependencies += "org.scala-lang.modules" %% "scala-xml" % "1.0.5"

libraryDependencies += "org.scalatest" % "scalatest_2.11" % "2.2.6" % "test"

libraryDependencies += "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0"

libraryDependencies += "org.json4s" %% "json4s-native" % "3.3.0"

libraryDependencies += "org.json4s" %% "json4s-jackson" % "3.3.0"

libraryDependencies += "net.java.dev.jets3t" % "jets3t" % "0.9.4"

libraryDependencies += "com.jayway.jsonpath" % "json-path" % "2.2.0"

libraryDependencies += "commons-lang" % "commons-lang" % "2.6"

libraryDependencies += "commons-io" % "commons-io" % "2.5"

libraryDependencies += "redis.clients" % "jedis" % "2.1.0"

libraryDependencies += "org.mockito" % "mockito-all" % "1.9.5"

publishMavenStyle := true
publishTo := Some(Resolver.mavenLocal)
