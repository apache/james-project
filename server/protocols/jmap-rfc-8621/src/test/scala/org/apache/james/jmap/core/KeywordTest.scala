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
import nl.jqno.equalsverifier.EqualsVerifier
import org.apache.commons.lang3.StringUtils
import org.apache.james.jmap.mail.Keyword
import org.assertj.core.api.Assertions.{assertThat, assertThatCode}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.util.Failure

object KeywordTest {
  private val FLAG_NAME_MAX_LENGTH = 255
  private val ANY_KEYWORD = "AnyKeyword"
}

class KeywordTest extends AnyWordSpec with Matchers {

  "respect bean contract" in {
    EqualsVerifier.forClass(classOf[Keyword]).withNonnullFields("flagName").verify()
  }

  "throw when flagName length less than minlength" in {
    assertThat(Keyword.of("")).isInstanceOf(classOf[Failure[IllegalArgumentException]])
  }

  "throw when flagName lengthMore than maxlength" in {
    val keywordTooLong: String = StringUtils.repeat("a", KeywordTest.FLAG_NAME_MAX_LENGTH + 1)
    assertThat(Keyword.of(keywordTooLong)).isInstanceOf(classOf[Failure[IllegalArgumentException]])
  }

  "createNewOne when flagName length equals maxlength" in {
    val maxlengthFlagName = StringUtils.repeat("a", KeywordTest.FLAG_NAME_MAX_LENGTH)
    val keyword = Keyword.of(maxlengthFlagName).get
    assertThat(keyword.getFlagName).hasSameSizeAs(maxlengthFlagName)
  }

  "createNewOne when flagName length equals minlength" in {
    val minlengthFlagName = "a"
    val keyword = Keyword.of(minlengthFlagName).get
    assertThat(keyword.getFlagName).hasSameSizeAs(minlengthFlagName)
  }

  "throw when flagName contains percentageCharacter" in {
    assertThat(Keyword.of("a%")).isInstanceOf(classOf[Failure[IllegalArgumentException]])
  }

  "throw when flagName contains leftBracket" in {
    assertThat(Keyword.of("a[")).isInstanceOf(classOf[Failure[IllegalArgumentException]])
  }

  "throw when flagName contains rightBracket" in {
    assertThat(Keyword.of("a]")).isInstanceOf(classOf[Failure[IllegalArgumentException]])
  }

  "throw when flagName contains leftBrace" in {
    assertThat(Keyword.of("a{")).isInstanceOf(classOf[Failure[IllegalArgumentException]])
  }

  "throw when flagName contains slash" in {
    assertThat(Keyword.of("a\\")).isInstanceOf(classOf[Failure[IllegalArgumentException]])
  }

  "throw when flagName containsStar" in {
    assertThat(Keyword.of("a*")).isInstanceOf(classOf[Failure[IllegalArgumentException]])
  }

  "throw when flagName containsQuote" in {
    assertThat(Keyword.of("a\"")).isInstanceOf(classOf[Failure[IllegalArgumentException]])
  }

  "throw when flagName contains openingParenthesis" in {
    assertThat(Keyword.of("a(")).isInstanceOf(classOf[Failure[IllegalArgumentException]])
  }

  "throw when flagName contains closingParenthesis" in {
    assertThat(Keyword.of("a)")).isInstanceOf(classOf[Failure[IllegalArgumentException]])
  }

  "throw when flagName contains space character" in {
    assertThat(Keyword.of("a b")).isInstanceOf(classOf[Failure[IllegalArgumentException]])
  }

  "isNotNonExposedImapKeyword should return false when deleted" in {
    assertThat(Keyword.DELETED.isExposedImapKeyword).isFalse
  }

  "isNotNonExposedImapKeyword should return false when recent" in {
    assertThat(Keyword.RECENT.isExposedImapKeyword).isFalse
  }

  "isNotNonExposedImapKeyword should return true when other systemFlag" in {
    assertThat(Keyword.DRAFT.isExposedImapKeyword).isTrue
  }

  "isNotNonExposedImapKeyword should return true when any userFlag" in {
    val keyword = Keyword.of(KeywordTest.ANY_KEYWORD).get
    assertThat(keyword.isExposedImapKeyword).isTrue
  }

  "asSystemFlag should return systemFlag" in {
    assertThat(Keyword.of("$Draft").get.asSystemFlag).isEqualTo(Some(Flags.Flag.DRAFT))
  }

  "asSystemFlag should return empty when non systemFlag" in {
    assertThat(Keyword.of(KeywordTest.ANY_KEYWORD).get.asSystemFlag.nonEmpty).isFalse
  }

  "asFlags should return flags when systemFlag" in {
    assertThat(Keyword.DELETED.asFlags).isEqualTo(new Flags(Flags.Flag.DELETED))
  }

  "asFlags should return flags when userFlag" in {
    val keyword = Keyword.of(KeywordTest.ANY_KEYWORD).get
    assertThat(keyword.asFlags).isEqualTo(new Flags(KeywordTest.ANY_KEYWORD))
  }

  "asFlags should return flags when userFlag contains underscore" in {
    val userFlag = "$has_cal"
    val keyword = Keyword.of(userFlag).get
    assertThat(keyword.asFlags).isEqualTo(new Flags(userFlag))
  }

  "hyphenMinus should be allowed inKeyword" in {
    val userFlag = "aa-bb"
    assertThatCode(() => Keyword.of(userFlag)).doesNotThrowAnyException()
  }
}