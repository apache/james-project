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

import javax.mail.Flags;

import org.apache.james.mailbox.MessageUid;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public class UpdatedFlagsTest {

    public static final MessageUid UID = MessageUid.of(45L);
    public static final long MOD_SEQ = 47L;

    @Test
    public void shouldMatchBeanContract() {
        EqualsVerifier.forClass(UpdatedFlags.class)
            .withIgnoredFields("modifiedFlags")
            .verify();
    }

    @Test
    public void isModifiedToSetShouldReturnTrueWhenFlagOnlyInNewFlag() {
        UpdatedFlags updatedFlags = UpdatedFlags.builder()
            .newFlags(new Flags(Flags.Flag.RECENT))
            .oldFlags(new Flags())
            .uid(UID)
            .modSeq(MOD_SEQ)
            .build();

        assertThat(updatedFlags.isModifiedToSet(Flags.Flag.RECENT)).isTrue();
    }

    @Test
    public void isModifiedToSetShouldReturnFalseWhenFlagOnlyInOldFlag() {
        UpdatedFlags updatedFlags = UpdatedFlags.builder()
            .newFlags(new Flags())
            .oldFlags(new Flags(Flags.Flag.RECENT))
            .uid(UID)
            .modSeq(MOD_SEQ)
            .build();

        assertThat(updatedFlags.isModifiedToSet(Flags.Flag.RECENT)).isFalse();
    }

    @Test
    public void isModifiedToSetShouldReturnFalseWhenFlagIsOnNone() {
        UpdatedFlags updatedFlags = UpdatedFlags.builder()
            .newFlags(new Flags())
            .oldFlags(new Flags())
            .uid(UID)
            .modSeq(MOD_SEQ)
            .build();

        assertThat(updatedFlags.isModifiedToSet(Flags.Flag.RECENT)).isFalse();
    }

    @Test
    public void isModifiedToSetShouldReturnFalseWhenFlagIsOnBoth() {
        UpdatedFlags updatedFlags = UpdatedFlags.builder()
            .newFlags(new Flags(Flags.Flag.RECENT))
            .oldFlags(new Flags(Flags.Flag.RECENT))
            .uid(UID)
            .modSeq(MOD_SEQ)
            .build();

        assertThat(updatedFlags.isModifiedToSet(Flags.Flag.RECENT)).isFalse();
    }

    @Test
    public void isModifiedToUnsetShouldReturnFalseWhenFlagOnlyInNewFlag() {
        UpdatedFlags updatedFlags = UpdatedFlags.builder()
            .newFlags(new Flags(Flags.Flag.RECENT))
            .oldFlags(new Flags())
            .uid(UID)
            .modSeq(MOD_SEQ)
            .build();

        assertThat(updatedFlags.isModifiedToUnset(Flags.Flag.RECENT)).isFalse();
    }

    @Test
    public void isModifiedToUnsetShouldReturnTrueWhenFlagOnlyInOldFlag() {
        UpdatedFlags updatedFlags = UpdatedFlags.builder()
            .newFlags(new Flags())
            .oldFlags(new Flags(Flags.Flag.RECENT))
            .uid(UID)
            .modSeq(MOD_SEQ)
            .build();

        assertThat(updatedFlags.isModifiedToUnset(Flags.Flag.RECENT)).isTrue();
    }

    @Test
    public void isModifiedToUnsetShouldReturnFalseWhenFlagIsOnNone() {
        UpdatedFlags updatedFlags = UpdatedFlags.builder()
            .newFlags(new Flags())
            .oldFlags(new Flags())
            .uid(UID)
            .modSeq(MOD_SEQ)
            .build();

        assertThat(updatedFlags.isModifiedToSet(Flags.Flag.RECENT)).isFalse();
    }

    @Test
    public void isModifiedToUnsetShouldReturnFalseWhenFlagIsOnBoth() {
        UpdatedFlags updatedFlags = UpdatedFlags.builder()
            .newFlags(new Flags(Flags.Flag.RECENT))
            .oldFlags(new Flags(Flags.Flag.RECENT))
            .uid(UID)
            .modSeq(MOD_SEQ)
            .build();

        assertThat(updatedFlags.isModifiedToUnset(Flags.Flag.RECENT)).isFalse();
    }

    @Test
    public void isUnchangedShouldReturnFalseWhenFlagOnlyInNewFlag() {
        UpdatedFlags updatedFlags = UpdatedFlags.builder()
            .newFlags(new Flags(Flags.Flag.RECENT))
            .oldFlags(new Flags())
            .uid(UID)
            .modSeq(MOD_SEQ)
            .build();

        assertThat(updatedFlags.isUnchanged(Flags.Flag.RECENT)).isFalse();
    }

    @Test
    public void isUnchangedShouldReturnFalseWhenFlagOnlyInOldFlag() {
        UpdatedFlags updatedFlags = UpdatedFlags.builder()
            .newFlags(new Flags())
            .oldFlags(new Flags(Flags.Flag.RECENT))
            .uid(UID)
            .modSeq(MOD_SEQ)
            .build();

        assertThat(updatedFlags.isUnchanged(Flags.Flag.RECENT)).isFalse();
    }

    @Test
    public void isUnchangedShouldReturnTrueWhenFlagIsOnNone() {
        UpdatedFlags updatedFlags = UpdatedFlags.builder()
            .newFlags(new Flags())
            .oldFlags(new Flags())
            .uid(UID)
            .modSeq(MOD_SEQ)
            .build();

        assertThat(updatedFlags.isUnchanged(Flags.Flag.RECENT)).isTrue();
    }

    @Test
    public void isUnchangedShouldReturnTrueWhenFlagIsOnBoth() {
        UpdatedFlags updatedFlags = UpdatedFlags.builder()
            .newFlags(new Flags(Flags.Flag.RECENT))
            .oldFlags(new Flags(Flags.Flag.RECENT))
            .uid(UID)
            .modSeq(MOD_SEQ)
            .build();

        assertThat(updatedFlags.isUnchanged(Flags.Flag.RECENT)).isTrue();
    }

}
