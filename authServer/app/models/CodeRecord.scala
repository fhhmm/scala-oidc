package models

/**
  * クライアントからの/authorizeへのリクエスト内容
  * /tokenでの検証で使用する
  */
final case class CodeRecord(
  clientId: String,
  redirectUri: String,
  codeChallenge: String,
  codeChallengeMethod: String,
  state: String
)
