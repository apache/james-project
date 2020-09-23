/****************************************************************
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
package org.apache.james.jmap.method


import org.apache.james.jmap.http.SessionSupplier
import org.apache.james.jmap.model.CapabilityIdentifier.CapabilityIdentifier
import org.apache.james.jmap.model.Invocation.MethodName
import org.apache.james.jmap.model.{AccountId, Capabilities, ErrorCode, Invocation, Session}
import org.apache.james.jmap.routes.ProcessingContext
import org.apache.james.mailbox.MailboxSession
import org.apache.james.mailbox.exception.MailboxNotFoundException
import org.apache.james.metrics.api.MetricFactory
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.SMono

trait Method {
  val JMAP_RFC8621_PREFIX: String = "JMAP-RFC8621-"

  val methodName: MethodName

  val requiredCapabilities: Capabilities

  def process(capabilities: Set[CapabilityIdentifier], invocation: Invocation, mailboxSession: MailboxSession, processingContext: ProcessingContext): Publisher[(Invocation, ProcessingContext)]
}

trait WithAccountId {
  def accountId: AccountId
}
trait MethodRequiringAccountId[REQUEST <: WithAccountId] extends Method {
  def metricFactory: MetricFactory
  def sessionSupplier: SessionSupplier

  override def process(capabilities: Set[CapabilityIdentifier], invocation: Invocation, mailboxSession: MailboxSession, processingContext: ProcessingContext): Publisher[(Invocation, ProcessingContext)] = {
    val result = (for {
      request <- getRequest(mailboxSession, invocation)
      response <- validateAccountId(request.accountId, mailboxSession, sessionSupplier, invocation).flatMap{
        case Right(_) =>  doProcess(capabilities, invocation, mailboxSession, processingContext, request)
        case Left(invocation) => SMono.just((invocation, processingContext))
      }
    } yield response)
      .onErrorResume {
      case e: IllegalArgumentException => SMono.just((Invocation.error(ErrorCode.InvalidArguments, e.getMessage, invocation.methodCallId), processingContext))
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

  def doProcess(capabilities: Set[CapabilityIdentifier], invocation: Invocation, mailboxSession: MailboxSession, processingContext: ProcessingContext, request: REQUEST): SMono[(Invocation, ProcessingContext)]

  def getRequest(mailboxSession: MailboxSession, invocation: Invocation): SMono[REQUEST]
}
