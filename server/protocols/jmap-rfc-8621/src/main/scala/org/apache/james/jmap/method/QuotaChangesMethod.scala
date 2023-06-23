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
import javax.inject.Inject
import org.apache.james.core.Username
import org.apache.james.jmap.api.change.CanNotCalculateChangesException
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JMAP_CORE, JMAP_QUOTA}
import org.apache.james.jmap.core.Id.Id
import org.apache.james.jmap.core.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.core.{ErrorCode, Invocation, SessionTranslator, UuidState}
import org.apache.james.jmap.json.QuotaSerializer
import org.apache.james.jmap.mail.{JmapQuota, QuotaChangesRequest, QuotaChangesResponse}
import org.apache.james.jmap.routes.SessionSupplier
import org.apache.james.mailbox.MailboxSession
import org.apache.james.mailbox.quota.{QuotaManager, UserQuotaRootResolver}
import org.apache.james.metrics.api.MetricFactory
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.SMono

class QuotaChangesMethod @Inject()(val metricFactory: MetricFactory,
                                   val sessionSupplier: SessionSupplier,
                                   val sessionTranslator: SessionTranslator,
                                   val quotaManager: QuotaManager,
                                   val quotaRootResolver: UserQuotaRootResolver) extends MethodRequiringAccountId[QuotaChangesRequest] {

  override val methodName: Invocation.MethodName = MethodName("Quota/changes")

  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_QUOTA, JMAP_CORE)

  val quotaChangesResolver: QuotaChangesResolver = QuotaChangesResolver(quotaManager, quotaRootResolver)

  override def doProcess(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext, mailboxSession: MailboxSession, request: QuotaChangesRequest): Publisher[InvocationWithContext] =
    quotaChangesResolver.getLatestState(mailboxSession.getUser, capabilities)
      .map(newState => QuotaChangesResponse.from(request.sinceState, newState, request.accountId))
      .map(response => InvocationWithContext(
        invocation = Invocation(
          methodName = methodName,
          arguments = Arguments(QuotaSerializer.serializeChanges(response)),
          methodCallId = invocation.invocation.methodCallId),
        processingContext = invocation.processingContext))
      .onErrorResume {
        case e: CanNotCalculateChangesException => SMono.just(InvocationWithContext(Invocation.error(ErrorCode.CannotCalculateChanges, e.getMessage, invocation.invocation.methodCallId), invocation.processingContext))
        case e => SMono.error(e)
      }

  override def getRequest(mailboxSession: MailboxSession, invocation: Invocation): Either[Exception, QuotaChangesRequest] =
    QuotaSerializer.deserializeQuotaChangesRequest(invocation.arguments.value).asEitherRequest
}

case class QuotaChangesResolver(private val quotaManager: QuotaManager,
                                private val quotaRootResolver: UserQuotaRootResolver) {
  private val jmapQuotaManagerWrapper: JmapQuotaManagerWrapper = JmapQuotaManagerWrapper(quotaManager, quotaRootResolver)

  def getLatestState(username: Username, capabilities: Set[CapabilityIdentifier]): SMono[(UuidState, Seq[Id])] =
    jmapQuotaManagerWrapper.list(username, capabilities)
      .collectSeq()
      .map(quotas => (JmapQuota.correspondingState(quotas), quotas.map(_.id)))
}