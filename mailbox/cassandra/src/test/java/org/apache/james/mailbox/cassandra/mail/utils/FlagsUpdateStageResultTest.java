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

package org.apache.james.mailbox.cassandra.mail.utils;

import static org.assertj.core.api.Assertions.assertThat;

import javax.mail.Flags;

import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.junit.Test;

import com.google.common.collect.ImmutableList;

import nl.jqno.equalsverifier.EqualsVerifier;

public class FlagsUpdateStageResultTest {

    public static final MessageUid UID = MessageUid.of(1L);
    public static final MessageUid OTHER_UID = MessageUid.of(2L);
    public static final UpdatedFlags UPDATED_FLAGS = UpdatedFlags.builder()
        .uid(UID)
        .modSeq(18L)
        .oldFlags(new Flags())
        .newFlags(new Flags(Flags.Flag.SEEN))
        .build();
    public static final UpdatedFlags OTHER_UPDATED_FLAGS = UpdatedFlags.builder()
        .uid(OTHER_UID)
        .modSeq(18L)
        .oldFlags(new Flags())
        .newFlags(new Flags(Flags.Flag.SEEN))
        .build();

    @Test
    public void classShouldRespectBeanContract() {
        EqualsVerifier.forClass(FlagsUpdateStageResult.class);
    }

    @Test
    public void noneShouldCreateResultWithoutSuccessOrFails() {
        assertThat(FlagsUpdateStageResult.none())
            .isEqualTo(new FlagsUpdateStageResult(ImmutableList.of(), ImmutableList.of()));
    }

    @Test
    public void failShouldCreateResultWithFailedUid() {
        assertThat(FlagsUpdateStageResult.fail(UID))
            .isEqualTo(new FlagsUpdateStageResult(ImmutableList.of(UID), ImmutableList.of()));
    }

    @Test
    public void successShouldCreateResultWithSucceededUpdatedFlags() {
        assertThat(FlagsUpdateStageResult.success(UPDATED_FLAGS))
            .isEqualTo(new FlagsUpdateStageResult(ImmutableList.of(), ImmutableList.of(UPDATED_FLAGS)));
    }

    @Test
    public void noneShouldBeWellMergedWithNone() {
        assertThat(FlagsUpdateStageResult.none().merge(FlagsUpdateStageResult.none()))
            .isEqualTo(FlagsUpdateStageResult.none());
    }

    @Test
    public void noneShouldBeWellMergedWithFail() {
        assertThat(FlagsUpdateStageResult.none().merge(FlagsUpdateStageResult.fail(UID)))
            .isEqualTo(FlagsUpdateStageResult.fail(UID));
    }

    @Test
    public void noneShouldBeWellMergedWithSuccess() {
        assertThat(FlagsUpdateStageResult.none().merge(FlagsUpdateStageResult.success(UPDATED_FLAGS)))
            .isEqualTo(FlagsUpdateStageResult.success(UPDATED_FLAGS));
    }

    @Test
    public void failShouldBeWellMergedWithFail() {
        assertThat(FlagsUpdateStageResult.fail(UID).merge(FlagsUpdateStageResult.fail(OTHER_UID)))
            .isEqualTo(new FlagsUpdateStageResult(ImmutableList.of(UID, OTHER_UID), ImmutableList.of()));
    }

    @Test
    public void successShouldBeWellMergedWithFail() {
        assertThat(FlagsUpdateStageResult.success(UPDATED_FLAGS).merge(FlagsUpdateStageResult.fail(UID)))
            .isEqualTo(new FlagsUpdateStageResult(ImmutableList.of(UID), ImmutableList.of(UPDATED_FLAGS)));
    }

    @Test
    public void successShouldBeWellMergedWithSuccess() {
        assertThat(FlagsUpdateStageResult.success(UPDATED_FLAGS).merge(FlagsUpdateStageResult.success(OTHER_UPDATED_FLAGS)))
            .isEqualTo(new FlagsUpdateStageResult(ImmutableList.of(), ImmutableList.of(UPDATED_FLAGS, OTHER_UPDATED_FLAGS)));
    }

    @Test
    public void getFailedShouldReturnFailedUid() {
        FlagsUpdateStageResult flagsUpdateStageResult = new FlagsUpdateStageResult(ImmutableList.of(UID), ImmutableList.of(UPDATED_FLAGS));

        assertThat(flagsUpdateStageResult.getFailed())
            .containsExactly(UID);
    }

    @Test
    public void getSucceededShouldReturnSucceedUpdatedFlags() {
        FlagsUpdateStageResult flagsUpdateStageResult = new FlagsUpdateStageResult(ImmutableList.of(UID), ImmutableList.of(UPDATED_FLAGS));

        assertThat(flagsUpdateStageResult.getSucceeded())
            .containsExactly(UPDATED_FLAGS);
    }

    @Test
    public void keepSuccessShouldDiscardFailedUids() {
        FlagsUpdateStageResult flagsUpdateStageResult = new FlagsUpdateStageResult(ImmutableList.of(UID), ImmutableList.of(UPDATED_FLAGS));

        assertThat(flagsUpdateStageResult.keepSucceded())
            .isEqualTo(FlagsUpdateStageResult.success(UPDATED_FLAGS));
    }

}
