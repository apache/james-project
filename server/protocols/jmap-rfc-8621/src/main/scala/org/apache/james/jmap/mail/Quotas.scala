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

package org.apache.james.jmap.mail

import com.google.common.hash.Hashing
import eu.timepit.refined.auto._
import org.apache.james.core.Domain
import org.apache.james.core.quota.{QuotaCountLimit, QuotaCountUsage, QuotaSizeLimit, QuotaSizeUsage}
import org.apache.james.jmap.core.Id.Id
import org.apache.james.jmap.core.UnsignedInt.UnsignedInt
import org.apache.james.jmap.core.UuidState.INSTANCE
import org.apache.james.jmap.core.{AccountId, Id, Properties, UnsignedInt, UuidState}
import org.apache.james.jmap.method.WithAccountId
import org.apache.james.mailbox.model.{Quota => ModelQuota, QuotaRoot => ModelQuotaRoot}

import java.nio.charset.StandardCharsets
import scala.compat.java8.OptionConverters._

object QuotaRoot {
  def toJmap(quotaRoot: ModelQuotaRoot) = QuotaRoot(quotaRoot.getValue, quotaRoot.getDomain.asScala)
}

case class QuotaRoot(value: String, domain: Option[Domain]) {
  def toModel: ModelQuotaRoot = ModelQuotaRoot.quotaRoot(value, domain.asJava)
}

object Quotas {
  sealed trait Type

  case object Storage extends Type

  case object Message extends Type

  def from(quotas: Map[QuotaId, Quota]) = new Quotas(quotas)

  def from(quotaId: QuotaId, quota: Quota) = new Quotas(Map(quotaId -> quota))
}

object QuotaId {
  def fromQuotaRoot(quotaRoot: QuotaRoot) = QuotaId(quotaRoot)
}

case class QuotaId(quotaRoot: QuotaRoot) extends AnyVal {
  def getName: String = quotaRoot.value
}

object Quota {
  def from(quota: Map[Quotas.Type, Value]) = new Quota(quota)
}

case class Quota(quota: Map[Quotas.Type, Value]) extends AnyVal

case class Value(used: UnsignedInt, max: Option[UnsignedInt])

case class Quotas(quotas: Map[QuotaId, Quota]) extends AnyVal

case class UnparsedQuotaId(id: Id)

case class QuotaIds(value: List[UnparsedQuotaId])

case class QuotaGetRequest(accountId: AccountId,
                           ids: Option[QuotaIds],
                           properties: Option[Properties]) extends WithAccountId

object JmapQuota {
  val WARN_LIMIT_PERCENTAGE = 0.9
  val allProperties: Properties = Properties("id", "resourceType", "used", "limit", "scope", "name", "dataTypes", "warnLimit", "softLimit", "description")
  val idProperty: Properties = Properties("id")

  def propertiesFiltered(requestedProperties: Properties): Properties = idProperty ++ requestedProperties

  def extractUserMessageCountQuota(quota: ModelQuota[QuotaCountLimit, QuotaCountUsage], countQuotaIdPlaceHolder: Id, quotaRoot: ModelQuotaRoot): Option[JmapQuota] =
    Option(quota.getLimitByScope.get(ModelQuota.Scope.User))
      .map(limit => JmapQuota(
        id = countQuotaIdPlaceHolder,
        resourceType = CountResourceType,
        used = UnsignedInt.liftOrThrow(quota.getUsed.asLong()),
        limit = UnsignedInt.liftOrThrow(limit.asLong()),
        scope = AccountScope,
        name = QuotaName.from(quotaRoot, AccountScope, CountResourceType, List(MailDataType)),
        dataTypes = List(MailDataType),
        warnLimit = Some(UnsignedInt.liftOrThrow((limit.asLong() * WARN_LIMIT_PERCENTAGE).toLong))))

  def extractUserMessageSizeQuota(quota: ModelQuota[QuotaSizeLimit, QuotaSizeUsage], sizeQuotaIdPlaceHolder: Id, quotaRoot: ModelQuotaRoot): Option[JmapQuota] =
    Option(quota.getLimitByScope.get(ModelQuota.Scope.User))
      .map(limit => JmapQuota(
        id = sizeQuotaIdPlaceHolder,
        resourceType = OctetsResourceType,
        used = UnsignedInt.liftOrThrow(quota.getUsed.asLong()),
        limit = UnsignedInt.liftOrThrow(limit.asLong()),
        scope = AccountScope,
        name = QuotaName.from(quotaRoot, AccountScope, OctetsResourceType, List(MailDataType)),
        dataTypes = List(MailDataType),
        warnLimit = Some(UnsignedInt.liftOrThrow((limit.asLong() * WARN_LIMIT_PERCENTAGE).toLong))))
}

case class JmapQuota(id: Id,
                     resourceType: ResourceType,
                     used: UnsignedInt,
                     limit: UnsignedInt,
                     scope: Scope,
                     name: QuotaName,
                     dataTypes: List[DataType],
                     warnLimit: Option[UnsignedInt] = None,
                     softLimit: Option[UnsignedInt] = None,
                     description: Option[QuotaDescription] = None)

object QuotaName {
  def from(quotaRoot: ModelQuotaRoot, scope: Scope, resourceType: ResourceType, dataTypes: List[DataType]): QuotaName =
    QuotaName(s"${quotaRoot.asString()}:${scope.asString()}:${resourceType.asString()}:${dataTypes.map(_.asString()).mkString("_")}")
}

case class QuotaName(string: String)

case class QuotaDescription(string: String)

sealed trait ResourceType {
  def asString(): String
}

case object CountResourceType extends ResourceType {
  override def asString(): String = "count"
}

case object OctetsResourceType extends ResourceType {
  override def asString(): String = "octets"
}

trait Scope {
  def asString(): String
}

case object AccountScope extends Scope {
  override def asString(): String = "account"
}

trait DataType {
  def asString(): String
}

case object MailDataType extends DataType {
  override def asString(): String = "Mail"
}

case class QuotaGetResponse(accountId: AccountId,
                            state: UuidState,
                            list: List[JmapQuota],
                            notFound: QuotaNotFound)

case class QuotaNotFound(value: Set[UnparsedQuotaId]) {
  def merge(other: QuotaNotFound): QuotaNotFound = QuotaNotFound(this.value ++ other.value)
}

object QuotaIdFactory {
  def from(quotaRoot: ModelQuotaRoot, resourceType: ResourceType): Id =
    Id.validate(Hashing.sha256.hashBytes((quotaRoot.asString() + resourceType.asString()).getBytes(StandardCharsets.UTF_8)).toString).toOption.get
}

object QuotaResponseGetResult {
  def empty: QuotaResponseGetResult = QuotaResponseGetResult()

  def merge(result1: QuotaResponseGetResult, result2: QuotaResponseGetResult): QuotaResponseGetResult = result1.merge(result2)
}

case class QuotaResponseGetResult(jmapQuotaSet: Set[JmapQuota] = Set(), notFound: QuotaNotFound = QuotaNotFound(Set())) {
  def merge(other: QuotaResponseGetResult): QuotaResponseGetResult =
    QuotaResponseGetResult(this.jmapQuotaSet ++ other.jmapQuotaSet,
      this.notFound.merge(other.notFound))

  def asResponse(accountId: AccountId): QuotaGetResponse =
    QuotaGetResponse(accountId = accountId,
      state = INSTANCE,
      list = jmapQuotaSet.toList,
      notFound = notFound)
}