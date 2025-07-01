package controllers

import javax.inject._
import play.api.mvc._

@Singleton
class HomeController @Inject()(cc: ControllerComponents) extends AbstractController(cc) {
  def index = Action {
    Ok("OIDC サンプルへようこそ")
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
}
