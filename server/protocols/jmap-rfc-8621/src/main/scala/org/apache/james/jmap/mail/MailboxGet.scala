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

import eu.timepit.refined
import eu.timepit.refined.api.Refined
import org.apache.james.jmap.api.change.{EmailChanges, Limit, MailboxChanges}
import org.apache.james.jmap.core.Id.IdConstraint
import org.apache.james.jmap.core.{AccountId, Properties, State}
import org.apache.james.jmap.mail.MailboxGet.UnparsedMailboxId
import org.apache.james.jmap.method.WithAccountId
import org.apache.james.mailbox.model.MailboxId

import scala.util.{Failure, Try}

object MailboxGet {
  type UnparsedMailboxId = String Refined IdConstraint

  def asUnparsed(mailboxId: MailboxId): UnparsedMailboxId = refined.refineV[IdConstraint](mailboxId.serialize()) match {
    case Left(e) => throw new IllegalArgumentException(e)
    case scala.Right(value) => value
  }

  def parse(mailboxIdFactory: MailboxId.Factory)(unparsed: UnparsedMailboxId): Try[MailboxId] =
    parseString(mailboxIdFactory)(unparsed.value)

  def parseString(mailboxIdFactory: MailboxId.Factory)(unparsed: String): Try[MailboxId] =
    unparsed match {
      case a if a.startsWith("#") =>
        Failure(new IllegalArgumentException(s"$unparsed was not used in previously defined creationIds"))
      case _ => Try(mailboxIdFactory.fromString(unparsed))
    }
}

case class Ids(value: List[UnparsedMailboxId])

case class MailboxGetRequest(accountId: AccountId,
                             ids: Option[Ids],
                             properties: Option[Properties]) extends WithAccountId

case class NotFound(value: Set[UnparsedMailboxId]) {
  def merge(other: NotFound): NotFound = NotFound(this.value ++ other.value)
}

case class MailboxGetResponse(accountId: AccountId,
                              state: State,
                              list: List[Mailbox],
                              notFound: NotFound)

object HasMoreChanges {
  def fromMailboxChanges(mailboxChanges: MailboxChanges): HasMoreChanges = HasMoreChanges(mailboxChanges.hasMoreChanges)

  def fromEmailChanges(emailChanges: EmailChanges): HasMoreChanges = HasMoreChanges(emailChanges.hasMoreChanges)
}

case class HasMoreChanges(value: Boolean) extends AnyVal

case class MailboxChangesRequest(accountId: AccountId,
                                 sinceState: State,
                                 maxChanges: Option[Limit]) extends WithAccountId

case class MailboxChangesResponse(accountId: AccountId,
                                  oldState: State,
                                  newState: State,
                                  hasMoreChanges: HasMoreChanges,
                                  updatedProperties: Option[Properties],
                                  created: Set[MailboxId],
                                  updated: Set[MailboxId],
                                  destroyed: Set[MailboxId])
