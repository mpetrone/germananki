# GermanAnki - Project Documentation

## Overview

GermanAnki is a web-based application for creating German language Anki flashcards with audio support. The application provides two main workflows:

1. **Daily German Integration**: Scrapes phrases from Your Daily German blog posts and creates Anki cloze deletion cards with authentic audio
2. **Text-to-Audio**: Generates synthetic audio for custom German sentences and creates cloze deletion cards

## Architecture

The project uses a **full-stack Scala architecture** with code sharing between frontend and backend:

- **Backend (JVM)**: Scala 3.3.6 with http4s server
- **Frontend (JavaScript)**: Scala.js compiled to JavaScript, using Laminar for reactive UI
- **Shared**: Common data models used by both frontend and backend

### Technology Stack

#### Backend
- **http4s** (Ember server): HTTP server and routing
- **Cats Effect**: Functional effects and async operations
- **Circe**: JSON encoding/decoding
- **Scala Scraper**: Web scraping for Daily German content
- **OpenAI Scala Client**: Text-to-speech generation
- **sttp**: HTTP client for AnkiConnect API
- **Dotenv**: Environment variable management

#### Frontend
- **Laminar**: Reactive UI framework with functional reactive programming (FRP)
- **Scala.js**: Scala to JavaScript transpiler
- **sttp (Fetch backend)**: HTTP client for API calls
- **Circe**: JSON serialization

## Project Structure

```
germananki/
├── jvm/                          # Backend (JVM)
│   └── src/main/scala/
│       ├── Main.scala            # HTTP server and API routes
│       ├── DailyGerman.scala     # Web scraping logic
│       ├── AnkiApi.scala         # AnkiConnect API client
│       └── OpenIA.scala          # OpenAI text-to-speech client
├── js/                           # Frontend (Scala.js)
│   ├── src/main/
│   │   ├── scala/germananki/ui/
│   │   │   └── Main.scala        # Laminar UI components
│   │   └── resources/
│   │       └── index.html        # HTML entry point
│   └── target/                   # Compiled JavaScript output
├── shared/                       # Shared code
│   └── src/main/scala/germananki/shared/
│       └── Models.scala          # Shared data models
├── html-examples/                # Sample HTML files
├── build.sbt                     # Multi-project sbt build
├── REQUIREMENTS.md              # Original requirements
└── README.md                     # Setup instructions
```

## Key Components

### Backend Components

#### 1. Main.scala (jvm/src/main/scala/Main.scala)
The HTTP server entry point with two main route groups:

**Daily German Routes:**
- `POST /api/daily-german/phrases`: Accepts URL, returns list of phrases with audio
- `POST /api/daily-german/card`: Creates Anki card from phrase with cloze deletions

**Text-to-Audio Routes:**
- `POST /api/text-to-audio/card`: Generates audio and creates Anki card from custom text

**Static Content:**
- Serves frontend resources from `js/src/main/resources`
- Serves compiled JavaScript from `js/target/scala-3.3.6/germananki-js-fastopt`

#### 2. DailyGerman.scala (jvm/src/main/scala/DailyGerman.scala)
Web scraping module for Your Daily German blog:

- **Authentication**: Logs into Your Daily German with credentials from `.env`
- **Scraping**: Extracts phrases and audio links from blog posts
- **Parsing**: Processes `.post-example` elements containing 4-item lists (phrase + audio)
- **HTML Cleaning**: Strips HTML tags and normalizes text

Key function:
- `getWebInfo(url: String): List[CardInfo]` - Returns phrases with MP3 links

#### 3. AnkiApi.scala (jvm/src/main/scala/AnkiApi.scala)
AnkiConnect API client for creating/managing Anki cards:

- **Card Creation**: `addNote()` creates new cloze deletion cards
- **Card Updates**: `updateNote()` modifies existing cards
- **Search**: `findNotes()` searches for cards by pattern
- **Info Retrieval**: `getNotesInfo()` fetches detailed card information

Supports two audio types:
- `AnkiAudioUrl`: Audio from URL (Daily German)
- `AnkiAudioPath`: Audio from local file path (Text-to-Audio)

#### 4. OpenIA.scala (jvm/src/main/scala/OpenIA.scala)
OpenAI text-to-speech integration:

- Uses OpenAI's TTS API to generate German audio
- Randomly selects from 6 voice types (alloy, echo, fable, onyx, nova, shimmer)
- Saves audio files to local filesystem
- Returns Future for async operation

### Frontend Components

#### Main.scala (js/src/main/scala/germananki/ui/Main.scala)
Laminar-based reactive UI with:

**State Management:**
- `urlVar`: Daily German URL input
- `phrasesVar`: List of fetched phrases
- `clozeWordsVar`: Map of phrase → cloze words
- `hintsVar`: Map of phrase → hints
- `sentenceVar`: Text-to-Audio sentence input
- `activeTabVar`: Current tab selection

**Features:**
- Tab-based navigation (Daily German / Text-to-Audio)
- Reactive form inputs with bidirectional data binding
- Async API calls with sttp client
- Alert notifications for success/error feedback

**UI Flow:**
1. User enters URL → clicks "Get Phrases"
2. App fetches phrases from backend
3. User inputs cloze words and hints for each phrase
4. User clicks "Create Card" → card created in Anki

### Shared Models (shared/src/main/scala/germananki/shared/Models.scala)

```scala
case class DailyGermanUrl(url: String)
case class DailyGermanPhrase(text: String, mp3Link: String)
case class Cloze(word: String, hint: String)
case class CreateAnkiCardRequest(phrase: DailyGermanPhrase, clozes: List[Cloze])
case class TextToAudioRequest(sentence: String, clozeWords: List[Cloze])
```

## API Endpoints

### POST /api/daily-german/phrases
Fetches phrases from a Daily German blog post.

**Request:**
```json
{
  "url": "https://yourdailygerman.com/..."
}
```

**Response:**
```json
[
  {
    "text": "Berlin ist die Hauptstadt von Deutschland",
    "mp3Link": "https://yourdailygerman.com/.../audio.mp3"
  }
]
```

### POST /api/daily-german/card
Creates an Anki card from a Daily German phrase.

**Request:**
```json
{
  "phrase": {
    "text": "Berlin ist die Hauptstadt von Deutschland",
    "mp3Link": "https://yourdailygerman.com/.../audio.mp3"
  },
  "clozes": [
    {"word": "Berlin", "hint": "capital city"},
    {"word": "Hauptstadt", "hint": ""}
  ]
}
```

**Response:** 200 OK

**Card Created:**
```
Text: {{c1::Berlin::capital city}} ist die {{c1::Hauptstadt::}} von Deutschland
Audio: [attached from mp3Link]
```

### POST /api/text-to-audio/card
Generates audio and creates an Anki card from custom text.

**Request:**
```json
{
  "sentence": "Ich gehe zum Supermarkt",
  "clozeWords": [
    {"word": "gehe", "hint": "verb"},
    {"word": "Supermarkt", "hint": ""}
  ]
}
```

**Response:** 200 OK

**Card Created:**
```
Text: Ich {{c1::gehe::verb}} zum {{c1::Supermarkt::}}
Audio: [generated via OpenAI TTS]
```

## Data Flow

### Daily German Workflow

```
User → Frontend → Backend → External Services → Anki

1. User enters Daily German URL in UI
2. Frontend sends POST to /api/daily-german/phrases
3. Backend scrapes Your Daily German (authenticated)
4. Backend returns phrases + audio URLs
5. Frontend displays phrases for user review
6. User specifies cloze words and hints
7. Frontend sends POST to /api/daily-german/card
8. Backend formats cloze deletions
9. Backend creates Anki card via AnkiConnect
10. Frontend shows success confirmation
```

### Text-to-Audio Workflow

```
User → Frontend → Backend → OpenAI → Anki

1. User enters German sentence in UI
2. User specifies cloze words and hints
3. Frontend sends POST to /api/text-to-audio/card
4. Backend formats cloze deletions
5. Backend generates audio via OpenAI TTS API
6. Backend saves audio to filesystem
7. Backend creates Anki card with local audio file
8. Frontend shows success confirmation
```

## Environment Configuration

Create a `.env` file in the project root:

```env
# Your Daily German credentials
DAILY_GERMAN_USERNAME=your_username
DAILY_GERMAN_PASSWORD=your_password

# OpenAI API key
OPENAI_API_KEY=sk-...
```

## Build and Run

### Prerequisites
- Java 8+
- sbt
- Anki with AnkiConnect plugin installed

### Running the Application

1. **Start the backend:**
   ```bash
   sbt jvm/run
   ```
   Server starts on http://localhost:9000

2. **Compile the frontend:**
   ```bash
   sbt js/fastOptJS
   ```
   Generates JavaScript in `js/target/scala-3.3.6/germananki-js-fastopt/`

3. **Access the application:**
   Open http://localhost:9000 in your browser

4. **Watch mode (optional):**
   ```bash
   sbt ~js/fastOptJS
   ```
   Auto-recompiles on file changes

## Dependencies

### Backend (jvm/build.sbt)
- scala-scraper 3.2.0 - Web scraping
- sttp.client4 4.0.9 - HTTP client
- openai-scala-client 1.2.0 - OpenAI API
- http4s 1.0.0-M40 - HTTP server
- circe 0.14.6 - JSON serialization
- logback-classic 1.5.13 - Logging
- java-dotenv 5.2.2 - Environment variables

### Frontend (js/build.sbt)
- laminar 16.0.0 - Reactive UI framework
- sttp.client4 4.0.9 - HTTP client (Fetch backend)
- circe 0.14.6 - JSON serialization

## Current Features

### ✅ Implemented
- [x] Web scraping from Your Daily German with authentication
- [x] Phrase extraction with audio links
- [x] User-specified cloze deletions with hints
- [x] OpenAI text-to-speech generation with random voice selection
- [x] AnkiConnect integration for card creation
- [x] Web UI with tab-based navigation
- [x] Reactive form handling with Laminar
- [x] Error handling and user feedback
- [x] Static file serving

### 🎯 Notable Implementation Details
- **Cloze Format**: All clozes use `{{c1::word::hint}}` (same cloze number)
- **Audio Voices**: Random selection from 6 OpenAI voices for variety
- **Authentication**: Automatic login to Your Daily German
- **HTML Parsing**: Filters 4-item lists with audio elements
- **Path Hardcoding**: Text-to-audio saves to `/home/petrm/Development/Scala/germananki/` (see Main.scala:83)

## Known Issues and Considerations

1. **Hardcoded Path**: Audio file path in `Main.scala:83` is hardcoded to `/home/petrm/Development/Scala/germananki/`
   - Should be configurable via environment variable

2. **Error Handling**: Basic error handling with console logs and alerts
   - Could be enhanced with better UI feedback

3. **Cloze Numbering**: All clozes use `c1` (same number)
   - Means all blanks are shown/hidden together
   - Consider supporting separate cloze numbers for independent blanks

4. **Audio Storage**: Text-to-audio files accumulate on filesystem
   - No cleanup mechanism implemented

5. **Scraping Fragility**: Web scraping depends on Daily German's HTML structure
   - May break if site structure changes

## Extension Points

### Potential Future Enhancements

1. **Configuration Management**
   - Move hardcoded paths to `.env`
   - Configurable audio output directory
   - Configurable Anki deck names

2. **Enhanced Cloze Support**
   - Support for multiple cloze numbers (c1, c2, c3, etc.)
   - Preview of cloze card before creation
   - Bulk card creation

3. **Audio Management**
   - Audio file cleanup/garbage collection
   - Audio preview in UI before card creation
   - Voice selection UI for text-to-audio

4. **Error Handling**
   - Detailed error messages in UI
   - Retry logic for failed API calls
   - Validation of cloze word existence in text

5. **User Experience**
   - Loading indicators during API calls
   - Keyboard shortcuts
   - Card creation history
   - Undo functionality

6. **Testing**
   - Unit tests for scraping logic
   - Integration tests for API endpoints
   - Frontend component tests

7. **Deployment**
   - Docker containerization
   - Production build optimization
   - Environment-specific configurations

## Git Status (as of last commit)

Current branch: `master`

Recent commits:
- b2e53f7: tab implementation
- c261725: .env creation
- c6d0bf5: web page implementation
- 7ee72ee: implementing an ui in Scalajs
- 558a5da: fix issue when there was an exercise without audio

Staged changes:
- New: `html-examples/aufhaben_ and _draufhaben_.html`
- New: `html-examples/nur vs erst vs einzige.html`
- Modified: `jvm/src/main/scala/DailyGerman.scala`

## References

- [REQUIREMENTS.md](./REQUIREMENTS.md) - Original project requirements
- [README.md](./README.md) - Setup and run instructions
- [AnkiConnect](https://foosoft.net/projects/anki-connect/) - Anki API documentation
- [Laminar](https://laminar.dev/) - Reactive UI framework documentation
- [http4s](https://http4s.org/) - HTTP server documentation
