## sbt project compiled with Scala 3

1. Install Prerequisites
Ensure you have Java 8+ installed.
Install sbt (Scala Build Tool). You can find installation instructions here.

2. Start the Backend
The backend is implemented in the jvm subproject. To start it:

sbt jvm/run

This will start the HTTP server on http://localhost:9000.

3. Build the Frontend
The frontend is implemented in the js subproject using Scala.js. To compile it:

sbt js/fastOptJS

This will generate the JavaScript file in main.js.

4. Access the Application
Open a browser and navigate to http://localhost:9000.
The static content (HTML and CSS) is served from resources, and the compiled JavaScript is served from germananki-js-fastopt.

5. Optional: Watch for Changes
If you want to automatically recompile the frontend when you make changes, you can use:

