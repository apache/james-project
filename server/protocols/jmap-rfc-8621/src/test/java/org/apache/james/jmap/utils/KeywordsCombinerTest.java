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

package org.apache.james.jmap.utils;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.jmap.model.Keyword;
import org.apache.james.jmap.model.Keywords;
import org.apache.james.util.CommutativityChecker;
import org.junit.Test;

import com.google.common.collect.ImmutableSet;

public class KeywordsCombinerTest {

    public static final Keywords.KeywordsFactory KEYWORDS_FACTORY = Keywords.lenientFactory();

    @Test
    public void applyShouldUnionSeenKeyword() {
        KeywordsCombiner keywordsCombiner = new KeywordsCombiner();

        assertThat(keywordsCombiner.apply(
            Keywords.DEFAULT_VALUE,
            KEYWORDS_FACTORY.from(Keyword.SEEN)))
            .isEqualTo(KEYWORDS_FACTORY.from(Keyword.SEEN));
    }

    @Test
    public void applyShouldUnionAnsweredKeyword() {
        KeywordsCombiner keywordsCombiner = new KeywordsCombiner();

        assertThat(keywordsCombiner.apply(
            Keywords.DEFAULT_VALUE,
            KEYWORDS_FACTORY.from(Keyword.ANSWERED)))
            .isEqualTo(KEYWORDS_FACTORY.from(Keyword.ANSWERED));
    }

    @Test
    public void applyShouldUnionFlaggedKeyword() {
        KeywordsCombiner keywordsCombiner = new KeywordsCombiner();

        assertThat(keywordsCombiner.apply(
            Keywords.DEFAULT_VALUE,
            KEYWORDS_FACTORY.from(Keyword.FLAGGED)))
            .isEqualTo(KEYWORDS_FACTORY.from(Keyword.FLAGGED));
    }

    @Test
    public void applyShouldIntersectDraftKeyword() {
        KeywordsCombiner keywordsCombiner = new KeywordsCombiner();

        assertThat(keywordsCombiner.apply(
            Keywords.DEFAULT_VALUE,
            KEYWORDS_FACTORY.from(Keyword.DRAFT)))
            .isEqualTo(Keywords.DEFAULT_VALUE);
    }

    @Test
    public void applyShouldUnionCustomKeyword() {
        KeywordsCombiner keywordsCombiner = new KeywordsCombiner();

        Keyword customKeyword = Keyword.of("$Any");
        assertThat(keywordsCombiner.apply(
            Keywords.DEFAULT_VALUE,
            KEYWORDS_FACTORY.from(customKeyword)))
            .isEqualTo(KEYWORDS_FACTORY.from(customKeyword));
    }

    @Test
    public void applyShouldAcceptEmptyAsAZeroValue() {
        KeywordsCombiner keywordsCombiner = new KeywordsCombiner();

        assertThat(keywordsCombiner.apply(
            Keywords.DEFAULT_VALUE,
            Keywords.DEFAULT_VALUE))
            .isEqualTo(Keywords.DEFAULT_VALUE);
    }

    @Test
    public void applyShouldUnionDifferentFlags() {
        KeywordsCombiner keywordsCombiner = new KeywordsCombiner();

        assertThat(keywordsCombiner.apply(
            KEYWORDS_FACTORY.from(Keyword.FLAGGED),
            KEYWORDS_FACTORY.from(Keyword.ANSWERED)))
            .isEqualTo(KEYWORDS_FACTORY.from(Keyword.FLAGGED, Keyword.ANSWERED));
    }

    @Test
    public void keywordsCombinerShouldBeCommutative() {
        Keywords allKeyword = KEYWORDS_FACTORY.from(Keyword.ANSWERED,
            Keyword.DELETED,
            Keyword.DRAFT,
            Keyword.FLAGGED,
            Keyword.SEEN,
            Keyword.of("$Forwarded"),
            Keyword.of("$Any"));

        ImmutableSet<Keywords> values = ImmutableSet.of(
            KEYWORDS_FACTORY.from(Keyword.ANSWERED),
            KEYWORDS_FACTORY.from(Keyword.DELETED),
            KEYWORDS_FACTORY.from(Keyword.DRAFT),
            KEYWORDS_FACTORY.from(Keyword.FLAGGED),
            KEYWORDS_FACTORY.from(Keyword.SEEN),
            KEYWORDS_FACTORY.from(),
            KEYWORDS_FACTORY.from(Keyword.of("$Forwarded")),
            KEYWORDS_FACTORY.from(Keyword.of("$Any")),
            allKeyword);

        assertThat(
            new CommutativityChecker<>(values, new KeywordsCombiner())
                .findNonCommutativeInput())
            .isEmpty();
    }
}