package org.pac4j.http4s

import org.http4s.{Response, _}
import org.pac4j.core.config.Config
import org.pac4j.core.engine.DefaultLogoutLogic
import org.pac4j.core.http.adapter.HttpActionAdapter

/**
  * Http4s Service to handle user logging out from the website
  *
  * @author Iain Cardnell
  */
class LogoutService[F[_]](config: Config,
                    defaultUrl: Option[String] = None,
                    logoutUrlPattern: Option[String] = None,
                    localLogout: Boolean = true,
                    destroySession: Boolean = false,
                    centralLogout: Boolean = false) {

  def logout(request: Request[F]): Response[F] = {
    val logoutLogic = new DefaultLogoutLogic[Response[F], Http4sWebContext]()
    val webContext = Http4sWebContext(request, config)
    logoutLogic.perform(webContext,
      config,
      config.getHttpActionAdapter.asInstanceOf[HttpActionAdapter[Response[F], Http4sWebContext]],
      this.defaultUrl.orNull,
      this.logoutUrlPattern.orNull,
      this.localLogout,
      this.destroySession,
      this.centralLogout)
  }
}
