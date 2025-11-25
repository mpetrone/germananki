import cats.effect._
import com.comcast.ip4s._
import org.http4s.ember.server._
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.server.Router
import org.http4s.HttpRoutes
import org.http4s.circe._
import io.circe.generic.auto._
import io.circe.syntax._
import germananki.shared._
import cats.implicits._
import scala.concurrent.Future
import org.http4s.circe.CirceEntityCodec._
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.noop.NoOpFactory
import org.http4s.server.staticcontent._
import fs2.io.file.Files

object Main extends IOApp.Simple {

  implicit val loggerFactory: LoggerFactory[IO] = NoOpFactory[IO]

  // Load cached cookies on startup
  DailyGerman.loadCachedCookies()

  val openIA = new OpenIA()

  val dailyGermanRoutes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ POST -> Root / "daily-german" / "phrases" =>
      for {
        dailyGermanUrl <- req.as[DailyGermanUrl]
        phrases <- IO.blocking(DailyGerman.getWebInfo(dailyGermanUrl.url))
        resp <- Ok(
          phrases.map(p => DailyGermanPhrase(p.text, p.mp3Link)).asJson
        )
      } yield resp

    case req @ POST -> Root / "daily-german" / "card" =>
      for {
        createCardRequest <- req.as[CreateAnkiCardRequest]
        clozeText = createCardRequest.clozes.zipWithIndex.foldLeft(
          createCardRequest.phrase.text
        ) { case (acc, (cloze, i)) =>
          acc.replace(cloze.word, s"{{c1::${cloze.word}::${cloze.hint}}}")
        }
        fileName = createCardRequest.phrase.mp3Link.substring(
          createCardRequest.phrase.mp3Link.lastIndexOf('/') + 1
        )
        note = AnkiApi.AnkiNoteInput(
          "German",
          "Cloze German",
          Map("Text" -> clozeText),
          List(
            AnkiApi.AnkiAudioUrl(
              createCardRequest.phrase.mp3Link,
              fileName,
              List("Audio")
            )
          )
        )
        _ <- IO.blocking(AnkiApi.addNote(note))
        resp <- Ok()
      } yield resp
  }

  val textToAudioRoutes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ POST -> Root / "text-to-audio" / "card" =>
      for {
        textToAudioRequest <- req.as[TextToAudioRequest]
        clozeText = textToAudioRequest.clozeWords.zipWithIndex.foldLeft(
          textToAudioRequest.sentence
        ) { case (acc, (cloze, i)) =>
          acc.replace(cloze.word, s"{{c1::${cloze.word}::${cloze.hint}}}")
        }
        fileName = java.util.UUID.randomUUID().toString
        _ <- IO.fromFuture(
          IO(openIA.textToSpeech(fileName, textToAudioRequest.sentence))
        )
        note = AnkiApi.AnkiNoteInput(
          "German",
          "Cloze German",
          Map("Text" -> clozeText),
          List(
            AnkiApi.AnkiAudioPath(
              s"/home/petrm/Development/Scala/germananki/$fileName.mp3",
              fileName,
              List("Audio")
            )
          )
        )
        _ <- IO.blocking(AnkiApi.addNote(note))
        resp <- Ok()
      } yield resp
  }

  val apiRoutes = dailyGermanRoutes <+> textToAudioRoutes

  val staticContentService =
    fileService[IO](FileService.Config("./js/src/main/resources"))
  val jsService = fileService[IO](
    FileService.Config("./js/target/scala-3.3.6/germananki-js-fastopt")
  )

  val httpApp = Router(
    "/api" -> apiRoutes,
    "/assets" -> jsService, // More specific path first
    "/" -> staticContentService
  ).orNotFound

  def run: IO[Unit] =
    EmberServerBuilder
      .default[IO]
      .withHost(ipv4"0.0.0.0")
      .withPort(port"9000")
      .withHttpApp(httpApp)
      .build
      .use(_ => IO.never)
}
