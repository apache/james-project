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
import java.util.Optional;
import javax.mail.Flags;
import javax.mail.Flags.Flag;

import org.apache.james.mailbox.FlagsBuilder;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

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
    public void fromOldKeywordsShouldReturnKeywordsFromIsAnswered() throws Exception {
        Optional<Boolean> isAnswered = Optional.of(true);
        Optional<Boolean> isUnread = Optional.empty();
        Optional<Boolean> isFlagged = Optional.empty();
        Optional<Boolean> isDraft = Optional.empty();
        Keywords keywords = Keywords.factory()
            .fromOldKeyword(new OldKeyword(isUnread, isFlagged, isAnswered, isDraft));

        assertThat(keywords.getKeywords())
            .containsOnly(Keyword.ANSWERED);
    }

    @Test
    public void fromOldKeywordsShouldReturnKeywordsFromIsDraft() throws Exception {
        Optional<Boolean> isAnswered = Optional.empty();
        Optional<Boolean> isUnread = Optional.empty();
        Optional<Boolean> isFlagged = Optional.empty();
        Optional<Boolean> isDraft = Optional.of(true);
        Keywords keywords = Keywords.factory()
            .fromOldKeyword(new OldKeyword(isUnread, isFlagged, isAnswered, isDraft));

        assertThat(keywords.getKeywords())
            .containsOnly(Keyword.DRAFT);
    }

    @Test
    public void fromOldKeywordsShouldReturnKeywordsFromIsFlagged() throws Exception {
        Optional<Boolean> isAnswered = Optional.empty();
        Optional<Boolean> isUnread = Optional.empty();
        Optional<Boolean> isFlagged = Optional.of(true);
        Optional<Boolean> isDraft = Optional.empty();
        Keywords keywords = Keywords.factory()
            .fromOldKeyword(new OldKeyword(isUnread, isFlagged, isAnswered, isDraft));

        assertThat(keywords.getKeywords())
            .containsOnly(Keyword.FLAGGED);
    }

    @Test
    public void fromOldKeywordsShouldReturnKeywordsFromIsUnread() throws Exception {
        Optional<Boolean> isAnswered = Optional.empty();
        Optional<Boolean> isUnread = Optional.of(false);
        Optional<Boolean> isFlagged = Optional.empty();
        Optional<Boolean> isDraft = Optional.empty();
        Keywords keywords = Keywords.factory()
            .fromOldKeyword(new OldKeyword(isUnread, isFlagged, isAnswered, isDraft));

        assertThat(keywords.getKeywords())
            .containsOnly(Keyword.SEEN);
    }

    @Test
    public void fromOldKeywordsShouldReturnKeywordsFromIsFlag() throws Exception {
        Optional<Boolean> isAnswered = Optional.of(true);
        Optional<Boolean> isUnread = Optional.of(true);
        Optional<Boolean> isFlagged = Optional.of(true);
        Optional<Boolean> isDraft = Optional.of(true);
        Keywords keywords = Keywords.factory()
            .fromOldKeyword(new OldKeyword(isUnread, isFlagged, isAnswered, isDraft));

        assertThat(keywords.getKeywords())
            .containsOnly(Keyword.ANSWERED, Keyword.DRAFT, Keyword.FLAGGED);
    }

    @Test
    public void fromMapOrOldKeywordShouldReturnEmptyWhenBothAreEmpty() throws Exception {
        assertThat(Keywords.factory()
            .fromMapOrOldKeyword(Optional.empty(), Optional.empty()))
            .isEmpty();
    }

    @Test
    public void fromMapOrOldKeywordShouldReturnKeywordsWhenMap() throws Exception {
        assertThat(Keywords.factory()
            .fromMapOrOldKeyword(Optional.of(ImmutableMap.of()), Optional.empty()))
            .isPresent();
    }

    @Test
    public void fromMapOrOldKeywordShouldReturnKeywordsWhenOldKeyword() throws Exception {
        Optional<Boolean> isAnswered = Optional.of(true);
        Optional<Boolean> isUnread = Optional.of(true);
        Optional<Boolean> isFlagged = Optional.of(true);
        Optional<Boolean> isDraft = Optional.of(true);

        OldKeyword oldKeyword = new OldKeyword(isUnread, isFlagged, isAnswered, isDraft);

        assertThat(Keywords.factory()
            .fromMapOrOldKeyword(Optional.empty(), Optional.of(oldKeyword)))
            .isPresent();
    }

    @Test
    public void fromMapOrOldKeywordShouldThrownWhenBothMapAndOldKeyword() throws Exception {
        expectedException.expect(IllegalArgumentException.class);
        Optional<Boolean> isAnswered = Optional.of(true);
        Optional<Boolean> isUnread = Optional.of(true);
        Optional<Boolean> isFlagged = Optional.of(true);
        Optional<Boolean> isDraft = Optional.of(true);

        OldKeyword oldKeyword = new OldKeyword(isUnread, isFlagged, isAnswered, isDraft);

        Keywords.factory()
            .fromMapOrOldKeyword(Optional.of(ImmutableMap.of()), Optional.of(oldKeyword));
    }

    @Test
    public void fromMapOrOldKeywordShouldReturnKeywordsFromMap() throws Exception {
        ImmutableMap<String, Boolean> mapKeywords = ImmutableMap.of(ANY_KEYWORD, Keyword.FLAG_VALUE);

        assertThat(Keywords.factory()
            .fromMapOrOldKeyword(Optional.of(mapKeywords), Optional.empty())
            .get()
            .getKeywords())
            .containsOnly(new Keyword(ANY_KEYWORD));
    }

    @Test
    public void fromMapOrOldKeywordShouldReturnKeywordsFromOldKeyword() throws Exception {
        Optional<Boolean> isAnswered = Optional.of(true);
        Optional<Boolean> isUnread = Optional.of(true);
        Optional<Boolean> isFlagged = Optional.of(true);
        Optional<Boolean> isDraft = Optional.of(true);

        OldKeyword oldKeyword = new OldKeyword(isUnread, isFlagged, isAnswered, isDraft);

        Keywords keywords = Keywords.factory()
            .fromMapOrOldKeyword(Optional.empty(), Optional.of(oldKeyword))
            .get();
        assertThat(keywords.getKeywords())
            .containsOnly(Keyword.ANSWERED, Keyword.DRAFT, Keyword.FLAGGED);
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