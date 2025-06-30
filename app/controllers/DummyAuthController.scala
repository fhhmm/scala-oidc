package controllers

import javax.inject._
import play.api.mvc._
import play.api.libs.json.Json
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.util.Date
import scala.collection.concurrent.TrieMap
import java.security.SecureRandom
import java.util.Base64


@Singleton
class DummyAuthController @Inject()(cc: ControllerComponents) extends AbstractController(cc) {
  private val codeStore = TrieMap.empty[String, CodeRecord]
  case class CodeRecord(
    clientId: String,
    redirectUri: String,
    codeChallenge: String,
    codeChallengeMethod: String,
    state: String
  )

  def authorize: Action[AnyContent] = Action { (request: Request[AnyContent]) =>
    val query = request.queryString.map { case (k,v) => k -> v.mkString }
    val clientId = query.getOrElse("client_id", "unknown")
    val redirectUri = query.getOrElse("redirect_uri", "")
    val responseType = query.getOrElse("response_type", "")
    val codeChallenge = query.getOrElse("code_challenge", "")
    val codeChallengeMethod = query.getOrElse("code_challenge_method", "")
    val state = query.getOrElse("state", "")

    // TODO: dummyAuth/errorにリダイレクト
    if (responseType != "code")
      BadRequest("Invalid response_type")

    if (codeChallengeMethod != "S256")
      BadRequest("Only S256 supported")
    
    val code = generateAuthorizationCode()
    codeStore.put(code, CodeRecord(clientId, redirectUri, codeChallenge, codeChallengeMethod, state))

    val html =
      s"""
         |<html>
         |<head><title>dummy authorization</title></head>
         |<body>
         |  <h1>【認可サーバー】権限付与(認可サーバーログイン)</h1>
         |  <p>client_id: $clientId</p>
         |  <p>redirect_uri: $redirectUri</p>
         |  <p>state: $state</p>
         |  <p>code: $code</p>
         |  <form method="get" action="/callback">
         |    <input type="hidden" name="redirect_uri" value="$redirectUri"/>
         |    <input type="hidden" name="state" value="$state"/>
         |    <input type="hidden" name="code" value="$code"/>
         |    <button type="submit">クライアントに権限を付与</button>
         |  </form>
         |</body>
         |</html>
         |""".stripMargin

    Ok(html).as(HTML)
  }

  def token: Action[AnyContent] = Action { (request: Request[AnyContent]) =>
    val form = request.body.asFormUrlEncoded.getOrElse(Map.empty)

    val grantType = form.get("grant_type").flatMap(_.headOption).getOrElse("")
    val code = form.get("code").flatMap(_.headOption).getOrElse("")
    val redirectUrl = form.get("redirect_url").flatMap(_.headOption).getOrElse("")
    val codeVerifier = form.get("code_verifier").flatMap(_.headOption).getOrElse("")
    val clientId = form.get("client_id").flatMap(_.headOption).getOrElse("client123")

    // TODO: Validation

    val secret = "my-secret-key-123456"
    val algorithm = Algorithm.HMAC256(secret)
    val now = System.currentTimeMillis()
    val expiresInSec = 3600
    val exp = new Date(now + expiresInSec * 1000)
    val iat = new Date(now)
    val nbf = new Date(now)
    val iss = "http://localhost:9000"
    val sub = "user-1234"
    val name = "テストユーザー"
    val email = "test@example.com"

    val accessToken = JWT.create()
      .withIssuer(iss)
      .withSubject(sub)
      .withAudience(clientId)
      .withExpiresAt(exp)
      .withIssuedAt(iat)
      .sign(algorithm)

    val idToken = JWT.create()
      .withIssuer(iss)
      .withSubject(sub)
      .withAudience(clientId)
      .withExpiresAt(exp)
      .withIssuedAt(iat)
      .withNotBefore(nbf)
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

  private def generateAuthorizationCode(lengthBytes: Int = 32): String = {
    val bytes = new Array[Byte](lengthBytes)
    SecureRandom.getInstanceStrong().nextBytes(bytes)
    Base64.getUrlEncoder.withoutPadding().encodeToString(bytes)
  }
}
