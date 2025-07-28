import scala.util._
import sys.process._
import scala.io.StdIn.readLine
import AnkiApi.AnkiNoteInfo
import scala.concurrent.Future
import scala.concurrent.Await
import concurrent.duration.DurationInt

@main def run(): Unit = {
  //searchAndReplace()
  //textToAudio()
  DailyGerman.addClozeFromDailyGerman("https://yourdailygerman.com/borgen-leihen-meaning/")
}

def stripHtmlTags(input: String): String = {
  val htmlTagRegex = """<[^>]*>""".r
  val cleaned = htmlTagRegex.replaceAllIn(input, "")
  cleaned.replaceAll("&nbsp;", " ")
}

def searchAndReplace(): Unit = {
  val notesId = AnkiApi.findNotes("""-sound deck:German card:Cloze -Reverse""")
  val notesInfo = AnkiApi.getNotesInfo(notesId)

  val updateNotes: List[(AnkiNoteInfo, AnkiApi.AnkiUpdateNote)] = notesInfo
  .map { noteInfo =>
    val newFields = noteInfo.fields.mapValues{field => stripHtmlTags(field.value)}.toMap
    noteInfo -> AnkiApi.AnkiUpdateNote(noteInfo.noteId, noteInfo.tags, newFields, List())
  }

  updateNotes.foreach{ case (old, update) =>
    println(s"Old: ${old.fields.mkString("\n")}")
    println()
    println(s"New: ${update.fields.mkString("\n")}")
    println()
    println("Would you like to update it? y/n")
    val answer = readLine().trim()
    if(answer == "y")
      AnkiApi.updateNote(update)
  }
}

def textToAudio(): Unit = {
  val openIA = new OpenIA()
  val notesId = AnkiApi.findNotes("""-sound deck:German card:Cloze""")
  val notesInfo = AnkiApi.getNotesInfo(notesId)

  val clozePattern = """\{\{c\d+::([^:}]+)(?:::.*?)?\}\}""".r
    
  notesInfo.foreach{ noteInfo =>
    val text = noteInfo.fields.get("Text").map(v => clozePattern.replaceAllIn(v.value, m => m.group(1))).get
    val fileName = noteInfo.noteId.toString
    println(s"Text to Speech for: $text")
    Await.result(openIA.textToSpeech(fileName, text), 40.seconds)
    val audio = List(AnkiApi.AnkiAudioPath(s"/Users/petrm/IdeaProjects/germananki/$fileName.mp3", fileName, List("Audio")))
    val newFields = noteInfo.fields.mapValues(_.value).toMap
    val updateNote = AnkiApi.AnkiUpdateNote(noteInfo.noteId, noteInfo.tags, newFields, audio)

    println(s"Old: $noteInfo")
    println()
    println(s"New: $updateNote")
    println()
    println()
    println()
    AnkiApi.updateNote(updateNote)
    Thread.sleep(10000)
  }

  println()
  println()
  println(s"Process Finished!")
}


