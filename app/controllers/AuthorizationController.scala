package controllers

import javax.inject._
import play.api.mvc._

@Singleton
class AuthorizationController @Inject()(cc: ControllerComponents) extends AbstractController(cc) {
  def authorize: Action[AnyContent] = Action { (request: Request[AnyContent]) =>
    val query = request.queryString.map { case (k,v) => k -> v.mkString }
    val clientId = query.getOrElse("client_id", "unknown")
    val redirectUri = query.getOrElse("redirect_uri", "")
    val state = query.getOrElse("state", "")
    val code = "sample-code-1234"

    Redirect(s"$redirectUri?code=$code&state=$state")
  }
}
