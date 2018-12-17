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
import java.util.Date

import javax.mail.{Flags => JavaMailFlags}
import org.apache.james.core.quota.QuotaValue
import org.apache.james.mailbox.acl.{ACLDiff => JavaACLDiff}
import org.apache.james.mailbox.model.{MailboxACL, MessageId, MailboxPath => JavaMailboxPath, MessageMetaData => JavaMessageMetaData,
  Quota => JavaQuota}
import org.apache.james.mailbox.{FlagsBuilder, MessageUid}

import scala.collection.JavaConverters._

object DTOs {

  object ACLDiff {
    def fromJava(javaACLDiff: JavaACLDiff): ACLDiff = ACLDiff(
      javaACLDiff.getOldACL.getEntries.asScala.toMap,
      javaACLDiff.getNewACL.getEntries.asScala.toMap)
  }

  object MailboxPath {
    def fromJava(javaMailboxPath: JavaMailboxPath): MailboxPath = MailboxPath(
      Option(javaMailboxPath.getNamespace),
      Option(javaMailboxPath.getUser),
      javaMailboxPath.getName)
  }

  object Quota {
    def toScala[T <: QuotaValue[T]](java: JavaQuota[T]): Quota[T] = Quota(
      used = java.getUsed,
      limit = java.getLimit,
      limits = java.getLimitByScope.asScala.toMap)
  }

  case class ACLDiff(oldACL: Map[MailboxACL.EntryKey, MailboxACL.Rfc4314Rights],
                     newACL: Map[MailboxACL.EntryKey, MailboxACL.Rfc4314Rights]) {
    def toJava: JavaACLDiff = new JavaACLDiff(new MailboxACL(oldACL.asJava), new MailboxACL(newACL.asJava))
  }

  case class MailboxPath(namespace: Option[String], user: Option[String], name: String) {
    def toJava: JavaMailboxPath = new JavaMailboxPath(namespace.orNull, user.orNull, name)
  }

  case class Quota[T <: QuotaValue[T]](used: T, limit: T, limits: Map[JavaQuota.Scope, T]) {
    def toJava: JavaQuota[T] =
      JavaQuota.builder[T]
        .used(used)
        .computedLimit(limit)
        .limitsByScope(limits.asJava)
        .build()
  }

  object MessageMetaData {
    def fromJava(javaMessageMetaData: JavaMessageMetaData): MessageMetaData = DTOs.MessageMetaData(
      javaMessageMetaData.getUid,
      javaMessageMetaData.getModSeq,
      javaMessageMetaData.getFlags,
      javaMessageMetaData.getSize,
      javaMessageMetaData.getInternalDate.toInstant,
      javaMessageMetaData.getMessageId)
  }

  case class MessageMetaData(uid: MessageUid, modSeq: Long, flags: JavaMailFlags, size: Long, internalDate: Instant, messageId: MessageId) {
    def toJava: JavaMessageMetaData = new JavaMessageMetaData(uid, modSeq, flags, size, Date.from(internalDate), messageId)
  }

  object Flags {
    val ANSWERED = "\\Answered"
    val DELETED = "\\Deleted"
    val DRAFT = "\\Draft"
    val FLAGGED = "\\Flagged"
    val RECENT = "\\Recent"
    val SEEN = "\\Seen"
    val ALL_SYSTEM_FLAGS = List(ANSWERED, DELETED, DRAFT, FLAGGED, RECENT, SEEN)

    def toJavaFlags(serializedFlags: Array[String]): JavaMailFlags = {
      serializedFlags
        .map(toJavaMailFlag)
        .foldLeft(new FlagsBuilder)((builder, flag) => builder.add(flag))
        .build()
    }

    def toJavaMailFlag(flag: String): JavaMailFlags = ALL_SYSTEM_FLAGS.contains(flag) match {
      case true => new FlagsBuilder().add(stringToSystemFlag(flag)).build()
      case false => new FlagsBuilder().add(flag).build()
    }

    def fromJavaFlags(flags: JavaMailFlags): Array[String] = {
      flags.getUserFlags ++ flags.getSystemFlags.map(flag => systemFlagToString(flag))
    }

    private def stringToSystemFlag(serializedFlag: String): JavaMailFlags.Flag = serializedFlag match {
      case ANSWERED => JavaMailFlags.Flag.ANSWERED
      case DELETED => JavaMailFlags.Flag.DELETED
      case DRAFT => JavaMailFlags.Flag.DRAFT
      case FLAGGED => JavaMailFlags.Flag.FLAGGED
      case RECENT => JavaMailFlags.Flag.RECENT
      case SEEN => JavaMailFlags.Flag.SEEN
      case _ => throw new IllegalArgumentException(serializedFlag + " is not a system flag")
    }

    private def systemFlagToString(flag: JavaMailFlags.Flag): String = flag match {
      case JavaMailFlags.Flag.ANSWERED => ANSWERED
      case JavaMailFlags.Flag.DELETED => DELETED
      case JavaMailFlags.Flag.DRAFT => DRAFT
      case JavaMailFlags.Flag.FLAGGED => FLAGGED
      case JavaMailFlags.Flag.RECENT => RECENT
      case JavaMailFlags.Flag.SEEN => SEEN
    }
  }
}
