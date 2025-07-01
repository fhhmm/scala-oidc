package controllers

import javax.inject._
import play.api.mvc._
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.DecodedJWT
import com.auth0.jwt.JWTVerifier

class MypageController @Inject()(cc: ControllerComponents) extends AbstractController(cc) {
  def index: Action[AnyContent] = Action { request =>
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
