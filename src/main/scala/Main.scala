import scala.util._
import sys.process._
import scala.io.StdIn.readLine
import AnkiApi.AnkiNoteInfo
import scala.concurrent.Future
import scala.concurrent.Await
import concurrent.duration.DurationInt

@main def run() = {
  val ip = getWindowsIp()

  //textToAudio(ip)
  DailyGerman.addClozeFromDailyGerman(ip, "https://yourdailygerman.com/passive-german-english-differences/")
}

def getWindowsIp(): String = {
  val ipRegex = """\b(?:[0-9]{1,3}\.){3}[0-9]{1,3}\b""".r
  val ip = ipRegex.findFirstIn("ip route show default".!!).getOrElse(throw Exception("IP not found!"))
  println(s"Localhost windows ip $ip")
  ip
}

def searchAndReplace(ip: String) = {
  val notesId = AnkiApi.findNotes(ip, """-sound deck:German card:Cloze -Reverse""")
  val notesInfo = AnkiApi.getNotesInfo(ip, notesId)

  val updateNotes: List[(AnkiNoteInfo, AnkiApi.AnkiUpdateNote)] = notesInfo
  .map { noteInfo =>
    val newFields = noteInfo.fields.mapValues{field =>
       field.value.replaceAll("&nbsp;", " ") 
    }.toMap
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
      AnkiApi.updateNote(ip, update)
  }
}

def textToAudio(ip: String) = {
  val openIA = new OpenIA()
  val notesId = AnkiApi.findNotes(ip, """-sound deck:German card:Cloze""")
  val notesInfo = AnkiApi.getNotesInfo(ip, notesId)

  val clozePattern = """\{\{c\d+::([^:}]+)(?:::.*?)?\}\}""".r
    
  notesInfo.foreach{ noteInfo =>
    val text = noteInfo.fields.get("Text").map(v => clozePattern.replaceAllIn(v.value, m => m.group(1))).get
    val fileName = noteInfo.noteId.toString()
    println(s"Text to Speech for: $text")
    Await.result(openIA.textToSpeech(fileName, text), 10.seconds)
    val audio = List(AnkiApi.AnkiAudioPath(s"\\\\wsl.localhost\\Ubuntu\\home\\petrm\\germananki\\$fileName.mp3", fileName, List("Audio")))
    val newFields = noteInfo.fields.mapValues(_.value).toMap
    val updateNote = AnkiApi.AnkiUpdateNote(noteInfo.noteId, noteInfo.tags, newFields, audio)

    println(s"Old: $noteInfo")
    println()
    println(s"New: $updateNote")
    println()
    println()
    println()
    AnkiApi.updateNote(ip, updateNote)
    Thread.sleep(40000)
  }
}


