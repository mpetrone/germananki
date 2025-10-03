package germananki.shared

case class DailyGermanUrl(url: String)
case class DailyGermanPhrase(text: String, mp3Link: String)

case class Cloze(word: String, hint: String)
case class CreateAnkiCardRequest(phrase: DailyGermanPhrase, clozes: List[Cloze])

case class TextToAudioRequest(sentence: String, clozeWords: List[Cloze])
