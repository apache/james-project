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


import org.apache.james.jmap.http.SessionSupplier
import org.apache.james.jmap.mail.{UnsupportedFilterException, UnsupportedNestingException, UnsupportedRequestParameterException, UnsupportedSortException}
import org.apache.james.jmap.model.CapabilityIdentifier.CapabilityIdentifier
import org.apache.james.jmap.model.Invocation.MethodName
import org.apache.james.jmap.model.{AccountId, Capabilities, ErrorCode, Invocation, Session}
import org.apache.james.jmap.routes.ProcessingContext
import org.apache.james.mailbox.MailboxSession
import org.apache.james.mailbox.exception.MailboxNotFoundException
import org.apache.james.metrics.api.MetricFactory
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.SMono

case class InvocationWithContext(invocation: Invocation, processingContext: ProcessingContext)

trait Method {
  val JMAP_RFC8621_PREFIX: String = "JMAP-RFC8621-"

  val methodName: MethodName

  val requiredCapabilities: Capabilities

  def process(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext, mailboxSession: MailboxSession): Publisher[InvocationWithContext]
}

trait WithAccountId {
  def accountId: AccountId
}
trait MethodRequiringAccountId[REQUEST <: WithAccountId] extends Method {
  def metricFactory: MetricFactory
  def sessionSupplier: SessionSupplier

  override def process(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext, mailboxSession: MailboxSession): Publisher[InvocationWithContext] = {
    val result = (for {
      request <- getRequest(mailboxSession, invocation.invocation)
      response <- validateAccountId(request.accountId, mailboxSession, sessionSupplier, invocation.invocation).flatMap{
        case Right(_) => doProcess(capabilities, invocation, mailboxSession, request)
        case Left(errorInvocation) => SMono.just(InvocationWithContext(errorInvocation, invocation.processingContext))
      }
    } yield response)
      .onErrorResume {
        case e: UnsupportedRequestParameterException => SMono.just(InvocationWithContext(Invocation.error(
          ErrorCode.InvalidArguments,
          s"The following parameter ${e.unsupportedParam} is syntactically valid, but is not supported by the server.",
          invocation.invocation.methodCallId), invocation.processingContext))
        case e: UnsupportedSortException => SMono.just(InvocationWithContext(Invocation.error(
          ErrorCode.UnsupportedSort,
          s"The sort ${e.unsupportedSort} is syntactically valid, but it includes a property the server does not support sorting on or a collation method it does not recognise.",
          invocation.invocation.methodCallId), invocation.processingContext))
        case e: UnsupportedFilterException => SMono.just(InvocationWithContext(Invocation.error(
          ErrorCode.UnsupportedFilter,
          s"The filter ${e.unsupportedFilter} is syntactically valid, but the server cannot process it. If the filter was the result of a user’s search input, the client SHOULD suggest that the user simplify their search.",
          invocation.invocation.methodCallId), invocation.processingContext))
        case e: UnsupportedNestingException => SMono.just(InvocationWithContext(Invocation.error(
          ErrorCode.UnsupportedFilter,
          description = e.message,
          invocation.invocation.methodCallId), invocation.processingContext))
        case e: IllegalArgumentException => SMono.just(InvocationWithContext(Invocation.error(ErrorCode.InvalidArguments, e.getMessage, invocation.invocation.methodCallId), invocation.processingContext))
        case e: MailboxNotFoundException => SMono.just(InvocationWithContext(Invocation.error(ErrorCode.InvalidArguments, e.getMessage, invocation.invocation.methodCallId), invocation.processingContext))
        case e: Throwable => SMono.raiseError(e)
      }


    metricFactory.decoratePublisherWithTimerMetricLogP99(JMAP_RFC8621_PREFIX + methodName.value, result)
  }

  private def validateAccountId(accountId: AccountId, mailboxSession: MailboxSession, sessionSupplier: SessionSupplier, invocation: Invocation): SMono[Either[Invocation, Session]] = {
    sessionSupplier.generate(mailboxSession.getUser)
      .filter(session => session.accounts.map(_.accountId).contains(accountId))
      .map(session => Right[Invocation, Session](session).asInstanceOf[Either[Invocation, Session]])
      .switchIfEmpty(SMono.just(Left[Invocation, Session](Invocation.error(ErrorCode.AccountNotFound, invocation.methodCallId))))
  }

  def doProcess(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext, mailboxSession: MailboxSession, request: REQUEST): SMono[InvocationWithContext]

  def getRequest(mailboxSession: MailboxSession, invocation: Invocation): SMono[REQUEST]
}
