scalaVersion := "2.13.2"
organization := "org.pac4j"

version      := "1.0.0-SNAPSHOT"

val circeVersion = "0.13.0"
val http4sVersion = "0.21.4"
val pac4jVersion = "4.0.0"
val specs2Version = "4.8.3"

libraryDependencies ++= Seq(
  "io.circe" %% "circe-core" % circeVersion,
  "io.circe" %% "circe-jawn" % circeVersion,
  "org.http4s" %% "http4s-dsl" % http4sVersion,
  "org.http4s" %% "http4s-server" % http4sVersion,
  "org.pac4j" % "pac4j-core" % pac4jVersion,
  "org.slf4j" % "slf4j-api" % "1.7.30",

  "io.circe" %% "circe-optics" % circeVersion % Test,
  "org.http4s" %% "http4s-jawn" % http4sVersion % Test,
  "org.specs2" %% "specs2-matcher-extra" % specs2Version % Test,
  "org.specs2" %% "specs2-scalacheck" % specs2Version % Test,
  "org.specs2" %% "specs2-scalaz" % specs2Version % Test
)

scalacOptions ++= Seq("-language:implicitConversions", "-language:higherKinds")
