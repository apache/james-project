/** **************************************************************
  * Licensed to the Apache Software Foundation (ASF) under one   *
  * or more contributor license agreements.  See the NOTICE file *
  * distributed with this work for additional information        *
  * regarding copyright ownership.  The ASF licenses this file   *
  * to you under the Apache License, Version 2.0 (the            *
  * "License"); you may not use this file except in compliance   *
  * with the License.  You may obtain a copy of the License at   *
  * *
  * http://www.apache.org/licenses/LICENSE-2.0                 *
  * *
  * Unless required by applicable law or agreed to in writing,   *
  * software distributed under the License is distributed on an  *
  * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
  * KIND, either express or implied.  See the License for the    *
  * specific language governing permissions and limitations      *
  * under the License.                                           *
  * ***************************************************************/

package org.apache.james.event.json

import java.time.Instant
import java.util.{Optional, TreeMap => JavaTreeMap}

import javax.inject.Inject
import julienrf.json.derived
import org.apache.james.core.quota.{QuotaCount, QuotaSize, QuotaValue}
import org.apache.james.core.{Domain, User}
import org.apache.james.event.json.DTOs.SystemFlag.SystemFlag
import org.apache.james.event.json.DTOs.{ACLDiff, Flags, MailboxPath, Quota, SystemFlag, UserFlag}
import org.apache.james.mailbox.MailboxSession.SessionId
import org.apache.james.mailbox.events.Event.EventId
import org.apache.james.mailbox.events.MailboxListener.{Added => JavaAdded, Expunged => JavaExpunged, FlagsUpdated => JavaFlagsUpdated, MailboxACLUpdated => JavaMailboxACLUpdated, MailboxAdded => JavaMailboxAdded, MailboxDeletion => JavaMailboxDeletion, MailboxRenamed => JavaMailboxRenamed, QuotaUsageUpdatedEvent => JavaQuotaUsageUpdatedEvent}
import org.apache.james.mailbox.events.{MessageMoveEvent => JavaMessageMoveEvent, Event => JavaEvent}
import org.apache.james.mailbox.model.{MailboxId, MessageId, MessageMoves, QuotaRoot, MailboxACL => JavaMailboxACL, MessageMetaData => JavaMessageMetaData, Quota => JavaQuota}
import org.apache.james.mailbox.{MessageUid, events}
import play.api.libs.json.{JsError, JsNull, JsNumber, JsObject, JsResult, JsString, JsSuccess, Json, OFormat, Reads, Writes}

import scala.collection.JavaConverters._

private sealed trait Event {
  def toJava: JavaEvent
}

private object DTO {
  case class MailboxACLUpdated(eventId: EventId, sessionId: SessionId, user: User, mailboxPath: MailboxPath, aclDiff: ACLDiff, mailboxId: MailboxId) extends Event {
    override def toJava: JavaEvent = new JavaMailboxACLUpdated(sessionId, user, mailboxPath.toJava, aclDiff.toJava, mailboxId, eventId)
  }

  case class MailboxAdded(eventId: EventId, mailboxPath: MailboxPath, mailboxId: MailboxId, user: User, sessionId: SessionId) extends Event {
    override def toJava: JavaEvent = new JavaMailboxAdded(sessionId, user, mailboxPath.toJava, mailboxId, eventId)
  }

  case class MailboxDeletion(eventId: EventId, sessionId: SessionId, user: User, path: MailboxPath, quotaRoot: QuotaRoot,
                             deletedMessageCount: QuotaCount, totalDeletedSize: QuotaSize, mailboxId: MailboxId) extends Event {
    override def toJava: JavaEvent = new JavaMailboxDeletion(sessionId, user, path.toJava, quotaRoot, deletedMessageCount,
      totalDeletedSize, mailboxId, eventId)
  }

  case class MailboxRenamed(eventId: EventId, sessionId: SessionId, user: User, path: MailboxPath, mailboxId: MailboxId, newPath: MailboxPath) extends Event {
    override def toJava: JavaEvent = new JavaMailboxRenamed(sessionId, user, path.toJava, mailboxId, newPath.toJava, eventId)
  }

  case class QuotaUsageUpdatedEvent(eventId: EventId, user: User, quotaRoot: QuotaRoot, countQuota: Quota[QuotaCount],
                                    sizeQuota: Quota[QuotaSize], time: Instant) extends Event {
    override def toJava: JavaEvent = new JavaQuotaUsageUpdatedEvent(eventId, user, quotaRoot, countQuota.toJava, sizeQuota.toJava, time)
  }

  case class Added(eventId: EventId, sessionId: SessionId, user: User, path: MailboxPath, mailboxId: MailboxId,
                   added: Map[MessageUid, DTOs.MessageMetaData]) extends Event {
    override def toJava: JavaEvent = new JavaAdded(
      sessionId,
      user,
      path.toJava,
      mailboxId,
      new JavaTreeMap[MessageUid, JavaMessageMetaData](added.mapValues(_.toJava).asJava),
      eventId)
  }

  case class Expunged(eventId: EventId, sessionId: SessionId, user: User, path: MailboxPath, mailboxId: MailboxId,
                      expunged: Map[MessageUid, DTOs.MessageMetaData]) extends Event {
    override def toJava: JavaEvent = new JavaExpunged(
      sessionId,
      user,
      path.toJava,
      mailboxId,
      expunged.mapValues(_.toJava).asJava,
      eventId)
  }

  case class MessageMoveEvent(eventId: EventId, user: User, previousMailboxIds: Iterable[MailboxId], targetMailboxIds: Iterable[MailboxId],
                              messageIds: Iterable[MessageId]) extends Event {
    override def toJava: JavaEvent = JavaMessageMoveEvent.builder()
      .eventId(eventId)
      .user(user)
      .messageId(messageIds.asJava)
      .messageMoves(MessageMoves.builder()
        .previousMailboxIds(previousMailboxIds.asJava)
        .targetMailboxIds(targetMailboxIds.asJava)
        .build())
      .build()
  }

  case class FlagsUpdated(eventId: EventId, sessionId: SessionId, user: User, path: MailboxPath, mailboxId: MailboxId,
                          updatedFlags: List[DTOs.UpdatedFlags]) extends Event {
    override def toJava: JavaEvent = new JavaFlagsUpdated(
      sessionId,
      user,
      path.toJava,
      mailboxId,
      updatedFlags.map(_.toJava).asJava,
      eventId)
  }
}

private object ScalaConverter {
  private def toScala(event: JavaMailboxACLUpdated): DTO.MailboxACLUpdated = DTO.MailboxACLUpdated(
    eventId = event.getEventId,
    sessionId = event.getSessionId,
    user = event.getUser,
    mailboxPath = MailboxPath.fromJava(event.getMailboxPath),
    aclDiff = ACLDiff.fromJava(event.getAclDiff),
    mailboxId = event.getMailboxId)

  private def toScala(event: JavaMailboxAdded): DTO.MailboxAdded = DTO.MailboxAdded(
    eventId = event.getEventId,
    mailboxPath = MailboxPath.fromJava(event.getMailboxPath),
    mailboxId = event.getMailboxId,
    user = event.getUser,
    sessionId = event.getSessionId)

  private def toScala(event: JavaMailboxDeletion): DTO.MailboxDeletion = DTO.MailboxDeletion(
    eventId = event.getEventId,
    sessionId = event.getSessionId,
    user = event.getUser,
    quotaRoot = event.getQuotaRoot,
    path = MailboxPath.fromJava(event.getMailboxPath),
    deletedMessageCount = event.getDeletedMessageCount,
    totalDeletedSize = event.getTotalDeletedSize,
    mailboxId = event.getMailboxId)

  private def toScala(event: JavaMailboxRenamed): DTO.MailboxRenamed = DTO.MailboxRenamed(
    eventId = event.getEventId,
    sessionId = event.getSessionId,
    user = event.getUser,
    path = MailboxPath.fromJava(event.getMailboxPath),
    mailboxId = event.getMailboxId,
    newPath = MailboxPath.fromJava(event.getNewPath))

  private def toScala(event: JavaQuotaUsageUpdatedEvent): DTO.QuotaUsageUpdatedEvent = DTO.QuotaUsageUpdatedEvent(
    eventId = event.getEventId,
    user = event.getUser,
    quotaRoot = event.getQuotaRoot,
    countQuota = Quota.toScala(event.getCountQuota),
    sizeQuota = Quota.toScala(event.getSizeQuota),
    time = event.getInstant)

  private def toScala(event: JavaAdded): DTO.Added = DTO.Added(
    eventId = event.getEventId,
    sessionId = event.getSessionId,
    user = event.getUser,
    path = MailboxPath.fromJava(event.getMailboxPath),
    mailboxId = event.getMailboxId,
    added = event.getAdded.asScala.mapValues(DTOs.MessageMetaData.fromJava).toMap)

  private def toScala(event: JavaExpunged): DTO.Expunged = DTO.Expunged(
    eventId = event.getEventId,
    sessionId = event.getSessionId,
    user = event.getUser,
    path = MailboxPath.fromJava(event.getMailboxPath),
    mailboxId = event.getMailboxId,
    expunged = event.getExpunged.asScala.mapValues(DTOs.MessageMetaData.fromJava).toMap)

  private def toScala(event: JavaMessageMoveEvent): DTO.MessageMoveEvent = DTO.MessageMoveEvent(
    eventId = event.getEventId,
    user = event.getUser,
    previousMailboxIds = event.getMessageMoves.getPreviousMailboxIds.asScala,
    targetMailboxIds = event.getMessageMoves.getTargetMailboxIds.asScala,
    messageIds = event.getMessageIds.asScala)

  private def toScala(event: JavaFlagsUpdated): DTO.FlagsUpdated = DTO.FlagsUpdated(
    eventId = event.getEventId,
    sessionId = event.getSessionId,
    user = event.getUser,
    path = DTOs.MailboxPath.fromJava(event.getMailboxPath),
    mailboxId = event.getMailboxId,
    updatedFlags = event.getUpdatedFlags.asScala.map(DTOs.UpdatedFlags.toUpdatedFlags).toList)

  def toScala(javaEvent: JavaEvent): Event = javaEvent match {
    case e: JavaAdded => toScala(e)
    case e: JavaExpunged => toScala(e)
    case e: JavaFlagsUpdated => toScala(e)
    case e: JavaMailboxACLUpdated => toScala(e)
    case e: JavaMailboxAdded => toScala(e)
    case e: JavaMailboxDeletion => toScala(e)
    case e: JavaMailboxRenamed => toScala(e)
    case e: JavaMessageMoveEvent => toScala(e)
    case e: JavaQuotaUsageUpdatedEvent => toScala(e)
    case _ => throw new RuntimeException("no Scala conversion known")
  }
}

class JsonSerialize(mailboxIdFactory: MailboxId.Factory, messageIdFactory: MessageId.Factory) {
  implicit val systemFlagsWrites: Writes[SystemFlag] = Writes.enumNameWrites
  implicit val userWriters: Writes[User] = (user: User) => JsString(user.asString)
  implicit val quotaRootWrites: Writes[QuotaRoot] = quotaRoot => JsString(quotaRoot.getValue)
  implicit val quotaValueWrites: Writes[QuotaValue[_]] = value => if (value.isUnlimited) JsNull else JsNumber(value.asLong())
  implicit val quotaScopeWrites: Writes[JavaQuota.Scope] = value => JsString(value.name)
  implicit val quotaCountWrites: Writes[Quota[QuotaCount]] = Json.writes[Quota[QuotaCount]]
  implicit val quotaSizeWrites: Writes[Quota[QuotaSize]] = Json.writes[Quota[QuotaSize]]
  implicit val mailboxPathWrites: Writes[MailboxPath] = Json.writes[MailboxPath]
  implicit val mailboxIdWrites: Writes[MailboxId] = value => JsString(value.serialize())
  implicit val sessionIdWrites: Writes[SessionId] = value => JsNumber(value.getValue)
  implicit val aclEntryKeyWrites: Writes[JavaMailboxACL.EntryKey] = value => JsString(value.serialize())
  implicit val aclRightsWrites: Writes[JavaMailboxACL.Rfc4314Rights] = value => JsString(value.serialize())
  implicit val aclDiffWrites: Writes[ACLDiff] = Json.writes[ACLDiff]
  implicit val messageIdWrites: Writes[MessageId] = value => JsString(value.serialize())
  implicit val messageUidWrites: Writes[MessageUid] = value => JsNumber(value.asLong())
  implicit val userFlagWrites: Writes[UserFlag] = value => JsString(value.value)
  implicit val flagWrites: Writes[Flags] = Json.writes[Flags]
  implicit val eventIdWrites: Writes[EventId] = value => JsString(value.getId.toString)

  implicit val messageMetaDataWrites: Writes[DTOs.MessageMetaData] = Json.writes[DTOs.MessageMetaData]
  implicit val updatedFlagsWrites: Writes[DTOs.UpdatedFlags] = Json.writes[DTOs.UpdatedFlags]

  implicit val systemFlagsReads: Reads[SystemFlag] = Reads.enumNameReads(SystemFlag)

  implicit val aclEntryKeyReads: Reads[JavaMailboxACL.EntryKey] = {
    case JsString(keyAsString) => JsSuccess(JavaMailboxACL.EntryKey.deserialize(keyAsString))
    case _ => JsError()
  }
  implicit val aclRightsReads: Reads[JavaMailboxACL.Rfc4314Rights] = {
    case JsString(rightsAsString) => JsSuccess(JavaMailboxACL.Rfc4314Rights.deserialize(rightsAsString))
    case _ => JsError()
  }
  implicit val mailboxIdReads: Reads[MailboxId] = {
    case JsString(serializedMailboxId) => JsSuccess(mailboxIdFactory.fromString(serializedMailboxId))
    case _ => JsError()
  }
  implicit val quotaCountReads: Reads[QuotaCount] = {
    case JsNumber(count) => JsSuccess(QuotaCount.count(count.toLong))
    case JsNull => JsSuccess(QuotaCount.unlimited())
    case _ => JsError()
  }
  implicit val quotaRootReads: Reads[QuotaRoot] = {
    case JsString(quotaRoot) => JsSuccess(QuotaRoot.quotaRoot(quotaRoot, Optional.empty[Domain]))
    case _ => JsError()
  }
  implicit val quotaScopeReads: Reads[JavaQuota.Scope] = {
    case JsString(value) => JsSuccess(JavaQuota.Scope.valueOf(value))
    case _ => JsError()
  }
  implicit val quotaSizeReads: Reads[QuotaSize] = {
    case JsNumber(size) => JsSuccess(QuotaSize.size(size.toLong))
    case JsNull => JsSuccess(QuotaSize.unlimited())
    case _ => JsError()
  }
  implicit val sessionIdReads: Reads[SessionId] = {
    case JsNumber(id) => JsSuccess(SessionId.of(id.longValue()))
    case _ => JsError()
  }
  implicit val userReads: Reads[User] = {
    case JsString(userAsString) => JsSuccess(User.fromUsername(userAsString))
    case _ => JsError()
  }
  implicit val messageIdReads: Reads[MessageId] = {
    case JsString(value) => JsSuccess(messageIdFactory.fromString(value))
    case _ => JsError()
  }
  implicit val messageUidReads: Reads[MessageUid] = {
    case JsNumber(value) => JsSuccess(MessageUid.of(value.toLong))
    case _ => JsError()
  }
  implicit val userFlagsReads: Reads[UserFlag] = {
    case JsString(x) => JsSuccess(UserFlag(x))
    case _ => JsError()
  }
  implicit val eventIdReads: Reads[EventId] = {
    case JsString(x) => JsSuccess(EventId.of(x))
    case _ => JsError()
  }

  implicit def scopeMapReads[V](implicit vr: Reads[V]): Reads[Map[JavaQuota.Scope, V]] =
    Reads.mapReads[JavaQuota.Scope, V] { str =>
      Json.fromJson[JavaQuota.Scope](JsString(str))
    }

  implicit def scopeMapWrite[V](implicit vr: Writes[V]): Writes[Map[JavaQuota.Scope, V]] =
    (m: Map[JavaQuota.Scope, V]) => {
      JsObject(m.map { case (k, v) => (k.toString, vr.writes(v)) }.toSeq)
    }


  implicit def scopeMapReadsACL[V](implicit vr: Reads[V]): Reads[Map[JavaMailboxACL.EntryKey, V]] =
    Reads.mapReads[JavaMailboxACL.EntryKey, V] { str =>
      Json.fromJson[JavaMailboxACL.EntryKey](JsString(str))
    }

  implicit def scopeMapWriteACL[V](implicit vr: Writes[V]): Writes[Map[JavaMailboxACL.EntryKey, V]] =
    (m: Map[JavaMailboxACL.EntryKey, V]) => {
      JsObject(m.map { case (k, v) => (k.toString, vr.writes(v)) }.toSeq)
    }

  implicit def scopeMessageUidMapReads[V](implicit vr: Reads[V]): Reads[Map[MessageUid, V]] =
    Reads.mapReads[MessageUid, V] { str =>
      JsSuccess(MessageUid.of(str.toLong))
    }

  implicit def scopeMessageUidMapWrite[V](implicit vr: Writes[V]): Writes[Map[MessageUid, V]] =
    (m: Map[MessageUid, V]) => {
      JsObject(m.map { case (k, v) => (String.valueOf(k.asLong()), vr.writes(v)) }.toSeq)
    }

  implicit val flagsReads: Reads[Flags] = Json.reads[Flags]
  implicit val aclDiffReads: Reads[ACLDiff] = Json.reads[ACLDiff]
  implicit val quotaCReads: Reads[DTOs.Quota[QuotaCount]] = Json.reads[DTOs.Quota[QuotaCount]]
  implicit val quotaSReads: Reads[DTOs.Quota[QuotaSize]] = Json.reads[DTOs.Quota[QuotaSize]]
  implicit val mailboxPathReads: Reads[DTOs.MailboxPath] = Json.reads[DTOs.MailboxPath]
  implicit val messageMetaDataReads: Reads[DTOs.MessageMetaData] = Json.reads[DTOs.MessageMetaData]
  implicit val updatedFlagsReads: Reads[DTOs.UpdatedFlags] = Json.reads[DTOs.UpdatedFlags]

  private class EventSerializerPrivateWrapper {
    implicit val eventOFormat: OFormat[Event] = derived.oformat()

    def toJson(event: Event): String = Json.toJson(event).toString()
    def fromJson(json: String): JsResult[Event] = Json.fromJson[Event](Json.parse(json))
  }

  private val eventSerializerPrivateWrapper = new EventSerializerPrivateWrapper()
  def toJson(event: JavaEvent): String = eventSerializerPrivateWrapper.toJson(ScalaConverter.toScala(event))
  def fromJson(json: String): JsResult[JavaEvent] = eventSerializerPrivateWrapper.fromJson(json)
    .map(event => event.toJava)
}

class EventSerializer @Inject() (mailboxIdFactory: MailboxId.Factory, messageIdFactory: MessageId.Factory) {
  private val jsonSerialize = new JsonSerialize(mailboxIdFactory, messageIdFactory)

  def toJson(event: JavaEvent): String = jsonSerialize.toJson(event)
  def fromJson(json: String): JsResult[JavaEvent] = jsonSerialize.fromJson(json)
}

