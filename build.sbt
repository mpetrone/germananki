val scala3Version = "3.3.1"

lazy val root = project
  .in(file("."))
  .settings(
    name := "germananki",
    version := "0.1.0-SNAPSHOT",

    scalaVersion := scala3Version,

    libraryDependencies ++= Seq(
      "net.ruippeixotog" %% "scala-scraper" % "3.2.0",
      "com.softwaremill.sttp.client4" %% "circe" % "4.0.9",
      "com.softwaremill.sttp.client4" %% "upickle" % "4.0.9",
      "io.cequence" %% "openai-scala-client" % "1.2.0",
      "ch.qos.logback" % "logback-classic" % "1.5.13"
    )
  )