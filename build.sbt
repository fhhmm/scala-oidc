import play.sbt.PlayImport._

ThisBuild / scalaVersion := "3.3.1"
ThisBuild / version := "0.1.0"

lazy val commonSettings = Seq(
  libraryDependencies ++= Seq(
    guice,
    ws,
    "com.auth0" % "java-jwt" % "4.4.0",
    "com.typesafe.play" %% "play-json" % "2.10.0-RC5"
  )
)

lazy val root = (project in file("."))
  .aggregate(clientServer, authServer, resourceServer)
  .settings(
    name := "scala-oidc-root"
  )

lazy val clientServer = (project in file("clientServer"))
  .enablePlugins(PlayScala)
  .settings(
    name := "clientServer"
  )
  .settings(commonSettings)

lazy val authServer = (project in file("authServer"))
  .enablePlugins(PlayScala)
  .settings(
    name := "authServer"
  )
  .settings(commonSettings)

lazy val resourceServer = (project in file("resourceServer"))
  .enablePlugins(PlayScala)
  .settings(
    name := "resourceServer"
  )
  .settings(commonSettings)
