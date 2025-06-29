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

@Singleton
class LoginController @Inject()(cc: ControllerComponents, ws: WSClient)(implicit ec: ExecutionContext) extends AbstractController(cc) {
  private val redirectUri = "localhost:9000/callback"
  private val clientId = "client123"

  def login: Action[AnyContent] = Action { (request: Request[AnyContent]) =>
    val state = UUID.randomUUID().toString

    val html =
      s"""
         |<html>
         |<head><title>login</title></head>
         |<body>
         |  <h1>OIDCログイン</h1>
         |  <form method="get" action="/dummyAuth/authorize">
         |    <input type="hidden" name="client_id" value="$clientId"/>
         |    <input type="hidden" name="redirect_uri" value="$redirectUri"/>
         |    <input type="hidden" name="response_type" value="code"/>
         |    <input type="hidden" name="scope" value="openid"/>
         |    <input type="hidden" name="state" value="$state"/>
         |    <input type="hidden" name="code_challenge" value="abc123"/>
         |    <input type="hidden" name="code_challenge_method" value="S256"/>
         |    <button type="submit">OIDCでログイン</button>
         |  </form>
         |</body>
         |</html>
         |""".stripMargin

    Ok(html).as(HTML).withSession(request.session + ("state" -> state))
  }

  def callback: Action[AnyContent] = Action.async { (request: Request[AnyContent]) =>
    val (code, state) = request.method match {
      case "POST" =>
        val form = request.body.asFormUrlEncoded.getOrElse(Map.empty)
        val code = form.get("code").flatMap(_.headOption).getOrElse("")
        val state = form.get("state").flatMap(_.headOption).getOrElse("")
        (code, state)
      case _ =>
        val query = request.queryString.map { case (k, v) => k -> v.mkString }
        val code = query.getOrElse("code", "")
        val state = query.getOrElse("state", "")
        (code, state)
    }

    val sessionState = request.session.get("state").getOrElse("")

    if (state != sessionState) {
      Future.successful(Redirect("/error?message=stateMismatch"))
    } else {
      val tokenRequest = ws.url("http://localhost:9000/dummyAuth/token")
        .post(Map(
          "grant_type" -> Seq("authorization_code"),
          "code" -> Seq(code),
          "redirect_uri" -> Seq(redirectUri),
          "client_id" -> Seq(clientId),
          "code_verifier" -> Seq("dummy")
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
