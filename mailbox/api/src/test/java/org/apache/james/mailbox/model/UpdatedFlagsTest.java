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

package org.apache.james.mailbox.model;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.mail.Flags;

import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class UpdatedFlagsTest {

    public static final MessageUid UID = MessageUid.of(45L);
    public static final ModSeq MOD_SEQ = ModSeq.of(47L);

    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(UpdatedFlags.class)
            .withIgnoredFields("modifiedFlags")
            .verify();
    }

    @Test
    void isModifiedToSetShouldReturnTrueWhenFlagOnlyInNewFlag() {
        UpdatedFlags updatedFlags = UpdatedFlags.builder()
            .newFlags(new Flags(Flags.Flag.RECENT))
            .oldFlags(new Flags())
            .uid(UID)
            .modSeq(MOD_SEQ)
            .build();

        assertThat(updatedFlags.isModifiedToSet(Flags.Flag.RECENT)).isTrue();
    }

    @Test
    void isModifiedToSetShouldReturnFalseWhenFlagOnlyInOldFlag() {
        UpdatedFlags updatedFlags = UpdatedFlags.builder()
            .newFlags(new Flags())
            .oldFlags(new Flags(Flags.Flag.RECENT))
            .uid(UID)
            .modSeq(MOD_SEQ)
            .build();

        assertThat(updatedFlags.isModifiedToSet(Flags.Flag.RECENT)).isFalse();
    }

    @Test
    void isModifiedToSetShouldReturnFalseWhenFlagIsOnNone() {
        UpdatedFlags updatedFlags = UpdatedFlags.builder()
            .newFlags(new Flags())
            .oldFlags(new Flags())
            .uid(UID)
            .modSeq(MOD_SEQ)
            .build();

        assertThat(updatedFlags.isModifiedToSet(Flags.Flag.RECENT)).isFalse();
    }

    @Test
    void isModifiedToSetShouldReturnFalseWhenFlagIsOnBoth() {
        UpdatedFlags updatedFlags = UpdatedFlags.builder()
            .newFlags(new Flags(Flags.Flag.RECENT))
            .oldFlags(new Flags(Flags.Flag.RECENT))
            .uid(UID)
            .modSeq(MOD_SEQ)
            .build();

        assertThat(updatedFlags.isModifiedToSet(Flags.Flag.RECENT)).isFalse();
    }

    @Test
    void isModifiedToUnsetShouldReturnFalseWhenFlagOnlyInNewFlag() {
        UpdatedFlags updatedFlags = UpdatedFlags.builder()
            .newFlags(new Flags(Flags.Flag.RECENT))
            .oldFlags(new Flags())
            .uid(UID)
            .modSeq(MOD_SEQ)
            .build();

        assertThat(updatedFlags.isModifiedToUnset(Flags.Flag.RECENT)).isFalse();
    }

    @Test
    void isModifiedToUnsetShouldReturnTrueWhenFlagOnlyInOldFlag() {
        UpdatedFlags updatedFlags = UpdatedFlags.builder()
            .newFlags(new Flags())
            .oldFlags(new Flags(Flags.Flag.RECENT))
            .uid(UID)
            .modSeq(MOD_SEQ)
            .build();

        assertThat(updatedFlags.isModifiedToUnset(Flags.Flag.RECENT)).isTrue();
    }

    @Test
    void isModifiedToUnsetShouldReturnFalseWhenFlagIsOnNone() {
        UpdatedFlags updatedFlags = UpdatedFlags.builder()
            .newFlags(new Flags())
            .oldFlags(new Flags())
            .uid(UID)
            .modSeq(MOD_SEQ)
            .build();

        assertThat(updatedFlags.isModifiedToSet(Flags.Flag.RECENT)).isFalse();
    }

    @Test
    void isModifiedToUnsetShouldReturnFalseWhenFlagIsOnBoth() {
        UpdatedFlags updatedFlags = UpdatedFlags.builder()
            .newFlags(new Flags(Flags.Flag.RECENT))
            .oldFlags(new Flags(Flags.Flag.RECENT))
            .uid(UID)
            .modSeq(MOD_SEQ)
            .build();

        assertThat(updatedFlags.isModifiedToUnset(Flags.Flag.RECENT)).isFalse();
    }

    @Test
    void isUnchangedShouldReturnFalseWhenFlagOnlyInNewFlag() {
        UpdatedFlags updatedFlags = UpdatedFlags.builder()
            .newFlags(new Flags(Flags.Flag.RECENT))
            .oldFlags(new Flags())
            .uid(UID)
            .modSeq(MOD_SEQ)
            .build();

        assertThat(updatedFlags.isUnchanged(Flags.Flag.RECENT)).isFalse();
    }

    @Test
    void isUnchangedShouldReturnFalseWhenFlagOnlyInOldFlag() {
        UpdatedFlags updatedFlags = UpdatedFlags.builder()
            .newFlags(new Flags())
            .oldFlags(new Flags(Flags.Flag.RECENT))
            .uid(UID)
            .modSeq(MOD_SEQ)
            .build();

        assertThat(updatedFlags.isUnchanged(Flags.Flag.RECENT)).isFalse();
    }

    @Test
    void isUnchangedShouldReturnTrueWhenFlagIsOnNone() {
        UpdatedFlags updatedFlags = UpdatedFlags.builder()
            .newFlags(new Flags())
            .oldFlags(new Flags())
            .uid(UID)
            .modSeq(MOD_SEQ)
            .build();

        assertThat(updatedFlags.isUnchanged(Flags.Flag.RECENT)).isTrue();
    }

    @Test
    void isUnchangedShouldReturnTrueWhenFlagIsOnBoth() {
        UpdatedFlags updatedFlags = UpdatedFlags.builder()
            .newFlags(new Flags(Flags.Flag.RECENT))
            .oldFlags(new Flags(Flags.Flag.RECENT))
            .uid(UID)
            .modSeq(MOD_SEQ)
            .build();

        assertThat(updatedFlags.isUnchanged(Flags.Flag.RECENT)).isTrue();
    }

}
