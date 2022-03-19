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

import java.util.UUID;

import jakarta.mail.Flags;

import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.junit.jupiter.api.Test;

import com.datastax.oss.driver.api.core.uuid.Uuids;
import com.google.common.collect.ImmutableList;

import nl.jqno.equalsverifier.EqualsVerifier;

class FlagsUpdateStageResultTest {

    private static final ComposedMessageId UID = new ComposedMessageId(
        CassandraId.of(UUID.fromString("464765a0-e4e7-11e4-aba4-710c1de3782b")),
        new CassandraMessageId.Factory().of(Uuids.timeBased()),
        MessageUid.of(1L));
    private static final ComposedMessageId OTHER_UID = new ComposedMessageId(
        CassandraId.of(UUID.fromString("464765a0-e4e7-11e4-aba4-710c1de3782b")),
        new CassandraMessageId.Factory().of(Uuids.timeBased()),
        MessageUid.of(2L));
    private static final UpdatedFlags UPDATED_FLAGS = UpdatedFlags.builder()
        .uid(UID.getUid())
        .modSeq(ModSeq.of(18))
        .oldFlags(new Flags())
        .newFlags(new Flags(Flags.Flag.SEEN))
        .build();
    private static final UpdatedFlags OTHER_UPDATED_FLAGS = UpdatedFlags.builder()
        .uid(OTHER_UID.getUid())
        .modSeq(ModSeq.of(18))
        .oldFlags(new Flags())
        .newFlags(new Flags(Flags.Flag.SEEN))
        .build();

    @Test
    void classShouldRespectBeanContract() {
        EqualsVerifier.forClass(FlagsUpdateStageResult.class);
    }

    @Test
    void noneShouldCreateResultWithoutSuccessOrFails() {
        assertThat(FlagsUpdateStageResult.none())
            .isEqualTo(new FlagsUpdateStageResult(ImmutableList.of(), ImmutableList.of()));
    }

    @Test
    void failShouldCreateResultWithFailedUid() {
        assertThat(FlagsUpdateStageResult.fail(UID))
            .isEqualTo(new FlagsUpdateStageResult(ImmutableList.of(UID), ImmutableList.of()));
    }

    @Test
    void successShouldCreateResultWithSucceededUpdatedFlags() {
        assertThat(FlagsUpdateStageResult.success(UPDATED_FLAGS))
            .isEqualTo(new FlagsUpdateStageResult(ImmutableList.of(), ImmutableList.of(UPDATED_FLAGS)));
    }

    @Test
    void noneShouldBeWellMergedWithNone() {
        assertThat(FlagsUpdateStageResult.none().merge(FlagsUpdateStageResult.none()))
            .isEqualTo(FlagsUpdateStageResult.none());
    }

    @Test
    void noneShouldBeWellMergedWithFail() {
        assertThat(FlagsUpdateStageResult.none().merge(FlagsUpdateStageResult.fail(UID)))
            .isEqualTo(FlagsUpdateStageResult.fail(UID));
    }

    @Test
    void noneShouldBeWellMergedWithSuccess() {
        assertThat(FlagsUpdateStageResult.none().merge(FlagsUpdateStageResult.success(UPDATED_FLAGS)))
            .isEqualTo(FlagsUpdateStageResult.success(UPDATED_FLAGS));
    }

    @Test
    void failShouldBeWellMergedWithFail() {
        assertThat(FlagsUpdateStageResult.fail(UID).merge(FlagsUpdateStageResult.fail(OTHER_UID)))
            .isEqualTo(new FlagsUpdateStageResult(ImmutableList.of(UID, OTHER_UID), ImmutableList.of()));
    }

    @Test
    void successShouldBeWellMergedWithFail() {
        assertThat(FlagsUpdateStageResult.success(UPDATED_FLAGS).merge(FlagsUpdateStageResult.fail(UID)))
            .isEqualTo(new FlagsUpdateStageResult(ImmutableList.of(UID), ImmutableList.of(UPDATED_FLAGS)));
    }

    @Test
    void successShouldBeWellMergedWithSuccess() {
        assertThat(FlagsUpdateStageResult.success(UPDATED_FLAGS).merge(FlagsUpdateStageResult.success(OTHER_UPDATED_FLAGS)))
            .isEqualTo(new FlagsUpdateStageResult(ImmutableList.of(), ImmutableList.of(UPDATED_FLAGS, OTHER_UPDATED_FLAGS)));
    }

    @Test
    void getFailedShouldReturnFailedUid() {
        FlagsUpdateStageResult flagsUpdateStageResult = new FlagsUpdateStageResult(ImmutableList.of(UID), ImmutableList.of(UPDATED_FLAGS));

        assertThat(flagsUpdateStageResult.getFailed())
            .containsExactly(UID);
    }

    @Test
    void getSucceededShouldReturnSucceedUpdatedFlags() {
        FlagsUpdateStageResult flagsUpdateStageResult = new FlagsUpdateStageResult(ImmutableList.of(UID), ImmutableList.of(UPDATED_FLAGS));

        assertThat(flagsUpdateStageResult.getSucceeded())
            .containsExactly(UPDATED_FLAGS);
    }

    @Test
    void keepSuccessShouldDiscardFailedUids() {
        FlagsUpdateStageResult flagsUpdateStageResult = new FlagsUpdateStageResult(ImmutableList.of(UID), ImmutableList.of(UPDATED_FLAGS));

        assertThat(flagsUpdateStageResult.keepSucceded())
            .isEqualTo(FlagsUpdateStageResult.success(UPDATED_FLAGS));
    }

    @Test
    void containsFailedResultsShouldReturnTrueWhenFailed() {
        assertThat(FlagsUpdateStageResult.fail(UID).containsFailedResults())
            .isTrue();
    }


    @Test
    void containsFailedResultsShouldReturnFalseWhenSucceeded() {
        assertThat(FlagsUpdateStageResult.success(UPDATED_FLAGS).containsFailedResults())
            .isFalse();
    }

}
