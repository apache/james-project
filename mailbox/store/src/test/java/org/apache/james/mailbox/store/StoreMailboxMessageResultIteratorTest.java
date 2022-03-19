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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

import jakarta.mail.Flags;

import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.ByteContent;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxCounters;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.DefaultMessageId;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.junit.jupiter.api.Test;

import com.google.common.collect.Iterables;

import reactor.core.publisher.Flux;

class StoreMailboxMessageResultIteratorTest {

    private final class TestMessageMapper implements MessageMapper {
        private final MessageRange messageRange;

        public TestMessageMapper(MessageRange messageRange) {
            this.messageRange = messageRange;
        }

        @Override
        public Flux<MessageUid> listAllMessageUids(Mailbox mailbox) {
            return Flux.fromIterable(messageRange);
        }

        @Override
        public MailboxCounters getMailboxCounters(Mailbox mailbox) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Iterator<MailboxMessage> findInMailbox(Mailbox mailbox, MessageRange set, FetchType type, int limit) {
            List<MailboxMessage> messages = new ArrayList<>();
            for (MessageUid uid: Iterables.limit(set, limit)) {
                if (messageRange.includes(uid)) {
                    messages.add(createMessage(uid));
                }    
            }
            return messages.iterator();
        }

        private SimpleMailboxMessage createMessage(MessageUid uid) {
            SimpleMailboxMessage message = new SimpleMailboxMessage(new DefaultMessageId(), ThreadId.fromBaseMessageId(new DefaultMessageId()), null, 0, 0, new ByteContent(
                    "".getBytes()), new Flags(), new PropertyBuilder().build(), TestId.of(1L));
            message.setUid(uid);
            return message;
        }

        @Override
        public List<MessageUid> retrieveMessagesMarkedForDeletion(Mailbox mailbox, MessageRange messageRange) throws MailboxException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Map<MessageUid, MessageMetaData> deleteMessages(Mailbox mailbox, List<MessageUid> uids) throws MailboxException {
            throw new UnsupportedOperationException();
        }

        @Override
        public long countMessagesInMailbox(Mailbox mailbox) throws MailboxException {
            throw new UnsupportedOperationException();

        }

        @Override
        public void delete(Mailbox mailbox, MailboxMessage message) throws MailboxException {
            throw new UnsupportedOperationException();
        }

        @Override
        public MessageUid findFirstUnseenMessageUid(Mailbox mailbox) throws MailboxException {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<MessageUid> findRecentMessageUidsInMailbox(Mailbox mailbox) throws MailboxException {
            throw new UnsupportedOperationException();

        }

        @Override
        public MessageMetaData add(Mailbox mailbox, MailboxMessage message) throws MailboxException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Iterator<UpdatedFlags> updateFlags(Mailbox mailbox, FlagsUpdateCalculator calculator, MessageRange set) throws MailboxException {
            throw new UnsupportedOperationException();
        }

        @Override
        public MessageMetaData copy(Mailbox mailbox, MailboxMessage original) throws MailboxException {
            throw new UnsupportedOperationException();

        }

        @Override
        public Optional<MessageUid> getLastUid(Mailbox mailbox) throws MailboxException {
            throw new UnsupportedOperationException();
        }

        @Override
        public ModSeq getHighestModSeq(Mailbox mailbox) throws MailboxException {
            throw new UnsupportedOperationException();
        }

        @Override
        public MessageMetaData move(Mailbox mailbox, MailboxMessage original) throws MailboxException {
            throw new UnsupportedOperationException();

        }

        @Override
        public Flags getApplicableFlag(Mailbox mailbox) throws MailboxException {
            throw new UnsupportedOperationException();
        }
    }

    @Test
    void testBatching() {
        MessageRange range = MessageRange.range(MessageUid.of(1), MessageUid.of(10));
        BatchSizes batchSize = BatchSizes.uniqueBatchSize(3);
        StoreMessageResultIterator it = new StoreMessageResultIterator(new TestMessageMapper(MessageRange.all()), null, range, batchSize, FetchGroup.MINIMAL);

        assertThat(it).toIterable()
            .extracting(input -> input.getUid().asLong())
            .containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L);
    }

    @Test
    void nextShouldReturnFirstElement() {
        MessageRange range = MessageUid.of(1).toRange();
        BatchSizes batchSize = BatchSizes.uniqueBatchSize(42);
        StoreMessageResultIterator iterator = new StoreMessageResultIterator(new TestMessageMapper(range), null, range, batchSize, FetchGroup.MINIMAL);

        assertThat(iterator.next()).isNotNull();
    }
    
    @Test
    void nextShouldThrowWhenNoElement() {
        MessageRange messages = MessageUid.of(1).toRange();
        MessageRange findRange = MessageUid.of(2).toRange();
        BatchSizes batchSize = BatchSizes.uniqueBatchSize(42);
        StoreMessageResultIterator iterator = new StoreMessageResultIterator(new TestMessageMapper(messages), null, findRange, batchSize, FetchGroup.MINIMAL);

        assertThatThrownBy(() -> iterator.next())
            .isInstanceOf(NoSuchElementException.class);
    }
    
    @Test
    void hasNextShouldReturnFalseWhenNoElement() {
        MessageRange messages = MessageUid.of(1).toRange();
        MessageRange findRange = MessageUid.of(2).toRange();
        BatchSizes batchSize = BatchSizes.uniqueBatchSize(42);
        StoreMessageResultIterator iterator = new StoreMessageResultIterator(new TestMessageMapper(messages), null, findRange, batchSize, FetchGroup.MINIMAL);

        assertThat(iterator.hasNext()).isFalse();
    }
}
