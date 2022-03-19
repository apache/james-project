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

package org.apache.james.jmap.draft.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import jakarta.mail.Flags;
import jakarta.mail.Flags.Flag;

import org.apache.james.mailbox.FlagsBuilder;
import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import nl.jqno.equalsverifier.EqualsVerifier;

public class KeywordsTest {
    public static final String ANY_KEYWORD = "AnyKeyword";

    @Test
    public void shouldRespectBeanContract() {
        EqualsVerifier.forClass(Keywords.class).verify();
    }

    @Test
    public void fromMapShouldThrowWhenWrongKeywordValue() {
        assertThatThrownBy(() -> Keywords.lenientFactory()
            .fromMap(ImmutableMap.of(ANY_KEYWORD, false)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void fromMapShouldReturnKeywordsFromMapStringAndBoolean() {
        Keywords keywords = Keywords.lenientFactory()
            .fromMap(ImmutableMap.of(ANY_KEYWORD, Keyword.FLAG_VALUE));

        assertThat(keywords.getKeywords())
            .containsOnly(Keyword.of(ANY_KEYWORD));
    }

    @Test
    public void fromFlagsShouldReturnKeywordsFromAllFlag() {
        Keywords keywords = Keywords.lenientFactory()
            .fromFlags(new Flags(Flags.Flag.ANSWERED));

        assertThat(keywords.getKeywords())
            .containsOnly(Keyword.ANSWERED);
    }

    @Test
    public void fromSetShouldReturnKeywordsFromSetOfKeywords() {
        Keywords keywords = Keywords.lenientFactory()
            .fromSet(ImmutableSet.of(Keyword.ANSWERED));

        assertThat(keywords.getKeywords())
            .containsOnly(Keyword.ANSWERED);
    }

    @Test
    public void asFlagsShouldBuildFlagsFromKeywords() {
        assertThat(Keywords.lenientFactory()
                .fromSet(ImmutableSet.of(Keyword.ANSWERED))
                .asFlags())
            .isEqualTo(new Flags(Flags.Flag.ANSWERED));
    }

    @Test
    public void asFlagsWithRecentAndDeletedFromShouldBuildFlagsFromKeywordsAndRecentOriginFlags() {
        Flags originFlags = FlagsBuilder.builder()
            .add(Flag.RECENT, Flag.DRAFT)
            .build();

        Flags expectedFlags = FlagsBuilder.builder()
            .add(Flag.ANSWERED, Flag.RECENT)
            .build();

        assertThat(Keywords.lenientFactory()
                .fromSet(ImmutableSet.of(Keyword.ANSWERED))
                .asFlagsWithRecentAndDeletedFrom(originFlags))
            .isEqualTo(expectedFlags);
    }

    @Test
    public void asFlagsWithRecentAndDeletedFromShouldBuildFlagsFromKeywordsWithDeletedAndRecentOriginFlags() {
        Flags originFlags = FlagsBuilder.builder()
            .add(Flag.RECENT, Flag.DELETED, Flag.DRAFT)
            .build();

        Flags expectedFlags = FlagsBuilder.builder()
            .add(Flag.ANSWERED, Flag.RECENT, Flag.DELETED)
            .build();

        assertThat(Keywords.lenientFactory()
                .fromSet(ImmutableSet.of(Keyword.ANSWERED))
                .asFlagsWithRecentAndDeletedFrom(originFlags))
            .isEqualTo(expectedFlags);
    }

    @Test
    public void asMapShouldReturnEmptyWhenEmptyMapOfStringAndBoolean() {
        assertThat(Keywords.lenientFactory()
                .fromSet(ImmutableSet.of())
                .asMap())
            .isEmpty();
    }

    @Test
    public void asMapShouldReturnMapOfStringAndBoolean() {
        Map<String, Boolean> expectedMap = ImmutableMap.of("$Answered", Keyword.FLAG_VALUE);
        assertThat(Keywords.lenientFactory()
                .fromSet(ImmutableSet.of(Keyword.ANSWERED))
                .asMap())
            .isEqualTo(expectedMap);
    }

    @Test
    public void throwWhenUnsupportedKeywordShouldThrowWhenHaveUnsupportedKeywords() {
        assertThatThrownBy(() ->
            Keywords.strictFactory()
                .fromSet(ImmutableSet.of(Keyword.DRAFT, Keyword.DELETED)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void throwWhenUnsupportedKeywordShouldNotThrowWhenHaveDraft() {
        Keywords keywords = Keywords.strictFactory()
            .fromSet(ImmutableSet.of(Keyword.ANSWERED, Keyword.DRAFT));

        assertThat(keywords.getKeywords())
            .containsOnly(Keyword.ANSWERED, Keyword.DRAFT);
    }

    @Test
    public void filterUnsupportedShouldFilter() {
        Keywords keywords = Keywords.lenientFactory()
            .fromSet(ImmutableSet.of(Keyword.ANSWERED, Keyword.DELETED, Keyword.RECENT, Keyword.DRAFT));

        assertThat(keywords.getKeywords())
            .containsOnly(Keyword.ANSWERED, Keyword.DRAFT);
    }

    @Test
    public void containsShouldReturnTrueWhenKeywordsContainKeyword() {
        Keywords keywords = Keywords.lenientFactory()
            .fromSet(ImmutableSet.of(Keyword.SEEN));

        assertThat(keywords.contains(Keyword.SEEN)).isTrue();
    }

    @Test
    public void containsShouldReturnFalseWhenKeywordsDoNotContainKeyword() {
        Keywords keywords = Keywords.lenientFactory()
            .fromSet(ImmutableSet.of());

        assertThat(keywords.contains(Keyword.SEEN)).isFalse();
    }

    @Test
    public void fromListShouldReturnKeywordsFromListOfStrings() {
        Keywords keywords = Keywords.lenientFactory()
            .fromCollection(ImmutableList.of("$Answered", "$Flagged"));

        assertThat(keywords.getKeywords())
            .containsOnly(Keyword.ANSWERED, Keyword.FLAGGED);
    }

    @Test
    public void fromListShouldNotThrowOnInvalidKeywordForLenientFactory() {
        assertThat(Keywords.lenientFactory()
            .fromCollection(ImmutableList.of("in&valid")))
            .isEqualTo(Keywords.DEFAULT_VALUE);
    }

    @Test
    public void fromMapShouldNotThrowOnInvalidKeywordForLenientFactory() {
        assertThat(Keywords.lenientFactory()
            .fromMap(ImmutableMap.of("in&valid", true)))
            .isEqualTo(Keywords.DEFAULT_VALUE);
    }

    @Test
    public void fromFlagsShouldNotThrowOnInvalidKeywordForLenientFactory() {
        assertThat(Keywords.lenientFactory()
            .fromFlags(new Flags("in&valid")))
            .isEqualTo(Keywords.DEFAULT_VALUE);
    }
}