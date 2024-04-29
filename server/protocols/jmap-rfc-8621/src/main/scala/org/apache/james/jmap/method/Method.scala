/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/
package org.apache.james.jmap.method

import org.apache.james.jmap.api.exception.ChangeNotFoundException
import org.apache.james.jmap.core.CapabilityIdentifier.CapabilityIdentifier
import org.apache.james.jmap.core.Invocation.MethodName
import org.apache.james.jmap.core.{AccountId, ErrorCode, Invocation, JmapRfc8621Configuration, SessionTranslator}
import org.apache.james.jmap.delegation.ForbiddenAccountManagementException
import org.apache.james.jmap.mail.{IdentityIdNotFoundException, RequestTooLargeException, UnsupportedFilterException, UnsupportedNestingException, UnsupportedRequestParameterException, UnsupportedSortException}
import org.apache.james.jmap.routes.{ProcessingContext, SessionSupplier}
import org.apache.james.mailbox.MailboxSession
import org.apache.james.mailbox.exception.MailboxNotFoundException
import org.apache.james.metrics.api.MetricFactory
import org.apache.james.util.MDCStructuredLogger
import org.reactivestreams.Publisher
import org.slf4j.LoggerFactory
import reactor.core.scala.publisher.SFlux

object Method {
  val LOGGER = LoggerFactory.getLogger(classOf[Method])
}
case class AccountNotFoundException() extends IllegalArgumentException
case class ForbiddenException() extends RuntimeException

case class InvocationWithContext(invocation: Invocation, processingContext: ProcessingContext) {
  def recordInvocation: InvocationWithContext = InvocationWithContext(invocation, processingContext.recordInvocation(invocation))
}

trait Method {
  val JMAP_RFC8621_PREFIX: String = "JMAP-RFC8621-"

  val methodName: MethodName

  val requiredCapabilities: Set[CapabilityIdentifier]

  def process(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext, mailboxSession: MailboxSession): Publisher[InvocationWithContext]
}

trait WithAccountId {
  def accountId: AccountId
}

trait GetRequest extends ValidableRequest {
  def idCount: Option[Int]

  override def validate(configuration: JmapRfc8621Configuration): Either[Exception, GetRequest] =
    if (idCount.exists(value => value > configuration.maxObjectsInGet.value.value)) {
      Left(RequestTooLargeException(s"Too many items in a get request ${this.getClass}. " +
        s"Got $idCount items instead of maximum ${configuration.maxObjectsInGet.value}."))
    } else {
      scala.Right(this)
    }
}

trait SetRequest extends ValidableRequest {
  def idCount: Int

  override def validate(configuration: JmapRfc8621Configuration): Either[Exception, SetRequest] =
    validateIdCounts(configuration)

  def validateIdCounts(configuration: JmapRfc8621Configuration) = {
    if (idCount > configuration.maxObjectsInGet.value.value) {
      Left(RequestTooLargeException(s"Too many items in a set request ${this.getClass}. " +
        s"Got $idCount items instead of maximum ${configuration.maxObjectsInSet.value}."))
    } else {
      scala.Right(this)
    }
  }
}

trait ValidableRequest {
  def validate(configuration: JmapRfc8621Configuration): Either[Exception, ValidableRequest]
}

trait MethodRequiringAccountId[REQUEST <: WithAccountId] extends Method {
  def metricFactory: MetricFactory
  def sessionSupplier: SessionSupplier
  def sessionTranslator: SessionTranslator

  override def process(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext, mailboxSession: MailboxSession): Publisher[InvocationWithContext] = {
    val either: Either[Exception, Publisher[InvocationWithContext]] = for {
      request <- getRequest(mailboxSession, invocation.invocation)
      translatedMailboxSession = sessionTranslator.delegateIfNeeded(mailboxSession, request.accountId)
    } yield {
      translatedMailboxSession.flatMapMany(translatedSession =>
        SFlux(doProcess(capabilities, invocation, translatedSession, request)))
    }

    def logClientSideError(e: Exception): Unit =
      MDCStructuredLogger.forLogger(Method.LOGGER)
        .field("protocol", "JMAP")
        .field("username", mailboxSession.getUser.asString())
        .field("method", invocation.invocation.methodName.value.value)
        .log(logger => logger.info("Client side error executing a JMAP method", e))

    val result: SFlux[InvocationWithContext] = SFlux.fromPublisher(either.fold(e => SFlux.error[InvocationWithContext](e), r => r))
      .onErrorResume[InvocationWithContext] {
        case e: AccountNotFoundException => SFlux.just[InvocationWithContext] (InvocationWithContext(Invocation.error(ErrorCode.AccountNotFound, invocation.invocation.methodCallId), invocation.processingContext))
          .doOnNext(_ => logClientSideError(e))
        case e: ForbiddenAccountManagementException => SFlux.just[InvocationWithContext] (InvocationWithContext(Invocation.error(ErrorCode.Forbidden,
            "Access to other accounts settings is forbidden",
            invocation.invocation.methodCallId), invocation.processingContext))
          .doOnNext(_ => logClientSideError(e))
        case e: UnsupportedRequestParameterException => SFlux.just[InvocationWithContext] (InvocationWithContext(Invocation.error(
            ErrorCode.InvalidArguments,
            s"The following parameter ${e.unsupportedParam} is syntactically valid, but is not supported by the server.",
            invocation.invocation.methodCallId), invocation.processingContext))
          .doOnNext(_ => logClientSideError(e))
        case e: UnsupportedSortException => SFlux.just[InvocationWithContext] (InvocationWithContext(Invocation.error(
            ErrorCode.UnsupportedSort,
            s"The sort ${e.unsupportedSort} is syntactically valid, but it includes a property the server does not support sorting on or a collation method it does not recognise.",
            invocation.invocation.methodCallId), invocation.processingContext))
          .doOnNext(_ => logClientSideError(e))
        case e: UnsupportedFilterException => SFlux.just[InvocationWithContext] (InvocationWithContext(Invocation.error(
            ErrorCode.UnsupportedFilter,
            s"The filter ${e.unsupportedFilter} is syntactically valid, but the server cannot process it. If the filter was the result of a user’s search input, the client SHOULD suggest that the user simplify their search.",
            invocation.invocation.methodCallId), invocation.processingContext))
          .doOnNext(_ => logClientSideError(e))
        case e: UnsupportedNestingException => SFlux.just[InvocationWithContext] (InvocationWithContext(Invocation.error(
            ErrorCode.UnsupportedFilter,
            description = e.message,
            invocation.invocation.methodCallId), invocation.processingContext))
          .doOnNext(_ => logClientSideError(e))
        case e: IllegalArgumentException => SFlux.just[InvocationWithContext] (InvocationWithContext(Invocation.error(ErrorCode.InvalidArguments, e.getMessage, invocation.invocation.methodCallId), invocation.processingContext))
          .doOnNext(_ => logClientSideError(e))
        case e: MailboxNotFoundException => SFlux.just[InvocationWithContext] (InvocationWithContext(Invocation.error(ErrorCode.InvalidArguments, e.getMessage, invocation.invocation.methodCallId), invocation.processingContext))
          .doOnNext(_ => logClientSideError(e))
        case e: ChangeNotFoundException => SFlux.just[InvocationWithContext] (InvocationWithContext(Invocation.error(ErrorCode.CannotCalculateChanges, e.getMessage, invocation.invocation.methodCallId), invocation.processingContext))
          .doOnNext(_ => logClientSideError(e))
        case e: RequestTooLargeException => SFlux.just[InvocationWithContext] (InvocationWithContext(Invocation.error(ErrorCode.RequestTooLarge, e.description, invocation.invocation.methodCallId), invocation.processingContext))
          .doOnNext(_ => logClientSideError(e))
        case e: IdentityIdNotFoundException => SFlux.just[InvocationWithContext] (InvocationWithContext(Invocation.error(ErrorCode.InvalidArguments, e.description, invocation.invocation.methodCallId), invocation.processingContext))
          .doOnNext(_ => logClientSideError(e))
        case e: Throwable => SFlux.error[InvocationWithContext] (e)
          .doOnError(e => MDCStructuredLogger.forLogger(Method.LOGGER)
            .field("protocol", "JMAP")
            .field("username", mailboxSession.getUser.asString())
            .field("method", invocation.invocation.methodName.value.value)
            .log(logger => logger.error("Server side error executing a JMAP method", e)))
      }

    metricFactory.decoratePublisherWithTimerMetric(JMAP_RFC8621_PREFIX + methodName.value, result)
  }

  def doProcess(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext, mailboxSession: MailboxSession, request: REQUEST): Publisher[InvocationWithContext]

  def getRequest(mailboxSession: MailboxSession, invocation: Invocation): Either[Exception, REQUEST]
}

trait WithoutAccountId {
}

trait MethodWithoutAccountId[REQUEST <: WithoutAccountId] extends Method {
  def metricFactory: MetricFactory

  def sessionSupplier: SessionSupplier

  override def process(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext, mailboxSession: MailboxSession): Publisher[InvocationWithContext] = {
    val either: Either[Exception, Publisher[InvocationWithContext]] = for {
      request <- getRequest(invocation.invocation)
    } yield {
      doProcess(invocation, mailboxSession, request)
    }

    val result: SFlux[InvocationWithContext] = SFlux.fromPublisher(either.fold(e => SFlux.error[InvocationWithContext](e), r => r))
      .onErrorResume[InvocationWithContext] {
        case e: UnsupportedRequestParameterException => SFlux.just[InvocationWithContext](InvocationWithContext(Invocation.error(
          ErrorCode.InvalidArguments,
          s"The following parameter ${e.unsupportedParam} is syntactically valid, but is not supported by the server.",
          invocation.invocation.methodCallId), invocation.processingContext))
        case e: UnsupportedNestingException => SFlux.just[InvocationWithContext](InvocationWithContext(Invocation.error(
          ErrorCode.UnsupportedFilter,
          description = e.message,
          invocation.invocation.methodCallId), invocation.processingContext))
        case e: IllegalArgumentException => SFlux.just[InvocationWithContext](InvocationWithContext(Invocation.error(ErrorCode.InvalidArguments, e.getMessage, invocation.invocation.methodCallId), invocation.processingContext))
        case e: RequestTooLargeException => SFlux.just[InvocationWithContext](InvocationWithContext(Invocation.error(ErrorCode.RequestTooLarge, e.description, invocation.invocation.methodCallId), invocation.processingContext))
        case e: Throwable => SFlux.error[InvocationWithContext](e)
      }

    metricFactory.decoratePublisherWithTimerMetric(JMAP_RFC8621_PREFIX + methodName.value, result)
  }

  def getRequest(invocation: Invocation): Either[Exception, REQUEST]

  def doProcess(invocation: InvocationWithContext, mailboxSession: MailboxSession, request: REQUEST): Publisher[InvocationWithContext]
}