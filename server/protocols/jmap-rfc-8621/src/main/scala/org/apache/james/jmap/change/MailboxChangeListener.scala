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

package org.apache.james.jmap.change

import java.time.{Clock, ZonedDateTime}

import javax.inject.{Inject, Named}
import org.apache.james.core.Username
import org.apache.james.events.Event.EventId
import org.apache.james.events.EventListener.ReactiveGroupEventListener
import org.apache.james.events.{Event, EventBus, Group}
import org.apache.james.jmap.InjectionKeys
import org.apache.james.jmap.api.change.{EmailChange, EmailChangeRepository, JmapChange, MailboxAndEmailChange, MailboxChange, MailboxChangeRepository}
import org.apache.james.jmap.api.model.{AccountId, State, TypeName}
import org.apache.james.jmap.change.MailboxChangeListener.LOGGER
import org.apache.james.jmap.core.UuidState
import org.apache.james.mailbox.MailboxManager
import org.apache.james.mailbox.events.MailboxEvents
import org.apache.james.mailbox.events.MailboxEvents.{Added, Expunged, FlagsUpdated, MailboxACLUpdated, MailboxAdded, MailboxDeletion, MailboxEvent, MailboxRenamed}
import org.apache.james.mailbox.model.{MailboxACL, MailboxId}
import org.apache.james.util.ReactorUtils.DEFAULT_CONCURRENCY
import org.reactivestreams.Publisher
import org.slf4j.{Logger, LoggerFactory}
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.jdk.CollectionConverters._

case class MailboxChangeListenerGroup() extends Group {}

object MailboxChangeListener {
  val LOGGER: Logger = LoggerFactory.getLogger(classOf[MailboxChangeListener])
}

case class MailboxChangeListener @Inject() (@Named(InjectionKeys.JMAP) eventBus: EventBus,
                                            mailboxChangeRepository: MailboxChangeRepository,
                                            mailboxChangeFactory: MailboxChange.Factory,
                                            emailChangeRepository: EmailChangeRepository,
                                            emailChangeFactory: MailboxAndEmailChange.Factory,
                                            mailboxManager: MailboxManager,
                                            clock: Clock) extends ReactiveGroupEventListener {

  override def reactiveEvent(event: Event): Publisher[Void] =
    jmapChanges(event.asInstanceOf[MailboxEvent])
      .flatMap(saveChangeEvent, DEFAULT_CONCURRENCY)
      .`then`()

  override def getDefaultGroup: Group = MailboxChangeListenerGroup()

  override def isHandling(event: Event): Boolean = event.isInstanceOf[MailboxEvent]

  private def jmapChanges(mailboxEvent: MailboxEvent): SFlux[JmapChange] = {
    val now: ZonedDateTime = ZonedDateTime.now(clock)
    val mailboxId: MailboxId = mailboxEvent.getMailboxId
    val username: Username = mailboxEvent.getUsername

    mailboxEvent match {
      case mailboxAdded: MailboxAdded =>
        SFlux.just(mailboxChangeFactory.fromMailboxAdded(mailboxAdded, now))
      case mailboxRenamed: MailboxRenamed =>
        getSharees(mailboxId, username)
          .flatMapIterable(sharees => mailboxChangeFactory.fromMailboxRenamed(mailboxRenamed, now, sharees.asJava).asScala)
      case mailboxACLUpdated: MailboxACLUpdated =>
        getSharees(mailboxId, username)
          .flatMapIterable(sharees => mailboxChangeFactory.fromMailboxACLUpdated(mailboxACLUpdated, now, sharees.asJava).asScala)
      case mailboxDeletion: MailboxDeletion =>
        SFlux.fromIterable(mailboxChangeFactory.fromMailboxDeletion(mailboxDeletion, now).asScala)
      case added: Added =>
        getSharees(mailboxId, username)
          .flatMapIterable(sharees => emailChangeFactory.fromAdded(added, now, sharees.asJava).asScala)
      case flagsUpdated: FlagsUpdated =>
        getSharees(mailboxId, username)
          .flatMapIterable(sharees => emailChangeFactory.fromFlagsUpdated(flagsUpdated, now, sharees.asJava).asScala)
      case expunged: Expunged =>
        getSharees(mailboxId, username)
          .flatMapMany(sharees =>
            SFlux(emailChangeFactory.fromExpunged(expunged, now, sharees.map(_.getIdentifier).map(Username.of).asJava)))
      case subscribed: MailboxEvents.MailboxSubscribedEvent =>
        SFlux.just(mailboxChangeFactory.fromMailboxSubscribed(subscribed, now))
      case unSubscribed: MailboxEvents.MailboxUnsubscribedEvent =>
        SFlux.just(mailboxChangeFactory.fromMailboxUnSubscribed(unSubscribed, now))
    }
  }

  private def saveChangeEvent(jmapChange: JmapChange): Publisher[Void] =
    SMono(jmapChange match {
      case mailboxChange: MailboxChange => mailboxChangeRepository.save(mailboxChange)
      case emailChange: EmailChange => emailChangeRepository.save(emailChange)
      case mailboxAndEmailChange: MailboxAndEmailChange => mailboxChangeRepository.save(mailboxAndEmailChange.getMailboxChange)
        .`then`(emailChangeRepository.save(mailboxAndEmailChange.getEmailChange))
    }).`then`(SMono(eventBus.dispatch(toStateChangeEvent(jmapChange), AccountIdRegistrationKey(jmapChange.getAccountId))))

  private def getSharees(mailboxId: MailboxId, username: Username): SMono[List[AccountId]] = {
    val session = mailboxManager.createSystemSession(username)
    SMono(mailboxManager.getMailboxReactive(mailboxId, session))
      .map(mailbox => mailbox.getResolvedAcl(session))
      .map(mailboxACL => mailboxACL.getEntries.keySet
        .asScala
        .filter(!_.isNegative)
        .filter(_.getNameType == MailboxACL.NameType.user)
        .map(_.getName)
        .map(AccountId.fromString)
        .toList)
      .onErrorResume(e => {
        LOGGER.warn("Could not get sharees for mailbox [%s] when listening to change events", mailboxId, e)
        SMono.just(List.empty)
      })
  }

  private def toStateChangeEvent(jmapChange: JmapChange): StateChangeEvent = jmapChange match {
    case emailChange: EmailChange => StateChangeEvent(
      eventId = EventId.random(),
      username = Username.of(emailChange.getAccountId.getIdentifier),
      map = emailStateMap(emailChange))
    case mailboxChange: MailboxChange => StateChangeEvent(
      eventId = EventId.random(),
      username = Username.of(mailboxChange.getAccountId.getIdentifier),
      map = mailboxStateMap(mailboxChange))
    case mailboxAndEmailChange: MailboxAndEmailChange => StateChangeEvent(
      eventId = EventId.random(),
      username = Username.of(mailboxAndEmailChange.getAccountId.getIdentifier),
      map = emailStateMap(mailboxAndEmailChange.getEmailChange) ++ mailboxStateMap(mailboxAndEmailChange.getMailboxChange))
  }

  private def mailboxStateMap(mailboxChange: MailboxChange): Map[TypeName, State] =
    Map(MailboxTypeName -> UuidState.fromJava(mailboxChange.getState))

  private def emailStateMap(emailChange: EmailChange): Map[TypeName, State] =
    (Map(EmailTypeName -> UuidState.fromJava(emailChange.getState)) ++
      Some(UuidState.fromJava(emailChange.getState))
        .filter(_ => emailChange.isDelivery && !emailChange.getCreated.isEmpty)
        .map(emailDeliveryState => Map(EmailDeliveryTypeName -> emailDeliveryState))
        .getOrElse(Map())).toMap
}
