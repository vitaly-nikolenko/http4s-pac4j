package org.pac4j.http4s

import java.util
import java.util.Optional

import cats.effect.{ContextShift, IO}
import io.chrisdavenport.vault.Key
import org.http4s
import org.http4s.headers.{`Content-Type`, Cookie => CookieHeader}
import org.http4s.util.CaseInsensitiveString
import org.http4s.{Charset, Header, HttpDate, MediaType, Request, Response, Status, UrlForm}
import org.pac4j.core.config.Config
import org.pac4j.core.context.session.SessionStore
import org.pac4j.core.context.{Cookie, WebContext}
import org.pac4j.core.profile.CommonProfile
import org.pac4j.core.util.Pac4jConstants
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext.Implicits.global
import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._

/**
 * Http4sWebContext is the adapter layer to allow Pac4j to interact with
 * Http4s request and response objects.
 *
 * @param request      Http4s request object currently being handled
 * @param sessionStore User session information
 * @author Iain Cardnell
 */
class Http4sWebContext(private var request: Request[IO],
  private val sessionStore: SessionStore[Http4sWebContext]) extends WebContext {

  implicit val cs: ContextShift[IO] = IO.contextShift(global)

  private val logger = LoggerFactory.getLogger(this.getClass)

  private var response: Response[IO] = Response[IO]()

  case class Pac4jUserProfiles(pac4jUserProfiles: util.LinkedHashMap[String, CommonProfile])

  val pac4jUserProfilesAttr: Key[Pac4jUserProfiles] = Key[Pac4jUserProfiles]
  val sessionIdAttr: Key[String] = Key[String]
  val pac4CsrfTokenAttr: Key[String] = Key[String]
  val pac4LoadProfileFromSessionAttr: Key[Boolean] = Key[Boolean]

  override def getSessionStore: SessionStore[Http4sWebContext] = sessionStore

  override def getRequestParameter(name: String): Optional[String] = {
    if (request.contentType.contains(`Content-Type`(new MediaType("application", "x-www-form-urlencoded")))) {
      logger.debug(s"getRequestParameter: Getting from Url Encoded Form name=$name")
      UrlForm.decodeString(Charset.`UTF-8`)(getRequestContent) match {
        case Left(err) => throw new Exception(err.toString)
        case Right(urlForm) => Optional.ofNullable(urlForm.getFirstOrElse(name, request.params.get(name).orNull))
      }
    } else {
      logger.debug(s"getRequestParameter: Getting from query params name=$name")
      Optional.ofNullable(request.params.get(name).orNull)
    }
  }

  override def getRequestParameters: util.Map[String, Array[String]] = {
    logger.debug(s"getRequestParameters")
    request.params.toSeq.map(a => (a._1, Array(a._2))).toMap.asJava
  }

  override def getRequestAttribute(name: String): Optional[_] = {
    logger.debug(s"getRequestAttribute: $name")
    val value = name match {
      case Pac4jConstants.USER_PROFILES =>
        request.attributes.lookup(pac4jUserProfilesAttr).toJava
      case Pac4jConstants.SESSION_ID =>
        request.attributes.lookup(sessionIdAttr).toJava
      case Pac4jConstants.CSRF_TOKEN =>
        request.attributes.lookup(pac4CsrfTokenAttr).toJava
      case Pac4jConstants.LOAD_PROFILES_FROM_SESSION =>
        request.attributes.lookup(pac4LoadProfileFromSessionAttr).toJava
      case _ =>
        throw new NotImplementedError(s"getRequestAttribute for $name not implemented")
    }
    logger.debug(s"value: $value")
    value
  }

  override def setRequestAttribute(name: String, value: Any): Unit = {
    logger.debug(s"setRequestAttribute: $name")
    request = name match {
      case Pac4jConstants.USER_PROFILES =>
        request.withAttribute(pac4jUserProfilesAttr,
          Pac4jUserProfiles(value.asInstanceOf[util.LinkedHashMap[String, CommonProfile]]))
      case Pac4jConstants.SESSION_ID =>
        request.withAttribute(sessionIdAttr, value.asInstanceOf[String])
      case Pac4jConstants.CSRF_TOKEN =>
        request.withAttribute(pac4CsrfTokenAttr, value.asInstanceOf[String])
      case Pac4jConstants.LOAD_PROFILES_FROM_SESSION =>
        request.withAttribute(pac4LoadProfileFromSessionAttr, value.asInstanceOf[Boolean])
      case _ =>
        throw new NotImplementedError(s"setRequestAttribute for $name not implemented")
    }
  }

  override def getRequestHeader(name: String): Optional[String] = Optional.ofNullable(
    request.headers.find(_.name == CaseInsensitiveString(name)).map(_.value).orNull)

  override def getRequestMethod: String = request.method.name

  override def getRemoteAddr: String = request.remoteAddr.orNull

  def setResponseStatus(code: Int): Unit = {
    logger.debug(s"setResponseStatus $code")
    modifyResponse { r =>
      r.withStatus(Status.fromInt(code).getOrElse(Status.Ok))
    }
  }

  override def setResponseHeader(name: String, value: String): Unit = {
    logger.debug(s"setResponseHeader $name = $value")
    modifyResponse { r =>
      r.putHeaders(Header(name, value))
    }
  }

  override def setResponseContentType(content: String): Unit = {
    logger.debug("setResponseContentType: " + content)
    // TODO Parse the input
    modifyResponse { r =>
      r.withContentType((`Content-Type`(new MediaType("text", "html"), Some(Charset.`UTF-8`))))
    }
  }

  override def getServerName: String = request.serverAddr

  override def getServerPort: Int = request.serverPort

  override def getScheme: String = request.uri.scheme.map(_.value).orNull

  override def isSecure: Boolean = request.isSecure.getOrElse(false)

  override def getFullRequestURL: String = request.uri.toString()

  override def getRequestCookies: util.Collection[Cookie] = {
    logger.debug("getRequestCookies")
    val convertCookie = (c: org.http4s.RequestCookie) => new org.pac4j.core.context.Cookie(c.name, c.content)
    val cookies = CookieHeader.from(request.headers).map(_.values.map(convertCookie))
    cookies match {
      case Some(list) => list.toList.asJavaCollection
      case None => Nil.asJavaCollection
    }
  }

  override def addResponseCookie(cookie: Cookie): Unit = {
    logger.debug(s"addResponseCookie: $cookie")
    val expires = if (cookie.getMaxAge == -1) {
      None
    } else {
      Some(HttpDate.unsafeFromEpochSecond(cookie.getMaxAge))
    }
    val http4sCookie = http4s.ResponseCookie(cookie.getName, cookie.getValue, expires, path = Option(cookie.getPath))
    response = response.addCookie(http4sCookie)
  }

  def removeResponseCookie(name: String): Unit = {
    logger.debug("removeResponseCookie")
    response = response.removeCookie(name)
  }

  override def getPath: String = request.uri.path

  override def getRequestContent: String = {
    request.bodyAsText.compile.string.unsafeRunSync()
  }

  override def getProtocol: String = request.uri.scheme.get.value

  def modifyResponse(f: Response[IO] => Response[IO]): Unit = {
    response = f(response)
  }

  def getRequest: Request[IO] = request

  def getResponse: Response[IO] = response
}

object Http4sWebContext {

  def apply(request: Request[IO], config: Config) =
    new Http4sWebContext(request, config.getSessionStore.asInstanceOf[SessionStore[Http4sWebContext]])
}
