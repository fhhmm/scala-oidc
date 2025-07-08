package controllers

import models.{AuthorizeRequest, CodeRecord}
import javax.inject._
import play.api.mvc._
import play.api.libs.json.Json
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.util.Date
import scala.collection.concurrent.TrieMap
import java.security.SecureRandom
import java.util.Base64
import java.security.MessageDigest


@Singleton
class AuthController @Inject()(cc: ControllerComponents) extends AbstractController(cc) {
  private val codeStore = TrieMap.empty[String, CodeRecord]

  def authorize: Action[AnyContent] = Action { (req: Request[AnyContent]) =>
    // TODO: リファクタ
    val result = for {
      request <- AuthorizeRequest.parse(req.queryString) match {
        case Left(message) => Left(Redirect(s"/error/$message"))
        case Right(v) => Right(v)
      }
      _ <- Either.cond(request.responseType == "code", (), Redirect("/error/message=invalidResponseType"))
      _ <- Either.cond(request.codeChallengeMethod == "S256", (), Redirect("/error/message=onlyS256Supported"))
    } yield {
      val code = generateAuthorizationCode()
      codeStore.put(code, CodeRecord(
        request.clientId,
        request.redirectUri,
        request.codeChallenge,
        request.codeChallengeMethod,
        request.state
      ))

      val html =
        s"""
          |<html>
          |<head><title>dummy authorization</title></head>
          |<body>
          |  <h1>【認可サーバー】権限付与(認可サーバーログイン)</h1>
          |  <p>client_id: ${request.clientId}</p>
          |  <p>redirect_uri: ${request.redirectUri}</p>
          |  <p>state: ${request.state}</p>
          |  <p>code: $code</p>
          |  <form method="get" action="${request.redirectUri}">
          |    <input type="hidden" name="redirect_uri" value="${request.redirectUri}"/>
          |    <input type="hidden" name="state" value="${request.state}"/>
          |    <input type="hidden" name="code" value="$code"/>
          |    <button type="submit">クライアントに権限を付与</button>
          |  </form>
          |</body>
          |</html>
          |""".stripMargin

      Ok(html).as(HTML)
    }
    result.fold(identity, identity)
  }

  def token: Action[AnyContent] = Action { (request: Request[AnyContent]) =>
    val form = request.body.asFormUrlEncoded.getOrElse(Map.empty)

    val grantType = form.get("grant_type").flatMap(_.headOption).getOrElse("")
    val code = form.get("code").flatMap(_.headOption).getOrElse("")
    val redirectUrl = form.get("redirect_url").flatMap(_.headOption).getOrElse("")
    val codeVerifier = form.get("code_verifier").flatMap(_.headOption).getOrElse("")
    val clientId = form.get("client_id").flatMap(_.headOption).getOrElse("client123")

    val maybeStoredCode = codeStore.get(code)
    
    // TODO: for式などにする
    if (grantType != "authorization_code") {
      Redirect("/error?message=invalidGrantType")
    } else if (maybeStoredCode.isEmpty) {
      Redirect("/error?message=invalidCode")
    } else if (!isCodeVerifierValid(codeVerifier, maybeStoredCode.get.codeChallenge, maybeStoredCode.get.codeChallengeMethod)) {
      Redirect("/error?message=invalidCodeVerifier")
    } else {

      val secret = "my-secret-key-123456"
      val algorithm = Algorithm.HMAC256(secret)
      val now = System.currentTimeMillis()
      val expiresInSec = 3600
      val exp = new Date(now + expiresInSec * 1000)
      val issuedAt = new Date(now)
      val notBefore = new Date(now)
      val issuer = "http://localhost:9000"
      // 実際はDBなどからユーザーを検索する
      val sub = "user-1234"
      val name = "テストユーザー"
      val email = "test@example.com"

      val accessToken = JWT.create()
        .withIssuer(issuer)
        .withSubject(sub)
        .withAudience(clientId)
        .withExpiresAt(exp)
        .withIssuedAt(issuedAt)
        .sign(algorithm)

      val idToken = JWT.create()
        .withIssuer(issuer)
        .withSubject(sub)
        .withAudience(clientId)
        .withExpiresAt(exp)
        .withIssuedAt(issuedAt)
        .withNotBefore(notBefore)
        .withClaim("name", name)
        .withClaim("email", email)
        .sign(algorithm)

      Ok(Json.obj(
        "access_token" -> accessToken,
        "token_type" -> "Bearer",
        "expires_in" -> expiresInSec.toString,
        "id_token" -> idToken
      ))
    }
  }

  def error: Action[AnyContent] = Action { (request: Request[AnyContent]) =>
    val messages: Seq[String] = request.queryString.get("message").getOrElse(Seq.empty)
    val html =
      s"""
         |<html>
         |<head><title>エラー</title></head>
         |<body>
         |  <h1>エラーが発生しました</h1>
         |  <ul>
         |    ${messages.map(m => s"<li>${play.twirl.api.HtmlFormat.escape(m)}</li>").mkString("\n    ")}
         |  </ul>
         |</body>
         |</html>
         |""".stripMargin
    Ok(html).as("text/html; charset=UTF-8")
  }

  private def generateAuthorizationCode(lengthBytes: Int = 32): String = {
    val bytes = new Array[Byte](lengthBytes)
    SecureRandom.getInstanceStrong().nextBytes(bytes)
    Base64.getUrlEncoder.withoutPadding().encodeToString(bytes)
  }

  private def isCodeVerifierValid(codeVerifier: String, storedCodeChallenge: String, method: String): Boolean = {
    method match {
      case "S256" =>
        val digest = MessageDigest.getInstance("SHA-256").digest(
          codeVerifier.getBytes("UTF-8")
        )
        val computedChallenge = Base64.getUrlEncoder.withoutPadding().encodeToString(digest)
        computedChallenge == storedCodeChallenge
      case _ =>
        false
    }
  }
}
