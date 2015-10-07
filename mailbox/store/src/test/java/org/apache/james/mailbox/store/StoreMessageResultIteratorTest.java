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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.mail.Flags;
import javax.mail.util.SharedByteArrayInputStream;

import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.model.MessageResult.FetchGroup;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.Message;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMessage;
import org.assertj.core.api.iterable.Extractor;
import org.junit.Test;

public class StoreMessageResultIteratorTest {

    @Test
    public void testBatching() {
        MessageRange range = MessageRange.range(1, 10);
        int batchSize = 3;
        StoreMessageResultIterator<TestId> it = new StoreMessageResultIterator<TestId>(new MessageMapper<TestId>() {

            @Override
            public void endRequest() {
                throw new UnsupportedOperationException();
            }

            @Override
            public <T> T execute(Transaction<T> transaction) throws MailboxException {
                throw new UnsupportedOperationException();
            }

            @Override
            public Iterator<Message<TestId>> findInMailbox(Mailbox<TestId> mailbox, MessageRange set,
                    org.apache.james.mailbox.store.mail.MessageMapper.FetchType type, int limit)
                    throws MailboxException {
                long start = set.getUidFrom();
                long end = set.getUidTo();
                long calcEnd = start + limit;
                if (calcEnd > end) {
                    calcEnd = end + 1;
                }

                List<Message<TestId>> messages = new ArrayList<Message<TestId>>();
                long i = start;
                while (i < calcEnd) {
                    long uid = i;
                    SimpleMessage<TestId> m = new SimpleMessage<TestId>(null, 0, 0, new SharedByteArrayInputStream(
                            "".getBytes()), new Flags(), new PropertyBuilder(), TestId.of(1L));
                    m.setUid(uid);
                    messages.add(m);
                    i++;
                }
                return messages.iterator();
            }

            @Override
            public Map<Long, MessageMetaData> expungeMarkedForDeletionInMailbox(Mailbox<TestId> mailbox, MessageRange set)
                    throws MailboxException {
                throw new UnsupportedOperationException();

            }

            @Override
            public long countMessagesInMailbox(Mailbox<TestId> mailbox) throws MailboxException {
                throw new UnsupportedOperationException();

            }

            @Override
            public long countUnseenMessagesInMailbox(Mailbox<TestId> mailbox) throws MailboxException {
                throw new UnsupportedOperationException();
            }

            @Override
            public void delete(Mailbox<TestId> mailbox, Message<TestId> message) throws MailboxException {
                throw new UnsupportedOperationException();
            }

            @Override
            public Long findFirstUnseenMessageUid(Mailbox<TestId> mailbox) throws MailboxException {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<Long> findRecentMessageUidsInMailbox(Mailbox<TestId> mailbox) throws MailboxException {
                throw new UnsupportedOperationException();

            }

            @Override
            public MessageMetaData add(Mailbox<TestId> mailbox, Message<TestId> message) throws MailboxException {
                throw new UnsupportedOperationException();
            }

            @Override
            public Iterator<UpdatedFlags> updateFlags(Mailbox<TestId> mailbox, FlagsUpdateCalculator calculator, MessageRange set) throws MailboxException {
                throw new UnsupportedOperationException();
            }

            @Override
            public MessageMetaData copy(Mailbox<TestId> mailbox, Message<TestId> original) throws MailboxException {
                throw new UnsupportedOperationException();

            }

            @Override
            public long getLastUid(Mailbox<TestId> mailbox) throws MailboxException {
                throw new UnsupportedOperationException();
            }

            @Override
            public long getHighestModSeq(Mailbox<TestId> mailbox) throws MailboxException {
                throw new UnsupportedOperationException();
            }

            @Override
            public MessageMetaData move(Mailbox<TestId> mailbox, Message<TestId> original) throws MailboxException {
                throw new UnsupportedOperationException();

            }

        }, null, range, batchSize, new FetchGroup() {

            @Override
            public Set<PartContentDescriptor> getPartContentDescriptors() {
                return null;
            }

            @Override
            public int content() {
                return FetchGroup.MINIMAL;
            }
        });

        assertThat(it).extracting(new Extractor<MessageResult, Long>(){
            @Override
            public Long extract(MessageResult input) {
                return input.getUid();
            }
        }).containsExactly(1l, 2l, 3l, 4l, 5l, 6l, 7l, 8l, 9l, 10l);
    }

}
