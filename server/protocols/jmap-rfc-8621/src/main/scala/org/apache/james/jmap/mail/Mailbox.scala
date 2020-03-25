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
 * ***************************************************************/

package org.apache.james.jmap.mail

import eu.timepit.refined.auto._
import org.apache.james.core.Username
import org.apache.james.jmap.model.UnsignedInt.UnsignedInt
import org.apache.james.mailbox.Role
import org.apache.james.mailbox.model.MailboxId

final case class MailboxName(name: String) {
  require(!name.isEmpty, "'name' is mandatory")
}

case class MayReadItems(value: Boolean) extends AnyVal
case class MayAddItems(value: Boolean) extends AnyVal
case class MayRemoveItems(value: Boolean) extends AnyVal
case class MaySetSeen(value: Boolean) extends AnyVal
case class MaySetKeywords(value: Boolean) extends AnyVal
case class MayCreateChild(value: Boolean) extends AnyVal
case class MayRename(value: Boolean) extends AnyVal
case class MayDelete(value: Boolean) extends AnyVal
case class MaySubmit(value: Boolean) extends AnyVal

case class MailboxRights(mayReadItems: MayReadItems,
                               mayAddItems: MayAddItems,
                               mayRemoveItems: MayRemoveItems,
                               maySetSeen: MaySetSeen,
                               maySetKeywords: MaySetKeywords,
                               mayCreateChild: MayCreateChild,
                               mayRename: MayRename,
                               mayDelete: MayDelete,
                               maySubmit: MaySubmit)

object MailboxNamespace {
  def delegated(owner: Username) = DelegatedNamespace(owner)

  def personal = PersonalNamespace
}

sealed trait MailboxNamespace {
  def owner: Option[Username]
}

case object PersonalNamespace extends MailboxNamespace {
  override def owner: Option[Username] = None
}

case class DelegatedNamespace(user: Username) extends MailboxNamespace {
  override val owner: Option[Username] = Some(user)
}

object SortOrder {
  private val inboxOrder : UnsignedInt = 10L
  private val archiveOrder : UnsignedInt = 20L
  private val draftOrder : UnsignedInt = 30L
  private val outboxOrder : UnsignedInt = 40L
  private val sentOrder : UnsignedInt = 50L
  private val trashOrder : UnsignedInt = 60L
  private val spamOrder : UnsignedInt = 70L
  private val templatesOrder : UnsignedInt = 80L
  private val restoredMessageOrder : UnsignedInt = 90L
  private val defaultOrder : UnsignedInt = 1000L
  private val defaultSortOrders = Map(
      Role.INBOX -> SortOrder(inboxOrder),
      Role.ARCHIVE -> SortOrder(archiveOrder),
      Role.DRAFTS -> SortOrder(draftOrder),
      Role.OUTBOX -> SortOrder(outboxOrder),
      Role.SENT -> SortOrder(sentOrder),
      Role.TRASH -> SortOrder(trashOrder),
      Role.SPAM -> SortOrder(spamOrder),
      Role.TEMPLATES -> SortOrder(templatesOrder),
      Role.RESTORED_MESSAGES -> SortOrder(restoredMessageOrder))
    .withDefaultValue( SortOrder(defaultOrder))

  def getSortOrder(role: Role): SortOrder = defaultSortOrders(role)
}

sealed case class SortOrder(sortOrder: UnsignedInt) extends Ordered[SortOrder] {
  override def compare(that: SortOrder): Int = this.sortOrder.value.compare(that.sortOrder.value)
}

case class TotalEmails(value: UnsignedInt)
case class UnreadEmails(value: UnsignedInt)
case class TotalThreads(value: UnsignedInt)
case class UnreadThreads(value: UnsignedInt)
case class IsSubscribed(value: Boolean) extends AnyVal

sealed trait MailboxExtension

sealed trait RightsExtension extends MailboxExtension {
  def rights: Rights
  def namespace: MailboxNamespace
}

sealed trait QuotasExtension extends MailboxExtension {
  def quotas: Quotas
}

case class Mailbox(id: MailboxId,
                   name: MailboxName,
                   parentId: Option[MailboxId],
                   role: Option[Role],
                   sortOrder: SortOrder,
                   totalEmails: TotalEmails,
                   unreadEmails: UnreadEmails,
                   totalThreads: TotalThreads,
                   unreadThreads: UnreadThreads,
                   myRights: MailboxRights,
                   isSubscribed: IsSubscribed,
                   namespace: MailboxNamespace,
                   rights: Rights,
                   quotas: Quotas) extends RightsExtension with QuotasExtension {
  def hasRole(role: Role): Boolean = this.role.contains(role)

  val hasSystemRole: Boolean = role.exists(_.isSystemRole)
}