import sbt.Keys._
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._

val scala3Version = "3.3.6"

lazy val root = project
  .in(file("."))
  .aggregate(jvm, js)
  .settings(
    name := "germananki"
  )

lazy val jvm = project
  .in(file("jvm"))
  .settings(
    name := "germananki-jvm",
    version := "0.1.0-SNAPSHOT",
    scalaVersion := scala3Version,
     libraryDependencies ++= Seq(
       "net.ruippeixotog" %% "scala-scraper" % "3.2.0",
       "com.softwaremill.sttp.client4" %% "circe" % "4.0.9",
       "com.softwaremill.sttp.client4" %% "upickle" % "4.0.9",
       "io.cequence" %% "openai-scala-client" % "1.2.0",
       "ch.qos.logback" % "logback-classic" % "1.5.13",
       "org.http4s" %% "http4s-ember-server" % "1.0.0-M40",
       "org.http4s" %% "http4s-ember-client" % "1.0.0-M40",
       "org.http4s" %% "http4s-dsl" % "1.0.0-M40",
       "org.http4s" %% "http4s-circe" % "1.0.0-M40",
       "io.circe" %% "circe-generic" % "0.14.6",
       "org.typelevel" %% "log4cats-noop" % "2.6.0",
       "co.fs2" %% "fs2-io" % "3.9.4",
       "io.github.cdimascio" % "java-dotenv" % "5.2.2"
     )
  )
  .dependsOn(shared)

lazy val js = project
  .in(file("js"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    name := "germananki-js",
    version := "0.1.0-SNAPSHOT",
    scalaVersion := scala3Version,
    libraryDependencies ++= Seq(
      "com.raquo" %%% "laminar" % "16.0.0",
      "com.softwaremill.sttp.client4" %%% "core" % "4.0.9",
      "com.softwaremill.sttp.client4" %%% "circe" % "4.0.9"
    ),
    scalaJSUseMainModuleInitializer := true
  )
  .dependsOn(shared)

lazy val shared = project
  .in(file("shared"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    name := "germananki-shared",
    version := "0.1.0-SNAPSHOT",
    scalaVersion := scala3Version,
    libraryDependencies ++= Seq(
      "io.circe" %%% "circe-generic" % "0.14.6",
      "io.circe" %%% "circe-parser" % "0.14.6"
    )
  )
