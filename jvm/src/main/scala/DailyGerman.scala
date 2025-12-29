import ch.qos.logback.classic.{Level, LoggerContext}
import io.github.cdimascio.dotenv.Dotenv
import net.ruippeixotog.scalascraper.browser.JsoupBrowser
import net.ruippeixotog.scalascraper.dsl.DSL.*
import net.ruippeixotog.scalascraper.dsl.DSL.Extract.*
import org.slf4j.LoggerFactory
import sttp.client4.*
import sttp.model.HeaderNames

import java.nio.file.{Files, Paths}
import scala.concurrent.duration.*
import scala.io.StdIn.readLine
import scala.util.{Failure, Success, Try}

object DailyGerman {

  val context = LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext]
  val httpLogger = context.getLogger("org.apache.http")
  httpLogger.setLevel(Level.OFF)
  httpLogger.setAdditive(false)
  val htmlunitLogger = context.getLogger("org.htmlunit")
  htmlunitLogger.setLevel(Level.OFF)
  htmlunitLogger.setAdditive(false)
  val dotenv = Dotenv.load()
  private val logger = LoggerFactory.getLogger(this.getClass)

  val username = dotenv.get("DAILY_GERMAN_USERNAME")
  val password = dotenv.get("DAILY_GERMAN_PASSWORD")

  // Use HttpURLConnection backend
  private val backend = DefaultSyncBackend()

  logger.info("Initializing DailyGerman object...")
  logger.info("Testing HTTP backend connectivity...")

  Try {
    val start = System.currentTimeMillis()
    val testResponse = basicRequest
      .get(uri"https://httpbin.org/get")
      .readTimeout(10.seconds)
      .send(backend)
    val elapsed = System.currentTimeMillis() - start
    logger.info(s"Backend test successful! Status: ${testResponse.code}, elapsed: ${elapsed}ms")
  }.recover {
    case ex: Throwable =>
      logger.error(s"Backend test failed: ${ex.getClass.getName}: ${ex.getMessage}")
      ex.printStackTrace()
  }
  private val cookieFile = Paths.get(".dailygerman_cookies")

  // Cache for session cookies
  @volatile private var cachedCookies: Option[String] = None

  case class CardInfo(text: String, mp3Link: String)

  def stripHtmlTags(input: String): String = {
    val htmlTagRegex = """<[^>]*>""".r
    val cleaned = htmlTagRegex.replaceAllIn(input, "")
    cleaned.replaceAll("&nbsp;", " ")
  }

  /**
   * Get or refresh session cookies by logging in
   */
  private def getSessionCookies(): String = {
    cachedCookies match {
      case Some(cookies) =>
        logger.info("Using cached cookies")
        cookies
      case None =>
        logger.info("No cached cookies, logging in...")
        login()
    }
  }

  /**
   * Perform login and get session cookies
   */
  private def login(): String = {
    logger.info("Starting login to Daily German")
    logger.info(s"Username: $username")

    logger.info("Building login request...")
    val request = basicRequest
      .post(uri"https://yourdailygerman.com/login/")
      .body(Map(
        "pmpro_login_form_used" -> "1",
        "log" -> username,
        "pwd" -> password,
        "wp-submit" -> "Log In"
      ))
      .followRedirects(false)  // Don't follow redirects - we just need the cookies
      .readTimeout(30.seconds)

    logger.info("Sending login request...")
    Try(request.send(backend)) match {
      case Success(loginResponse) =>
        logger.info(s"Login response received with status: ${loginResponse.code}")
        logger.info(s"Response headers: Location = ${loginResponse.header("Location")}")

        val cookies = loginResponse.cookies
          .collect { case Right(cookie) => s"${cookie.name}=${cookie.value}" }
          .mkString("; ")

        if (cookies.nonEmpty) {
          logger.info(s"Login successful, ${loginResponse.cookies.length} cookies obtained")
          logger.info(s"Cookie names: ${loginResponse.cookies.collect { case Right(c) => c.name }.mkString(", ")}")
          cachedCookies = Some(cookies)
          // Optionally save to file for persistence across app restarts
          Try(Files.write(cookieFile, cookies.getBytes))
          cookies
        } else {
          logger.error(s"Login failed, no cookies received. Status code: ${loginResponse.code}")
          throw new RuntimeException("Login failed - no cookies received")
        }
      case Failure(ex) =>
        logger.error(s"Login request failed with exception: ${ex.getMessage}", ex)
        throw new RuntimeException(s"Login request failed: ${ex.getMessage}", ex)
    }
  }

  /**
   * Fetch HTML content with authentication
   */
  private def fetchAuthenticatedPage(url: String): String = {
    val cookies = getSessionCookies()

    val response = basicRequest
      .get(uri"$url")
      .header(HeaderNames.Cookie, cookies)
      .followRedirects(true)
      .readTimeout(30.seconds)
      .send(backend)

    response.body match {
      case Right(html) =>
        // Check if we got redirected to login (session expired)
        if (html.contains("login") && html.contains("password")) {
          logger.info("Session expired, re-logging in...")
          cachedCookies = None
          fetchAuthenticatedPage(url) // Retry with fresh login
        } else {
          html
        }
      case Left(error) =>
        throw new RuntimeException(s"Failed to fetch page: $error")
    }
  }

  def getWebInfo(url: String): List[CardInfo] = {
    // Fetch the page HTML with authentication
    val html = fetchAuthenticatedPage(url)

    // Parse with JsoupBrowser (much faster than HtmlUnitBrowser)
    val browser = JsoupBrowser()
    val doc = browser.parseString(html)

    logger.info("Page fetched and parsed")
    logger.info("Target page URL: " + url)

    val allPostExamples = doc >> elementList(".post-example")
    logger.info(s"Found ${allPostExamples.length} .post-example elements")

    val textsWithSound = allPostExamples.flatMap { ulElement =>
      val nodes = ulElement >> elementList("li")
      if (nodes.length == 4) {
        nodes.headOption.map(_.innerHtml)
      } else {
        None
      }
    }

    val audios = allPostExamples.flatMap { ulElement =>
      val nodes = ulElement >> elementList("li")
      if (nodes.length == 4) {
        nodes(2) >?> element("source") >> attr("src")
      } else {
        None
      }
    }

    if (textsWithSound.length != audios.length) {
      throw new RuntimeException(
        "texts and audios have different sizes, the parsing is incorrect"
      )
    }

    textsWithSound.zip(audios).map { case (text, mp3Link) =>
      CardInfo(stripHtmlTags(text), mp3Link)
    }
  }

  /**
   * Load cached cookies from file on startup
   */
  def loadCachedCookies(): Unit = {
    if (Files.exists(cookieFile)) {
      Try {
        val cookies = new String(Files.readAllBytes(cookieFile))
        cachedCookies = Some(cookies)
        logger.info("Loaded cached cookies from file")
      }.recover {
        case e => logger.warn(s"Failed to load cached cookies: ${e.getMessage}")
      }
    }
  }

  def addClozeFromDailyGerman(url: String): Unit = {
    val cards = DailyGerman.getWebInfo(url)

    cards.foreach { card =>
      val fileName = card.mp3Link.substring(
        card.mp3Link.lastIndexOf('/') + 1,
        card.mp3Link.length()
      )
      println(s"Text for the new card: ${card.text}")
      println(s"Which cloze do you wanna add (coma separated)?")
      val clozes = readLine().split(",").map(_.trim())
      if (clozes.exists(!_.isBlank)) {
        println(s"Which word do you wanna show?")
        val showies = readLine().split(",").map(_.trim())
        val clozeText = clozes.indices.foldLeft(card.text)((acc, i) =>
          acc.replace(
            clozes(i),
            s"{{c1::${clozes(i)}::${showies.lift(i).getOrElse("")}}}"
          )
        )
        println(s"Result cloze card will be: $clozeText")
        val note = AnkiApi.AnkiNoteInput(
          "German",
          "Cloze German",
          Map("Text" -> clozeText),
          List(AnkiApi.AnkiAudioUrl(card.mp3Link, fileName, List("Audio")))
        )
        AnkiApi.addNote(note)
      } else println(s"Skipping card")
      println()
      println()
    }
  }
}
