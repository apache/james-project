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
  val Post = Right(JavaRight.Post)
  val DeleteMailbox = Right(JavaRight.DeleteMailbox)

  private val allRights = Seq(Administer, Expunge, Insert, Lookup, Read, Seen, DeleteMessages, Write, Post, DeleteMailbox)

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

  def of(entryKey: EntryKey, right: Right): Rights = of(entryKey, Seq(right))

  def of(entryKey: EntryKey, rights: Seq[Right]): Rights = {
    require(rights.nonEmpty, "'rights' should not be empty")

    Rights(Map(entryKey -> rights))
  }

  def fromACL(acl: MailboxACL): Rights = acl.entries
    .filter {
      case (entryKey, _) => isSupported(entryKey)
    }
    .map {
      case (entryKey, aclRights) => toRights(entryKey, aclRights)
    }
    .fold(EMPTY)(_ combine _)

  private def toRights(entryKey: EntryKey, aclRights: Rfc4314Rights): Rights = of(entryKey, aclRights.toRights)

  private def isSupported(key: EntryKey): Boolean = key match {
    case k if k.isNegative =>
      LOGGER.info("Negative keys are not supported")
      false
    case k if k.getNameType == JavaMailboxACL.NameType.special && k.getName != JavaMailboxACL.SpecialName.anyone.name() =>
      if (!k.getName.equals(JavaMailboxACL.SpecialName.owner.name())) {
        LOGGER.info("Special name {} is not supported. Only 'anyone' is.", key.getName)
      }
      false
    case k if k.getNameType == JavaMailboxACL.NameType.group =>
      LOGGER.info("Name type {} is not supported. Only 'user' and 'special.anyone' are.", key.getNameType)
      false
    case _ => true
  }
}

sealed trait RightsApplicability
case object Applicable extends RightsApplicability
case object NotApplicable extends RightsApplicability
case object Unsupported extends RightsApplicability

case class Rights(rights: Map[EntryKey, Seq[Right]]) {
  def removeEntriesFor(entryKey: EntryKey) = copy(rights = rights - entryKey)

  def toMailboxAcl: MailboxACL = {
    val map: Map[EntryKey, Rfc4314Rights] = rights.view
      .mapValues(Rfc4314Rights.fromRights)
      .toMap
      .map {
        case (entryKey, rfc4314Rights) => (entryKey, rfc4314Rights)
      }
    MailboxACL(map)
  }

  def append(entryKey: EntryKey, right: Right): Rights = append(entryKey, Seq(right))

  def append(entryKey: EntryKey, rights: Seq[Right]): Rights = copy(rights = this.rights + (entryKey -> rights))

  def combine(that: Rights): Rights = Rights(this.rights ++ that.rights)

  def mayReadItems(entryKey: EntryKey): RightsApplicability = containsRight(entryKey, Right.Read)

  def mayAddItems(entryKey: EntryKey): RightsApplicability = containsRight(entryKey, Right.Insert)

  def mayRemoveItems(entryKey: EntryKey): RightsApplicability = containsRight(entryKey, Right.DeleteMessages)

  def mayCreateChild(entryKey: EntryKey): RightsApplicability = Unsupported

  def mayRename(entryKey: EntryKey): RightsApplicability = Unsupported

  def mayDelete(entryKey: EntryKey): RightsApplicability = Unsupported

  private def containsRight(entryKey: EntryKey, right: Right): RightsApplicability = {
    val contains = rights.get(entryKey)
      .map(_.contains(right))
    contains match {
      case Some(true) => Applicable
      case Some(false) => NotApplicable
      case None => NotApplicable
    }
  }
}
