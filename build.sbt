import play.sbt.PlayImport._

enablePlugins(PlayScala)

name := "scala-oidc"

version := "0.1.0"

scalaVersion := "3.3.1"

libraryDependencies ++= Seq(
  guice,
  ws,
  "com.auth0" % "java-jwt" % "4.4.0",
  "com.typesafe.play" %% "play-json" % "2.10.0-RC5",
  "com.auth0" % "java-jwt" % "4.4.0"
)
