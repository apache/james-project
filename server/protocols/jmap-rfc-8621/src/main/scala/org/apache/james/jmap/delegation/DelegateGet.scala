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

package org.apache.james.jmap.delegation

import java.util.UUID

import eu.timepit.refined.auto._
import org.apache.james.core.Username
import org.apache.james.jmap.core.Id.Id
import org.apache.james.jmap.core.{AccountId, Properties}
import org.apache.james.jmap.method.WithAccountId

import scala.util.Try

import java.util.UUID
import scala.util.Try

object DelegateGet {
  val allProperties: Properties = Properties("id", "username")
  val idProperty: Properties = Properties("id")
  def propertiesFiltered(requestedProperties: Properties): Properties = idProperty ++ requestedProperties
}

case class UnparsedDelegateId(id: Id) {
  def parse: Either[IllegalArgumentException, DelegationId] =
    Try(UUID.fromString(id.value))
      .map(DelegationId(_))
      .toEither
      .left.map({
      case e: IllegalArgumentException => e
      case e => new IllegalArgumentException(e)
    })
}

case class DelegateIds(value: List[UnparsedDelegateId])

case class DelegateGetRequest(accountId: AccountId,
                              ids: Option[DelegateIds],
                              properties: Option[Properties]) extends WithAccountId

object Delegate {
  def from(username: Username): Delegate =
    AccountId.from(username)
      .map(accountId => Delegate(accountId, username)) match {
      case Right(value) => value
      case Left(error) => throw error
    }

}

case class Delegate(id: AccountId,
                    username: Username)

case class DelegateNotFound(value: Set[UnparsedDelegateId])

object DelegateGetResult {
  def from(delegates: Seq[Delegate], requestIds: Option[Set[Id]]): DelegateGetResult =
    requestIds match {
      case None => DelegateGetResult(delegates)
      case Some(value) => DelegateGetResult(
        list = delegates.filter(delegate => value.contains(delegate.id.id)),
        notFound = DelegateNotFound(value.diff(delegates.map(_.id.id).toSet).map(UnparsedDelegateId)))
    }
}

case class DelegateGetResult(list: Seq[Delegate],
                             notFound: DelegateNotFound = DelegateNotFound(Set())) {
  def asResponse(accountId: AccountId): DelegateGetResponse =
    DelegateGetResponse(accountId, list, notFound)

}

case class DelegateGetResponse(accountId: AccountId,
                               list: Seq[Delegate],
                               notFound: DelegateNotFound = DelegateNotFound(Set()))
