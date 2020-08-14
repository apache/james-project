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

import org.apache.james.core.Username
import org.apache.james.mailbox.model.MailboxACL.{EntryKey, Rfc4314Rights => JavaRfc4314Rights, Right => JavaRight}
import org.apache.james.mailbox.model.{MailboxACL => JavaMailboxACL}
import org.slf4j.{Logger, LoggerFactory}

import scala.jdk.CollectionConverters._

object Rfc4314Rights {
  def fromJava(rights: JavaRfc4314Rights) = Rfc4314Rights(rights.list().asScala.toSeq)

  def fromRights(seq: Seq[Right]): Rfc4314Rights = Rfc4314Rights(seq.map(_.right))
}

case class Rfc4314Rights(rights: Seq[JavaRight]) {
  val asJava: JavaRfc4314Rights = JavaRfc4314Rights.of(rights.asJava)

  def toRights: Seq[Right] = rights.flatMap(Right.forRight)
}

object MailboxACL {
  def fromJava(acl: JavaMailboxACL) = MailboxACL(acl.getEntries
    .asScala
    .view
    .mapValues(Rfc4314Rights.fromJava)
    .toMap)
}

case class MailboxACL(entries: Map[EntryKey, Rfc4314Rights]) {
  val asJava: JavaMailboxACL = {
    val map: Map[EntryKey, JavaRfc4314Rights] = entries.view
    .mapValues(seq => seq.asJava)
    .toMap

    new JavaMailboxACL(map.asJava)
  }
}

object Right {
  val UNSUPPORTED: Option[Boolean] = None

  val Administer = Right(JavaRight.Administer)
  val Expunge = Right(JavaRight.PerformExpunge)
  val Insert = Right(JavaRight.Insert)
  val Lookup = Right(JavaRight.Lookup)
  val Read = Right(JavaRight.Read)
  val Seen = Right(JavaRight.WriteSeenFlag)
  val DeleteMessages = Right(JavaRight.DeleteMessages)
  val Write = Right(JavaRight.Write)

  private val allRights = Seq(Administer, Expunge, Insert, Lookup, Read, Seen, DeleteMessages, Write)

  def forRight(right: JavaRight): Option[Right] = allRights.find(_.right.equals(right))

  def forChar(c: Char): Option[Right] = allRights.find(_.asCharacter == c)
}

final case class Right(right: JavaRight) {
  val asCharacter: Char = right.asCharacter

  val toMailboxRight: JavaRight = right
}

object Rights {
  private val LOGGER: Logger = LoggerFactory.getLogger(classOf[Rights])

  val EMPTY = Rights(Map())

  def of(username: Username, right: Right): Rights = of(username, Seq(right))

  def of(username: Username, rights: Seq[Right]): Rights = {
    require(rights.nonEmpty, "'rights' should not be empty")

    Rights(Map(username -> rights))
  }

  def fromACL(acl: MailboxACL): Rights = acl.entries
    .filter {
      case (entryKey, _) => isSupported(entryKey)
    }
    .map {
      case (entryKey, aclRights) => toRights(entryKey, aclRights)
    }
    .fold(EMPTY)(_ combine _)

  private def toRights(entryKey: EntryKey, aclRights: Rfc4314Rights): Rights = of(Username.of(entryKey.getName), aclRights.toRights)

  private def isSupported(key: EntryKey): Boolean = key match {
    case k if k.isNegative =>
      LOGGER.info("Negative keys are not supported")
      false
    case JavaMailboxACL.OWNER_KEY => false
    case k if k.getNameType ne JavaMailboxACL.NameType.user =>
      LOGGER.info("{} is not supported. Only 'user' is.", key.getNameType)
      false
    case _ => true
  }
}

sealed trait RightsApplicability
case object Applicable extends RightsApplicability
case object NotApplicable extends RightsApplicability
case object Unsupported extends RightsApplicability

case class Rights(rights: Map[Username, Seq[Right]]) {
  def removeEntriesFor(username: Username) = copy(rights = rights - username)

  def toMailboxAcl: MailboxACL = {
    val map: Map[EntryKey, Rfc4314Rights] = rights.view
      .mapValues(Rfc4314Rights.fromRights)
      .toMap
      .map {
        case (user, rfc4314Rights) => (EntryKey.createUserEntryKey(user), rfc4314Rights)
      }
    MailboxACL(map)
  }

  def append(username: Username, right: Right): Rights = append(username, Seq(right))

  def append(username: Username, rights: Seq[Right]): Rights = copy(rights = this.rights + (username -> rights))

  def combine(that: Rights): Rights = Rights(this.rights ++ that.rights)

  def mayReadItems(username: Username): RightsApplicability = containsRight(username, Right.Read)

  def mayAddItems(username: Username): RightsApplicability = containsRight(username, Right.Insert)

  def mayRemoveItems(username: Username): RightsApplicability = containsRight(username, Right.DeleteMessages)

  def mayCreateChild(username: Username): RightsApplicability = Unsupported

  def mayRename(username: Username): RightsApplicability = Unsupported

  def mayDelete(username: Username): RightsApplicability = Unsupported

  private def containsRight(username: Username, right: Right): RightsApplicability = {
    val contains = rights.get(username)
      .map(_.contains(right))
    contains match {
      case Some(true) => Applicable
      case Some(false) => NotApplicable
      case None => NotApplicable
    }
  }
}
