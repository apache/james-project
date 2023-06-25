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

import eu.timepit.refined.auto._
import javax.inject.Inject
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JMAP_CORE, JMAP_QUOTA}
import org.apache.james.jmap.core.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.core.{CanCalculateChanges, ErrorCode, Invocation, Limit, Position, QueryState, SessionTranslator}
import org.apache.james.jmap.json.QuotaSerializer
import org.apache.james.jmap.mail.{JmapQuota, QuotaQueryFilter, QuotaQueryRequest, QuotaQueryResponse}
import org.apache.james.jmap.routes.SessionSupplier
import org.apache.james.mailbox.MailboxSession
import org.apache.james.mailbox.quota.{QuotaManager, UserQuotaRootResolver}
import org.apache.james.metrics.api.MetricFactory
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.SMono

class QuotaQueryMethod @Inject()(val metricFactory: MetricFactory,
                                 val sessionSupplier: SessionSupplier,
                                 val sessionTranslator: SessionTranslator,
                                 val quotaManager: QuotaManager,
                                 val quotaRootResolver: UserQuotaRootResolver) extends MethodRequiringAccountId[QuotaQueryRequest] {

  override val methodName: Invocation.MethodName = MethodName("Quota/query")
  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_QUOTA, JMAP_CORE)

  val jmapQuotaManagerWrapper: JmapQuotaManagerWrapper = JmapQuotaManagerWrapper(quotaManager, quotaRootResolver)

  override def doProcess(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext, mailboxSession: MailboxSession, request: QuotaQueryRequest): Publisher[InvocationWithContext] =
    processRequest(mailboxSession, invocation.invocation, request, capabilities)
      .onErrorResume {
        case e: IllegalArgumentException => SMono.just(
          Invocation.error(
            errorCode = ErrorCode.InvalidArguments,
            description = e.getMessage,
            methodCallId = invocation.invocation.methodCallId))
        case e: Throwable => SMono.error(e)
      }
      .map(invocationResult => InvocationWithContext(invocationResult, invocation.processingContext))

  override def getRequest(mailboxSession: MailboxSession, invocation: Invocation): Either[Exception, QuotaQueryRequest] =
    QuotaSerializer.deserializeQuotaQueryRequest(invocation.arguments.value).asEitherRequest

  private def processRequest(mailboxSession: MailboxSession, invocation: Invocation, request: QuotaQueryRequest, capabilities: Set[CapabilityIdentifier]): SMono[Invocation] =
    jmapQuotaManagerWrapper.list(mailboxSession.getUser, capabilities)
      .filter(filterPredicate(request.filter))
      .collectSeq()
      .map(quotas => QuotaQueryResponse(
        accountId = request.accountId,
        queryState = QueryState.forQuotaIds(quotas.map(_.id).sortBy(_.value)),
        canCalculateChanges = CanCalculateChanges(false),
        ids = quotas.map(_.id),
        position = Position.zero,
        limit = Some(Limit.default)))
      .map(response => Invocation(methodName = methodName, arguments = Arguments(QuotaSerializer.serializeQuery(response)), methodCallId = invocation.methodCallId))

  private def filterPredicate(filter: QuotaQueryFilter): (JmapQuota) => scala.Boolean =
    quota => {
      ((filter.name match {
        case None => true
        case Some(value) => value.string == quota.name.string
      })
        && filter.scope.forall(_.contains(quota.scope))
        && filter.resourceTypes.forall(_.contains(quota.resourceType))
        && filter.dataTypes.forall(dataTypesValue => quota.dataTypes.toSet.subsetOf(dataTypesValue)))
    }
}
