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

package org.apache.james.mailbox.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MessageRange;
import org.junit.jupiter.api.Test;

import com.google.common.collect.Lists;

class MessageBatcherTest {

    private MessageBatcher.BatchedOperation incrementBatcher =
        messageRange -> Lists.<MessageRange>newArrayList(MessageRange.range(
            messageRange.getUidFrom().next(),
            messageRange.getUidTo().next()));

    @Test
    void batchMessagesShouldWorkOnSingleRangeMode() throws Exception {
        MessageBatcher messageBatcher = new MessageBatcher(0);
        
        assertThat(messageBatcher.batchMessages(MessageRange.range(MessageUid.of(1), MessageUid.of(10)), incrementBatcher))
            .containsOnly(MessageRange.range(MessageUid.of(2), MessageUid.of(11)));
    }

    @Test
    void batchMessagesShouldWorkWithNonZeroBatchedSize() throws Exception {
        MessageBatcher messageBatcher = new MessageBatcher(5);

        assertThat(messageBatcher.batchMessages(MessageRange.range(MessageUid.of(1), MessageUid.of(10)), incrementBatcher))
            .containsOnly(MessageRange.range(MessageUid.of(2), MessageUid.of(6)), MessageRange.range(MessageUid.of(7), MessageUid.of(11)));
    }

    @Test
    void batchMessagesShouldPropagateExceptions() {
        MessageBatcher messageBatcher = new MessageBatcher(0);

        assertThatThrownBy(() -> messageBatcher.batchMessages(MessageRange.range(MessageUid.of(1), MessageUid.of(10)),
                messageRange -> {
                    throw new MailboxException();
                }))
            .isInstanceOf(MailboxException.class);
    }

    @Test
    void messageBatcherShouldThrowOnNegativeBatchSize() {
        assertThatThrownBy(() -> new MessageBatcher(-1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getBatchSizeShouldReturnTheBatchSize() {
        int batchSize = 123;
        MessageBatcher messageBatcher = new MessageBatcher(batchSize);
        assertThat(messageBatcher.getBatchSize()).isEqualTo(batchSize);
    }
}
