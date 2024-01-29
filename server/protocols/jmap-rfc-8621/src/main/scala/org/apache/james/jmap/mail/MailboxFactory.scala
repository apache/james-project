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

import javax.inject.Inject
import org.apache.james.jmap.core.UnsignedInt
import org.apache.james.jmap.mail.MailboxName.MailboxName
import org.apache.james.jmap.utils.quotas.QuotaLoader
import org.apache.james.mailbox._
import org.apache.james.mailbox.exception.{MailboxNameException, MailboxNotFoundException}
import org.apache.james.mailbox.model.MailboxACL.EntryKey
import org.apache.james.mailbox.model.{MailboxCounters, MailboxId, MailboxMetaData, MailboxPath, MailboxACL => JavaMailboxACL}
import reactor.core.scala.publisher.SMono

import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._

object MailboxValidation {
  private def retrieveMailboxName(mailboxPath: MailboxPath, pathDelimiter: Char): Either[MailboxNameException, MailboxName] =
    mailboxPath.getName
      .split(pathDelimiter)
      .lastOption match {
        case Some(name) => MailboxName.validate(name)
        case None => Left(new MailboxNameException("No name for the mailbox found"))
      }

  def validate(mailboxPath: MailboxPath,
               pathDelimiter: Char,
               unreadEmails: Long,
               unreadThreads: Long,
               totalEmails: Long,
               totalThreads: Long): Either[Exception, MailboxValidation] = {
    for {
      validatedName <- retrieveMailboxName(mailboxPath, pathDelimiter)
      validatedUnreadEmails <- UnsignedInt.validate(unreadEmails).map(UnreadEmails)
      validatedUnreadThreads <- UnsignedInt.validate(unreadThreads).map(UnreadThreads)
      validatedTotalEmails <- UnsignedInt.validate(totalEmails).map(TotalEmails)
      validatedTotalThreads <- UnsignedInt.validate(totalThreads).map(TotalThreads)
    } yield MailboxValidation(
      mailboxName = validatedName,
      unreadEmails = validatedUnreadEmails,
      unreadThreads = validatedUnreadThreads,
      totalEmails = validatedTotalEmails,
      totalThreads = validatedTotalThreads)
  }
}

case class MailboxValidation(mailboxName: MailboxName,
                             unreadEmails: UnreadEmails,
                             unreadThreads: UnreadThreads,
                             totalEmails: TotalEmails,
                             totalThreads: TotalThreads)

case class Subscriptions(subscribedNames: Set[MailboxPath]) {
  def isSubscribed(name: MailboxPath): IsSubscribed = IsSubscribed(subscribedNames.contains(name))

  def isSubscribed(metaData: MailboxMetaData): IsSubscribed = isSubscribed(metaData.getPath)
}

class MailboxFactory @Inject() (mailboxManager: MailboxManager,
                                namespaceFactory: NamespaceFactory,
                                sortOrderProvider: SortOrderProvider) {

  private def getRole(mailboxPath: MailboxPath, mailboxSession: MailboxSession): Option[Role] = Role.from(mailboxPath.getName)
    .filter(_ => mailboxPath.belongsTo(mailboxSession)).toScala

  private def getSortOrder(role: Option[Role]): SortOrder = role.map(sortOrderProvider.retrieveSortOrder).getOrElse(SortOrder.defaultSortOrder)

  private def getRights(resolveMailboxACL: JavaMailboxACL): Rights = Rights.fromACL(MailboxACL.fromJava(resolveMailboxACL))

  private def getNamespace(mailboxPath: MailboxPath, mailboxSession: MailboxSession): MailboxNamespace =
    namespaceFactory.from(mailboxPath, mailboxSession)

  private def getParentPath(mailboxPath: MailboxPath, mailboxSession: MailboxSession): Option[MailboxPath] = mailboxPath
    .getHierarchyLevels(mailboxSession.getPathDelimiter)
    .asScala
    .reverse
    .drop(1)
    .headOption

  private def aclEntryKey(mailboxSession: MailboxSession): EntryKey = EntryKey.createUserEntryKey(mailboxSession.getUser)

  private def getMyRights(mailboxPath: MailboxPath, resolveMailboxACL: JavaMailboxACL, mailboxSession: MailboxSession): MailboxRights = mailboxPath.belongsTo(mailboxSession) match {
    case true => MailboxRights.FULL
    case false =>
      val rights = Rfc4314Rights.fromJava(resolveMailboxACL
        .getEntries
        .getOrDefault(aclEntryKey(mailboxSession), JavaMailboxACL.NO_RIGHTS))
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

  def create(mailboxMetaData: MailboxMetaData,
             mailboxSession: MailboxSession,
             subscriptions: Subscriptions,
             allMailboxesMetadata: Map[MailboxPath, MailboxMetaData],
             quotaLoader: QuotaLoader): SMono[Mailbox] = {

    val sanitizedCounters: MailboxCounters.Sanitized = mailboxMetaData.getCounters.sanitize()

    MailboxValidation.validate(mailboxMetaData.getPath, mailboxSession.getPathDelimiter, sanitizedCounters.getUnseen, sanitizedCounters.getUnseen, sanitizedCounters.getCount, sanitizedCounters.getCount) match {
      case Left(error) => SMono.error(error)
      case scala.Right(mailboxValidation) =>
        SMono.fromPublisher(quotaLoader.getQuotas(mailboxMetaData.getPath))
          .map(quotas => {
            val id: MailboxId = mailboxMetaData.getId
            val role: Option[Role] = getRole(mailboxMetaData.getPath, mailboxSession)
            val sortOrder: SortOrder = getSortOrder(role)
            val rights: Rights = getRights(mailboxMetaData.getResolvedAcls)
            val namespace: MailboxNamespace = getNamespace(mailboxMetaData.getPath, mailboxSession)
            val parentPath: Option[MailboxPath] = getParentPath(mailboxMetaData.getPath, mailboxSession)
            val parentId: Option[MailboxId] = parentPath.flatMap(path => allMailboxesMetadata.get(path)).map(_.getId)
            val myRights: MailboxRights = getMyRights(mailboxMetaData.getPath, mailboxMetaData.getResolvedAcls, mailboxSession)
            val isSubscribed: IsSubscribed = subscriptions.isSubscribed(mailboxMetaData)

            Mailbox(
              id = id,
              name = mailboxValidation.mailboxName,
              parentId = parentId,
              role = role,
              sortOrder = sortOrder,
              unreadEmails = mailboxValidation.unreadEmails,
              totalEmails = mailboxValidation.totalEmails,
              unreadThreads = mailboxValidation.unreadThreads,
              totalThreads = mailboxValidation.totalThreads,
              myRights = myRights,
              namespace = namespace,
              rights = rights,
              quotas = quotas,
              isSubscribed = isSubscribed)
          })
    }
  }

  def create(id: MailboxId, mailboxSession: MailboxSession, quotaLoader: QuotaLoader, subscriptions: Subscriptions): SMono[Mailbox] =
    for {
      messageManager <- SMono(mailboxManager.getMailboxReactive(id, mailboxSession))
      counters <- SMono(messageManager.getMailboxCountersReactive(mailboxSession))
      sanitizedCounters = counters.sanitize()
      mailbox <- instanciateMailbox(id, mailboxSession, quotaLoader, messageManager, sanitizedCounters, subscriptions)
    } yield {
      mailbox
    }

  private def instanciateMailbox(id: MailboxId, mailboxSession: MailboxSession, quotaLoader: QuotaLoader, messageManager: MessageManager, sanitizedCounters: MailboxCounters.Sanitized, subscriptions: Subscriptions): SMono[Mailbox] = {
    try {
      MailboxValidation.validate(messageManager.getMailboxPath, mailboxSession.getPathDelimiter, sanitizedCounters.getUnseen, sanitizedCounters.getUnseen, sanitizedCounters.getCount, sanitizedCounters.getCount) match {
        case Left(error) => SMono.error(error)
        case scala.Right(mailboxValidation) =>

          SMono.zip(array => (array(0).asInstanceOf[Option[MailboxId]],
              array(1).asInstanceOf[Quotas]),
              getParentId(messageManager, mailboxSession),
              SMono.fromPublisher(quotaLoader.getQuotas(messageManager.getMailboxPath)))
            .map(tuple2 => {
              val resolvedACL = messageManager.getResolvedAcl(mailboxSession)
              val role: Option[Role] = getRole(messageManager.getMailboxPath, mailboxSession)
              val sortOrder: SortOrder = getSortOrder(role)
              val rights: Rights = getRights(resolvedACL)
              val namespace: MailboxNamespace = getNamespace(messageManager.getMailboxPath, mailboxSession)
              val myRights: MailboxRights = getMyRights(messageManager.getMailboxPath, resolvedACL, mailboxSession)
              val isSubscribed: IsSubscribed = subscriptions.isSubscribed(messageManager.getMailboxPath)

              Mailbox(
                id = id,
                name = mailboxValidation.mailboxName,
                parentId = tuple2._1,
                role = role,
                sortOrder = sortOrder,
                unreadEmails = mailboxValidation.unreadEmails,
                totalEmails = mailboxValidation.totalEmails,
                unreadThreads = mailboxValidation.unreadThreads,
                totalThreads = mailboxValidation.totalThreads,
                myRights = myRights,
                namespace = namespace,
                rights = rights,
                quotas = tuple2._2,
                isSubscribed = isSubscribed)
            })
      }
    } catch {
      case error: Exception => SMono.error(error)
    }
  }

  private def getParentId(messageManager: MessageManager, mailboxSession: MailboxSession): SMono[Option[MailboxId]] =
    getParentPath(messageManager.getMailboxPath, mailboxSession)
      .map(parentPath => SMono(mailboxManager.getMailboxReactive(parentPath, mailboxSession))
        .map(_.getId)
        .map(Some(_))
        .onErrorResume {
          case _: MailboxNotFoundException => SMono.just(None)
          case e => SMono.error(e)
        })
      .getOrElse(SMono.just(None))
}