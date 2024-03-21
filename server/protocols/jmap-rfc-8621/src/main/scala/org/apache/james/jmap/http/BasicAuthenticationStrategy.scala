/** **************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 * *
 * http://www.apache.org/licenses/LICENSE-2.0                 *
 * *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 * ***************************************************************/

package org.apache.james.jmap.http

import java.util.Base64

import com.google.common.base.CharMatcher
import eu.timepit.refined.api.{Refined, Validate}
import eu.timepit.refined.auto._
import eu.timepit.refined.refineV
import jakarta.inject.Inject
import org.apache.james.core.Username
import org.apache.james.jmap.exceptions.UnauthorizedException
import org.apache.james.jmap.http.UserCredential._
import org.apache.james.mailbox.{MailboxManager, MailboxSession}
import org.apache.james.user.api.UsersRepository
import org.apache.james.util.ReactorUtils
import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono
import reactor.core.scala.publisher.{SMono, scalaOption2JavaOptional}
import reactor.netty.http.server.HttpServerRequest

import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._
import scala.util.{Failure, Success, Try}

object UserCredential {
  type BasicAuthenticationHeaderValue = String Refined BasicAuthenticationHeaderValueConstraint

  type CredentialsAsString = String Refined CredentialsAsStringConstraint

  case class CredentialsAsStringConstraint()
  case class BasicAuthenticationHeaderValueConstraint()

  private val charMatcher: CharMatcher = CharMatcher.inRange('a', 'z')
    .or(CharMatcher.inRange('0', '9'))
    .or(CharMatcher.inRange('A', 'Z'))
    .or(CharMatcher.is('_'))
    .or(CharMatcher.is('='))
    .or(CharMatcher.is('-'))
    .or(CharMatcher.is('#'))

  implicit val validateCredentialsAsString: Validate.Plain[String, CredentialsAsStringConstraint] =
    Validate.fromPredicate(s => s.contains(':'),
      _ => "Credential string must contains ':'",
      CredentialsAsStringConstraint())

  implicit val validateBasicAuthenticationHeaderValueConstraint: Validate.Plain[String, BasicAuthenticationHeaderValueConstraint] =
    Validate.fromPredicate(s => s.startsWith("Basic ") && charMatcher.matchesAllOf(s.substring(6)),
      _ => "Must start with 'Basic' prefix then be only letter or digits",
      BasicAuthenticationHeaderValueConstraint())

  private val logger = LoggerFactory.getLogger(classOf[UserCredential])
  private val BASIC_AUTHENTICATION_PREFIX: String = "Basic "

  def parseUserCredentials(token: String): Option[UserCredential] = {
    val refinedValue: Either[String, BasicAuthenticationHeaderValue] = refineV(token)

    refinedValue match {
      // Ignore Authentication headers not being Basic Auth
      case Left(_) => None
      case Right(value) => extractUserCredentialsAsString(value)
    }
  }

  private def extractUserCredentialsAsString(token: BasicAuthenticationHeaderValue): Option[UserCredential] = {
    val encodedCredentials = token.substring(BASIC_AUTHENTICATION_PREFIX.length)
    val decodedCredentialsString = new String(Base64.getDecoder.decode(encodedCredentials))
    val refinedValue: Either[String, CredentialsAsString] = refineV(decodedCredentialsString)

    refinedValue match {
      case Left(errorMessage: String) =>
        throw new UnauthorizedException(s"Supplied basic authentication credentials do not match expected format. $errorMessage")
      case Right(value) => toCredential(value)
    }
  }

  private def toCredential(token: CredentialsAsString): Option[UserCredential] = {
    val partSeparatorIndex: Int = token.indexOf(':')
    val usernameString: String = token.substring(0, partSeparatorIndex)
    val passwordString: String = token.substring(partSeparatorIndex + 1)

    Try(UserCredential(Username.of(usernameString), passwordString)) match {
      case Success(credential) => Some(credential)
      case Failure(throwable:IllegalArgumentException) =>
        throw new UnauthorizedException("Username is not valid", throwable)
      case Failure(unexpectedException) =>
        logger.error("Unexpected Exception", unexpectedException)
        throw unexpectedException
    }
  }
}

case class UserCredential(username: Username, password: String)

class BasicAuthenticationStrategy @Inject()(val usersRepository: UsersRepository,
                                            val mailboxManager: MailboxManager) extends AuthenticationStrategy {

  override def createMailboxSession(httpRequest: HttpServerRequest): Mono[MailboxSession] =
    SMono.fromCallable(() => authHeaders(httpRequest))
      .map(parseUserCredentials)
      .handle(publishNext)
      .flatMap(getAuthenticatedUsername)
      .map(loggedInUser => mailboxManager.authenticate(loggedInUser).withoutDelegation())
      .asJava()


  override def correspondingChallenge(): AuthenticationChallenge = AuthenticationChallenge.of(
    AuthenticationScheme.of("Basic"), Map("realm" -> "simple").asJava)

  private def publishNext[T]: (Option[T], reactor.core.publisher.SynchronousSink[T]) => Unit =
    (maybeT, sink) => maybeT.foreach(t => sink.next(t))

  private def getAuthenticatedUsername(userCredential: UserCredential): SMono[Username] =
    SMono.fromCallable(() => usersRepository.test(userCredential.username, userCredential.password)
          .orElseThrow(() => new UnauthorizedException("Wrong credentials provided")))
      .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER)
}
