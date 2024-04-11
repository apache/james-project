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

package org.apache.james.jmap.core

import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.UUID

import com.google.common.hash.Hashing
import eu.timepit.refined.api.Refined
import eu.timepit.refined.refineV
import eu.timepit.refined.string.Uuid
import org.apache.james.core.Username
import org.apache.james.jmap.api.change.{EmailChanges, MailboxChanges, State => JavaState}
import org.apache.james.jmap.api.model.State
import org.apache.james.jmap.core.CapabilityIdentifier.CapabilityIdentifier
import org.apache.james.jmap.core.Id.Id
import org.apache.james.jmap.core.UuidState.INSTANCE

import scala.util.Try

case class IsPersonal(value: Boolean) extends AnyVal
case class IsReadOnly(value: Boolean) extends AnyVal

object AccountId {
  def from(username: Username): Either[IllegalArgumentException, AccountId] = {
    val sha256String = Hashing.sha256()
      .hashString(username.asString(), StandardCharsets.UTF_8)
      .toString
    val refinedId: Either[String, Id] = refineV(sha256String)

    refinedId match {
      case Left(errorMessage: String) => Left(new IllegalArgumentException(errorMessage))
      case Right(id) => Right(AccountId(id))
    }
  }
}

final case class AccountId(id: Id)

object Account {
  private[jmap] val NAME = "name";
  private[jmap] val IS_PERSONAL = "isPersonal"
  private[jmap] val IS_READ_ONLY = "isReadOnly"
  private[jmap] val ACCOUNT_CAPABILITIES = "accountCapabilities"

  def from(name: Username,
           isPersonal: IsPersonal,
           isReadOnly: IsReadOnly,
           accountCapabilities: Set[_ <: Capability]): Either[IllegalArgumentException, Account] =
    AccountId.from(name)
      .map(Account(_, name, isPersonal, isReadOnly, accountCapabilities))

  def unapplyIgnoreAccountId(account: Account): Some[(Username, IsPersonal, IsReadOnly, Set[_ <: Capability])] =
    Some(account.name, account.isPersonal, account.isReadOnly, account.accountCapabilities)
}

final case class Account private(accountId: AccountId,
                                 name: Username,
                                 isPersonal: IsPersonal,
                                 isReadOnly: IsReadOnly,
                                 accountCapabilities: Set[_ <: Capability])

object UuidState {
  type UUIDString = String Refined Uuid

  val INSTANCE: UuidState = fromJava(JavaState.INITIAL)

  def fromStringUnchecked(value: String): UuidState =
    refineV[Uuid](value)
      .fold(
        failure => throw new IllegalArgumentException(failure),
        success => UuidState.fromString(success))

  def fromString(value: UUIDString): UuidState = UuidState(UUID.fromString(value.value))

  def fromMailboxChanges(mailboxChanges: MailboxChanges): UuidState = fromJava(mailboxChanges.getNewState)

  def fromEmailChanges(emailChanges: EmailChanges): UuidState = fromJava(emailChanges.getNewState)

  def fromJava(javaState: JavaState): UuidState = UuidState(javaState.getValue)

  def fromGenerateUuid(): UuidState = UuidState(UUID.randomUUID())

  def parse(string: String): Either[IllegalArgumentException, UuidState] = Try(UUID.fromString(string))
    .toEither
    .map(UuidState(_))
    .left.map(new IllegalArgumentException(_))
}

case class UuidState(value: UUID) extends State {
  override def serialize: String = value.toString
}

case class URL(value: String) extends AnyVal

final case class Session(capabilities: Capabilities,
                         accounts: List[Account],
                         primaryAccounts: Map[CapabilityIdentifier, AccountId],
                         username: Username,
                         apiUrl: URL,
                         downloadUrl: URL,
                         uploadUrl: URL,
                         eventSourceUrl: URL,
                         state: UuidState = INSTANCE)
