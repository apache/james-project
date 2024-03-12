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

package org.apache.james.jmap.mail

import java.util.UUID

import eu.timepit.refined.auto._
import org.apache.james.jmap.api.model.{Identity, IdentityId}
import org.apache.james.jmap.core.Id.Id
import org.apache.james.jmap.core.UuidState.INSTANCE
import org.apache.james.jmap.core.{AccountId, Id, Properties, UuidState}
import org.apache.james.jmap.method.{GetRequest, WithAccountId}

import scala.util.Try

object IdentityGet {
  val allProperties: Properties = Properties("id", "name", "email", "replyTo", "bcc", "textSignature", "htmlSignature",
    "mayDelete", "sortOrder")
  val idProperty: Properties = Properties("id")
}

case class UnparsedIdentityId(id: Id) {
  def validate: Either[IllegalArgumentException, IdentityId] = Try(UUID.fromString(id.value))
    .toEither
    .map(IdentityId(_))
    .left.map {
    case e: IllegalArgumentException => e
    case e => new IllegalArgumentException(e)
  }
}
case class IdentityIds(ids: List[UnparsedIdentityId]) {
  def contains(identityId: IdentityId): Boolean = ids.contains(identityId)
  def nonEmpty: Boolean = ids.nonEmpty
  def validIds: List[IdentityId] = ids.flatMap(_.validate.toOption)
  def distinct(other: List[IdentityId]): IdentityIds = {
    val found = other.map(id => UnparsedIdentityId(Id.validate(id.id.toString).toOption.get))
    IdentityIds(ids.filter(id => !found.contains(id)))
  }
}

case class IdentityGetRequest(accountId: AccountId,
                              ids: Option[IdentityIds],
                              properties: Option[Properties]) extends WithAccountId with GetRequest {
  def computeResponse(identities: List[Identity]): IdentityGetResponse = {
    val list: List[Identity] = identities.filter(identity => isRequested(identity.id))
    val notFound: Option[IdentityIds] = ids
      .map(ids => ids.distinct(list.map(_.id)))
      .filter(_.nonEmpty)

    IdentityGetResponse(
      accountId = accountId,
      state = INSTANCE,
      list = list,
      notFound = notFound)
  }

  private def isRequested(id: IdentityId): Boolean = ids.forall(_.validIds.contains(id))

  override def idCount: Option[Int] = ids.map(_.ids).map(_.size)
}

case class IdentityGetResponse(accountId: AccountId,
                               state: UuidState,
                               list: List[Identity],
                               notFound: Option[IdentityIds])
