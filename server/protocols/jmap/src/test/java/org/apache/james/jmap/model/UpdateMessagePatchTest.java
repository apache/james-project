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

import java.util.Arrays;
import java.util.List;
import javax.mail.Flags;

import org.junit.Test;

public class UpdateMessagePatchTest {

    @Test
    public void UnsetUpdatePatchShouldBeValid() {
        UpdateMessagePatch emptyPatch = UpdateMessagePatch.builder().build();
        assertThat(emptyPatch.isValid()).isTrue();
        assertThat(emptyPatch.isUnread()).isEmpty();
        assertThat(emptyPatch.isFlagged()).isEmpty();
        assertThat(emptyPatch.isAnswered()).isEmpty();
    }

    @Test
    public void builderShouldSetUnreadFalseWhenBuiltWithIsUnreadFalse() {
        UpdateMessagePatch testee = UpdateMessagePatch.builder().isUnread(false).build();
        assertThat(testee.isUnread()).isPresent();
        assertThat(testee.isUnread().get()).isFalse();
    }

    @Test
    public void applyStateShouldSetFlaggedOnlyWhenUnsetPatchAppliedToFlaggedState() {
        UpdateMessagePatch testee = UpdateMessagePatch.builder().build();
        Flags isFlaggedSet = new Flags(Flags.Flag.FLAGGED);
        List<Flags.Flag> updatedFlags = Arrays.asList(testee.applyToState(isFlaggedSet).getSystemFlags());
        assertThat(updatedFlags).containsExactly(Flags.Flag.FLAGGED);
    }


    @Test
    public void applyStateShouldReturnUnreadFlagWhenUnreadSetOnSeenMessage() {
        UpdateMessagePatch testee = UpdateMessagePatch.builder().isUnread(true).build();
        Flags isSeen = new Flags(Flags.Flag.SEEN);
        List<Flags.Flag> updatedFlags = Arrays.asList(testee.applyToState(isSeen).getSystemFlags());
        assertThat(updatedFlags).doesNotContain(Flags.Flag.SEEN);
    }

    @Test
    public void applyStateShouldReturnFlaggedWhenEmptyPatchOnFlaggedMessage() {
        UpdateMessagePatch testee = UpdateMessagePatch.builder().build();
        Flags isFlagged = new Flags(Flags.Flag.FLAGGED);
        List<Flags.Flag> updatedFlags = Arrays.asList(testee.applyToState(isFlagged).getSystemFlags());
        assertThat(updatedFlags).containsExactly(Flags.Flag.FLAGGED);
    }

    @Test
    public void applyStateShouldReturnSeenWhenPatchSetsSeenOnSeenMessage() {
        UpdateMessagePatch testee = UpdateMessagePatch.builder().isUnread(false).build();
        Flags isSeen = new Flags(Flags.Flag.SEEN);
        List<Flags.Flag> updatedFlags = Arrays.asList(testee.applyToState(isSeen).getSystemFlags());
        assertThat(updatedFlags).containsExactly(Flags.Flag.SEEN);
    }

}