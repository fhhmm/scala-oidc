package controllers

import javax.inject._
import play.api.mvc._

@Singleton
class LoginController @Inject()(cc: ControllerComponents) extends AbstractController(cc) {
  def login = Action {
    Redirect("/authorize?client_id=client123&redirect_uri=http://localhost:9000/callback&response_type=code&scope=openid&state=xyz&code_challenge=abc123&code_challenge_method=S256")
  }
}
