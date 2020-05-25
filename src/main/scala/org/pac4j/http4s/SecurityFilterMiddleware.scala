package org.pac4j.http4s

import java.util

import cats.data.{Kleisli, OptionT}
import cats.effect.IO
import org.http4s.{HttpRoutes, Request, Response}
import org.pac4j.core.config.Config
import org.pac4j.core.engine.{DefaultSecurityLogic, SecurityGrantedAccessAdapter}
import org.pac4j.core.http.adapter.HttpActionAdapter
import org.pac4j.core.profile.UserProfile


/**
 * DefaultSecurityGrantedAccessAdapter gets called if user is granted access
 *
 * It should proceed to real request
 *
 * @param service The http4s route that is being protected
 */
class DefaultSecurityGrantedAccessAdapter(
  service: HttpRoutes[IO]) extends SecurityGrantedAccessAdapter[OptionT[IO, Response[IO]], Http4sWebContext] {

  override def adapt(context: Http4sWebContext,
    profiles: util.Collection[UserProfile],
    parameters: Any*): OptionT[IO, Response[IO]] = OptionT(service(context.getRequest).value)
}

/**
 * SecurityFilterMiddleware is applied to all routes that need authentication and
 * authorisation.
 *
 * @author Iain Cardnell
 */
object SecurityFilterMiddleware {

  def securityFilter(service: HttpRoutes[IO],
    config: Config,
    clients: Option[String] = None,
    authorizers: Option[String] = None,
    matchers: Option[String] = None,
    multiProfile: Boolean = false,
    securityGrantedAccessAdapter: HttpRoutes[IO] => SecurityGrantedAccessAdapter[OptionT[IO, Response[IO]], Http4sWebContext] = new DefaultSecurityGrantedAccessAdapter(
      _)): HttpRoutes[IO] =
    Kleisli { request: Request[IO] => {
      val securityLogic = new DefaultSecurityLogic[OptionT[IO, Response[IO]], Http4sWebContext]
      val context = Http4sWebContext(request, config)
      securityLogic.perform(context,
        config,
        securityGrantedAccessAdapter(service),
        config.getHttpActionAdapter.asInstanceOf[HttpActionAdapter[OptionT[IO, Response[IO]], Http4sWebContext]],
        clients.orNull,
        authorizers.orNull,
        matchers.orNull,
        multiProfile)
    }
    }
}
