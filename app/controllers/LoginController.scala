package controllers

import javax.inject._
import play.api.mvc._
import play.api.libs.ws._
import scala.concurrent.{ExecutionContext, Future}
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.DecodedJWT
import com.auth0.jwt.JWTVerifier
import play.api.libs.json.Json
import java.util.UUID
import scala.collection.concurrent.TrieMap
import java.security.SecureRandom
import java.util.Base64
import java.security.MessageDigest
import java.nio.charset.StandardCharsets


@Singleton
class LoginController @Inject()(cc: ControllerComponents, ws: WSClient)(implicit ec: ExecutionContext) extends AbstractController(cc) {
  // state -> (codeVerifier, codeChallenge)
  private val codeStore = TrieMap.empty[String, (String, String)]
  private val redirectUri = "localhost:9000/callback"
  private val clientId = "client123"
  private val responseType = "code"
  private val codeChallengeMethod = "S256"

  def login: Action[AnyContent] = Action { (request: Request[AnyContent]) =>
    val state = UUID.randomUUID().toString
    val codeVerifier = generateCodeVerifier()
    val codeChallenge = generateCodeChallenge(codeVerifier)
    codeStore.put(state, (codeVerifier, codeChallenge))

    val html =
      s"""
         |<html>
         |<head><title>login</title></head>
         |<body>
         |  <h1>OIDCログイン</h1>
         |  <form method="get" action="/dummyAuth/authorize">
         |    <input type="hidden" name="client_id" value="$clientId"/>
         |    <input type="hidden" name="redirect_uri" value="$redirectUri"/>
         |    <input type="hidden" name="response_type" value="$responseType"/>
         |    <input type="hidden" name="scope" value="openid"/>
         |    <input type="hidden" name="state" value="$state"/>
         |    <input type="hidden" name="code_challenge" value=$codeChallenge/>
         |    <input type="hidden" name="code_challenge_method" value="$codeChallengeMethod"/>
         |    <button type="submit">OIDCでログイン</button>
         |  </form>
         |</body>
         |</html>
         |""".stripMargin

    Ok(html).as(HTML).withSession(request.session + ("state" -> state))
  }

  def callback: Action[AnyContent] = Action.async { (request: Request[AnyContent]) =>
    val query = request.queryString.map { case (k, v) => k -> v.mkString }
    val code = query.getOrElse("code", "")
    val state = query.getOrElse("state", "")
    val sessionState = request.session.get("state").getOrElse("")

    if (state != sessionState) {
      Future.successful(Redirect("/error?message=stateMismatch"))
    } else {
      val (codeVerifier, _) = codeStore.get(sessionState).getOrElse(("", ""))
      val tokenRequest = ws.url("http://localhost:9000/dummyAuth/token")
        .post(Map(
          "grant_type" -> "authorization_code",
          "code" -> code,
          "redirect_uri" -> redirectUri,
          "client_id" -> clientId,
          "code_verifier" -> codeVerifier
        ))

      tokenRequest.map { wsResponse =>
        val tokenResponseBody = wsResponse.body
        val responseJson = Json.parse(tokenResponseBody)
        val maybeIdToken = (responseJson \ "id_token").asOpt[String]

        maybeIdToken match {
          case Some(idToken) =>
            Redirect("/mypage").withSession(
              "id_token" -> idToken
            )
          case None =>
            Redirect("/login")
        }
      }
    }
  }

  def mypage: Action[AnyContent] = Action { request =>
    val maybeIdToken = request.session.get("id_token")

    maybeIdToken match {
      case Some(idToken) =>
        verifyAndDecodeIdToken(idToken) match {
          case Right(jwt) =>
            val sub = jwt.getSubject
            val aud = jwt.getAudience
            val exp = jwt.getExpiresAt
            val name = jwt.getClaim("name").asString()
            val email = jwt.getClaim("email").asString()

            Ok(s"""
                  |ログイン済みです。<br>
                  |ユーザーID: $sub<br>
                  |名前: $name<br>
                  |メール: $email<br>
                  |Audience: $aud<br>
                  |有効期限: $exp
          """.stripMargin).as("text/html; charset=UTF-8")

          case Left(error) =>
            Unauthorized(s"トークンの検証に失敗しました: $error")
        }

      case None =>
        Redirect("/login")
    }
  }

  private def generateCodeVerifier(): String = {
    val bytes = new Array[Byte](32)
    new SecureRandom().nextBytes(bytes)
    Base64.getUrlEncoder.withoutPadding().encodeToString(bytes)
  }

  private def generateCodeChallenge(codeVerifier: String): String = {
    val digest = MessageDigest.getInstance("SHA-256").digest(codeVerifier.getBytes(StandardCharsets.US_ASCII))
    Base64.getUrlEncoder.withoutPadding.encodeToString(digest)
  }

  private def verifyAndDecodeIdToken(idToken: String): Either[String, DecodedJWT] = {
    try {
      val algorithm = Algorithm.HMAC256("my-secret-key-123456")
      val verifier: JWTVerifier = JWT
        .require(algorithm)
        .withIssuer("http://localhost:9000")
        .withAudience("client123")
        .build()

      val decoded: DecodedJWT = verifier.verify(idToken)
      Right(decoded)
    } catch {
      case e: Exception =>
        Left(s"Invalid token: ${e.getMessage}")
    }
  }
}
