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
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JMAP_CORE, JMAP_QUOTA}
import org.apache.james.jmap.core.Invocation.{Arguments, MethodCallId, MethodName}
import org.apache.james.jmap.core.{ErrorCode, Invocation, MissingCapabilityException, Properties, UnsignedInt}
import org.apache.james.jmap.json.{QuotaSerializer, ResponseSerializer}
import org.apache.james.jmap.mail.{CountResourceType, JmapQuota, MailDataType, OctetsResourceType, QuotaGetRequest, QuotaIdFactory, QuotaName, QuotaNotFound, QuotaResponseGetResult, StorageDataType}
import org.apache.james.jmap.routes.SessionSupplier
import org.apache.james.lifecycle.api.Startable
import org.apache.james.mailbox.MailboxSession
import org.apache.james.mailbox.model.QuotaRoot
import org.apache.james.mailbox.quota.{QuotaManager, UserQuotaRootResolver}
import org.apache.james.metrics.api.MetricFactory
import org.reactivestreams.Publisher
import play.api.libs.json.{JsError, JsObject, JsSuccess}
import reactor.core.scala.publisher.{SFlux, SMono}

import javax.inject.Inject
import scala.collection.mutable.ListBuffer

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
      case invalidProperties if invalidProperties.isEmpty() => getQuotaGetResponse(request, mailboxSession.getUser)
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
    QuotaSerializer.deserializeQuotaGetRequest(invocation.arguments.value) match {
      case JsSuccess(quotaGetRequest, _) => Right(quotaGetRequest)
      case errors: JsError => Left(new IllegalArgumentException(ResponseSerializer.serialize(errors).toString))
    }

  private def getQuotaGetResponse(quotaGetRequest: QuotaGetRequest, username: Username): SFlux[QuotaResponseGetResult] =
    quotaGetRequest.ids match {
      case None =>
        jmapQuotaManagerWrapper.list(username)
          .collectSeq()
          .map(listJmapQuota => QuotaResponseGetResult(jmapQuotaSet = listJmapQuota.toSet))
          .flatMapMany(result => SFlux.just(result))
      case Some(ids) => SFlux.fromIterable(ids.value)
        .flatMap(id => jmapQuotaManagerWrapper.get(username, id.id.value)
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
  def get(username: Username, quotaId: String): SFlux[JmapQuota] =
    SMono.just(quotaRootResolver.forUser(username))
      .filter(quotaRoot => quotaId.isEmpty || QuotaIdFactory.from(quotaRoot).value.equals(quotaId))
      .flatMapMany(getJmapQuota)

  def list(username: Username): SFlux[JmapQuota] =
    SMono.just(quotaRootResolver.forUser(username))
      .flatMapMany(getJmapQuota)

  private def getJmapQuota(quotaRoot: QuotaRoot): SFlux[JmapQuota] =
    SMono(quotaManager.getQuotasReactive(quotaRoot))
      .flatMapIterable(quotas => convertFromJava(quotas, quotaRoot))

  private def convertFromJava(quotas: QuotaManager.Quotas, quotaRoot: QuotaRoot): List[JmapQuota] = {
    val quotaId = QuotaIdFactory.from(quotaRoot)
    val messageQuota = quotas.getMessageQuota
    val jmapMessageQuotas = ListBuffer[JmapQuota]()

    messageQuota.getLimitByScope.entrySet().forEach(quota => {
      val dataTypes = List(MailDataType)
      val scope = org.apache.james.jmap.mail.Scope.fromJava(quota.getKey)
      val resourceType = CountResourceType

      jmapMessageQuotas += JmapQuota(
        id = quotaId,
        resourceType = resourceType,
        used = UnsignedInt.liftOrThrow(messageQuota.getUsed.asLong()),
        limit = UnsignedInt.liftOrThrow(quota.getValue.asLong()),
        scope = scope,
        name = QuotaName.from(scope, resourceType, dataTypes),
        dataTypes = dataTypes)
    })

    val storageQuota = quotas.getStorageQuota
    val jmapStorageQuotas = ListBuffer[JmapQuota]()
    storageQuota.getLimitByScope.entrySet().forEach(quota => {

      val dataTypes = List(MailDataType)
      val scope = org.apache.james.jmap.mail.Scope.fromJava(quota.getKey)
      val resourceType = OctetsResourceType

      jmapStorageQuotas += JmapQuota(
        id = quotaId,
        resourceType = resourceType,
        used = UnsignedInt.liftOrThrow(storageQuota.getUsed.asLong()),
        limit = UnsignedInt.liftOrThrow(quota.getValue.asLong()),
        scope = scope,
        name = QuotaName.from(scope, resourceType, dataTypes),
        dataTypes = dataTypes)
    })

    jmapMessageQuotas.addAll(jmapStorageQuotas.toList).toList
  }

}