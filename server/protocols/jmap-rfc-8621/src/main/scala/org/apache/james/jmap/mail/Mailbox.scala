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

import eu.timepit.refined
import eu.timepit.refined.api.Refined
import eu.timepit.refined.auto._
import eu.timepit.refined.collection.NonEmpty
import org.apache.james.jmap.core.CapabilityIdentifier.CapabilityIdentifier
import org.apache.james.jmap.core.UnsignedInt.UnsignedInt
import org.apache.james.jmap.core.{CapabilityIdentifier, Properties}
import org.apache.james.jmap.mail.MailboxName.MailboxName
import org.apache.james.mailbox.Role
import org.apache.james.mailbox.exception.MailboxNameException
import org.apache.james.mailbox.model.MailboxId

case class MayReadItems(value: Boolean) extends AnyVal
case class MayAddItems(value: Boolean) extends AnyVal
case class MayRemoveItems(value: Boolean) extends AnyVal
case class MaySetSeen(value: Boolean) extends AnyVal
case class MaySetKeywords(value: Boolean) extends AnyVal
case class MayCreateChild(value: Boolean) extends AnyVal
case class MayRename(value: Boolean) extends AnyVal
case class MayDelete(value: Boolean) extends AnyVal
case class MaySubmit(value: Boolean) extends AnyVal

object MailboxRights {
  val FULL: MailboxRights = MailboxRights(
    mayReadItems = MayReadItems(true),
    mayAddItems = MayAddItems(true),
    mayRemoveItems = MayRemoveItems(true),
    maySetSeen = MaySetSeen(true),
    maySetKeywords = MaySetKeywords(true),
    mayCreateChild = MayCreateChild(true),
    mayRename = MayRename(true),
    mayDelete = MayDelete(true),
    maySubmit = MaySubmit(true),
  )
}

case class MailboxRights(mayReadItems: MayReadItems,
                         mayAddItems: MayAddItems,
                         mayRemoveItems: MayRemoveItems,
                         maySetSeen: MaySetSeen,
                         maySetKeywords: MaySetKeywords,
                         mayCreateChild: MayCreateChild,
                         mayRename: MayRename,
                         mayDelete: MayDelete,
                         maySubmit: MaySubmit)

object SortOrder {
  val defaultSortOrder: SortOrder = SortOrder(1000L)

  private val defaultSortOrders = Map(
      Role.INBOX -> SortOrder(10L),
      Role.ARCHIVE -> SortOrder(20L),
      Role.DRAFTS -> SortOrder(30L),
      Role.OUTBOX -> SortOrder(40L),
      Role.SENT -> SortOrder(50L),
      Role.TRASH -> SortOrder(60L),
      Role.SPAM -> SortOrder(70L),
      Role.TEMPLATES -> SortOrder(80L),
      Role.RESTORED_MESSAGES -> SortOrder(90L))
    .withDefaultValue(defaultSortOrder)

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

sealed trait MailboxExtensionAdditionalFields

sealed trait RightsExtension extends MailboxExtensionAdditionalFields {
  def rights: Rights
  def namespace: MailboxNamespace
}

sealed trait QuotasExtension extends MailboxExtensionAdditionalFields {
  def quotas: Quotas
}

object MailboxName {
  type MailboxNameConstraint = NonEmpty
  type MailboxName = String Refined MailboxNameConstraint

  def validate(value: String): Either[MailboxNameException, MailboxName] =
    refined.refineV[MailboxNameConstraint](value) match {
      case Left(error) => Left(new MailboxNameException(error))
      case scala.Right(value) => scala.Right(value)
    }
}

object SortOrderProvider {
  val DEFAULT: SortOrderProvider = role => SortOrder.getSortOrder(role)
}

trait SortOrderProvider {
  def retrieveSortOrder(role: Role): SortOrder
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

object Mailbox {
  val allProperties: Properties = Properties("id", "name", "parentId", "role", "sortOrder", "totalEmails", "unreadEmails",
    "totalThreads", "unreadThreads", "myRights", "isSubscribed", "namespace", "rights", "quotas")
  val idProperty: Properties = Properties("id")
  val propertiesForCapabilities: Map[CapabilityIdentifier, Properties] = Map(
    CapabilityIdentifier.JAMES_QUOTA -> Properties("quotas"),
    CapabilityIdentifier.JAMES_SHARES -> Properties("namespace", "rights"))

  def propertiesFiltered(requestedProperties: Properties, allowedCapabilities : Set[CapabilityIdentifier]) : Properties = {
    val propertiesToHide: Properties = Properties(propertiesForCapabilities.filterNot(entry => allowedCapabilities.contains(entry._1))
      .values
      .flatMap(p => p.value)
      .toSet)

    (idProperty ++ requestedProperties) -- propertiesToHide
  }
}
