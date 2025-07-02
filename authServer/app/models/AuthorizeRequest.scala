package models

final case class AuthorizeRequest(
  clientId: String,
  redirectUri: String,
  responseType: String,
  codeChallenge: String,
  codeChallengeMethod: String,
  state: String
)

object AuthorizeRequest {
  def parse(queryString: Map[String, Seq[String]]): Either[String, AuthorizeRequest] = {
    val query = queryString.map { case (k, v) => k -> v.mkString }
    for {
      clientId           <- query.get("client_id").toRight("missing client_id")
      redirectUri        <- query.get("redirect_uri").toRight("missing redirect_uri")
      responseType       <- query.get("response_type").toRight("missing response_type")
      codeChallenge      <- query.get("code_challenge").toRight("missing code_challenge")
      codeChallengeMethod <- query.get("code_challenge_method").toRight("missing code_challenge_method")
      state              <- query.get("state").toRight("missing state")
    } yield AuthorizeRequest(
      clientId,
      redirectUri,
      responseType,
      codeChallenge,
      codeChallengeMethod,
      state
    )
  }
}