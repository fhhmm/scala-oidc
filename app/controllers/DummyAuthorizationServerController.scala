package controllers

import javax.inject._
import play.api.mvc._

@Singleton
class DummyAuthorizationServerController @Inject()(cc: ControllerComponents) extends AbstractController(cc) {
  def index = cc.actionBuilder { (request: Request[AnyContent]) =>
    val code = request.getQueryString("code").getOrElse("なし")
    val state = request.getQueryString("state").getOrElse("なし")
    
    val response = s"""
      |ダミー認可サーバー
      |
      |code: $code
      |state: $state
      |""".stripMargin
    
    Ok(response)
  }
}