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
import javax.mail.Flags.Flag;

import org.apache.james.mailbox.MessageUid;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public class ComposedMessageIdWithMetaDataTest {

    public static final TestId TEST_ID = TestId.of(1L);
    public static final TestMessageId TEST_MESSAGE_ID = new TestMessageId("2");
    public static final MessageUid MESSAGE_UID = MessageUid.of(3);
    public static final ComposedMessageId COMPOSED_MESSAGE_ID = new ComposedMessageId(TEST_ID, TEST_MESSAGE_ID, MESSAGE_UID);

    @Test(expected = NullPointerException.class)
    public void buildShoudThrownWhenComposedMessageIdIsNull() {
        ComposedMessageIdWithMetaData.builder().build();
    }

    @Test(expected = NullPointerException.class)
    public void buildShoudThrownWhenFlagsIsNull() {
        ComposedMessageIdWithMetaData.builder()
            .composedMessageId(COMPOSED_MESSAGE_ID)
            .build();
    }

    @Test(expected = NullPointerException.class)
    public void buildShoudThrownWhenModSeqIsNull() {
        ComposedMessageIdWithMetaData.builder()
            .composedMessageId(COMPOSED_MESSAGE_ID)
            .flags(new Flags())
            .build();
    }

    @Test
    public void buildShoudWork() {
        Flags flags = new Flags(Flag.RECENT);
        long modSeq = 1;

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
    public void isMatchingShouldReturnTrueWhenSameMessageId() {
        ComposedMessageIdWithMetaData composedMessageIdWithMetaData = ComposedMessageIdWithMetaData.builder()
                .composedMessageId(new ComposedMessageId(TEST_ID, TEST_MESSAGE_ID, MESSAGE_UID))
                .flags(new Flags(Flag.RECENT))
                .modSeq((long) 1)
                .build();

        assertThat(composedMessageIdWithMetaData.isMatching(TEST_MESSAGE_ID)).isTrue();
    }

    @Test
    public void isMatchingShouldReturnFalseWhenOtherMessageId() {
        ComposedMessageIdWithMetaData composedMessageIdWithMetaData = ComposedMessageIdWithMetaData.builder()
                .composedMessageId(COMPOSED_MESSAGE_ID)
                .flags(new Flags(Flag.RECENT))
                .modSeq((long) 1)
                .build();

        assertThat(composedMessageIdWithMetaData.isMatching(new TestMessageId("3"))).isFalse();
    }

    @Test
    public void shouldRespectJavaBeanContract() {
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
        
    }
}
