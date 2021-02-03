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
import org.apache.james.events.{Event, EventBus, Group, RegistrationKey}
import org.apache.james.jmap.InjectionKeys
import org.apache.james.jmap.api.change.{EmailChange, EmailChangeRepository, JmapChange, MailboxChange, MailboxChangeRepository}
import org.apache.james.jmap.api.model.AccountId
import org.apache.james.jmap.change.MailboxChangeListener.LOGGER
import org.apache.james.jmap.core.State
import org.apache.james.mailbox.events.MailboxEvents.{Added, Expunged, FlagsUpdated, MailboxACLUpdated, MailboxAdded, MailboxDeletion, MailboxEvent, MailboxRenamed}
import org.apache.james.mailbox.exception.MailboxException
import org.apache.james.mailbox.model.{MailboxACL, MailboxId}
import org.apache.james.mailbox.{MailboxManager, MailboxSession}
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
                                            emailChangeFactory: EmailChange.Factory,
                                            mailboxManager: MailboxManager,
                                            clock: Clock) extends ReactiveGroupEventListener {

  override def reactiveEvent(event: Event): Publisher[Void] =
    handleEvent(event.asInstanceOf[MailboxEvent])
      .`then`(SMono.empty[Void])
      .asJava

  override def getDefaultGroup: Group = MailboxChangeListenerGroup()

  override def isHandling(event: Event): Boolean = event.isInstanceOf[MailboxEvent]

  private def handleEvent(mailboxEvent: MailboxEvent): SMono[Unit] = {
    val now: ZonedDateTime = ZonedDateTime.now(clock)
    val mailboxId: MailboxId = mailboxEvent.getMailboxId
    val username: Username = mailboxEvent.getUsername

    SFlux.fromIterable(
      mailboxEvent match {
        case mailboxAdded: MailboxAdded =>
          mailboxChangeFactory.fromMailboxAdded(mailboxAdded, now).asScala
        case mailboxRenamed: MailboxRenamed =>
          mailboxChangeFactory.fromMailboxRenamed(mailboxRenamed, now, getSharees(mailboxId, username).asJava).asScala
        case mailboxACLUpdated: MailboxACLUpdated =>
          mailboxChangeFactory.fromMailboxACLUpdated(mailboxACLUpdated, now, getSharees(mailboxId, username).asJava).asScala
        case mailboxDeletion: MailboxDeletion =>
          mailboxChangeFactory.fromMailboxDeletion(mailboxDeletion, now).asScala
        case added: Added =>
          val sharees = getSharees(mailboxId, username).asJava
          mailboxChangeFactory.fromAdded(added, now, sharees).asScala
            .concat(emailChangeFactory.fromAdded(added, now, sharees).asScala)
        case flagsUpdated: FlagsUpdated =>
          val sharees = getSharees(mailboxId, username).asJava
          mailboxChangeFactory.fromFlagsUpdated(flagsUpdated, now, sharees).asScala
            .concat(emailChangeFactory.fromFlagsUpdated(flagsUpdated, now, sharees).asScala)
        case expunged: Expunged =>
          val sharees = getSharees(mailboxId, username).asJava
          mailboxChangeFactory.fromExpunged(expunged, now, sharees).asScala
            .concat(emailChangeFactory.fromExpunged(expunged, now, sharees).asScala)
      })
      .flatMap(saveChangeEvent, DEFAULT_CONCURRENCY)
      .`then`()
  }

  private def saveChangeEvent(jmapChange: JmapChange): Publisher[Void] =
    SMono(jmapChange match {
      case mailboxChange: MailboxChange => mailboxChangeRepository.save(mailboxChange)
      case emailChange: EmailChange => emailChangeRepository.save(emailChange)
    }).`then`(SMono(eventBus.dispatch(toStateChangeEvent(jmapChange), Set[RegistrationKey]().asJava)))


  private def getSharees(mailboxId: MailboxId, username: Username): List[AccountId] = {
    val mailboxSession: MailboxSession = mailboxManager.createSystemSession(username)
    try {
      val mailboxACL = mailboxManager.listRights(mailboxId, mailboxSession)
      mailboxACL.getEntries.keySet
        .asScala
        .filter(!_.isNegative)
        .filter(_.getNameType == MailboxACL.NameType.user)
        .map(_.getName)
        .map(AccountId.fromString)
        .toList
    } catch {
      case e: MailboxException =>
        LOGGER.warn("Could not get sharees for mailbox [%s] when listening to change events", mailboxId)
        List.empty
    }
  }

  private def toStateChangeEvent(jmapChange: JmapChange): StateChangeEvent = jmapChange match {
    case emailChange: EmailChange => StateChangeEvent(
      eventId = EventId.random(),
      username = Username.of(emailChange.getAccountId.getIdentifier),
      emailState = Some(State.fromJava(emailChange.getState)),
      mailboxState = None)
    case mailboxChange: MailboxChange => StateChangeEvent(
      eventId = EventId.random(),
      username = Username.of(mailboxChange.getAccountId.getIdentifier),
      emailState = Some(State.fromJava(mailboxChange.getState)),
      mailboxState = None)
  }
}
