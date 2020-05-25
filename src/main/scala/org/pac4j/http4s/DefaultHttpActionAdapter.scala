package org.pac4j.http4s

import cats.data.OptionT
import cats.effect.IO
import org.http4s.Response
import org.pac4j.core.exception.http.{HttpAction, WithLocationAction}
import org.pac4j.core.http.adapter.HttpActionAdapter

/**
  * DefaultHttpActionAdapter sets the correct status codes on the response.
  *
  * @author Iain Cardnell
  */
object DefaultHttpActionAdapter extends HttpActionAdapter[OptionT[IO, Response[IO]], Http4sWebContext] {

  override def adapt(action: HttpAction, context: Http4sWebContext): OptionT[IO, Response[IO]] = {
    OptionT(
      IO.delay {
        action match {
          case a: WithLocationAction => context.setResponseHeader("Location", a.getLocation)
        }
        context.setResponseStatus(action.getCode)
        Option(context.getResponse)
      }
    )
  }
}
