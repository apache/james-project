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

package org.apache.james.jmap.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import javax.mail.Flags;
import javax.mail.Flags.Flag;

import org.apache.james.mailbox.FlagsBuilder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import nl.jqno.equalsverifier.EqualsVerifier;

public class KeywordsTest {
    public static final String ANY_KEYWORD = "AnyKeyword";
    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void shouldRespectBeanContract() {
        EqualsVerifier.forClass(Keywords.class).verify();
    }

    @Test
    public void fromMapShouldThrowWhenWrongKeywordValue() throws Exception {
        expectedException.expect(IllegalArgumentException.class);

        Keywords.factory()
            .fromMap(ImmutableMap.of(ANY_KEYWORD, false));
    }

    @Test
    public void fromMapShouldReturnKeywordsFromMapStringAndBoolean() throws Exception {
        Keywords keywords = Keywords.factory()
            .fromMap(ImmutableMap.of(ANY_KEYWORD, Keyword.FLAG_VALUE));

        assertThat(keywords.getKeywords())
            .containsOnly(new Keyword(ANY_KEYWORD));
    }

    @Test
    public void fromFlagsShouldReturnKeywordsFromAllFlag() throws Exception {
        Keywords keywords = Keywords.factory()
            .fromFlags(new Flags(Flags.Flag.ANSWERED));

        assertThat(keywords.getKeywords())
            .containsOnly(Keyword.ANSWERED);
    }

    @Test
    public void fromSetShouldReturnKeywordsFromSetOfKeywords() throws Exception {
        Keywords keywords = Keywords.factory()
            .fromSet(ImmutableSet.of(Keyword.ANSWERED));

        assertThat(keywords.getKeywords())
            .containsOnly(Keyword.ANSWERED);
    }

    @Test
    public void asFlagsShouldBuildFlagsFromKeywords() throws Exception {
        assertThat(Keywords.factory()
                .fromSet(ImmutableSet.of(Keyword.ANSWERED))
                .asFlags())
            .isEqualTo(new Flags(Flags.Flag.ANSWERED));
    }

    @Test
    public void asFlagsWithRecentAndDeletedFromShouldBuildFlagsFromKeywordsAndRecentOriginFlags() throws Exception {
        Flags originFlags = FlagsBuilder.builder()
            .add(Flag.RECENT, Flag.DRAFT)
            .build();

        Flags expectedFlags = FlagsBuilder.builder()
            .add(Flag.ANSWERED, Flag.RECENT)
            .build();

        assertThat(Keywords.factory()
                .fromSet(ImmutableSet.of(Keyword.ANSWERED))
                .asFlagsWithRecentAndDeletedFrom(originFlags))
            .isEqualTo(expectedFlags);
    }

    @Test
    public void asFlagsWithRecentAndDeletedFromShouldBuildFlagsFromKeywordsWithDeletedAndRecentOriginFlags() throws Exception {
        Flags originFlags = FlagsBuilder.builder()
            .add(Flag.RECENT, Flag.DELETED, Flag.DRAFT)
            .build();

        Flags expectedFlags = FlagsBuilder.builder()
            .add(Flag.ANSWERED, Flag.RECENT, Flag.DELETED)
            .build();

        assertThat(Keywords.factory()
                .fromSet(ImmutableSet.of(Keyword.ANSWERED))
                .asFlagsWithRecentAndDeletedFrom(originFlags))
            .isEqualTo(expectedFlags);
    }

    @Test
    public void asMapShouldReturnEmptyWhenEmptyMapOfStringAndBoolean() throws Exception {
        assertThat(Keywords.factory()
                .fromSet(ImmutableSet.of())
                .asMap())
            .isEmpty();;
    }

    @Test
    public void asMapShouldReturnMapOfStringAndBoolean() throws Exception {
        Map<String, Boolean> expectedMap = ImmutableMap.of("$Answered", Keyword.FLAG_VALUE);
        assertThat(Keywords.factory()
                .fromSet(ImmutableSet.of(Keyword.ANSWERED))
                .asMap())
            .isEqualTo(expectedMap);
    }

    @Test
    public void throwWhenUnsupportedKeywordShouldThrowWhenHaveUnsupportedKeywords() throws Exception {
        expectedException.expect(IllegalArgumentException.class);

        Keywords.factory()
            .throwOnImapNonExposedKeywords()
            .fromSet(ImmutableSet.of(Keyword.DRAFT, Keyword.DELETED));
    }

    @Test
    public void throwWhenUnsupportedKeywordShouldNotThrowWhenHaveDraft() throws Exception {
        Keywords keywords = Keywords.factory()
            .throwOnImapNonExposedKeywords()
            .fromSet(ImmutableSet.of(Keyword.ANSWERED, Keyword.DRAFT));

        assertThat(keywords.getKeywords())
            .containsOnly(Keyword.ANSWERED, Keyword.DRAFT);
    }

    @Test
    public void filterUnsupportedShouldFilter() throws Exception {
        Keywords keywords = Keywords.factory()
            .filterImapNonExposedKeywords()
            .fromSet(ImmutableSet.of(Keyword.ANSWERED, Keyword.DELETED, Keyword.RECENT, Keyword.DRAFT));

        assertThat(keywords.getKeywords())
            .containsOnly(Keyword.ANSWERED, Keyword.DRAFT);
    }

    @Test
    public void containsShouldReturnTrueWhenKeywordsContainKeyword() {
        Keywords keywords = Keywords.factory()
            .fromSet(ImmutableSet.of(Keyword.SEEN));

        assertThat(keywords.contains(Keyword.SEEN)).isTrue();
    }

    @Test
    public void containsShouldReturnFalseWhenKeywordsDoNotContainKeyword() {
        Keywords keywords = Keywords.factory()
            .fromSet(ImmutableSet.of());

        assertThat(keywords.contains(Keyword.SEEN)).isFalse();
    }

    @Test
    public void fromListShouldReturnKeywordsFromListOfStrings() throws Exception {
        Keywords keywords = Keywords.factory()
            .fromList(ImmutableList.of("$Answered", "$Flagged"));

        assertThat(keywords.getKeywords())
            .containsOnly(Keyword.ANSWERED, Keyword.FLAGGED);
    }
}