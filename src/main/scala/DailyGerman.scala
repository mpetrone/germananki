import net.ruippeixotog.scalascraper.browser.JsoupBrowser
import net.ruippeixotog.scalascraper.dsl.DSL._
import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
import net.ruippeixotog.scalascraper.dsl.DSL.Parse._
import org.jsoup.nodes.DocumentType
import scala.io.StdIn.readLine

object DailyGerman {
  case class CardInfo(text: String, mp3Link: String)

  def getWebInfo(url: String): List[CardInfo] = {
    val browser = JsoupBrowser()
    val doc = browser.get(url)
    val texts = 
    (doc >> elementList(".post-example") >?> element("li") .map(_.innerHtml)).flatten

    val audios = 
      (doc >> elementList(".post-example") >?> element("source") >> attr("src")).flatten

    texts.zip(audios).map(CardInfo.apply)  
  }

  def addClozeFromDailyGerman(ip: String, url: String) = {
    val cards = DailyGerman.getWebInfo(url)
    
    cards.foreach { card =>
      val fileName = card.mp3Link.substring(card.mp3Link.lastIndexOf('/') + 1, card.mp3Link.length());
      println(s"Text for the new card: ${card.text}")
      println(s"Which cloze do you wanna add (coma separated)?")
      val clozes = readLine().split(",").map(_.trim())
      if(!clozes.filter(!_.isBlank()).isEmpty) {
        println(s"Which word do you wanna show?")
        val showies = readLine().split(",").map(_.trim())
        val clozeText = (0 to clozes.size-1).foldLeft(card.text)((acc, i) => acc.replace(clozes(i), s"{{c1::${clozes(i)}::${showies.lift(i).getOrElse("")}}}"))
        println(s"Result cloze card will be: $clozeText")
        val note = AnkiApi.AnkiNoteInput("German", "Cloze German", 
          Map("Text" -> clozeText), List(AnkiApi.AnkiAudioUrl(card.mp3Link, fileName, List("Audio"))))
        AnkiApi.addNote(ip, note)
      } else  println(s"Skiping card")
      println()
      println()
    }
  }
}