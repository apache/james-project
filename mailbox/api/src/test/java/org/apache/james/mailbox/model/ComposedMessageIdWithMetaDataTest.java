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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import javax.mail.Flags;
import javax.mail.Flags.Flag;

import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class ComposedMessageIdWithMetaDataTest {

    private static final TestId TEST_ID = TestId.of(1L);
    private static final TestMessageId TEST_MESSAGE_ID = new TestMessageId("2");
    private static final MessageUid MESSAGE_UID = MessageUid.of(3);
    private static final ComposedMessageId COMPOSED_MESSAGE_ID = new ComposedMessageId(TEST_ID, TEST_MESSAGE_ID, MESSAGE_UID);

    @Test
    void buildShoudThrownWhenComposedMessageIdIsNull() {
        assertThatThrownBy(() -> ComposedMessageIdWithMetaData.builder().build())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void buildShoudThrownWhenFlagsIsNull() {
        assertThatThrownBy(() -> ComposedMessageIdWithMetaData.builder()
                .composedMessageId(COMPOSED_MESSAGE_ID)
                .build())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void buildShoudThrownWhenModSeqIsNull() {
        assertThatThrownBy(() -> ComposedMessageIdWithMetaData.builder()
                .composedMessageId(COMPOSED_MESSAGE_ID)
                .flags(new Flags())
                .build())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void buildShoudWork() {
        Flags flags = new Flags(Flag.RECENT);
        ModSeq modSeq = ModSeq.of(1);

        ComposedMessageIdWithMetaData composedMessageIdWithMetaData = ComposedMessageIdWithMetaData.builder()
            .composedMessageId(COMPOSED_MESSAGE_ID)
            .flags(flags)
            .modSeq(modSeq)
            .build();

        assertThat(composedMessageIdWithMetaData.getComposedMessageId()).isEqualTo(COMPOSED_MESSAGE_ID);
        assertThat(composedMessageIdWithMetaData.getFlags()).isEqualTo(flags);
        assertThat(composedMessageIdWithMetaData.getModSeq()).isEqualTo(modSeq);
    }

    @Test
    void isMatchingShouldReturnTrueWhenSameMessageId() {
        ComposedMessageIdWithMetaData composedMessageIdWithMetaData = ComposedMessageIdWithMetaData.builder()
                .composedMessageId(new ComposedMessageId(TEST_ID, TEST_MESSAGE_ID, MESSAGE_UID))
                .flags(new Flags(Flag.RECENT))
                .modSeq(ModSeq.of(1))
                .build();

        assertThat(composedMessageIdWithMetaData.isMatching(TEST_MESSAGE_ID)).isTrue();
    }

    @Test
    void isMatchingShouldReturnFalseWhenOtherMessageId() {
        ComposedMessageIdWithMetaData composedMessageIdWithMetaData = ComposedMessageIdWithMetaData.builder()
                .composedMessageId(COMPOSED_MESSAGE_ID)
                .flags(new Flags(Flag.RECENT))
                .modSeq(ModSeq.of(1))
                .build();

        assertThat(composedMessageIdWithMetaData.isMatching(new TestMessageId("3"))).isFalse();
    }

    @Test
    void shouldRespectJavaBeanContract() {
        EqualsVerifier.forClass(ComposedMessageIdWithMetaData.class)
            .verify();
    }

    private static class TestMessageId implements MessageId {

        private final String id;

        public TestMessageId(String id) {
            this.id = id;
        }

        @Override
        public String serialize() {
            return id;
        }

        @Override
        public boolean isSerializable() {
            return true;
        }
        
    }
}
