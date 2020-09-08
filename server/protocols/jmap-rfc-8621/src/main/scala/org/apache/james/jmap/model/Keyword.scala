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
 * **************************************************************/
package org.apache.james.jmap.model

import com.ibm.icu.text.UnicodeSet
import javax.mail.Flags
import org.apache.commons.lang3.StringUtils

import scala.util.{Failure, Success, Try}

object Keyword {
  private val FLAG_NAME_MIN_LENGTH = 1
  private val FLAG_NAME_MAX_LENGTH = 255
  private val VALIDATION_MESSAGE: String = "FlagName must not be null or empty, must have length form 1-255," +
      "must not contain characters with hex from '\\u0000' to '\\u00019'" +
      " or {'(' ')' '{' ']' '%' '*' '\"' '\\'} "

  private val FLAG_NAME_PATTERN = new UnicodeSet("[[a-z][A-Z][0-9]$_-]").freeze
  val DRAFT = Keyword.of("$Draft").get
  val SEEN = Keyword.of("$Seen").get
  val FLAGGED = Keyword.of("$Flagged").get
  val ANSWERED = Keyword.of("$Answered").get
  val DELETED = Keyword.of("$Deleted").get
  val RECENT = Keyword.of("$Recent").get
  val FORWARDED = Keyword.of("$Forwarded").get
  val FLAG_VALUE: Boolean = true
  private val NON_EXPOSED_IMAP_KEYWORDS = List(Keyword.RECENT, Keyword.DELETED)
  private val IMAP_SYSTEM_FLAGS: Map[Flags.Flag, Keyword] =
    Map(
      Flags.Flag.DRAFT -> DRAFT,
      Flags.Flag.SEEN -> SEEN,
      Flags.Flag.FLAGGED -> FLAGGED,
      Flags.Flag.ANSWERED -> ANSWERED,
      Flags.Flag.RECENT -> RECENT,
      Flags.Flag.DELETED -> DELETED)

  def parse(flagName: String): Either[String, Keyword] = Either.cond(isValid(flagName), Keyword(flagName), VALIDATION_MESSAGE)

  def of(flagName: String): Try[Keyword] = parse(flagName) match {
    case Left(errorMessage: String) => Failure(throw new IllegalArgumentException(errorMessage))
    case Right(keyword: Keyword) => Success(keyword)
  }

  def fromFlag(flag: Flags.Flag): Option[Keyword] = IMAP_SYSTEM_FLAGS.get(flag)

  def isValid(flagName: String): Boolean = flagName match {
      case _ if StringUtils.isBlank(flagName) => false
      case _ if flagName.length < FLAG_NAME_MIN_LENGTH || flagName.length > FLAG_NAME_MAX_LENGTH => false
      case _ => FLAG_NAME_PATTERN.containsAll(flagName)
    }
}

final case class Keyword(flagName: String) {
  def getFlagName: String = flagName

  def isExposedImapKeyword: Boolean = !Keyword.NON_EXPOSED_IMAP_KEYWORDS.contains(this)

  def isDraft: Boolean = Keyword.DRAFT == this

  def asSystemFlag: Option[Flags.Flag] = Keyword.IMAP_SYSTEM_FLAGS
    .filter(entry => entry._2.equals(this))
    .keys
    .collectFirst(flag => flag)

  def asFlags: Flags = asSystemFlag.map(Flag => new Flags(Flag)).getOrElse(new Flags(flagName))
}