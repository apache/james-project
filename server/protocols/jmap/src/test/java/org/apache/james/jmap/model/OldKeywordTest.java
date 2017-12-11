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

package org.apache.james.jmap.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import javax.mail.Flags;
import javax.mail.Flags.Flag;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public class OldKeywordTest {
    @Test
    public void shouldRespectBeanContract() {
        EqualsVerifier.forClass(OldKeyword.class).verify();
    }

    @Test
    public void computeOldKeywordsShouldReturnEmptyWhenAllEmpty() {
        Optional<OldKeyword> testee = OldKeyword.builder()
            .isUnread(Optional.empty())
            .isFlagged(Optional.empty())
            .isAnswered(Optional.empty())
            .isDraft(Optional.empty())
            .isForwarded(Optional.empty())
            .computeOldKeyword();

        assertThat(testee).isEmpty();
    }

    @Test
    public void applyStateShouldSetFlaggedOnlyWhenIsFlagged() {
        Optional<OldKeyword> testee = OldKeyword.builder()
            .isUnread(Optional.empty())
            .isFlagged(Optional.of(true))
            .isAnswered(Optional.empty())
            .isDraft(Optional.empty())
            .isForwarded(Optional.empty())
            .computeOldKeyword();

        assertThat(testee.get().applyToState(new Flags())).isEqualTo(new Flags(Flag.FLAGGED));
    }

    @Test
    public void applyStateShouldRemoveFlaggedWhenEmptyIsFlaggedOnFlaggedMessage() {
        Optional<OldKeyword> testee = OldKeyword.builder()
            .isUnread(Optional.empty())
            .isFlagged(Optional.of(false))
            .isAnswered(Optional.empty())
            .isDraft(Optional.empty())
            .isForwarded(Optional.empty())
            .computeOldKeyword();

        assertThat(testee.get().applyToState(new Flags(Flag.FLAGGED))).isEqualTo(new Flags());
    }


    @Test
    public void applyStateShouldReturnUnreadFlagWhenUnreadSetOnSeenMessage() {
        Optional<OldKeyword> testee = OldKeyword.builder()
            .isUnread(Optional.of(true))
            .isFlagged(Optional.empty())
            .isAnswered(Optional.empty())
            .isDraft(Optional.empty())
            .isForwarded(Optional.empty())
            .computeOldKeyword();

        assertThat(testee.get().applyToState(new Flags(Flag.SEEN))).isEqualTo(new Flags());
    }

    @Test
    public void applyStateShouldReturnSeenWhenPatchSetsSeenOnSeenMessage() {
        Optional<OldKeyword> testee = OldKeyword.builder()
            .isUnread(Optional.of(false))
            .isFlagged(Optional.empty())
            .isAnswered(Optional.empty())
            .isDraft(Optional.empty())
            .isForwarded(Optional.empty())
            .computeOldKeyword();

        assertThat(testee.get().applyToState(new Flags(Flag.SEEN))).isEqualTo(new Flags(Flag.SEEN));
    }
}