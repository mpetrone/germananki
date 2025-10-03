import scala.concurrent.ExecutionContext
import akka.stream.Materializer
import akka.actor.ActorSystem
import io.cequence.openaiscala.service.OpenAIServiceFactory
import akka.stream.scaladsl.FileIO
import java.nio.file.Paths
import io.cequence.openaiscala.domain.settings.CreateSpeechSettings
import io.cequence.openaiscala.domain.settings.SpeechResponseFormatType.mp3
import scala.concurrent.Future
import io.cequence.openaiscala.service.adapter.OpenAIServiceAdapters
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import scala.util.Random
import io.cequence.openaiscala.domain.settings.VoiceType._

val API_KEY =
  ""
class OpenIA() {

  val logger: Logger = LoggerFactory.getLogger(this.getClass)
  given ec: ExecutionContext = ExecutionContext.global
  given materializer: Materializer = Materializer(ActorSystem())
  private val rawService = OpenAIServiceFactory(apiKey = API_KEY, orgId = None)
  private val adapters = OpenAIServiceAdapters.forFullService
  private val service = adapters.log(
    rawService,
    "openAIService",
    logger.info
  )

  def textToSpeech(filename: String, text: String): Future[Unit] = {
    (for {
      source <- service.createAudioSpeech(text, buildSettings())
      result <- source.runWith(FileIO.toPath(Paths.get(s"$filename.mp3")))
    } yield {
      ()
    }).recover { case ex =>
      logger.error("Error", ex)
    }
  }

  def buildSettings(): CreateSpeechSettings = {
    Random.between(0, 6) match {
      case 0 => CreateSpeechSettings("tts-1", alloy, Some(mp3), None)
      case 1 => CreateSpeechSettings("tts-1", echo, Some(mp3), None)
      case 2 => CreateSpeechSettings("tts-1", fable, Some(mp3), None)
      case 3 => CreateSpeechSettings("tts-1", onyx, Some(mp3), None)
      case 4 => CreateSpeechSettings("tts-1", nova, Some(mp3), None)
      case 5 => CreateSpeechSettings("tts-1", shimmer, Some(mp3), None)
    }
  }
}
