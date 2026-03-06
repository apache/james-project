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

package org.apache.james.jmap.method

import java.time.ZonedDateTime

import jakarta.inject.Inject
import org.apache.james.jmap.JMAPConfiguration
import org.apache.james.jmap.api.projections.EmailQueryViewManager
import org.apache.james.jmap.core.Limit.Limit
import org.apache.james.jmap.core.Position.Position
import org.apache.james.jmap.mail.{Comparator, EmailQueryRequest, FilterCondition}
import org.apache.james.mailbox.exception.MailboxNotFoundException
import org.apache.james.mailbox.model.MultimailboxesSearchQuery.Namespace
import org.apache.james.mailbox.model.{MailboxId, MessageId, MultimailboxesSearchQuery}
import org.apache.james.mailbox.{MailboxManager, MailboxSession}
import org.apache.james.util.streams.{Limit => JavaLimit}
import reactor.core.scala.publisher.{SFlux, SMono}

trait EmailQueryOptimizer {
  def apply(request: EmailQueryRequest, session: MailboxSession, searchQuery: MultimailboxesSearchQuery, position: Position, limit: Limit): Option[SFlux[MessageId]]
}

class EmailQueryViewOptimizer @Inject() (mailboxManager: MailboxManager,
                                         val configuration: JMAPConfiguration,
                                         val emailQueryViewManager: EmailQueryViewManager) extends EmailQueryOptimizer {
  override def apply(request: EmailQueryRequest, session: MailboxSession, searchQuery: MultimailboxesSearchQuery, position: Position, limit: Limit): Option[SFlux[MessageId]] =
    request match {
      case request: EmailQueryRequest if matchesInMailboxSortedByReceivedAt(request) =>
        Some(queryViewForListingSortedByReceivedAt(session, position, limit, request, searchQuery.getNamespace))
      case request: EmailQueryRequest if matchesInMailboxAfterSortedByReceivedAt(request) =>
        Some(queryViewForContentAfterSortedByReceivedAt(session, position, limit, request, searchQuery.getNamespace))
      case request: EmailQueryRequest if matchesInMailboxBeforeSortedByReceivedAt(request) =>
        Some(queryViewForContentBeforeSortedByReceivedAt(session, position, limit, request, searchQuery.getNamespace))
      case _ => None
    }

  private def matchesInMailboxSortedByReceivedAt(request: EmailQueryRequest): Boolean =
    configuration.isEmailQueryViewEnabled &&
      request.filter.exists(_.inMailboxFilterOnly) &&
      request.sort.contains(Set(Comparator.RECEIVED_AT_DESC))

  private def matchesInMailboxAfterSortedByReceivedAt(request: EmailQueryRequest): Boolean =
    configuration.isEmailQueryViewEnabled &&
      request.filter.exists(_.inMailboxAndAfterFilterOnly) &&
      request.sort.contains(Set(Comparator.RECEIVED_AT_DESC))

  private def matchesInMailboxBeforeSortedByReceivedAt(request: EmailQueryRequest): Boolean =
    configuration.isEmailQueryViewEnabled &&
      request.filter.exists(_.inMailboxAndBeforeFilterOnly) &&
      request.sort.contains(Set(Comparator.RECEIVED_AT_DESC))

  private def queryViewForListingSortedByReceivedAt(mailboxSession: MailboxSession, position: Position, limitToUse: Limit, request: EmailQueryRequest, namespace: Namespace): SFlux[MessageId] = {
    val mailboxId: MailboxId = request.filter.get.asInstanceOf[FilterCondition].inMailbox.get
    val collapseThreads: Boolean = getCollapseThreads(request)

    val queryViewEntries: SFlux[MessageId] = SFlux.fromPublisher(emailQueryViewManager
      .getEmailQueryView(mailboxSession.getUser).listMailboxContentSortedByReceivedAt(mailboxId, JavaLimit.from(limitToUse.value + position.value), collapseThreads))

    fromQueryViewEntries(mailboxId, queryViewEntries, mailboxSession, position, limitToUse, namespace)
  }

  private def getCollapseThreads(request: EmailQueryRequest): Boolean =
    request.collapseThreads match {
      case Some(collapseThreads) => collapseThreads.value
      case None => false
    }

  private def fromQueryViewEntries(mailboxId: MailboxId, queryViewEntries: SFlux[MessageId], mailboxSession: MailboxSession, position: Position, limitToUse: Limit, namespace: Namespace): SFlux[MessageId] =
    SMono(mailboxManager.getMailboxReactive(mailboxId, mailboxSession))
      .filter(messageManager => namespace.keepAccessible(messageManager.getMailboxEntity))
      .flatMapMany(_ => queryViewEntries
        .drop(position.value)
        .take(limitToUse.value))
      .onErrorResume({
        case _: MailboxNotFoundException => SFlux.empty
        case e => SFlux.error[MessageId](e)
      })

  private def queryViewForContentAfterSortedByReceivedAt(mailboxSession: MailboxSession, position: Position, limitToUse: Limit, request: EmailQueryRequest, namespace: Namespace): SFlux[MessageId] = {
    val condition: FilterCondition = request.filter.get.asInstanceOf[FilterCondition]
    val mailboxId: MailboxId = condition.inMailbox.get
    val after: ZonedDateTime = condition.after.get.asUTC
    val collapseThreads: Boolean = getCollapseThreads(request)

    val queryViewEntries: SFlux[MessageId] = SFlux.fromPublisher(emailQueryViewManager.getEmailQueryView(mailboxSession.getUser)
      .listMailboxContentSinceAfterSortedByReceivedAt(mailboxId, after, JavaLimit.from(limitToUse.value + position.value), collapseThreads))

    fromQueryViewEntries(mailboxId, queryViewEntries, mailboxSession, position, limitToUse, namespace)
  }

  private def queryViewForContentBeforeSortedByReceivedAt(mailboxSession: MailboxSession, position: Position, limitToUse: Limit, request: EmailQueryRequest, namespace: Namespace): SFlux[MessageId] = {
    val condition: FilterCondition = request.filter.get.asInstanceOf[FilterCondition]
    val mailboxId: MailboxId = condition.inMailbox.get
    val before: ZonedDateTime = condition.before.get.asUTC
    val collapseThreads: Boolean = getCollapseThreads(request)

    val queryViewEntries: SFlux[MessageId] = SFlux.fromPublisher(emailQueryViewManager.getEmailQueryView(mailboxSession.getUser)
      .listMailboxContentBeforeSortedByReceivedAt(mailboxId, before, JavaLimit.from(limitToUse.value + position.value), collapseThreads))

    fromQueryViewEntries(mailboxId, queryViewEntries, mailboxSession, position, limitToUse, namespace)
  }
}
