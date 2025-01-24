import sttp.client4._
import sttp.client4.upicklejson.default._
import sttp.model.Uri
import upickle.default._

object AnkiApi {
  sealed trait AnkiAudio
  case class AnkiAudioUrl(url: String, filename: String, fields: List[String]) extends AnkiAudio
  case class AnkiAudioPath(path: String, filename: String, fields: List[String]) extends AnkiAudio
  case class AnkiNoteInput(deckName: String, modelName: String, fields: Map[String, String], audio: List[AnkiAudio])
  case class AnkiNoteField(value: String, order: Int)
  case class AnkiNoteInfo(noteId: Long, modelName: String, tags: List[String], fields: Map[String, AnkiNoteField])
  case class AnkiUpdateNote(id: Long, tags: List[String], fields: Map[String, String], audio: List[AnkiAudio])
  case class AnkiConnectRequest[T](action: String, version: Int, params: Map[String, T])
  case class AnkiConnectResponse[T](result: T, error: Option[String])


  val backend = DefaultSyncBackend()
  implicit val audioUrlRW: ReadWriter[AnkiAudioUrl] = macroRW[AnkiAudioUrl]
  implicit val audioPathRW: ReadWriter[AnkiAudioPath] = macroRW[AnkiAudioPath]
  implicit val audioRW: ReadWriter[AnkiAudio] = macroRW[AnkiAudio]
  implicit val noteNoteInputRW: ReadWriter[AnkiNoteInput] = macroRW[AnkiNoteInput]
  implicit val noteFieldRW: ReadWriter[AnkiNoteField] = macroRW[AnkiNoteField]
  implicit val noteInfoRW: ReadWriter[AnkiNoteInfo] = macroRW[AnkiNoteInfo]
  implicit val updateNoteRW: ReadWriter[AnkiUpdateNote] = macroRW[AnkiUpdateNote]
  implicit val addNoteRequestRW: ReadWriter[AnkiConnectRequest[AnkiNoteInput]] = macroRW[AnkiConnectRequest[AnkiNoteInput]]
  implicit val findNotesRequestRW: ReadWriter[AnkiConnectRequest[String]] = macroRW[AnkiConnectRequest[String]]
  implicit val findNotesResposeRW: ReadWriter[AnkiConnectResponse[List[Long]]] = macroRW[AnkiConnectResponse[List[Long]]]
  implicit val getNotesInfoRequestRW: ReadWriter[AnkiConnectRequest[List[Long]]] = macroRW[AnkiConnectRequest[List[Long]]]
  implicit val getNotesInfoResposeRW: ReadWriter[AnkiConnectResponse[List[AnkiNoteInfo]]] = macroRW[AnkiConnectResponse[List[AnkiNoteInfo]]]
  implicit val updateNonteRequestRW: ReadWriter[AnkiConnectRequest[AnkiUpdateNote]] = macroRW[AnkiConnectRequest[AnkiUpdateNote]]
  implicit val updateNonteResponseRW: ReadWriter[AnkiConnectResponse[Option[String]]] = macroRW[AnkiConnectResponse[Option[String]]]

  def addNote(ip: String, note: AnkiNoteInput) = {
    val request = basicRequest
        .post(Uri.unsafeApply(ip, 8765))
        .body(AnkiConnectRequest(action = "addNote", version = 6, params = Map("note" -> note)))
        .response(asString)
    val response = request.send(backend)
    response.body match
      case Left(error) => println(s"Error while creating the note: $error")
      case Right(result) =>
        if(!result.contains(""""error": null"""))
          println(s"Error return from Anki-connect: $result")
        else
          println("Anki note created successfully")  
  }

  def updateNote(ip: String, note: AnkiUpdateNote) = {
    val request = basicRequest
      .post(Uri.unsafeApply(ip, 8765))
      .body(AnkiConnectRequest(action = "updateNote", version = 6, params = Map("note" -> note)))
      .response(asJson[AnkiConnectResponse[Option[String]]])
    val response = request.send(backend)
    response.body match
      case Left(error) => 
        println(s"Error while updating the note: $error")
        List()
      case Right(AnkiConnectResponse(_, Some(error))) =>
          println(s"Error return from Anki-connect: $error")
          List()
      case Right(AnkiConnectResponse(_, _)) => ()
  }

  def findNotes(ip: String, pattern: String): List[Long] = {
    val request = basicRequest
      .post(Uri.unsafeApply(ip, 8765))
      .body(AnkiConnectRequest(action = "findNotes", version = 6, params = Map("query" -> pattern)))
      .response(asJson[AnkiConnectResponse[List[Long]]])
    val response = request.send(backend)
    response.body match
      case Left(error) => 
        println(s"Error while fiding notes: $error")
        List()
      case Right(AnkiConnectResponse(_, Some(error))) =>
          println(s"Error return from Anki-connect: $error")
          List()
      case Right(AnkiConnectResponse(list, _)) => list
  }

  def getNotesInfo(ip: String, notesId: List[Long]): List[AnkiNoteInfo] = {
    val request = basicRequest
      .post(Uri.unsafeApply(ip, 8765))
      .body(AnkiConnectRequest(action = "notesInfo", version = 6, params = Map("notes" -> notesId)))
      .response(asJson[AnkiConnectResponse[List[AnkiNoteInfo]]])
    val response = request.send(backend)
    response.body match
      case Left(error) => 
        println(s"Error while getting notes info: $error")
        List()
      case Right(AnkiConnectResponse(_, Some(error))) =>
          println(s"Error return from Anki-connect: $error")
          List()
      case Right(AnkiConnectResponse(list, _)) => list
  }
}