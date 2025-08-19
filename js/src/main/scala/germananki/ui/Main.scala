package germananki.ui

import com.raquo.laminar.api.L._
import org.scalajs.dom
import germananki.shared._
import sttp.client4._
import sttp.client4.circe._
import io.circe.generic.auto._
import io.circe.syntax._
import scala.concurrent.ExecutionContext.Implicits.global
import sttp.client4.fetch.FetchBackend

object Main {

  private val urlVar: Var[String] = Var("")
  private val phrasesVar: Var[List[DailyGermanPhrase]] = Var[List[DailyGermanPhrase]](List.empty)
  private val clozeWordsVar: Var[Map[String, String]] = Var[Map[String, String]](Map.empty)
  private val hintsVar: Var[Map[String, String]] = Var[Map[String, String]](Map.empty) // New Var for hints

  val sentenceVar: Var[String] = Var("")
  val textToAudioClozeWordsVar: Var[String] = Var("")

  def getPhrases(): Unit = {
    val backend = FetchBackend()
    val response = basicRequest
      .post(uri"http://localhost:8080/api/daily-german/phrases")
      .body(DailyGermanUrl(urlVar.now()).asJson.noSpaces)
      .response(asJson[List[DailyGermanPhrase]])
      .send(backend)

    response.onComplete {
      case scala.util.Success(res) =>
        res.body match {
          case Right(phrases) => phrasesVar.set(phrases)
          case Left(err) => dom.console.log(err.toString)
        }
      case scala.util.Failure(err) =>
        dom.console.log(err.getMessage)
    }
  }

  def createCard(phrase: DailyGermanPhrase): Unit = {
    val words = clozeWordsVar.now().get(phrase.text).map(_.split(",").map(_.trim)).getOrElse(Array.empty[String])
    val hints = hintsVar.now().get(phrase.text).map(_.split(",").map(_.trim)).getOrElse(Array.empty[String])

    val clozes = words.zipAll(hints, "", "").map { case (word, hint) => Cloze(word, hint) }.toList

    val backend = FetchBackend()
    val response = basicRequest
      .post(uri"http://localhost:8080/api/daily-german/card")
      .body(CreateAnkiCardRequest(phrase, clozes).asJson.noSpaces)
      .send(backend)

    response.onComplete {
      case scala.util.Success(_) => dom.window.alert("Card created!")
      case scala.util.Failure(err) => dom.console.log(err.getMessage)
    }
  }

  def createTextToAudioCard(): Unit = {
    val clozeWords = textToAudioClozeWordsVar.now().split(",").map(_.trim).toList
    val backend = FetchBackend()
    val response = basicRequest
      .post(uri"http://localhost:8080/api/text-to-audio/card")
      .body(TextToAudioRequest(sentenceVar.now(), clozeWords).asJson.noSpaces)
      .send(backend)

    response.onComplete {
      case scala.util.Success(_) => dom.window.alert("Card created!")
      case scala.util.Failure(err) => dom.console.log(err.getMessage)
    }
  }

  def appElement(): Element = {
    div(
      h1("GermanAnki"),
      div(
        h2("Daily German"),
        div(
          input(
            placeholder("Enter Daily German URL"),
            onInput.mapToValue --> urlVar,
            value <-- urlVar
          ), 
          br(),
          br(),   
          button("Get Phrases", onClick --> (_ => getPhrases()))
        ),
        ul(
          children <-- phrasesVar.signal.map { phrases =>
            phrases.map { phrase =>
              li(
                p(phrase.text),
                input(
                  cls("card-input"),
                  placeholder("Enter cloze words (comma separated)"),
                  onInput.mapToValue --> (s => clozeWordsVar.update(_ + (phrase.text -> s)))
                ),
                br(),
                input( // New input for hints
                  cls("card-input"),
                  placeholder("Enter hints (comma separated)"),
                  onInput.mapToValue --> (s => hintsVar.update(_ + (phrase.text -> s)))
                ), 
                  br(),
                  br(),
                  button("Create Card", onClick --> (_ => createCard(phrase)))
              )
            }
          }
        )
      ),
      div(
        h2("Text-to-Audio"),
        div(
          input(
            placeholder("Enter sentence"),
            onInput.mapToValue --> sentenceVar,
            value <-- sentenceVar
          ),
            br(),
          input(
            placeholder("Enter cloze words (comma separated)"),
            onInput.mapToValue --> textToAudioClozeWordsVar,
            value <-- textToAudioClozeWordsVar
          ),
            br(),
            br(),
          button("Create Card", onClick --> (_ => createTextToAudioCard()))
        )
      )
    )
  }

  def main(args: Array[String]): Unit = {
    renderOnDomContentLoaded(
      dom.document.getElementById("app"),
      appElement()
    )
  }
}
