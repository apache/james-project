/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE 2.0                 *
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

import java.util.Optional;

import jakarta.mail.Flags;
import jakarta.mail.Flags.Flag;

import org.apache.james.mailbox.FlagsBuilder;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public class OldKeywordTest {
    @Test
    public void shouldRespectBeanContract() {
        EqualsVerifier.forClass(OldKeyword.class).verify();
    }

    @Test
    public void asKeywordsShouldContainFlaggedWhenIsFlagged() {
        Optional<OldKeyword> testee = OldKeyword.builder()
            .isFlagged(Optional.of(true))
            .computeOldKeyword();

        assertThat(testee.get().asKeywords())
            .isEqualTo(Keywords.strictFactory().from(Keyword.FLAGGED));
    }

    @Test
    public void asKeywordsShouldNotContainFlaggedWhenIsNotFlagged() {
        Optional<OldKeyword> testee = OldKeyword.builder()
            .isFlagged(Optional.of(false))
            .computeOldKeyword();

        assertThat(testee.get().asKeywords())
            .isEqualTo(Keywords.strictFactory().from());
    }

    @Test
    public void asKeywordsShouldNotContainSeenWhenIsUnRead() {
        Optional<OldKeyword> testee = OldKeyword.builder()
            .isUnread(Optional.of(true))
            .computeOldKeyword();

        assertThat(testee.get().asKeywords())
            .isEqualTo(Keywords.strictFactory().from());
    }

    @Test
    public void asKeywordsShouldContainSeenWhenIsRead() {
        Optional<OldKeyword> testee = OldKeyword.builder()
            .isUnread(Optional.of(false))
            .computeOldKeyword();


        assertThat(testee.get().asKeywords())
            .isEqualTo(Keywords.strictFactory().from(Keyword.SEEN));
    }

    @Test
    public void asKeywordsShouldContainAnsweredWhenIsAnswered() {
        Optional<OldKeyword> testee = OldKeyword.builder()
            .isAnswered(Optional.of(true))
            .computeOldKeyword();

        assertThat(testee.get().asKeywords())
            .isEqualTo(Keywords.strictFactory().from(Keyword.ANSWERED));
    }

    @Test
    public void asKeywordsShouldNotContainAnsweredWhenIsNotAnswered() {
        Optional<OldKeyword> testee = OldKeyword.builder()
            .isAnswered(Optional.of(false))
            .computeOldKeyword();

        assertThat(testee.get().asKeywords())
            .isEqualTo(Keywords.strictFactory().from());
    }

    @Test
    public void asKeywordsShouldContainDraftWhenIsDraft() {
        Optional<OldKeyword> testee = OldKeyword.builder()
            .isDraft(Optional.of(true))
            .computeOldKeyword();

        assertThat(testee.get().asKeywords())
            .isEqualTo(Keywords.strictFactory().from(Keyword.DRAFT));
    }

    @Test
    public void asKeywordsShouldNotContainDraftWhenIsNotDraft() {
        Optional<OldKeyword> testee = OldKeyword.builder()
            .isDraft(Optional.of(false))
            .computeOldKeyword();

        assertThat(testee.get().asKeywords())
            .isEqualTo(Keywords.strictFactory().from());
    }

    @Test
    public void asKeywordsShouldContainForwardedWhenIsForwarded() {
        Optional<OldKeyword> testee = OldKeyword.builder()
            .isForwarded(Optional.of(true))
            .computeOldKeyword();

        assertThat(testee.get().asKeywords())
            .isEqualTo(Keywords.strictFactory().from(Keyword.FORWARDED));
    }

    @Test
    public void asKeywordsShouldNotContainForwardedWhenIsNotForwarded() {
        Optional<OldKeyword> testee = OldKeyword.builder()
            .isForwarded(Optional.of(false))
            .computeOldKeyword();

        assertThat(testee.get().asKeywords())
            .isEqualTo(Keywords.strictFactory().from());
    }

    @Test
    public void computeOldKeywordsShouldReturnEmptyWhenAllEmpty() {
        Optional<OldKeyword> testee = OldKeyword.builder()
            .computeOldKeyword();

        assertThat(testee).isEmpty();
    }

    @Test
    public void applyStateShouldSetFlaggedOnlyWhenIsFlagged() {
        Optional<OldKeyword> testee = OldKeyword.builder()
            .isFlagged(Optional.of(true))
            .computeOldKeyword();

        assertThat(testee.get().applyToState(new Flags())).isEqualTo(new Flags(Flag.FLAGGED));
    }

    @Test
    public void applyStateShouldRemoveFlaggedWhenEmptyIsFlaggedOnFlaggedMessage() {
        Optional<OldKeyword> testee = OldKeyword.builder()
            .isFlagged(Optional.of(false))
            .computeOldKeyword();

        assertThat(testee.get().applyToState(new Flags(Flag.FLAGGED))).isEqualTo(new Flags());
    }


    @Test
    public void applyStateShouldReturnUnreadFlagWhenUnreadSetOnSeenMessage() {
        Optional<OldKeyword> testee = OldKeyword.builder()
            .isUnread(Optional.of(true))
            .computeOldKeyword();

        assertThat(testee.get().applyToState(new Flags(Flag.SEEN))).isEqualTo(new Flags());
    }

    @Test
    public void applyStateShouldReturnSeenWhenPatchSetsSeenOnSeenMessage() {
        Optional<OldKeyword> testee = OldKeyword.builder()
            .isUnread(Optional.of(false))
            .computeOldKeyword();

        assertThat(testee.get().applyToState(new Flags(Flag.SEEN))).isEqualTo(new Flags(Flag.SEEN));
    }

    @Test
    public void applyStateShouldPreserveRecentFlag() {
        Optional<OldKeyword> testee = OldKeyword.builder()
            .isUnread(Optional.of(false))
            .computeOldKeyword();

        assertThat(testee.get().applyToState(new Flags(Flag.RECENT)))
            .isEqualTo(new FlagsBuilder().add(Flag.RECENT, Flag.SEEN).build());
    }

    @Test
    public void applyStateShouldPreserveDeletedFlag() {
        Optional<OldKeyword> testee = OldKeyword.builder()
            .isUnread(Optional.of(false))
            .computeOldKeyword();

        assertThat(testee.get().applyToState(new Flags(Flag.DELETED)))
            .isEqualTo(new FlagsBuilder().add(Flag.DELETED, Flag.SEEN).build());
    }

    @Test
    public void applyStateShouldPreserveCustomFlag() {
        Optional<OldKeyword> testee = OldKeyword.builder()
            .isUnread(Optional.of(false))
            .computeOldKeyword();

        String customFlag = "custom";
        assertThat(testee.get().applyToState(new Flags(customFlag)))
            .isEqualTo(new FlagsBuilder().add(Flag.SEEN).add(customFlag).build());
    }
}