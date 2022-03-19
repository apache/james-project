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

import jakarta.mail.Flags;

import org.apache.james.mailbox.FlagsBuilder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.google.common.collect.ImmutableMap;

public class UpdateMessagePatchTest {
    private static final String FORWARDED = "forwarded";
    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void unsetUpdatePatchShouldBeValid() {
        UpdateMessagePatch emptyPatch = UpdateMessagePatch.builder().build();
        assertThat(emptyPatch.isValid()).isTrue();
    }

    @Test
    public void builderShouldSetUnreadFalseWhenBuiltWithIsUnreadFalse() {
        UpdateMessagePatch.builder().isUnread(false).build();
    }

    @Test
    public void applyToStateShouldNotResetSystemFlagsWhenUsingOldKeywords() {
        UpdateMessagePatch testee = UpdateMessagePatch.builder()
            .isAnswered(true)
            .build();

        Flags isSeen = new Flags(Flags.Flag.SEEN);
        assertThat(testee.applyToState(isSeen).getSystemFlags())
            .containsExactly(Flags.Flag.ANSWERED, Flags.Flag.SEEN);
    }

    @Test
    public void applyToStateShouldNotModifySpecifiedOldKeywordsWhenAlreadySet() {
        UpdateMessagePatch testee = UpdateMessagePatch.builder()
            .isAnswered(true)
            .build();

        Flags isSeen = new Flags(Flags.Flag.ANSWERED);
        assertThat(testee.applyToState(isSeen).getSystemFlags())
            .containsExactly(Flags.Flag.ANSWERED);
    }

    @Test
    public void applyToStateShouldResetSpecifiedOldKeywords() {
        UpdateMessagePatch testee = UpdateMessagePatch.builder()
            .isAnswered(false)
            .build();

        Flags isSeen = new Flags(Flags.Flag.ANSWERED);
        assertThat(testee.applyToState(isSeen).getSystemFlags())
            .containsExactly();
    }

    @Test
    public void applyStateShouldReturnNewFlagsWhenKeywords() {
        ImmutableMap<String, Boolean> keywords = ImmutableMap.of(
                "$Answered", true,
                "$Flagged", true);

        UpdateMessagePatch testee = UpdateMessagePatch.builder()
            .keywords(keywords)
            .build();
        Flags isSeen = new Flags(Flags.Flag.SEEN);
        assertThat(testee.applyToState(isSeen).getSystemFlags())
            .containsExactly(Flags.Flag.ANSWERED, Flags.Flag.FLAGGED);
    }

    @Test
    public void applyStateShouldReturnRemoveFlagsWhenKeywords() {
        UpdateMessagePatch testee = UpdateMessagePatch.builder()
            .keywords(ImmutableMap.of())
            .build();
        Flags isSeen = new Flags(Flags.Flag.SEEN);
        assertThat(testee.applyToState(isSeen).getSystemFlags()).isEmpty();
    }

    @Test
    public void applyStateShouldThrowWhenKeywordsContainDeletedFlag() {
        expectedException.expect(IllegalArgumentException.class);
        ImmutableMap<String, Boolean> keywords = ImmutableMap.of(
                "$Deleted", true);

        UpdateMessagePatch testee = UpdateMessagePatch.builder()
            .keywords(keywords)
            .build();

        Flags currentFlags = new Flags(Flags.Flag.SEEN);

        testee.applyToState(currentFlags);
    }

    @Test
    public void applyStateShouldThrowWhenKeywordsContainRecentFlag() {
        expectedException.expect(IllegalArgumentException.class);

        ImmutableMap<String, Boolean> keywords = ImmutableMap.of(
                "$Recent", true);
        UpdateMessagePatch testee = UpdateMessagePatch.builder()
            .keywords(keywords)
            .build();

        Flags currentFlags = new Flags(Flags.Flag.SEEN);

        testee.applyToState(currentFlags);
    }

    @Test
    public void applyStateShouldReturnFlagsWithoutUserFlagWhenKeywordsContainForwarded() {
        ImmutableMap<String, Boolean> keywords = ImmutableMap.of(
                "$Answered", Keyword.FLAG_VALUE,
                FORWARDED, Keyword.FLAG_VALUE);

        UpdateMessagePatch testee = UpdateMessagePatch.builder()
            .keywords(keywords)
            .build();
        Flags isSeen = new Flags(Flags.Flag.SEEN);
        assertThat(testee.applyToState(isSeen).getSystemFlags())
            .doesNotContain(Flags.Flag.USER);
    }

    @Test
    public void applyStateShouldReturnFlagsWithUserFlagStringWhenKeywordsContainForwarded() {
        ImmutableMap<String, Boolean> keywords = ImmutableMap.of(
                "$Answered", Keyword.FLAG_VALUE,
                FORWARDED, Keyword.FLAG_VALUE);

        UpdateMessagePatch testee = UpdateMessagePatch.builder()
            .keywords(keywords)
            .build();
        Flags isSeen = new Flags(Flags.Flag.SEEN);
        assertThat(testee.applyToState(isSeen).getUserFlags())
            .containsExactly(FORWARDED);
    }

    @Test
    public void applyStateShouldReturnFlagsWithDeletedFlagWhenKeywordsDoNotContainDeletedButOriginFlagsHaveDeleted() {
        ImmutableMap<String, Boolean> keywords = ImmutableMap.of(
                "$Answered", Keyword.FLAG_VALUE);

        UpdateMessagePatch testee = UpdateMessagePatch.builder()
            .keywords(keywords)
            .build();
        Flags isSeen = new Flags(Flags.Flag.DELETED);
        assertThat(testee.applyToState(isSeen).getSystemFlags())
            .containsOnly(Flags.Flag.DELETED, Flags.Flag.ANSWERED);
    }

    @Test
    public void applyStateShouldReturnFlagsWithRecentFlagWhenKeywordsDoNotContainRecentButOriginFlagsHaveRecent() {
        ImmutableMap<String, Boolean> keywords = ImmutableMap.of(
                "$Answered", Keyword.FLAG_VALUE);

        UpdateMessagePatch testee = UpdateMessagePatch.builder()
            .keywords(keywords)
            .build();
        Flags flags = FlagsBuilder.builder()
            .add(Flags.Flag.DELETED, Flags.Flag.RECENT)
            .build();
        assertThat(testee.applyToState(flags).getSystemFlags())
            .containsOnly(Flags.Flag.DELETED, Flags.Flag.RECENT, Flags.Flag.ANSWERED);
    }

    @Test
    public void isIdentityShouldReturnTrueWhenNoFlagsAndEmptyKeywords() {
        UpdateMessagePatch messagePatch = UpdateMessagePatch.builder().build();

        assertThat(messagePatch.isFlagsIdentity()).isTrue();
    }

    @Test
    public void isIdentityShouldReturnFalseWhenFlagsAnsweredUpdated() {
        UpdateMessagePatch messagePatch = UpdateMessagePatch.builder()
            .isAnswered(true)
            .build();

        assertThat(messagePatch.isFlagsIdentity()).isFalse();
    }

    @Test
    public void isIdentityShouldReturnFalseWhenFlagsUnreadUpdated() {
        UpdateMessagePatch messagePatch = UpdateMessagePatch.builder()
            .isUnread(true)
            .build();

        assertThat(messagePatch.isFlagsIdentity()).isFalse();
    }

    @Test
    public void isIdentityShouldReturnFalseWhenFlagsFlaggedUpdated() {
        UpdateMessagePatch messagePatch = UpdateMessagePatch.builder()
            .isFlagged(true)
            .build();

        assertThat(messagePatch.isFlagsIdentity()).isFalse();
    }

    @Test
    public void isIdentityShouldReturnFalseWhenKeywords() {
        ImmutableMap<String, Boolean> keywords = ImmutableMap.of(
            "$Answered", Keyword.FLAG_VALUE,
            "$Flagged", Keyword.FLAG_VALUE);

        UpdateMessagePatch messagePatch = UpdateMessagePatch.builder()
                .keywords(keywords)
                .build();

        assertThat(messagePatch.isFlagsIdentity()).isFalse();
    }

    @Test
    public void isIdentityShouldReturnFalseWhenEmptyKeywords() {
        UpdateMessagePatch messagePatch = UpdateMessagePatch.builder()
                .keywords(ImmutableMap.of())
                .build();

        assertThat(messagePatch.isFlagsIdentity()).isFalse();
    }

    @Test
    public void isIdentityShouldThrowWhenBothIsFlagAndKeywords() {
        expectedException.expect(IllegalArgumentException.class);

        UpdateMessagePatch messagePatch = UpdateMessagePatch.builder()
                .keywords(ImmutableMap.of())
                .isAnswered(false)
                .build();

        messagePatch.isFlagsIdentity();
    }

    @Test
    public void applyStateShouldKeepKeywordsWhenNoKeywordPatchDefined() {
        UpdateMessagePatch messagePatch = UpdateMessagePatch.builder()
            .build();
        Flags flags = FlagsBuilder.builder()
            .add(Flags.Flag.DELETED, Flags.Flag.RECENT, Flags.Flag.DRAFT)
            .build();
        assertThat(messagePatch.applyToState(flags)).isEqualTo(flags);
    }
}