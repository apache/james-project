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

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.mail.Flags;

import org.apache.commons.lang.NotImplementedException;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.exception.SubscriptionException;
import org.apache.james.mailbox.manager.MailboxManagerFixture;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.store.mail.AnnotationMapper;
import org.apache.james.mailbox.store.mail.AttachmentMapper;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.MessageIdMapper;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.ModSeqProvider;
import org.apache.james.mailbox.store.mail.UidProvider;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailbox;
import org.apache.james.mailbox.store.user.SubscriptionMapper;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Throwables;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableMap;

public class TestMailboxSessionMapperFactory extends MailboxSessionMapperFactory {
    private static final long MOD_SEQ = 18;
    private static final MessageUid UID = MessageUid.of(28);
    private static final long UID_VALIDITY = 42;

    private final SimpleMailbox mailbox1;
    private final SimpleMailbox mailbox2;
    private final SimpleMailbox mailbox3;
    private final SimpleMailbox mailbox4;

    private final List<MailboxMessage> messages = new ArrayList<MailboxMessage>();
    private final MailboxMapper mailboxMapper;
    private final MessageIdMapper messageIdMapper;

    public TestMailboxSessionMapperFactory() {


        mailbox1 = new SimpleMailbox(MailboxManagerFixture.MAILBOX_PATH1, UID_VALIDITY, TestId.of(36));
        mailbox2 = new SimpleMailbox(MailboxManagerFixture.MAILBOX_PATH2, UID_VALIDITY, TestId.of(46));
        mailbox3 = new SimpleMailbox(MailboxManagerFixture.MAILBOX_PATH3, UID_VALIDITY, TestId.of(56));
        mailbox4 = new SimpleMailbox(MailboxManagerFixture.MAILBOX_PATH4, UID_VALIDITY, TestId.of(66));

        mailboxMapper = new MailboxMapper() {
            @Override
            public MailboxId save(Mailbox mailbox) throws MailboxException {
                throw new NotImplementedException();
            }

            @Override
            public void delete(Mailbox mailbox) throws MailboxException {
                throw new NotImplementedException();

            }

            @Override
            public Mailbox findMailboxByPath(MailboxPath mailboxName) throws MailboxException {
                if (mailboxName.equals(MailboxManagerFixture.MAILBOX_PATH1)) {
                    return mailbox1;
                }
                if (mailboxName.equals(MailboxManagerFixture.MAILBOX_PATH2)) {
                    return mailbox2;
                }
                if (mailboxName.equals(MailboxManagerFixture.MAILBOX_PATH3)) {
                    return mailbox3;
                }
                throw new IllegalArgumentException("Unknown mailbox : " + mailboxName + " must be one of "
                    + MailboxManagerFixture.MAILBOX_PATH1 + " "
                    + MailboxManagerFixture.MAILBOX_PATH2 + " "
                    + MailboxManagerFixture.MAILBOX_PATH3);
            }

            @Override
            public Mailbox findMailboxById(MailboxId mailboxId) throws MailboxException {
                if (mailboxId.equals(mailbox1.getMailboxId())) {
                    return mailbox1;
                }
                if (mailboxId.equals(mailbox2.getMailboxId())) {
                    return mailbox2;
                }
                if (mailboxId.equals(mailbox3.getMailboxId())) {
                    return mailbox3;
                }
                throw new IllegalArgumentException("Unknown mailboxId : " + mailboxId + " must be one of " + mailbox1.getMailboxId() + " " + mailbox2.getMailboxId() + " " + mailbox3.getMailboxId());
            }

            @Override
            public List<Mailbox> findMailboxWithPathLike(MailboxPath mailboxPath) throws MailboxException {
                throw new NotImplementedException();
            }

            @Override
            public boolean hasChildren(Mailbox mailbox, char delimiter) throws MailboxException {
                throw new NotImplementedException();
            }

            @Override
            public void updateACL(Mailbox mailbox, MailboxACL.MailboxACLCommand mailboxACLCommand) throws MailboxException {
                throw new NotImplementedException();
            }

            @Override
            public List<Mailbox> list() throws MailboxException {
                throw new NotImplementedException();
            }

            @Override
            public void endRequest() {
                throw new NotImplementedException();
            }

            @Override
            public <T> T execute(Transaction<T> transaction) throws MailboxException {
                throw new NotImplementedException();
            }
        };
        messageIdMapper = new MessageIdMapper() {

            @Override
            public List<MailboxMessage> find(final List<MessageId> messageIds, MessageMapper.FetchType fetchType) {
                return FluentIterable.from(messages)
                    .filter(withMessageIdOneOf(messageIds))
                    .toList();
            }

            @Override
            public List<MailboxId> findMailboxes(final MessageId messageId) {
                return FluentIterable.from(messages)
                    .filter(withMessageId(messageId))
                    .transform(MailboxMessage::getMailboxId)
                    .toList();
            }

            @Override
            public void save(MailboxMessage mailboxMessage) throws MailboxException {
                messages.add(mailboxMessage);
            }

            @Override
            public void copyInMailbox(MailboxMessage mailboxMessage) throws MailboxNotFoundException, MailboxException {
                messages.add(mailboxMessage);
            }

            @Override
            public void delete(final MessageId messageId) {
                messages.removeAll(
                    FluentIterable.from(messages)
                        .filter(inMailbox(messageId))
                        .toList());
            }

            @Override
            public void delete(final MessageId messageId, final List<MailboxId> mailboxIds) {
                messages.removeAll(
                    FluentIterable.from(messages)
                        .filter(withMessageId(messageId))
                        .filter(inMailboxes(mailboxIds))
                        .toList());
            }

            @Override
            public Map<MailboxId, UpdatedFlags> setFlags(MessageId messageId, List<MailboxId> mailboxIds, Flags newState, MessageManager.FlagsUpdateMode updateMode) throws MailboxException {
                final List<Map.Entry<MailboxId, UpdatedFlags>> entries = FluentIterable.from(messages)
                    .filter(withMessageId(messageId))
                    .filter(inMailboxes(mailboxIds))
                    .transform(toMapEntryOfUpdatedFlags(newState, updateMode))
                    .filter(isChanged())
                    .toList();
                ImmutableMap.Builder<MailboxId, UpdatedFlags> builder = ImmutableMap.builder();
                for (Map.Entry<MailboxId, UpdatedFlags> entry : entries) {
                    builder.put(entry);
                }
                return builder.build();
            }
        };
    }

    public SimpleMailbox getMailbox1() {
        return mailbox1;
    }

    public SimpleMailbox getMailbox2() {
        return mailbox2;
    }

    public SimpleMailbox getMailbox3() {
        return mailbox3;
    }

    public SimpleMailbox getMailbox4() {
        return mailbox4;
    }

    @Override
    public UidProvider getUidProvider() {
        UidProvider uidProvider = mock(UidProvider.class);
        try {
            when(uidProvider.nextUid(any(MailboxSession.class), any(MailboxId.class))).thenReturn(UID);
        } catch (MailboxException e) {
            throw Throwables.propagate(e);
        }
        return uidProvider;
    }

    @Override
    public ModSeqProvider getModSeqProvider() {
        ModSeqProvider modSeqProvider = mock(ModSeqProvider.class);
        try {
            when(modSeqProvider.nextModSeq(any(MailboxSession.class), any(MailboxId.class))).thenReturn(MOD_SEQ);
        } catch (MailboxException e) {
            throw Throwables.propagate(e);
        }
        return modSeqProvider;
    }

    @Override
    public AnnotationMapper createAnnotationMapper(MailboxSession session) throws MailboxException {
        throw new NotImplementedException();
    }

    @Override
    public MessageMapper createMessageMapper(MailboxSession session) throws MailboxException {
        throw new NotImplementedException();
    }

    @Override
    public AttachmentMapper createAttachmentMapper(MailboxSession session) throws MailboxException {
        throw new NotImplementedException();
    }

    @Override
    public MailboxMapper createMailboxMapper(MailboxSession session) throws MailboxException {
        return mailboxMapper;
    }

    @Override
    public MessageIdMapper createMessageIdMapper(MailboxSession session) throws MailboxException {
        return messageIdMapper;
    }

    @Override
    public SubscriptionMapper createSubscriptionMapper(MailboxSession session) throws SubscriptionException {
        throw new NotImplementedException();
    }

    public void clean() {
        messages.clear();
    }

    private Predicate<MailboxMessage> withMessageIdOneOf(final List<MessageId> messageIds) {
        return mailboxMessage -> messageIds.contains(mailboxMessage.getMessageId());
    }

    private Predicate<MailboxMessage> inMailbox(final MessageId messageId) {
        return mailboxMessage -> mailboxMessage.getMailboxId().equals(messageId);
    }

    private Predicate<MailboxMessage> inMailboxes(final List<MailboxId> mailboxIds) {
        return mailboxMessage -> mailboxIds.contains(mailboxMessage.getMailboxId());
    }

    private Predicate<MailboxMessage> withMessageId(final MessageId messageId) {
        return mailboxMessage -> mailboxMessage.getMessageId().equals(messageId);
    }

    private Predicate<Map.Entry<MailboxId, UpdatedFlags>> isChanged() {
        return entry -> entry.getValue().flagsChanged();
    }

    private Function<MailboxMessage, Map.Entry<MailboxId, UpdatedFlags>> toMapEntryOfUpdatedFlags(final Flags newState, final MessageManager.FlagsUpdateMode updateMode) {
        return mailboxMessage -> {
            Preconditions.checkState(updateMode.equals(MessageManager.FlagsUpdateMode.ADD));
            return new AbstractMap.SimpleEntry<>(mailboxMessage.getMailboxId(),
                UpdatedFlags.builder()
                    .uid(mailboxMessage.getUid())
                    .modSeq(mailboxMessage.getModSeq())
                    .newFlags(newState)
                    .oldFlags(mailboxMessage.createFlags())
                    .build());
        };
    }
}
