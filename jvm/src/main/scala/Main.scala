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
        _ <- IO(println(s"Creating card for phrase: ${createCardRequest.phrase.text}"))
        _ <- IO(println(s"Clozes: ${createCardRequest.clozes}"))
        clozeText = createCardRequest.clozes.zipWithIndex.foldLeft(
          createCardRequest.phrase.text
        ) { case (acc, (cloze, i)) =>
          acc.replace(cloze.word, s"{{c1::${cloze.word}::${cloze.hint}}}")
        }
        _ <- IO(println(s"Cloze text generated: $clozeText"))
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
        _ <- IO(println(s"Sending note to AnkiConnect..."))
        _ <- IO.blocking(AnkiApi.addNote(note)).handleErrorWith { ex =>
          IO(println(s"ERROR creating Anki card: ${ex.getMessage}")) *>
          IO(ex.printStackTrace()) *>
          IO.raiseError(ex)
        }
        _ <- IO(println(s"Card created successfully"))
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

  // Helper function to extract plain text from cloze format
  def extractPlainTextFromCloze(clozeText: String): String = {
    // First remove [sound:...] tags
    val soundPattern = """\[sound:[^\]]+\]""".r
    val withoutSound = soundPattern.replaceAllIn(clozeText, "")
    // Then remove cloze patterns {{c1::word}} or {{c1::word::hint}}
    val clozePattern = """\{\{c\d+::(.*?)(?:::[^}]*)?\}\}""".r
    clozePattern.replaceAllIn(withoutSound, m => m.group(1))
  }

  // Check if a note has audio in any field
  def noteHasAudio(note: AnkiApi.AnkiNoteInfo): Boolean = {
    note.fields.values.exists(_.value.contains("[sound:"))
  }

  // Check if a note is a reverse card (has "Reverse" in Extra field)
  def isReverseCard(note: AnkiApi.AnkiNoteInfo): Boolean = {
    note.fields.get("Extra").exists(_.value.contains("Reverse"))
  }

  val audioToClozeRoutes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    // GET /api/audio-to-cloze/cards - Fetch all cards without audio
    case GET -> Root / "audio-to-cloze" / "cards" =>
      for {
        noteIds <- IO.blocking(AnkiApi.findNotes("deck:German note:\"Cloze German\""))
        notesInfo <- IO.blocking(AnkiApi.getNotesInfo(noteIds))
        cardsWithoutAudio = notesInfo.filter { note =>
          !noteHasAudio(note) && !isReverseCard(note)
        }.map { note =>
          val textField = note.fields.get("Text").map(_.value).getOrElse("")
          val plainText = extractPlainTextFromCloze(textField)
          CardWithoutAudio(note.noteId, textField, plainText)
        }
        resp <- Ok(CardsWithoutAudioResponse(cardsWithoutAudio).asJson)
      } yield resp

    // POST /api/audio-to-cloze/generate - Generate audio for selected cards
    case req @ POST -> Root / "audio-to-cloze" / "generate" =>
      for {
        request <- req.as[GenerateAudioRequest]
        notesInfo <- IO.blocking(AnkiApi.getNotesInfo(request.noteIds))
        results <- notesInfo.traverse { note =>
          val textField = note.fields.get("Text").map(_.value).getOrElse("")
          val plainText = extractPlainTextFromCloze(textField)
          val fileName = java.util.UUID.randomUUID().toString
          val audioPath = s"/Users/matias.petrone/IdeaProjects/germananki/$fileName.mp3"

          (for {
            _ <- IO.fromFuture(IO(openIA.textToSpeech(fileName, plainText)))
            updateNote = AnkiApi.AnkiUpdateNote(
              id = note.noteId,
              tags = note.tags,
              fields = Map.empty,
              audio = List(AnkiApi.AnkiAudioPath(audioPath, s"$fileName.mp3", List("Audio")))
            )
            _ <- IO.blocking(AnkiApi.updateNote(updateNote))
          } yield true).handleError(_ => false)
        }
        processed = results.count(_ == true)
        failed = results.count(_ == false)
        resp <- Ok(GenerateAudioResponse(processed, failed).asJson)
      } yield resp
  }

  val apiRoutes = dailyGermanRoutes <+> textToAudioRoutes <+> audioToClozeRoutes

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
