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

import org.apache.james.jmap.model.KeywordsFactory.LENIENT_KEYWORDS_FACTORY
import org.apache.james.util.CommutativityChecker
import org.assertj.core.api.Assertions.assertThat
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scala.jdk.CollectionConverters._

class KeywordsCombinerTest extends AnyWordSpec with Matchers {
  "apply should union seen keyword" in {
      val keywordsCombiner = KeywordsCombiner()
    assertThat(keywordsCombiner.apply(Keywords.DEFAULT_VALUE, LENIENT_KEYWORDS_FACTORY.from(Keyword.SEEN).get))
      .isEqualTo(LENIENT_KEYWORDS_FACTORY.from(Keyword.SEEN).get)
  }

  "apply should union answered keyword" in {
    val keywordsCombiner = KeywordsCombiner()
    assertThat(keywordsCombiner.apply(Keywords.DEFAULT_VALUE, LENIENT_KEYWORDS_FACTORY.from(Keyword.ANSWERED).get))
      .isEqualTo(LENIENT_KEYWORDS_FACTORY.from(Keyword.ANSWERED).get)
  }

  "apply should union flagged keyword" in {
    val keywordsCombiner = KeywordsCombiner()
    assertThat(keywordsCombiner.apply(Keywords.DEFAULT_VALUE, LENIENT_KEYWORDS_FACTORY.from(Keyword.FLAGGED).get))
      .isEqualTo(LENIENT_KEYWORDS_FACTORY.from(Keyword.FLAGGED).get)
  }

  "apply should intersect draft keyword" in {
    val keywordsCombiner = KeywordsCombiner()
    assertThat(keywordsCombiner.apply(Keywords.DEFAULT_VALUE, LENIENT_KEYWORDS_FACTORY.from(Keyword.DRAFT).get))
      .isEqualTo(Keywords.DEFAULT_VALUE)
  }

  "apply should union custom keyword" in {
    val keywordsCombiner = KeywordsCombiner()
    val customKeyword = Keyword.of("$Any")
    assertThat(keywordsCombiner.apply(Keywords.DEFAULT_VALUE, LENIENT_KEYWORDS_FACTORY.from(customKeyword.get).get))
      .isEqualTo(LENIENT_KEYWORDS_FACTORY.from(customKeyword.get).get)
  }

  "apply should accept empty as a zeroValue" in {
    val keywordsCombiner = KeywordsCombiner()
    assertThat(keywordsCombiner.apply(Keywords.DEFAULT_VALUE, Keywords.DEFAULT_VALUE))
      .isEqualTo(Keywords.DEFAULT_VALUE)
  }

  "apply should union different flags" in {
    val keywordsCombiner = KeywordsCombiner()
    val keywords: Keywords = keywordsCombiner.apply(LENIENT_KEYWORDS_FACTORY.from(Keyword.FLAGGED).get,
      LENIENT_KEYWORDS_FACTORY.from(Keyword.ANSWERED).get)

    assertThat(keywords)
      .isEqualTo(LENIENT_KEYWORDS_FACTORY.from(Keyword.FLAGGED, Keyword.ANSWERED).get)
  }

  "keywords combiner should be commutative" in {
    val allKeyword = LENIENT_KEYWORDS_FACTORY.from(
      Keyword.ANSWERED,
      Keyword.DELETED,
      Keyword.DRAFT,
      Keyword.FLAGGED,
      Keyword.SEEN,
      Keyword.of("$Forwarded").get,
      Keyword.of("$Any").get).get

    val values:Set[Keywords] = Set(
      LENIENT_KEYWORDS_FACTORY.from(Keyword.ANSWERED).get,
      LENIENT_KEYWORDS_FACTORY.from(Keyword.DELETED).get,
      LENIENT_KEYWORDS_FACTORY.from(Keyword.DRAFT).get,
      LENIENT_KEYWORDS_FACTORY.from(Keyword.FLAGGED).get,
      LENIENT_KEYWORDS_FACTORY.from(Keyword.SEEN).get,
      Keywords.DEFAULT_VALUE,
      LENIENT_KEYWORDS_FACTORY.from(Keyword.of("$Forwarded").get).get,
      LENIENT_KEYWORDS_FACTORY.from(Keyword.of("$Any").get).get,
      allKeyword)

    assertThat(new CommutativityChecker(values.asJava, KeywordsCombiner()).findNonCommutativeInput)
      .isEmpty()
  }
}