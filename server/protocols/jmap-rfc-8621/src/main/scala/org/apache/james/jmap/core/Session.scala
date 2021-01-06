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
import org.apache.james.jmap.core.CapabilityIdentifier.CapabilityIdentifier
import org.apache.james.jmap.core.Id.Id
import org.apache.james.jmap.core.State.INSTANCE

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

object State {
  type UUIDString = String Refined Uuid

  val INSTANCE: State = fromJava(JavaState.INITIAL)

  def fromString(value: UUIDString): State = State(UUID.fromString(value.value))

  def fromMailboxChanges(mailboxChanges: MailboxChanges): State = fromJava(mailboxChanges.getNewState)

  def fromEmailChanges(emailChanges: EmailChanges): State = fromJava(emailChanges.getNewState)

  def fromJava(javaState: JavaState): State = State(javaState.getValue)
}

case class State(value: UUID)

final case class Session(capabilities: Capabilities,
                         accounts: List[Account],
                         primaryAccounts: Map[CapabilityIdentifier, AccountId],
                         username: Username,
                         apiUrl: URL,
                         downloadUrl: URL,
                         uploadUrl: URL,
                         eventSourceUrl: URL,
                         state: State = INSTANCE)
