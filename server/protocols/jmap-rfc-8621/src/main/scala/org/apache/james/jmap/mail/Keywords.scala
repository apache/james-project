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
package org.apache.james.jmap.mail

import jakarta.mail.Flags
import org.apache.james.mailbox.FlagsBuilder

import scala.util.{Failure, Success, Try}

trait ToKeyword {
  def toKeyword(value: String): Option[Keyword]
}

trait KeywordFilter extends IterableOnce[Keyword]

trait KeywordsValidator {
  def validate(keywords: Set[Keyword]): Try[Unit]
}

object ToKeyword {
  val STRICT: ToKeyword = (value: String) => Keyword.of(value) match {
    case Failure(_) => None
    case Success(value: Keyword) => Some(value)
  }

  val LENIENT: ToKeyword = (value: String) => Keyword.parse(value) match {
    case Left(_) => None
    case scala.Right(value: Keyword) => Some(value)
  }
}

object KeywordsValidator {
  val THROW_ON_IMAP_NON_EXPOSED_KEYWORDS: KeywordsValidator = (keywords: Set[Keyword]) => {
    val exposedKeywords = keywords.filter(_.isExposedImapKeyword)
    if (keywords.isEmpty) {
      Success((): Unit)
    } else if (exposedKeywords.isEmpty || exposedKeywords.size != keywords.size) {
      Failure(new IllegalArgumentException("Does not allow to update 'Deleted' or 'Recent' flag"))
    } else {
      Success((): Unit)
    }
  }

  val IGNORE_NON_EXPOSED_IMAP_KEYWORDS: KeywordsValidator = _ => Success((): Unit)
}

object KeywordFilter {
  val FILTER_IMAP_NON_EXPOSED_KEYWORDS: Keyword => Boolean = _.isExposedImapKeyword
  val KEEP_ALL: Keyword => Boolean = _ => true
}

object KeywordsFactory {
    val STRICT_KEYWORDS_FACTORY = new KeywordsFactory(KeywordsValidator.THROW_ON_IMAP_NON_EXPOSED_KEYWORDS, KeywordFilter.KEEP_ALL, ToKeyword.STRICT)
    val LENIENT_KEYWORDS_FACTORY = new KeywordsFactory(KeywordsValidator.IGNORE_NON_EXPOSED_IMAP_KEYWORDS, KeywordFilter.FILTER_IMAP_NON_EXPOSED_KEYWORDS, ToKeyword.LENIENT)
}

object Keywords {
  val DEFAULT_VALUE: Keywords = Keywords(Set.empty)
}

final case class Keywords(keywords: Set[Keyword]) {

  def asFlags: Flags = keywords.foldLeft(new FlagsBuilder)((flag: FlagsBuilder, keyword: Keyword) => flag.add(keyword.asFlags))
    .build()

  def asFlagsWithRecentAndDeletedFrom(originFlags: Flags): Flags = {
    val flags = asFlags
    if (originFlags.contains(Flags.Flag.DELETED)) {
      flags.add(Flags.Flag.DELETED)
    }
    if (originFlags.contains(Flags.Flag.RECENT)) {
      flags.add(Flags.Flag.RECENT)
    }
    flags
  }

  def asMap: Map[Keyword, Boolean] = keywords.map((_, true)).toMap

  def getKeywords: Set[Keyword] = keywords

  def ++(other: Keywords): Keywords = Keywords(keywords ++ other.keywords)

  def --(other: Keywords): Keywords = Keywords(keywords -- other.keywords)

  def contains(keyword: Keyword): Boolean = keywords.contains(keyword)
}

class KeywordsFactory(val validator: KeywordsValidator, val filter: Keyword => Boolean, val toKeyword: ToKeyword) {
  def fromSet(setKeywords: Set[Keyword]): Try[Keywords] = {
    validator.validate(setKeywords) match {
      case Success(_) => Success(Keywords(setKeywords.filter(filter)))
      case Failure(throwable: Throwable) => Failure(throwable)
    }
  }

  def from(keywords: Keyword*): Try[Keywords] = {
    fromSet(keywords.toSet)
  }

  def fromCollection(keywords: Set[String]): Try[Keywords] = fromSet(keywords.flatMap(toKeyword.toKeyword))

  def fromMap(mapKeywords: Map[String, Boolean]): Try[Keywords] = {
    if (mapKeywords.values.exists(!_)) {
      Failure(new IllegalArgumentException("Keyword must be true"))
    } else {
      fromCollection(mapKeywords.keySet)
    }
  }

  def fromFlags(flags: Flags): Try[Keywords] = {
    val fromUserFlags: Set[Option[Keyword]] = flags.getUserFlags.map[Option[Keyword]](toKeyword.toKeyword).toSet
    val fromSystemFlags: Set[Option[Keyword]] = flags.getSystemFlags.map[Option[Keyword]](Keyword.fromFlag).toSet

    fromSet((fromUserFlags ++ fromSystemFlags).flatten)
  }
}