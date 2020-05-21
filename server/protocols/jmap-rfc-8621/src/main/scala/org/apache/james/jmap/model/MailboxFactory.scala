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

package org.apache.james.jmap.model

import javax.inject.Inject
import org.apache.james.jmap.mail.MailboxName.MailboxName
import org.apache.james.jmap.mail._
import org.apache.james.jmap.utils.quotas.QuotaLoader
import org.apache.james.mailbox.model.MailboxACL.EntryKey
import org.apache.james.mailbox.model.{MailboxCounters, MailboxId, MailboxMetaData, MailboxPath, MailboxACL => JavaMailboxACL}
import org.apache.james.mailbox.{MailboxSession, Role, SubscriptionManager}
import reactor.core.scala.publisher.SMono

import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._

sealed trait MailboxConstructionOrder

class Factory

class MailboxFactory @Inject() (subscriptionManager: SubscriptionManager) {

  def create(mailboxMetaData: MailboxMetaData,
             mailboxSession: MailboxSession,
             allMailboxesMetadata: Seq[MailboxMetaData],
             quotaLoader: QuotaLoader): SMono[Mailbox] = {

    val id: MailboxId = mailboxMetaData.getId

    val name: MailboxName = MailboxName.liftOrThrow(mailboxMetaData.getPath
      .getName
      .split(mailboxSession.getPathDelimiter)
      .last)

    val role: Option[Role] = Role.from(mailboxMetaData.getPath.getName)
      .filter(_ => mailboxMetaData.getPath.belongsTo(mailboxSession)).toScala
    val sortOrder: SortOrder = role.map(SortOrder.getSortOrder).getOrElse(SortOrder.defaultSortOrder)
    val quotas: SMono[Quotas] = quotaLoader.getQuotas(mailboxMetaData.getPath)
    val rights: Rights = Rights.fromACL(MailboxACL.fromJava(mailboxMetaData.getResolvedAcls))

    val sanitizedCounters: MailboxCounters = mailboxMetaData.getCounters.sanitize()
    val unreadEmails: UnreadEmails = UnreadEmails(UnsignedInt.liftOrThrow(sanitizedCounters.getUnseen))
    val unreadThreads: UnreadThreads = UnreadThreads(UnsignedInt.liftOrThrow(sanitizedCounters.getUnseen))
    val totalEmails: TotalEmails = TotalEmails(UnsignedInt.liftOrThrow(sanitizedCounters.getCount))
    val totalThreads: TotalThreads = TotalThreads(UnsignedInt.liftOrThrow(sanitizedCounters.getCount))

    val isOwner = mailboxMetaData.getPath.belongsTo(mailboxSession)
    val aclEntryKey: EntryKey = EntryKey.createUserEntryKey(mailboxSession.getUser)

    val namespace: MailboxNamespace = if (isOwner) {
      PersonalNamespace()
    } else {
      DelegatedNamespace(mailboxMetaData.getPath.getUser)
    }

    val parentPath: Option[MailboxPath] =
      mailboxMetaData.getPath
        .getHierarchyLevels(mailboxSession.getPathDelimiter)
        .asScala
        .reverse
        .drop(1)
        .headOption

    val parentId: Option[MailboxId] = allMailboxesMetadata.filter(otherMetadata => parentPath.contains(otherMetadata.getPath))
      .map(_.getId)
      .headOption

    val myRights: MailboxRights = if (isOwner) {
      MailboxRights.FULL
    } else {
      val rights = Rfc4314Rights.fromJava(mailboxMetaData.getResolvedAcls
        .getEntries
        .getOrDefault(aclEntryKey, JavaMailboxACL.NO_RIGHTS))
        .toRights
      MailboxRights(
        mayReadItems = MayReadItems(rights.contains(Right.Read)),
        mayAddItems = MayAddItems(rights.contains(Right.Insert)),
        mayRemoveItems = MayRemoveItems(rights.contains(Right.DeleteMessages)),
        maySetSeen = MaySetSeen(rights.contains(Right.Seen)),
        maySetKeywords = MaySetKeywords(rights.contains(Right.Write)),
        mayCreateChild = MayCreateChild(false),
        mayRename = MayRename(false),
        mayDelete = MayDelete(false),
        maySubmit = MaySubmit(false))
    }

    def retrieveIsSubscribed: IsSubscribed = IsSubscribed(subscriptionManager
      .subscriptions(mailboxSession)
      .contains(mailboxMetaData.getPath.getName))

    SMono.fromPublisher(quotas)
      .map(quotas => Mailbox(
        id = id,
        name = name,
        parentId = parentId,
        role = role,
        sortOrder = sortOrder,
        unreadEmails = unreadEmails,
        totalEmails = totalEmails,
        unreadThreads = unreadThreads,
        totalThreads = totalThreads,
        myRights = myRights,
        namespace = namespace,
        rights = rights,
        quotas = quotas,
        isSubscribed = retrieveIsSubscribed))
  }
}