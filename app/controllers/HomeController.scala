package controllers

import javax.inject._
import play.api.mvc._

@Singleton
class HomeController @Inject()(cc: ControllerComponents) extends AbstractController(cc) {
  def index = Action {
    Ok("OIDC サンプルへようこそ")
  }

  def error: Action[AnyContent] = Action { (request: Request[AnyContent]) =>
    val message = request.queryString.getOrElse("message", "")
    Ok(s"エラーが発生しました: $message")
  }
}
