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
  private val phrasesVar: Var[List[DailyGermanPhrase]] =
    Var[List[DailyGermanPhrase]](List.empty)
  private val clozeWordsVar: Var[Map[String, String]] =
    Var[Map[String, String]](Map.empty)
  private val hintsVar: Var[Map[String, String]] =
    Var[Map[String, String]](Map.empty) // New Var for hints

  val sentenceVar: Var[String] = Var("")
  val textToAudioClozeWordsVar: Var[String] = Var("")
  val textToAudioClozeHintsVar: Var[String] = Var("")
  val activeTabVar: Var[String] = Var("daily-german")

  // Audio to Cloze state
  val cardsWithoutAudioVar: Var[List[CardWithoutAudio]] = Var(List.empty)
  val selectedCardsVar: Var[Set[Long]] = Var(Set.empty)
  val processingVar: Var[Boolean] = Var(false)

  def getPhrases(): Unit = {
    val backend = FetchBackend()
    val response = basicRequest
      .post(uri"http://localhost:9000/api/daily-german/phrases")
      .body(DailyGermanUrl(urlVar.now()).asJson.noSpaces)
      .response(asJson[List[DailyGermanPhrase]])
      .send(backend)

    response.onComplete {
      case scala.util.Success(res) =>
        res.body match {
          case Right(phrases) => phrasesVar.set(phrases)
          case Left(err)      => dom.console.log(err.toString)
        }
      case scala.util.Failure(err) =>
        dom.console.log(err.getMessage)
    }
  }

  def createCard(phrase: DailyGermanPhrase): Unit = {
    val words = clozeWordsVar
      .now()
      .get(phrase.text)
      .map(_.split(",").map(_.trim))
      .getOrElse(Array.empty[String])
    val hints = hintsVar
      .now()
      .get(phrase.text)
      .map(_.split(",").map(_.trim))
      .getOrElse(Array.empty[String])

    val clozes = words
      .zipAll(hints, "", "")
      .map { case (word, hint) => Cloze(word, hint) }
      .toList

    val backend = FetchBackend()
    val response = basicRequest
      .post(uri"http://localhost:9000/api/daily-german/card")
      .body(CreateAnkiCardRequest(phrase, clozes).asJson.noSpaces)
      .send(backend)

    response.onComplete {
      case scala.util.Success(_)   => dom.window.alert("Card created!")
      case scala.util.Failure(err) => dom.console.log(err.getMessage)
    }
  }

  def createTextToAudioCard(): Unit = {
    val words =
      textToAudioClozeWordsVar.now().split(",").map(_.trim).toList
    val hints =
      textToAudioClozeHintsVar.now().split(",").map(_.trim).toList
    val clozes = words
      .zipAll(hints, "", "")
      .map { case (word, hint) => Cloze(word, hint) }
    val backend = FetchBackend()
    val response = basicRequest
      .post(uri"http://localhost:9000/api/text-to-audio/card")
      .body(TextToAudioRequest(sentenceVar.now(), clozes).asJson.noSpaces)
      .send(backend)

    response.onComplete {
      case scala.util.Success(_)   => dom.window.alert("Card created!")
      case scala.util.Failure(err) => dom.console.log(err.getMessage)
    }
  }

  // Audio to Cloze functions
  def fetchCardsWithoutAudio(): Unit = {
    val backend = FetchBackend()
    val response = basicRequest
      .get(uri"http://localhost:9000/api/audio-to-cloze/cards")
      .response(asJson[CardsWithoutAudioResponse])
      .send(backend)

    response.onComplete {
      case scala.util.Success(res) =>
        res.body match {
          case Right(resp) =>
            cardsWithoutAudioVar.set(resp.cards)
            selectedCardsVar.set(Set.empty)
          case Left(err) => dom.console.log(err.toString)
        }
      case scala.util.Failure(err) => dom.console.log(err.getMessage)
    }
  }

  def generateAudioForSelected(): Unit = {
    val selectedIds = selectedCardsVar.now().toList
    if (selectedIds.isEmpty) {
      dom.window.alert("Please select at least one card")
      return
    }

    processingVar.set(true)

    val backend = FetchBackend()
    val response = basicRequest
      .post(uri"http://localhost:9000/api/audio-to-cloze/generate")
      .body(GenerateAudioRequest(selectedIds).asJson.noSpaces)
      .response(asJson[GenerateAudioResponse])
      .send(backend)

    response.onComplete {
      case scala.util.Success(res) =>
        res.body match {
          case Right(result) =>
            processingVar.set(false)
            dom.window.alert(s"Processed: ${result.processed}, Failed: ${result.failed}")
            fetchCardsWithoutAudio()
          case Left(err) =>
            processingVar.set(false)
            dom.console.log(err.toString)
        }
      case scala.util.Failure(err) =>
        processingVar.set(false)
        dom.console.log(err.getMessage)
    }
  }

  def toggleCardSelection(noteId: Long): Unit = {
    selectedCardsVar.update { selected =>
      if (selected.contains(noteId)) selected - noteId
      else selected + noteId
    }
  }

  def selectAllCards(): Unit = {
    selectedCardsVar.set(cardsWithoutAudioVar.now().map(_.noteId).toSet)
  }

  def deselectAllCards(): Unit = {
    selectedCardsVar.set(Set.empty)
  }

  def appElement(): Element = {
    div(
      h1("GermanAnki"),
      div(
        cls("tabs"),
        button(
          "Daily German",
          cls <-- activeTabVar.signal.map(tab => if (tab == "daily-german") "tab active" else "tab"),
          onClick --> (_ => activeTabVar.set("daily-german"))
        ),
        button(
          "Text-to-Audio",
          cls <-- activeTabVar.signal.map(tab => if (tab == "text-to-audio") "tab active" else "tab"),
          onClick --> (_ => activeTabVar.set("text-to-audio"))
        ),
        button(
          "Audio to Cloze",
          cls <-- activeTabVar.signal.map(tab => if (tab == "audio-to-cloze") "tab active" else "tab"),
          onClick --> (_ => activeTabVar.set("audio-to-cloze"))
        )
      ),
      child <-- activeTabVar.signal.map {
        case "daily-german" =>
          div(
            h2("Daily German"),
            div(
              input(
                cls("card-input"),
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
                      onInput.mapToValue --> (s =>
                        clozeWordsVar.update(_ + (phrase.text -> s))
                      )
                    ),
                    br(),
                    input( // New input for hints
                      cls("card-input"),
                      placeholder("Enter hints (comma separated)"),
                      onInput.mapToValue --> (s =>
                        hintsVar.update(_ + (phrase.text -> s))
                      )
                    ),
                    br(),
                    br(),
                    button("Create Card", onClick --> (_ => createCard(phrase)))
                  )
                }
              }
            )
          )
        case "text-to-audio" =>
          div(
            h2("Text-to-Audio"),
            div(
              input(
                cls("card-input"),
                placeholder("Enter sentence"),
                onInput.mapToValue --> sentenceVar,
                value <-- sentenceVar
              ),
              br(),
              br(),
              input(
                cls("card-input"),
                placeholder("Enter cloze words (comma separated)"),
                onInput.mapToValue --> textToAudioClozeWordsVar,
                value <-- textToAudioClozeWordsVar
              ),
              br(),
              input( // New input for hints
                cls("card-input"),
                placeholder("Enter hints (comma separated)"),
                onInput.mapToValue --> textToAudioClozeHintsVar,
                value <-- textToAudioClozeHintsVar
              ),
              br(),
              br(),
              button("Create Card", onClick --> (_ => createTextToAudioCard()))
            )
          )
        case "audio-to-cloze" =>
          div(
            h2("Audio to Cloze"),
            div(
              button("Fetch Cards Without Audio", onClick --> (_ => fetchCardsWithoutAudio())),
              span(" "),
              button("Select All", onClick --> (_ => selectAllCards())),
              span(" "),
              button("Deselect All", onClick --> (_ => deselectAllCards())),
              span(" "),
              button(
                "Generate Audio for Selected",
                disabled <-- processingVar.signal,
                onClick --> (_ => generateAudioForSelected())
              )
            ),
            div(
              child <-- processingVar.signal.map { processing =>
                if (processing) p("Processing... Please wait.") else span()
              }
            ),
            div(
              child <-- cardsWithoutAudioVar.signal.map { cards =>
                if (cards.isEmpty) p("No cards without audio found. Click 'Fetch Cards' to load.")
                else p(s"Found ${cards.length} cards without audio")
              }
            ),
            ul(
              children <-- cardsWithoutAudioVar.signal.combineWith(selectedCardsVar.signal).map {
                case (cards, selected) =>
                  cards.map { card =>
                    li(
                      input(
                        typ("checkbox"),
                        checked(selected.contains(card.noteId)),
                        onChange --> (_ => toggleCardSelection(card.noteId))
                      ),
                      span(s" [${card.noteId}] ${card.plainText}"),
                      small(s" (${card.textField})")
                    )
                  }
              }
            )
          )
        case _ => div("Unknown tab")
      }
    )
  }

  def main(args: Array[String]): Unit = {
    renderOnDomContentLoaded(
      dom.document.getElementById("app"),
      appElement()
    )
  }
}
