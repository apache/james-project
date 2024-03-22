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
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JMAP_CORE, JMAP_MAIL}
import org.apache.james.jmap.core.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.core.{AccountId, Invocation, JmapRfc8621Configuration, SessionTranslator, UuidState}
import org.apache.james.jmap.json.ThreadSerializer
import org.apache.james.jmap.mail.{Thread, ThreadGetRequest, ThreadGetResponse, ThreadNotFound, UnparsedThreadId}
import org.apache.james.jmap.routes.SessionSupplier
import org.apache.james.mailbox.exception.ThreadNotFoundException
import org.apache.james.mailbox.model.{ThreadId => JavaThreadId}
import org.apache.james.mailbox.{MailboxManager, MailboxSession}
import org.apache.james.metrics.api.MetricFactory
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.util.Try

object ThreadGetResult {
  def empty: ThreadGetResult = ThreadGetResult(Set.empty, ThreadNotFound(Set.empty))

  def merge(result1: ThreadGetResult, result2: ThreadGetResult): ThreadGetResult = result1.merge(result2)

  def found(thread: Thread): ThreadGetResult =
    ThreadGetResult(Set(thread), ThreadNotFound(Set.empty))

  def notFound(unparsedThreadId: UnparsedThreadId): ThreadGetResult =
    ThreadGetResult(Set.empty, ThreadNotFound(Set(unparsedThreadId)))
}

case class ThreadGetResult(threads: Set[Thread], notFound: ThreadNotFound) {
  def merge(other: ThreadGetResult): ThreadGetResult =
    ThreadGetResult(this.threads ++ other.threads, this.notFound.merge(other.notFound))

  def asResponse(accountId: AccountId): ThreadGetResponse =
    ThreadGetResponse(
      accountId = accountId,
      state = UuidState.INSTANCE,
      list = threads.toList,
      notFound = notFound)
}

class ThreadGetMethod @Inject()(val metricFactory: MetricFactory,
                                val configuration: JmapRfc8621Configuration,
                                val sessionSupplier: SessionSupplier,
                                val sessionTranslator: SessionTranslator,
                                val threadIdFactory: JavaThreadId.Factory,
                                val mailboxManager: MailboxManager) extends MethodRequiringAccountId[ThreadGetRequest] {
  override val methodName: MethodName = MethodName("Thread/get")
  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE, JMAP_MAIL)

  override def doProcess(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext, mailboxSession: MailboxSession, request: ThreadGetRequest): SMono[InvocationWithContext] =
    getThreadResponse(request, mailboxSession)
      .reduce(ThreadGetResult.empty)(ThreadGetResult.merge)
      .map(threadGetResult => threadGetResult.asResponse(request.accountId))
      .map(threadGetResponse => Invocation(
        methodName = methodName,
        arguments = Arguments(ThreadSerializer.serialize(threadGetResponse)),
        methodCallId = invocation.invocation.methodCallId))
      .map(InvocationWithContext(_, invocation.processingContext))

  override def getRequest(mailboxSession: MailboxSession, invocation: Invocation): Either[Exception, ThreadGetRequest] =
    ThreadSerializer.deserialize(invocation.arguments.value).asEitherRequest
      .flatMap(request => request.validate(configuration).map(_ => request))

  private def getThreadResponse(threadGetRequest: ThreadGetRequest,
                                mailboxSession: MailboxSession): SFlux[ThreadGetResult] =
    SFlux.fromIterable(threadGetRequest.ids)
      .flatMap(unparsedThreadId => {
        Try(threadIdFactory.fromString(unparsedThreadId.id.toString()))
          .fold(_ => SFlux.just(ThreadGetResult.notFound(unparsedThreadId)),
            threadId => SFlux.fromPublisher(mailboxManager.getThread(threadId, mailboxSession))
              .collectSeq()
              .map(seq => Thread(id = unparsedThreadId.id, emailIds = seq.toList))
              .map(ThreadGetResult.found)
              .onErrorResume({
                case _: ThreadNotFoundException => SMono.just(ThreadGetResult.notFound(unparsedThreadId))
                case e => SMono.error(e)
              }))
      })
}
