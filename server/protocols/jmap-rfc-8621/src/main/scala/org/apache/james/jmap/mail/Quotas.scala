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

import java.nio.charset.StandardCharsets
import java.util.UUID

import com.google.common.hash.Hashing
import eu.timepit.refined.auto._
import org.apache.james.core.Domain
import org.apache.james.core.quota.{QuotaCountLimit, QuotaCountUsage, QuotaSizeLimit, QuotaSizeUsage}
import org.apache.james.jmap.core.Id.Id
import org.apache.james.jmap.core.Limit.Limit
import org.apache.james.jmap.core.Position.Position
import org.apache.james.jmap.core.UnsignedInt.UnsignedInt
import org.apache.james.jmap.core.{AccountId, CanCalculateChanges, Id, Properties, QueryState, UnsignedInt, UuidState}
import org.apache.james.jmap.method.WithAccountId
import org.apache.james.mailbox.model.{Quota => ModelQuota, QuotaRoot => ModelQuotaRoot}

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
  private val WARN_LIMIT_PERCENTAGE = 0.9
  private val allRfc9245Properties: Properties = Properties("id", "resourceType", "used", "hardLimit", "scope", "name", "types", "warnLimit", "softLimit", "description")
  private val allRfc9245PropertiesWithDraftProperties: Properties = allRfc9245Properties ++ Properties("dataTypes", "limit")
  private val idProperty: Properties = Properties("id")

  def propertiesFiltered(requestedProperties: Properties): Properties = idProperty ++ requestedProperties

  def allProperties(draftBackwardCompatibility: Boolean): Properties =
    if (draftBackwardCompatibility) {
      allRfc9245PropertiesWithDraftProperties
    } else {
      allRfc9245Properties
    }

  def extractUserMessageCountQuota(quota: ModelQuota[QuotaCountLimit, QuotaCountUsage], countQuotaIdPlaceHolder: Id, quotaRoot: ModelQuotaRoot): Option[JmapQuota] =
    Option(quota.getLimit)
      .filter(_.isLimited)
      .map(limit => JmapQuota(
        id = countQuotaIdPlaceHolder,
        resourceType = CountResourceType,
        used = UnsignedInt.liftOrThrow(quota.getUsed.asLong()),
        hardLimit = UnsignedInt.liftOrThrow(limit.asLong()),
        limit = Some(UnsignedInt.liftOrThrow(limit.asLong())),
        scope = AccountScope,
        name = QuotaName.from(quotaRoot, AccountScope, CountResourceType, List(MailDataType)),
        types = List(MailDataType),
        dataTypes = Some(List(MailDataType)),
        warnLimit = Some(UnsignedInt.liftOrThrow((limit.asLong() * WARN_LIMIT_PERCENTAGE).toLong))))

  def extractUserMessageSizeQuota(quota: ModelQuota[QuotaSizeLimit, QuotaSizeUsage], sizeQuotaIdPlaceHolder: Id, quotaRoot: ModelQuotaRoot): Option[JmapQuota] =
    Option(quota.getLimit)
      .filter(_.isLimited)
      .map(limit => JmapQuota(
        id = sizeQuotaIdPlaceHolder,
        resourceType = OctetsResourceType,
        used = UnsignedInt.liftOrThrow(quota.getUsed.asLong()),
        hardLimit = UnsignedInt.liftOrThrow(limit.asLong()),
        limit = Some(UnsignedInt.liftOrThrow(limit.asLong())),
        scope = AccountScope,
        name = QuotaName.from(quotaRoot, AccountScope, OctetsResourceType, List(MailDataType)),
        types = List(MailDataType),
        dataTypes = Some(List(MailDataType)),
        warnLimit = Some(UnsignedInt.liftOrThrow((limit.asLong() * WARN_LIMIT_PERCENTAGE).toLong))))

  def correspondingState(quotas: Seq[JmapQuota]): UuidState =
    UuidState(UUID.nameUUIDFromBytes(s"${quotas.sortBy(_.name.string).map(_.name.string).mkString("_")}:${quotas.map(_.used.value).sum + quotas.map(_.hardLimit.value).sum}"
      .getBytes(StandardCharsets.UTF_8)))
}

case class JmapQuota(id: Id,
                     resourceType: ResourceType,
                     used: UnsignedInt,
                     hardLimit: UnsignedInt,
                     limit: Option[UnsignedInt] = None,
                     scope: Scope,
                     name: QuotaName,
                     types: List[DataType],
                     dataTypes: Option[List[DataType]] = None,
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
  def from(quotas: Seq[JmapQuota], requestIds: Option[Set[Id]]): QuotaResponseGetResult =
    requestIds match {
      case None => QuotaResponseGetResult(quotas.toSet, state = JmapQuota.correspondingState(quotas))
      case Some(value) => QuotaResponseGetResult(
        jmapQuotaSet = quotas.filter(quota => value.contains(quota.id)).toSet,
        notFound = QuotaNotFound(value.diff(quotas.map(_.id).toSet).map(UnparsedQuotaId)),
        state = JmapQuota.correspondingState(quotas))
    }
}

case class QuotaResponseGetResult(jmapQuotaSet: Set[JmapQuota] = Set(),
                                  notFound: QuotaNotFound = QuotaNotFound(Set()),
                                  state: UuidState) {
  def asResponse(accountId: AccountId): QuotaGetResponse =
    QuotaGetResponse(accountId = accountId,
      state = state,
      list = jmapQuotaSet.toList,
      notFound = notFound)
}

case class QuotaChangesRequest(accountId: AccountId,
                               sinceState: UuidState,
                               maxChanges: Option[org.apache.james.jmap.core.Limit.Limit]) extends WithAccountId

object QuotaChangesResponse {
  def from(oldState: UuidState, newState: (UuidState, Seq[Id]), accountId: AccountId): QuotaChangesResponse =
    QuotaChangesResponse(
      accountId = accountId,
      oldState = oldState,
      newState = newState._1,
      hasMoreChanges = HasMoreChanges(false),
      updated = if (oldState.value.equals(newState._1.value)) {
        Set()
      } else {
        newState._2.toSet
      })
}

case class QuotaChangesResponse(accountId: AccountId,
                                oldState: UuidState,
                                newState: UuidState,
                                hasMoreChanges: HasMoreChanges,
                                updatedProperties: Option[Properties] = None,
                                created: Set[Id] = Set(),
                                updated: Set[Id] = Set(),
                                destroyed: Set[Id] = Set())

case class QuotaQueryRequest(accountId: AccountId, filter: QuotaQueryFilter) extends WithAccountId

object QuotaQueryFilter {
  val SUPPORTED: Set[String] = Set("scope", "name", "resourceType", "type")
}

case class QuotaQueryFilter(scope: Option[Scope],
                            name: Option[QuotaName],
                            resourceType: Option[ResourceType],
                            `type`: Option[DataType])

case class QuotaQueryResponse(accountId: AccountId,
                              queryState: QueryState,
                              canCalculateChanges: CanCalculateChanges,
                              ids: Seq[Id],
                              position: Position,
                              limit: Option[Limit])
