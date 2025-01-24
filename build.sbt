val scala3Version = "3.3.1"

lazy val root = project
  .in(file("."))
  .settings(
    name := "germananki",
    version := "0.1.0-SNAPSHOT",

    scalaVersion := scala3Version,

    libraryDependencies ++= Seq(
      "net.ruippeixotog" %% "scala-scraper" % "3.1.0",
      "com.softwaremill.sttp.client4" %% "circe" % "4.0.0-M8",
      "com.softwaremill.sttp.client4" %% "upickle" % "4.0.0-M8",
      "io.cequence" %% "openai-scala-client" % "1.1.0.RC.1",
      "ch.qos.logback" % "logback-classic" % "1.4.14" 
    )
  )