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

import java.util.Optional

import com.google.common.base.{MoreObjects, Objects}
import com.google.common.collect.{ImmutableBiMap, ImmutableList}
import com.ibm.icu.text.UnicodeSet
import javax.mail.Flags
import org.apache.commons.lang3.StringUtils

object Keyword {
  private val FLAG_NAME_MIN_LENGTH = 1
  private val FLAG_NAME_MAX_LENGTH = 255
  private val FLAG_NAME_PATTERN = new UnicodeSet("[[a-z][A-Z][0-9]]").add('$').add('_').add('-').freeze
  val DRAFT = Keyword.of("$Draft")
  val SEEN = Keyword.of("$Seen")
  val FLAGGED = Keyword.of("$Flagged")
  val ANSWERED = Keyword.of("$Answered")
  val DELETED = Keyword.of("$Deleted")
  val RECENT = Keyword.of("$Recent")
  val FORWARDED = Keyword.of("$Forwarded")
  val FLAG_VALUE = true
  private val NON_EXPOSED_IMAP_KEYWORDS = ImmutableList.of(Keyword.RECENT, Keyword.DELETED)
  private val IMAP_SYSTEM_FLAGS = ImmutableBiMap.builder[Flags.Flag, Keyword]
    .put(Flags.Flag.DRAFT, DRAFT)
    .put(Flags.Flag.SEEN, SEEN)
    .put(Flags.Flag.FLAGGED, FLAGGED)
    .put(Flags.Flag.ANSWERED, ANSWERED)
    .put(Flags.Flag.RECENT, RECENT)
    .put(Flags.Flag.DELETED, DELETED)
    .build

  private val VALIDATION_MESSAGE = """FlagName must not be null or empty, must have length form 1-255,
      | must not contain characters with hex from '\\u0000' to '\\u00019'
      | or {'(' ')' '{' ']' '%' '*' '\"' '\\'} """.stripMargin

  def parse(flagName: String): Either[String, Keyword] = {
    if (isValid(flagName)) Right(new Keyword(flagName))
    Left(VALIDATION_MESSAGE)
  }

  def of(flagName: String): Keyword = parse(flagName) match {
    case Left(errorMessage: String) => throw new IllegalArgumentException(errorMessage)
    case Right(keyword: Keyword) => keyword
  }

  def fromFlag(flag: Flags.Flag): Option[Keyword] = Some(IMAP_SYSTEM_FLAGS.get(flag))

  def isValid(flagName: String): Boolean = flagName match {
      case _ if StringUtils.isBlank(flagName) => false
      case _ if flagName.length < FLAG_NAME_MIN_LENGTH || flagName.length > FLAG_NAME_MAX_LENGTH => false
      case _ => FLAG_NAME_PATTERN.containsAll(flagName)
    }
}

class Keyword(val flagName: String) {
  def getFlagName(): String = flagName

  def isExposedImapKeyword(): Boolean = !Keyword.NON_EXPOSED_IMAP_KEYWORDS.contains(this)

  def isDraft(): Boolean = Keyword.DRAFT == this

  def asSystemFlag(): Optional[Flags.Flag] = Optional.ofNullable(Keyword.IMAP_SYSTEM_FLAGS.inverse.get(this))

  def asFlags(): Flags = asSystemFlag().map(Flag => new Flags(Flag)).orElse(new Flags(flagName))
}