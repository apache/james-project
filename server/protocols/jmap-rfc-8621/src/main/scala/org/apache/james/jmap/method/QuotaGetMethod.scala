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
import org.apache.james.core.Username
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JAMES_SHARES, JMAP_CORE, JMAP_QUOTA}
import org.apache.james.jmap.core.Id.Id
import org.apache.james.jmap.core.Invocation.{Arguments, MethodCallId, MethodName}
import org.apache.james.jmap.core.{ErrorCode, Invocation, MissingCapabilityException, Properties}
import org.apache.james.jmap.json.{QuotaSerializer, ResponseSerializer}
import org.apache.james.jmap.mail.{CountResourceType, JmapQuota, OctetsResourceType, QuotaGetRequest, QuotaIdFactory, QuotaNotFound, QuotaResponseGetResult}
import org.apache.james.jmap.routes.SessionSupplier
import org.apache.james.lifecycle.api.Startable
import org.apache.james.mailbox.MailboxSession
import org.apache.james.mailbox.model.QuotaRoot
import org.apache.james.mailbox.quota.{QuotaManager, UserQuotaRootResolver}
import org.apache.james.metrics.api.MetricFactory
import org.apache.james.util.ReactorUtils
import org.reactivestreams.Publisher
import play.api.libs.json.JsObject
import reactor.core.scala.publisher.{SFlux, SMono}

import javax.inject.Inject

class QuotaGetMethod @Inject()(val metricFactory: MetricFactory,
                               val sessionSupplier: SessionSupplier,
                               val quotaManager: QuotaManager,
                               val quotaRootResolver: UserQuotaRootResolver) extends MethodRequiringAccountId[QuotaGetRequest] with Startable {

  override val methodName: Invocation.MethodName = MethodName("Quota/get")
  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_QUOTA, JMAP_CORE)
  val jmapQuotaManagerWrapper: JmapQuotaManagerWrapper = JmapQuotaManagerWrapper(quotaManager, quotaRootResolver)

  override def doProcess(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext, mailboxSession: MailboxSession, request: QuotaGetRequest): Publisher[InvocationWithContext] = {
    val requestedProperties: Properties = request.properties.getOrElse(JmapQuota.allProperties)

    (requestedProperties -- JmapQuota.allProperties match {
      case invalidProperties if invalidProperties.isEmpty() => getQuotaGetResponse(request, mailboxSession.getUser, capabilities)
        .reduce(QuotaResponseGetResult.empty)(QuotaResponseGetResult.merge)
        .map(result => result.asResponse(accountId = request.accountId))
        .map(response => Invocation(
          methodName = methodName,
          arguments = Arguments(QuotaSerializer.serialize(response, requestedProperties).as[JsObject]),
          methodCallId = invocation.invocation.methodCallId))
      case invalidProperties: Properties => SMono.just(Invocation.error(
        errorCode = ErrorCode.InvalidArguments,
        description = s"The following properties [${invalidProperties.format()}] do not exist.",
        methodCallId = invocation.invocation.methodCallId))
    })
      .map(InvocationWithContext(_, invocation.processingContext))
      .onErrorResume { case e: Exception => handleRequestValidationErrors(e, invocation.invocation.methodCallId)
        .map(errorInvocation => InvocationWithContext(errorInvocation, invocation.processingContext))
      }
  }

  override def getRequest(mailboxSession: MailboxSession, invocation: Invocation): Either[Exception, QuotaGetRequest] =
    QuotaSerializer.deserializeQuotaGetRequest(invocation.arguments.value)
      .asEither.left.map(ResponseSerializer.asException)

  private def getQuotaGetResponse(quotaGetRequest: QuotaGetRequest, username: Username, capabilities: Set[CapabilityIdentifier]): SFlux[QuotaResponseGetResult] =
    quotaGetRequest.ids match {
      case None =>
        jmapQuotaManagerWrapper.list(username, capabilities)
          .collectSeq()
          .map(listJmapQuota => QuotaResponseGetResult(jmapQuotaSet = listJmapQuota.toSet))
          .flatMapMany(result => SFlux.just(result))
      case Some(ids) => SFlux.fromIterable(ids.value)
        .flatMap(id => jmapQuotaManagerWrapper.get(username, id.id, capabilities)
          .map(jmapQuota => QuotaResponseGetResult(jmapQuotaSet = Set(jmapQuota)))
          .switchIfEmpty(SMono.just(QuotaResponseGetResult(notFound = QuotaNotFound(Set(id))))))
    }

  private def handleRequestValidationErrors(exception: Exception, methodCallId: MethodCallId): SMono[Invocation] = exception match {
    case _: MissingCapabilityException => SMono.just(Invocation.error(ErrorCode.UnknownMethod, methodCallId))
    case e: IllegalArgumentException => SMono.just(Invocation.error(ErrorCode.InvalidArguments, e.getMessage, methodCallId))
  }
}

case class JmapQuotaManagerWrapper(private var quotaManager: QuotaManager,
                                   private var quotaRootResolver: UserQuotaRootResolver) {
  def get(username: Username, quotaId: Id, capabilities: Set[CapabilityIdentifier]): SFlux[JmapQuota] =
    retrieveQuotaRoot(username, capabilities)
      .flatMap(quotaRoot => getJmapQuota(quotaRoot, Some(quotaId)))

  def list(username: Username, capabilities: Set[CapabilityIdentifier]): SFlux[JmapQuota] =
    retrieveQuotaRoot(username, capabilities)
      .flatMap(quotaRoot => getJmapQuota(quotaRoot))

  def retrieveQuotaRoot(username: Username, capabilities: Set[CapabilityIdentifier]): SFlux[QuotaRoot] =
    SMono.just(capabilities)
      .map(value => value.contains(JAMES_SHARES))
      .filter(containSharedCapability => containSharedCapability)
      .flatMapMany(_ => SFlux(quotaRootResolver.listAllAccessibleQuotaRoots(username)))
      .switchIfEmpty(SFlux.just(quotaRootResolver.forUser(username)))

  private def getJmapQuota(quotaRoot: QuotaRoot, quotaId: Option[Id] = None): SFlux[JmapQuota] =
    (quotaId match {
      case None => SMono(quotaManager.getQuotasReactive(quotaRoot))
        .flatMapMany(quotas => SMono.fromCallable(() => JmapQuota.extractUserMessageCountQuota(quotas.getMessageQuota, QuotaIdFactory.from(quotaRoot, CountResourceType), quotaRoot))
          .mergeWith(SMono.fromCallable(() => JmapQuota.extractUserMessageSizeQuota(quotas.getStorageQuota, QuotaIdFactory.from(quotaRoot, OctetsResourceType), quotaRoot))))

      case Some(quotaIdValue) =>
        val quotaCountPublisher = SMono.fromCallable(() => QuotaIdFactory.from(quotaRoot, CountResourceType))
          .filter(countQuotaId => countQuotaId.value.equals(quotaIdValue.value))
          .flatMap(_ => SMono.fromCallable(() => quotaManager.getMessageQuota(quotaRoot))
            .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER))
          .map(quota => JmapQuota.extractUserMessageCountQuota(quota, quotaIdValue, quotaRoot))

        val quotaSizePublisher = SMono.fromCallable(() => QuotaIdFactory.from(quotaRoot, OctetsResourceType))
          .filter(sizeQuotaId => sizeQuotaId.value.equals(quotaIdValue.value))
          .flatMap(_ => SMono.fromCallable(() => quotaManager.getStorageQuota(quotaRoot))
            .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER))
          .map(quota => JmapQuota.extractUserMessageSizeQuota(quota, quotaIdValue, quotaRoot))

        quotaCountPublisher.mergeWith(quotaSizePublisher)
    }).flatMap(SMono.justOrEmpty)

}