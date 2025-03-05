/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/
package org.apache.james.jmap.method

import eu.timepit.refined.auto._
import jakarta.inject.Inject
import org.apache.james.jmap.api.projections.EmailQueryViewManager
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JMAP_CORE, JMAP_MAIL}
import org.apache.james.jmap.core.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.core.Limit.Limit
import org.apache.james.jmap.core.Position.Position
import org.apache.james.jmap.core._
import org.apache.james.jmap.json.EmailQuerySerializer
import org.apache.james.jmap.mail._
import org.apache.james.jmap.routes.SessionSupplier
import org.apache.james.mailbox.exception.MailboxNotFoundException
import org.apache.james.mailbox.model.{ThreadId, ThreadId => JavaThreadId, _}
import org.apache.james.mailbox.{MailboxManager, MailboxSession}
import org.apache.james.metrics.api.MetricFactory
import org.apache.james.util.streams.{Limit => JavaLimit}
import reactor.core.scala.publisher.{SFlux, SMono}


class ThreadQueryMethod @Inject()(serializer: EmailQuerySerializer,
                                          val metricFactory: MetricFactory,
                                          val configuration: JmapRfc8621Configuration,
                                          val sessionSupplier: SessionSupplier,
                                          val sessionTranslator: SessionTranslator,
                                          val threadIdFactory: JavaThreadId.Factory,
                                          val threadQueryViewManager: EmailQueryViewManager,
                                          val mailboxManager: MailboxManager) extends MethodRequiringAccountId[ThreadQueryRequest] {
  override val methodName: MethodName = MethodName("Thread/query")
  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE, JMAP_MAIL)

  override def doProcess(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext, mailboxSession: MailboxSession, request: ThreadQueryRequest): SMono[InvocationWithContext] = {
    processRequest(mailboxSession, invocation.invocation, request, capabilities)
      .map(invocationResult => InvocationWithContext(invocationResult, invocation.processingContext))
  }

  private def processRequest(mailboxSession: MailboxSession,
                             invocation: Invocation,
                             request: ThreadQueryRequest,
                             capabilities: Set[CapabilityIdentifier]): SMono[Invocation] = {
    def validation: Either[Throwable, SMono[Invocation]] = for {
        limit <- Limit.validateRequestLimit(request.limit)
        position <- Position.validateRequestPosition(request.position)
      } yield {
        executeQuery(mailboxSession, request, position, limit)
          .map(response => Invocation(
            methodName = methodName,
            arguments = Arguments(serializer.serializeThreadQuery(response)),
            methodCallId = invocation.methodCallId))
      }
    validation.fold(SMono.error, res => res)
  }

  override def getRequest(mailboxSession: MailboxSession, invocation: Invocation): Either[Exception, ThreadQueryRequest] = {
    serializer.deserializeThreadQuery(invocation.arguments.value).asEitherRequest
  }

  private def executeQuery(session: MailboxSession, request: ThreadQueryRequest, position: Position, limit: Limit): SMono[ThreadQueryResponse] = {
    println("Email Query Method execute Query called ")
    println("req: ", request)

    val ids1: SMono[Seq[ThreadId]] = testThread(session, position, limit, request)
    println("TESTING PURPOSE:", ids1)

    ids1.map(ids => toResponse(request, ids))
  }


  private def testThread(mailboxSession: MailboxSession, position: Position, limitToUse: Limit, request: ThreadQueryRequest): SMono[Seq[ThreadId]] = {
    println("kaka queryViewForListingSortedByReceivedAt")
    val mailboxId: MailboxId = request.filter.get.asInstanceOf[FilterCondition].inMailbox.get
    val thread: SFlux[ThreadId] = SFlux.fromPublisher(threadQueryViewManager
      .getThreadQueryView(mailboxSession.getUser).listMailboxContentSortedBySentAt(mailboxId,JavaLimit.from(limitToUse.value + position.value)))

    fromThreadViewEntries(mailboxId, thread, mailboxSession, position, limitToUse)
  }

  private def fromThreadViewEntries(mailboxId: MailboxId, queryViewEntries: SFlux[ThreadId], mailboxSession: MailboxSession, position: Position, limitToUse: Limit): SMono[Seq[ThreadId]] =
    SMono(mailboxManager.getMailboxReactive(mailboxId, mailboxSession))
      .flatMap(_ => queryViewEntries
        .drop(position.value)
        .take(limitToUse.value)
        .collectSeq())
      .switchIfEmpty(SMono.just[Seq[ThreadId]](Seq()))
      .onErrorResume({
        case _: MailboxNotFoundException => SMono.just[Seq[ThreadId]](Seq())
        case e => SMono.error[Seq[ThreadId]](e)
      })

  private def toResponse(request: ThreadQueryRequest, ids: Seq[ThreadId]): ThreadQueryResponse =
    ThreadQueryResponse(accountId = request.accountId,
      ids = ids)
}