import net.ruippeixotog.scalascraper.browser.JsoupBrowser
import net.ruippeixotog.scalascraper.dsl.DSL._
import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
import net.ruippeixotog.scalascraper.dsl.DSL.Parse._
import scala.io.StdIn.readLine

object DailyGerman {
  case class CardInfo(text: String, mp3Link: String)

  def getWebInfo(url: String): List[CardInfo] = {
    val browser = JsoupBrowser()
    val doc = browser.get(url)

    val allPostExamples = doc >> elementList(".post-example")

    val textsWithSound = allPostExamples.flatMap { ulElement =>
        if (ulElement.childNodes.toSeq.length == 4) {
            (ulElement >> elementList("li"))
                .headOption
                .map(_.innerHtml)
        } else {
            None
        }
    }

  val audios = allPostExamples.flatMap { ulElement =>
      if (ulElement.childNodes.toSeq.length == 4) {
          (ulElement >> elementList("li"))(2)  >?> element("source") >> attr("src")
      } else {
          None
      }
  }

     if(textsWithSound.length != audios.length) {
         throw new RuntimeException("texts and audios have different sizes, the parsing is incorrect")
     }

      textsWithSound.zip(audios).map(CardInfo.apply)
  }

  def addClozeFromDailyGerman(url: String): Unit = {
    val cards = DailyGerman.getWebInfo(url)

    cards.foreach { card =>
      val fileName = card.mp3Link.substring(card.mp3Link.lastIndexOf('/') + 1, card.mp3Link.length())
      println(s"Text for the new card: ${card.text}")
      println(s"Which cloze do you wanna add (coma separated)?")
      val clozes = readLine().split(",").map(_.trim())
      if(clozes.exists(!_.isBlank)) {
        println(s"Which word do you wanna show?")
        val showies = readLine().split(",").map(_.trim())
        val clozeText = clozes.indices.foldLeft(card.text)((acc, i) => acc.replace(clozes(i), s"{{c1::${clozes(i)}::${showies.lift(i).getOrElse("")}}}"))
        println(s"Result cloze card will be: $clozeText")
        val note = AnkiApi.AnkiNoteInput("German", "Cloze German",
          Map("Text" -> clozeText), List(AnkiApi.AnkiAudioUrl(card.mp3Link, fileName, List("Audio"))))
        AnkiApi.addNote(note)
      } else  println(s"Skipping card")
      println()
      println()
    }
  }
}
