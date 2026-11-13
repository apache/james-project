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
import java.util

import jakarta.inject.{Inject, Named}
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
import org.apache.james.mailbox.exception.{MailboxException, MailboxNotFoundException}
import org.apache.james.mailbox.model.{MailboxACL, MailboxId}
import org.apache.james.util.ReactorUtils.DEFAULT_CONCURRENCY
import org.reactivestreams.Publisher
import org.slf4j.{Logger, LoggerFactory}
import reactor.core.publisher.Mono
import reactor.core.scala.publisher.{SFlux, SMono, publisherUnit2PublisherVoid}

import scala.jdk.CollectionConverters._

case class MailboxChangeListenerGroup() extends Group {}

private[change] object Sharees {
  val EMPTY: Sharees = Sharees(List.empty, Set.empty)

  def from(mailboxACL: MailboxACL): Sharees = {
    val entries: Map[MailboxACL.EntryKey, MailboxACL.Rfc4314Rights] = mailboxACL.getEntries.asScala.toMap
    val userKeys: List[MailboxACL.EntryKey] = entries.keys
      .filter(!_.isNegative)
      .filter(_.getNameType == MailboxACL.NameType.user)
      .toList

    Sharees(
      mailboxSharees = userKeys.map(key => AccountId.fromString(key.getName)),
      emailSharees = userKeys
        .filter(key => resolveRights(entries, key).contains(MailboxACL.Right.Read))
        .map(key => AccountId.fromString(key.getName))
        .toSet)
  }

  private def resolveRights(entries: Map[MailboxACL.EntryKey, MailboxACL.Rfc4314Rights], positiveKey: MailboxACL.EntryKey): MailboxACL.Rfc4314Rights =
    entries.get(MailboxACL.EntryKey.createUserEntryKey(positiveKey.getName, true))
      .map(negativeRights => entries(positiveKey).except(negativeRights))
      .getOrElse(entries(positiveKey))
}

/**
 * @param mailboxSharees users seeing the mailbox: they receive mailbox changes
 * @param emailSharees users allowed to read the messages of the mailbox: they also receive email changes
 */
private[change] case class Sharees(mailboxSharees: List[AccountId], emailSharees: Set[AccountId])

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
      .map(toStateChangeEvent)
      .flatMap(dispactChangeEvent, DEFAULT_CONCURRENCY)
      .`then`()

  override def reactiveEvent(events: util.List[Event]): Publisher[Void] =
    SFlux.fromIterable(events.asScala)
      .filter(isHandling)
      .concatMap(event => jmapChanges(event.asInstanceOf[MailboxEvent]))
      .concatMap(saveChangeEvent, DEFAULT_CONCURRENCY)
      .groupBy(_.getAccountId)
      .flatMap(group => group.map(a => toStateChangeEvent(a))
        .fold[StateChangeEvent](StateChangeEvent(EventId.random(), Username.of(group.key().getIdentifier), Map())) {
          (a, b) => mergeStateChangeEvents(a, b)
        })
      .flatMap(dispactChangeEvent, DEFAULT_CONCURRENCY)
      .`then`()

  private def mergeStateChangeEvents(one: StateChangeEvent, other: StateChangeEvent): StateChangeEvent = StateChangeEvent(
    one.eventId,
    one.username,
    one.map ++ other.map) // will keep the rightmost value

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
          .flatMapIterable(sharees => mailboxChangeFactory.fromMailboxRenamed(mailboxRenamed, now, sharees.mailboxSharees.asJava).asScala)
      case mailboxACLUpdated: MailboxACLUpdated =>
        getSharees(mailboxId, username)
          .flatMapIterable(sharees => mailboxChangeFactory.fromMailboxACLUpdated(mailboxACLUpdated, now, sharees.mailboxSharees.asJava).asScala)
      case mailboxDeletion: MailboxDeletion =>
        SFlux.fromIterable(mailboxChangeFactory.fromMailboxDeletion(mailboxDeletion, now).asScala)
      case added: Added =>
        getSharees(mailboxId, username)
          .flatMapIterable(sharees => emailChangeFactory.fromAdded(added, now, sharees.mailboxSharees.asJava, sharees.emailSharees.asJava).asScala)
      case flagsUpdated: FlagsUpdated =>
        getSharees(mailboxId, username)
          .flatMapIterable(sharees => emailChangeFactory.fromFlagsUpdated(flagsUpdated, now, sharees.mailboxSharees.asJava, sharees.emailSharees.asJava).asScala)
      case expunged: Expunged =>
        getSharees(mailboxId, username)
          .flatMapMany(sharees =>
            SFlux(emailChangeFactory.fromExpunged(expunged, now,
              sharees.mailboxSharees.map(_.getIdentifier).map(Username.of).asJava,
              sharees.emailSharees.map(_.getIdentifier).map(Username.of).asJava)))
      case subscribed: MailboxEvents.MailboxSubscribedEvent =>
        SFlux.just(mailboxChangeFactory.fromMailboxSubscribed(subscribed, now))
      case unSubscribed: MailboxEvents.MailboxUnsubscribedEvent =>
        SFlux.just(mailboxChangeFactory.fromMailboxUnSubscribed(unSubscribed, now))
    }
  }

  private def saveChangeEvent(jmapChange: JmapChange): Publisher[JmapChange] =
    SMono(jmapChange match {
      case mailboxChange: MailboxChange => mailboxChangeRepository.save(mailboxChange).`then`(Mono.just(jmapChange))
      case emailChange: EmailChange => emailChangeRepository.save(emailChange).`then`(Mono.just(jmapChange))
      case mailboxAndEmailChange: MailboxAndEmailChange => mailboxChangeRepository.save(mailboxAndEmailChange.getMailboxChange)
        .`then`(emailChangeRepository.save(mailboxAndEmailChange.getEmailChange)).`then`(Mono.just(jmapChange))
    })

  private def dispactChangeEvent(jmapChange: StateChangeEvent): Publisher[Void] =
    SMono(eventBus.dispatch(jmapChange, AccountIdRegistrationKey(AccountId.fromUsername(jmapChange.getUsername))))

  private def getSharees(mailboxId: MailboxId, username: Username): SMono[Sharees] = {
    val session = mailboxManager.createSystemSession(username)
    SMono(mailboxManager.getMailboxReactive(mailboxId, session))
      .map(mailbox => mailbox.getResolvedAcl(session))
      .map(Sharees.from)
      .onErrorResume({
        case e: MailboxNotFoundException =>
          LOGGER.warn("Could not get sharees for mailbox {} as the mailbox was deleted", mailboxId)
          SMono.just(Sharees.EMPTY)
        case e =>
          LOGGER.warn("Could not get sharees for mailbox {} when listening to change events", mailboxId, e)
          SMono.just(Sharees.EMPTY)
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
