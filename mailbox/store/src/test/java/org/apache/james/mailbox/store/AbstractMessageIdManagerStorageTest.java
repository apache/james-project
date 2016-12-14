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
import static org.mockito.Mockito.mock;

import java.util.AbstractMap;
import java.util.List;
import java.util.Map;

import javax.mail.Flags;

import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.model.FetchGroupImpl;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public abstract class AbstractMessageIdManagerStorageTest {

    public static final Flags FLAGS = new Flags();
    private static final MessageUid messageUid1 = MessageUid.of(111);
    private static final MessageUid messageUid2 = MessageUid.of(222);

    private static final Function<MessageResult, Flags> getFlags() {
        return new Function<MessageResult, Flags>() {
            @Override
            public Flags apply(MessageResult input) {
                return input.getFlags();
            }
        };
    }

    private static final Function<MessageResult, Map.Entry<MessageId, Flags>> toMapEntryOfFlags() {
        return new Function<MessageResult, Map.Entry<MessageId, Flags>>() {
            @Override
            public Map.Entry<MessageId, Flags> apply(MessageResult input) {
                return new AbstractMap.SimpleEntry<MessageId, Flags>(input.getMessageId(), input.getFlags());
            }
        };
    }

    private static Function<MessageResult, MailboxId> getMailboxId() {
        return new Function<MessageResult, MailboxId>() {
            @Override
            public MailboxId apply(MessageResult input) {
                return input.getMailboxId();
            }
        };
    }

    private MessageIdManagerTestSystem testingData;
    private MessageIdManager messageIdManager;
    private Mailbox mailbox1;
    private Mailbox mailbox2;
    private Mailbox mailbox3;
    private MailboxSession session;

    protected abstract MessageIdManagerTestSystem createTestingData();

    @Before
    public void setUp() {
        testingData = createTestingData();
        messageIdManager = testingData.getMessageIdManager();
        mailbox1 = testingData.getMailbox1();
        mailbox2 = testingData.getMailbox2();
        mailbox3 = testingData.getMailbox3();

        session = mock(MailboxSession.class);
    }

    @After
    public void tearDown() {
        testingData.clean();
    }

    @Test
    public void getMessagesShouldReturnEmptyListWhenMessageIdNotUsed() throws Exception {
        MessageId messageId = testingData.createNotUsedMessageId();

        assertThat(messageIdManager.getMessages(ImmutableList.of(messageId), FetchGroupImpl.MINIMAL, session))
            .isEmpty();
    }

    @Test
    public void setFlagsShouldNotFailWhenMessageDoesNotExist() throws Exception {
        MessageId messageId = testingData.createNotUsedMessageId();

        messageIdManager.setFlags(FLAGS, MessageManager.FlagsUpdateMode.ADD, messageId, ImmutableList.of(mailbox1.getMailboxId()), session);
    }

    @Test
    public void deleteMessageShouldNotFailWhenMessageDoesNotExist() throws Exception {
        MessageId messageId = testingData.createNotUsedMessageId();

        messageIdManager.delete(messageId, ImmutableList.of(mailbox1.getMailboxId()), session);
    }

    @Test
    public void setInMailboxesShouldNotFailWhenMessageDoesNotExist() throws Exception {
        MessageId messageId = testingData.createNotUsedMessageId();

        messageIdManager.setInMailboxes(messageId, ImmutableList.of(mailbox1.getMailboxId()), session);
    }

    @Test
    public void getMessagesShouldReturnStoredResults() throws Exception {
        MessageId messageId = testingData.persist(mailbox1.getMailboxId(), messageUid1, FLAGS);

        assertThat(messageIdManager.getMessages(ImmutableList.of(messageId), FetchGroupImpl.MINIMAL, session))
            .hasSize(1);
    }

    @Test
    public void setInMailboxesShouldSetMessageInBothMailboxes() throws Exception {
        MessageId messageId = testingData.persist(mailbox1.getMailboxId(), messageUid1, FLAGS);

        messageIdManager.setInMailboxes(messageId, ImmutableList.of(mailbox2.getMailboxId()), session);

        assertThat(messageIdManager.getMessages(ImmutableList.of(messageId), FetchGroupImpl.MINIMAL, session))
            .hasSize(2);
    }

    @Test
    public void setInMailboxesShouldNotDuplicateMessageIfSameMailbox() throws Exception {
        MessageId messageId = testingData.persist(mailbox1.getMailboxId(), messageUid1, FLAGS);

        messageIdManager.setInMailboxes(messageId, ImmutableList.of(mailbox1.getMailboxId()), session);

        assertThat(messageIdManager.getMessages(ImmutableList.of(messageId), FetchGroupImpl.MINIMAL, session))
            .hasSize(1);
    }

    @Test
    public void setInMailboxesShouldSetHighestUidInNewMailbox() throws Exception {
        MessageId messageId1 = testingData.persist(mailbox1.getMailboxId(), messageUid1, FLAGS);
        MessageId messageId2 = testingData.persist(mailbox2.getMailboxId(), messageUid2, FLAGS);

        messageIdManager.setInMailboxes(messageId2, ImmutableList.of(mailbox1.getMailboxId()), session);

        MessageUid uidMessage1Mailbox1 = messageIdManager.getMessages(ImmutableList.of(messageId1), FetchGroupImpl.MINIMAL, session)
            .get(0)
            .getUid();
        MessageUid uidMessage2Mailbox1 = FluentIterable
            .from(messageIdManager.getMessages(ImmutableList.of(messageId1), FetchGroupImpl.MINIMAL, session))
            .filter(inMailbox(mailbox1.getMailboxId()))
            .toList()
            .get(0)
            .getUid();

        assertThat(uidMessage2Mailbox1).isGreaterThan(uidMessage1Mailbox1);
    }

    @Test
    public void setInMailboxesShouldSetHighestModSeqInNewMailbox() throws Exception {
        MessageId messageId1 = testingData.persist(mailbox1.getMailboxId(), messageUid1, FLAGS);
        MessageId messageId2 = testingData.persist(mailbox2.getMailboxId(), messageUid2, FLAGS);

        messageIdManager.setInMailboxes(messageId2, ImmutableList.of(mailbox1.getMailboxId(), mailbox2.getMailboxId()), session);

        long modSeqMessage1Mailbox1 = messageIdManager.getMessages(ImmutableList.of(messageId1), FetchGroupImpl.MINIMAL, session)
            .get(0)
            .getModSeq();
        long modSeqMessage2Mailbox1 = FluentIterable
            .from(messageIdManager.getMessages(ImmutableList.of(messageId2), FetchGroupImpl.MINIMAL, session))
            .filter(inMailbox(mailbox1.getMailboxId()))
            .toList()
            .get(0)
            .getModSeq();

        assertThat(modSeqMessage2Mailbox1).isGreaterThan(modSeqMessage1Mailbox1);
    }

    @Test
    public void setInMailboxesShouldNotChangeUidAndModSeqInOriginalMailbox() throws Exception {
        MessageId messageId = testingData.persist(mailbox1.getMailboxId(), messageUid1, FLAGS);
        MessageResult messageResult1 = messageIdManager.getMessages(ImmutableList.of(messageId), FetchGroupImpl.MINIMAL, session).get(0);
        MessageUid messageUid1 = messageResult1.getUid();
        long modSeq1 = messageResult1.getModSeq();

        messageIdManager.setInMailboxes(messageId, ImmutableList.of(mailbox2.getMailboxId()), session);

        MessageResult messageResult2 = FluentIterable
            .from(messageIdManager.getMessages(ImmutableList.of(messageId), FetchGroupImpl.MINIMAL, session))
            .filter(inMailbox(mailbox1.getMailboxId()))
            .toList()
            .get(0);
        MessageUid messageUid2 = messageResult2.getUid();
        long modSeq2 = messageResult2.getModSeq();

        assertThat(messageUid1).isEqualTo(messageUid2);
        assertThat(modSeq1).isEqualTo(modSeq2);
    }

    @Test
    public void setInMailboxesShouldAddAndRemoveMailboxes() throws Exception {
        MessageId messageId = testingData.persist(mailbox1.getMailboxId(), messageUid1, FLAGS);
        messageIdManager.setInMailboxes(messageId, ImmutableList.of(mailbox1.getMailboxId(), mailbox2.getMailboxId()), session);
        messageIdManager.setInMailboxes(messageId, ImmutableList.of(mailbox1.getMailboxId(), mailbox3.getMailboxId()), session);

        List<MailboxId> messageMailboxIds = FluentIterable.from(messageIdManager.getMessages(ImmutableList.of(messageId), FetchGroupImpl.MINIMAL, session))
            .transform(getMailboxId())
            .toList();

        assertThat(messageMailboxIds).containsOnly(mailbox1.getMailboxId(), mailbox3.getMailboxId());
    }

    @Test
    public void setInMailboxesShouldReplaceFlagsOfMessageInAddedMailboxes() throws Exception {
        Flags newFlags = new Flags(Flags.Flag.SEEN);
        MessageId messageId = testingData.persist(mailbox1.getMailboxId(), messageUid1, FLAGS);
        messageIdManager.setInMailboxes(messageId, ImmutableList.of(mailbox1.getMailboxId(), mailbox2.getMailboxId()), session);
        messageIdManager.setFlags(newFlags, MessageManager.FlagsUpdateMode.ADD, messageId, ImmutableList.of(mailbox1.getMailboxId(), mailbox2.getMailboxId()), session);

        messageIdManager.setInMailboxes(messageId, ImmutableList.of(mailbox1.getMailboxId(), mailbox3.getMailboxId()), session);

        MessageResult messageResult3 = FluentIterable
            .from(messageIdManager.getMessages(ImmutableList.of(messageId), FetchGroupImpl.MINIMAL, session))
            .filter(inMailbox(mailbox3.getMailboxId()))
            .toList()
            .get(0);
        assertThat(messageResult3.getFlags()).isEqualTo(newFlags);
    }

    @Test
    public void deleteMessageShouldRemoveMessageFromMailbox() throws Exception {
        MessageId messageId = testingData.persist(mailbox1.getMailboxId(), messageUid1, FLAGS);

        messageIdManager.delete(messageId, ImmutableList.of(mailbox1.getMailboxId()), session);

        assertThat(messageIdManager.getMessages(ImmutableList.of(messageId), FetchGroupImpl.MINIMAL, session)).isEmpty();
    }

    @Test
    public void deleteMessageShouldRemoveMessageOnlyFromMailbox() throws Exception {
        MessageId messageId = testingData.persist(mailbox1.getMailboxId(), messageUid1, FLAGS);
        messageIdManager.setInMailboxes(messageId, ImmutableList.of(mailbox2.getMailboxId()), session);

        messageIdManager.delete(messageId, ImmutableList.of(mailbox1.getMailboxId()), session);

        List<MessageResult> messageResults = messageIdManager.getMessages(ImmutableList.of(messageId), FetchGroupImpl.MINIMAL, session);
        assertThat(messageResults).hasSize(1);
        assertThat(messageResults.get(0).getMailboxId()).isEqualTo(mailbox2.getMailboxId());
    }

    @Test
    public void deleteMessageShouldNotRemoveMessageOnAnotherMailbox() throws Exception {
        MessageId messageId = testingData.persist(mailbox1.getMailboxId(), messageUid1, FLAGS);

        messageIdManager.delete(messageId, ImmutableList.of(mailbox2.getMailboxId()), session);

        List<MessageResult> messageResults = messageIdManager.getMessages(ImmutableList.of(messageId), FetchGroupImpl.MINIMAL, session);
        assertThat(messageResults).hasSize(1);
        assertThat(messageResults.get(0).getMailboxId()).isEqualTo(mailbox1.getMailboxId());
    }

    @Test
    public void setFlagsShouldUpdateFlags() throws Exception {
        Flags newFlags = new Flags(Flags.Flag.SEEN);
        MessageId messageId = testingData.persist(mailbox1.getMailboxId(), messageUid1, FLAGS);

        messageIdManager.setFlags(newFlags, MessageManager.FlagsUpdateMode.ADD, messageId, ImmutableList.of(mailbox1.getMailboxId()), session);

        MessageResult messageResult = messageIdManager.getMessages(ImmutableList.of(messageId), FetchGroupImpl.MINIMAL, session).get(0);
        assertThat(messageResult.getFlags()).isEqualTo(newFlags);
    }

    @Test
    public void setFlagsShouldNotChangeTheUid() throws Exception {
        Flags newFlags = new Flags(Flags.Flag.SEEN);
        MessageId messageId = testingData.persist(mailbox1.getMailboxId(), messageUid1, FLAGS);

        MessageResult messageResult1 = messageIdManager.getMessages(ImmutableList.of(messageId), FetchGroupImpl.MINIMAL, session).get(0);
        MessageUid messageUid1 = messageResult1.getUid();

        messageIdManager.setFlags(newFlags, MessageManager.FlagsUpdateMode.ADD, messageId, ImmutableList.of(mailbox1.getMailboxId()), session);

        MessageResult messageResult2 = messageIdManager.getMessages(ImmutableList.of(messageId), FetchGroupImpl.MINIMAL, session).get(0);
        MessageUid messageUid2 = messageResult2.getUid();

        assertThat(messageUid2).isEqualTo(messageUid1);
    }

    @Test
    public void setFlagsShouldChangeTheModSeq() throws Exception {
        Flags newFlags = new Flags(Flags.Flag.SEEN);
        MessageId messageId = testingData.persist(mailbox1.getMailboxId(), messageUid1, FLAGS);

        MessageResult messageResult1 = messageIdManager.getMessages(ImmutableList.of(messageId), FetchGroupImpl.MINIMAL, session).get(0);
        long modSeq1 = messageResult1.getModSeq();

        messageIdManager.setFlags(newFlags, MessageManager.FlagsUpdateMode.ADD, messageId, ImmutableList.of(mailbox1.getMailboxId()), session);

        MessageResult messageResult2 = messageIdManager.getMessages(ImmutableList.of(messageId), FetchGroupImpl.MINIMAL, session).get(0);
        long modSeq2 = messageResult2.getModSeq();

        assertThat(modSeq2).isGreaterThan(modSeq1);
    }

    @Test
    public void setFlagsShouldChangeFlagsInAllMailboxes() throws Exception {
        Flags newFlags = new Flags(Flags.Flag.SEEN);
        MessageId messageId = testingData.persist(mailbox1.getMailboxId(), messageUid1, FLAGS);
        messageIdManager.setInMailboxes(messageId, ImmutableList.of(mailbox2.getMailboxId()), session);
        
        messageIdManager.setFlags(newFlags, MessageManager.FlagsUpdateMode.ADD, messageId, ImmutableList.of(mailbox1.getMailboxId(), mailbox2.getMailboxId()), session);
        
        List<Flags> flags = FluentIterable
                .from(messageIdManager.getMessages(ImmutableList.of(messageId), FetchGroupImpl.MINIMAL, session))
                .transform(getFlags())
                .toList();

        assertThat(flags).hasSize(2);
        assertThat(flags.get(0)).isEqualTo(newFlags);
        assertThat(flags.get(1)).isEqualTo(newFlags);
    }

    @Test
    public void setFlagsShouldNotChangeFlagsOfAnotherMessageInSameMailbox() throws Exception {
        Flags newFlags = new Flags(Flags.Flag.SEEN);
        MessageId messageId1 = testingData.persist(mailbox1.getMailboxId(), messageUid1, FLAGS);
        MessageId messageId2 = testingData.persist(mailbox1.getMailboxId(), messageUid2, FLAGS);

        messageIdManager.setFlags(newFlags, MessageManager.FlagsUpdateMode.ADD, messageId2, ImmutableList.of(mailbox1.getMailboxId()), session);
        
        List<Flags> flags = FluentIterable
                .from(messageIdManager.getMessages(ImmutableList.of(messageId1), FetchGroupImpl.MINIMAL, session))
                .transform(getFlags())
                .toList();

        assertThat(flags).hasSize(1);
        assertThat(flags.get(0)).isEqualTo(FLAGS);
    }

    @Test
    public void setFlagsShouldNotChangeFlagsWhenEmptyMailboxes() throws Exception {
        Flags newFlags = new Flags(Flags.Flag.SEEN);
        MessageId messageId1 = testingData.persist(mailbox1.getMailboxId(), messageUid1, FLAGS);

        messageIdManager.setFlags(newFlags, MessageManager.FlagsUpdateMode.ADD, messageId1, ImmutableList.<MailboxId>of(), session);

        List<Flags> flags = FluentIterable
            .from(messageIdManager.getMessages(ImmutableList.of(messageId1), FetchGroupImpl.MINIMAL, session))
            .transform(getFlags())
            .toList();

        assertThat(flags).hasSize(1);
        assertThat(flags.get(0)).isEqualTo(FLAGS);
    }

    @Test
    public void setFlagsShouldNotChangeFlagsWhenMessageDoesNotBelongToTheMailboxes() throws Exception {
        Flags newFlags = new Flags(Flags.Flag.SEEN);
        MessageId messageId1 = testingData.persist(mailbox1.getMailboxId(), messageUid1, FLAGS);

        messageIdManager.setFlags(newFlags, MessageManager.FlagsUpdateMode.ADD, messageId1, ImmutableList.of(mailbox2.getMailboxId()), session);

        List<Flags> flags = FluentIterable
            .from(messageIdManager.getMessages(ImmutableList.of(messageId1), FetchGroupImpl.MINIMAL, session))
            .transform(getFlags())
            .toList();

        assertThat(flags).hasSize(1);
        assertThat(flags.get(0)).isEqualTo(FLAGS);
    }

    @Test
    public void setFlagsShouldChangeFlagsWhenMessageBelongsToTheMailboxes() throws Exception {
        Flags newFlags = new Flags(Flags.Flag.SEEN);
        MessageId messageId1 = testingData.persist(mailbox1.getMailboxId(), messageUid1, FLAGS);
        MessageId messageId2 = testingData.persist(mailbox2.getMailboxId(), messageUid2, FLAGS);

        messageIdManager.setFlags(newFlags, MessageManager.FlagsUpdateMode.ADD, messageId1, ImmutableList.of(mailbox1.getMailboxId(), mailbox2.getMailboxId()), session);

        final List<Map.Entry<MessageId, Flags>> entries = FluentIterable.from(messageIdManager.getMessages(ImmutableList.of(messageId1, messageId2), FetchGroupImpl.MINIMAL, session))
            .transform(toMapEntryOfFlags())
            .toList();
        ImmutableMap.Builder<MessageId, Flags> builder = ImmutableMap.builder();
        for (Map.Entry<MessageId, Flags> entry : entries) {
            builder.put(entry);
        }
        Map<MessageId, Flags> flags = builder.build();

        assertThat(flags).hasSize(2);
        assertThat(flags.get(messageId1)).isEqualTo(newFlags);
        assertThat(flags.get(messageId2)).isEqualTo(FLAGS);
    }

    @Test
    public void getMessageShouldBeEmptyWhenMessageHasNoMoreMailboxes() throws Exception {
        MessageId messageId = testingData.persist(mailbox1.getMailboxId(), messageUid1, FLAGS);

        testingData.deleteMailbox(mailbox1.getMailboxId());

        assertThat(messageIdManager.getMessages(ImmutableList.of(messageId), FetchGroupImpl.MINIMAL, session)).isEmpty();
    }

    @Test
    public void setInMailboxesShouldPreserveMessageFromOneMailboxDeletion() throws Exception {
        MessageId messageId = testingData.persist(mailbox1.getMailboxId(), messageUid1, FLAGS);
        messageIdManager.setInMailboxes(messageId, ImmutableList.of(mailbox2.getMailboxId()), session);

        testingData.deleteMailbox(mailbox1.getMailboxId());

        List<MessageResult> messageResults = messageIdManager.getMessages(ImmutableList.of(messageId), FetchGroupImpl.MINIMAL, session);
        assertThat(messageResults).hasSize(1);
        assertThat(messageResults.get(0).getMailboxId()).isEqualTo(mailbox2.getMailboxId());
    }

    private Predicate<MessageResult> inMailbox(final MailboxId mailboxId) {
        return new Predicate<MessageResult>() {
            @Override
            public boolean apply(MessageResult input) {
                return input.getMailboxId().equals(mailboxId);
            }
        };
    }

}
