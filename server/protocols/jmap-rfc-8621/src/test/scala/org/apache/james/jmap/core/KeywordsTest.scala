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

package org.apache.james.jmap.core

import jakarta.mail.Flags
import jakarta.mail.Flags.Flag
import nl.jqno.equalsverifier.EqualsVerifier
import org.apache.james.jmap.mail.KeywordsFactory.{LENIENT_KEYWORDS_FACTORY, STRICT_KEYWORDS_FACTORY}
import org.apache.james.jmap.mail.{Keyword, Keywords}
import org.apache.james.mailbox.FlagsBuilder
import org.assertj.core.api.Assertions.assertThat
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

object KeywordsTest {
  val ANY_KEYWORD = "AnyKeyword"
}

class KeywordsTest extends AnyWordSpec with Matchers {

  "Respect Bean Contract" in {
    EqualsVerifier.forClass(classOf[Keywords]).verify()
  }

  "fromMap should return empty when wrong keyword value" in {
    assertThat(LENIENT_KEYWORDS_FACTORY.fromMap(Map(KeywordsTest.ANY_KEYWORD -> false)).failed.get)
      .isInstanceOf(classOf[IllegalArgumentException])
  }

  "fromMap should return keywords fromMap String and boolean" in {
    val keywords = LENIENT_KEYWORDS_FACTORY.fromMap(Map(KeywordsTest.ANY_KEYWORD -> Keyword.FLAG_VALUE))
    assertThat(keywords.get.getKeywords).isEqualTo(Set(Keyword.of(KeywordsTest.ANY_KEYWORD).get))
  }

  "fromFlags should return keywords from allFlag" in {
    val keywords = LENIENT_KEYWORDS_FACTORY.fromFlags(new Flags(Flag.ANSWERED)).get
    assertThat(keywords.getKeywords).isEqualTo(Set(Keyword.ANSWERED))
  }

  "fromSet should return keywords from set of keywords" in {
    val keywords = LENIENT_KEYWORDS_FACTORY.fromSet(Set(Keyword.ANSWERED)).get
    assertThat(keywords.getKeywords).isEqualTo(Set(Keyword.ANSWERED))
  }

  "asFlags should build flags from keywords" in {
    assertThat(LENIENT_KEYWORDS_FACTORY.fromSet(Set(Keyword.ANSWERED)).get.asFlags).isEqualTo(new Flags(Flag.ANSWERED))
  }

  "asFlags with recent and deleted from should build flags from keywords and recent originFlags" in {
    val originFlags = FlagsBuilder.builder.add(Flag.RECENT, Flag.DRAFT).build
    val expectedFlags = FlagsBuilder.builder.add(Flag.ANSWERED, Flag.RECENT).build
    assertThat(LENIENT_KEYWORDS_FACTORY.fromSet(Set(Keyword.ANSWERED)).get.asFlagsWithRecentAndDeletedFrom(originFlags)).isEqualTo(expectedFlags)
  }

  "asFlags with recent and deleted from should build flags from keywords with deleted and recent originFlags" in {
    val originFlags = FlagsBuilder.builder.add(Flag.RECENT, Flag.DELETED, Flag.DRAFT).build
    val expectedFlags = FlagsBuilder.builder.add(Flag.ANSWERED, Flag.RECENT, Flag.DELETED).build
    assertThat(LENIENT_KEYWORDS_FACTORY.fromSet(Set(Keyword.ANSWERED)).get.asFlagsWithRecentAndDeletedFrom(originFlags)).isEqualTo(expectedFlags)
  }

  "asMap should return empty when empty map of string and boolean" in {
    assertThat(LENIENT_KEYWORDS_FACTORY.fromSet(Set.empty).isFailure).isFalse
  }

  "asMap should return map of string and boolean" in {
    val expectedMap = Map(Keyword.of("$Answered").get -> Keyword.FLAG_VALUE)
    assertThat(LENIENT_KEYWORDS_FACTORY.fromSet(Set(Keyword.ANSWERED)).get.asMap).isEqualTo(expectedMap)
  }

  "throwWhenUnsupportedKeyword should return failure when have unsupportedKeywords" in {
    assertThat(STRICT_KEYWORDS_FACTORY.fromSet(Set(Keyword.DRAFT, Keyword.DELETED)).isFailure).isTrue
  }

  "throwWhenUnsupportedKeyword should not throw when have draft" in {
    val keywords = STRICT_KEYWORDS_FACTORY.fromSet(Set(Keyword.ANSWERED, Keyword.DRAFT)).get
    assertThat(keywords.getKeywords).isEqualTo(Set(Keyword.ANSWERED, Keyword.DRAFT))
  }

  "filterUnsupported should filter" in {
    val keywords = LENIENT_KEYWORDS_FACTORY.fromSet(Set(Keyword.ANSWERED, Keyword.DELETED, Keyword.RECENT, Keyword.DRAFT)).get
    assertThat(keywords.getKeywords).isEqualTo(Set(Keyword.ANSWERED, Keyword.DRAFT))
  }

  "contains should return true when keywords contain keyword" in {
    val keywords = LENIENT_KEYWORDS_FACTORY.fromSet(Set(Keyword.SEEN)).get
    assertThat(keywords.contains(Keyword.SEEN)).isTrue
  }

  "contains should return false when keywords do not contain keyword" in {
    val keywords = LENIENT_KEYWORDS_FACTORY.fromSet(Set()).get
    assertThat(keywords.contains(Keyword.SEEN)).isFalse
  }

  "fromList should return keywords fromList of strings" in {
    val keywords = LENIENT_KEYWORDS_FACTORY.fromCollection(Set("$Answered", "$Flagged")).get
    assertThat(keywords.getKeywords).isEqualTo(Set(Keyword.ANSWERED, Keyword.FLAGGED))
  }

  "fromList should not throw on invalidKeyword for lenientFactory" in {
    assertThat(LENIENT_KEYWORDS_FACTORY.fromCollection(Set("in&valid")).get.keywords).isEqualTo(Keywords.DEFAULT_VALUE.keywords)
  }

  "fromMap should Not Throw On InvalidKeyword For LenientFactory" in {
    assertThat(LENIENT_KEYWORDS_FACTORY.fromMap(Map("in&valid" -> true)).get.keywords).isEqualTo(Keywords.DEFAULT_VALUE.keywords)
  }

  "fromFlags should Not Throw On InvalidKeyword For LenientFactory" in {
    assertThat(LENIENT_KEYWORDS_FACTORY.fromFlags(new Flags("in&valid")).get.keywords).isEqualTo(Keywords.DEFAULT_VALUE.keywords)
  }
}
