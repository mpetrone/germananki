# Project: GermanAnki Web UI

## 1. Overview

The goal of this project is to create a web-based user interface (UI) for the existing `germananki` Scala application. 
This UI will replicate and potentially enhance the current command-line functionalities, providing a more interactive and user-friendly experience. 
The new UI will be built using the Scala.js framework, allowing the frontend to be written in Scala and compiled to JavaScript.

## 2. Core Requirements

### 2.1. Technology Stack

*   **Frontend Framework:** Scala.js
*   **Build Tool:** sbt, with the `sbt-scalajs` plugin.
*   **Communication:** The frontend will communicate with the existing Scala backend via an HTTP API (to be created).

### 2.2. Key Features

The web UI must implement the following features, which are currently available in the command-line version:

#### 2.2.1. "Daily German" Card Creation

*   **Action:** The user shall be able to add as input a dailyGerman blog url.
*   **Process:**
    *   This action will trigger a call to the backend.
    *   The backend will execute the logic currently in `DailyGerman.scala` to fetch the phrases of the blog post with the audios
    *   The ui will show the the phrases and the user can specify the pars of the sentece to be clozed (also can select to skip the phrase)
    *   The backend will then use the logic from `AnkiApi.scala` to create a new Anki card.
*   **Feedback:** The UI shall display a confirmation message to the user indicating whether the card was created successfully or if an error occurred.

#### 2.2.2. Text-to-Audio for Cloze Deletion Cards

*   **Action:** The user shall be presented with an input field to enter text for a cloze deletion card.
*   **Process:**
    *   The user enters a sentence (e.g., "Berlin ist die Hauptstadt von Deutschland").
    *   The user specifies the part of the sentence to be clozed (e.g., "Berlin").
    *   The application will generate the cloze deletion text (e.g., "{{c1::Berlin}} ist die Hauptstadt von Deutschland").
    *   A call will be made to the backend to generate an audio file for the *full sentence* using the functionality from `OpenIA.scala`.
    *   The backend will create a new Anki cloze card containing both the cloze text and the generated audio file.
*   **Feedback:** The UI shall provide a status indicator for the audio generation and card creation process, followed by a success or error message.

## 3. Backend Modifications (Implied Tasks)

*   An HTTP server (e.g., using http4s) will need to be added to the existing Scala project to expose the required functionality to the frontend.

## 4. Project Structure Changes

*   The sbt build (`build.sbt`) will be updated to define a multi-project structure:
    *   A `shared` project for any code to be used by both backend and frontend.
    *   A `jvm` project for the existing backend code and the new HTTP API.
    *   A `js` project for the new Scala.js frontend code.
