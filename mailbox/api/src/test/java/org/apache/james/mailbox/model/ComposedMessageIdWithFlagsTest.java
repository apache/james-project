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

public class ComposedMessageIdWithFlagsTest {

    @Test(expected = NullPointerException.class)
    public void buildShoudThrownWhenComposedMessageIdIsNull() {
        ComposedMessageIdWithFlags.builder().build();
    }

    @Test(expected = NullPointerException.class)
    public void buildShoudThrownWhenFlagsIsNull() {
        ComposedMessageIdWithFlags.builder()
            .composedMessageId(new ComposedMessageId(new TestId("1"), new TestMessageId("2"), MessageUid.of(3)))
            .build();
    }

    @Test
    public void buildShoudWork() {
        ComposedMessageId composedMessageId = new ComposedMessageId(new TestId("1"), new TestMessageId("2"), MessageUid.of(3));
        Flags flags = new Flags(Flag.RECENT);

        ComposedMessageIdWithFlags composedMessageIdWithFlags = ComposedMessageIdWithFlags.builder()
            .composedMessageId(composedMessageId)
            .flags(flags)
            .build();

        assertThat(composedMessageIdWithFlags.getComposedMessageId()).isEqualTo(composedMessageId);
        assertThat(composedMessageIdWithFlags.getFlags()).isEqualTo(flags);
    }

    @Test
    public void shouldRespectJavaBeanContract() {
        EqualsVerifier.forClass(ComposedMessageIdWithFlags.class)
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
