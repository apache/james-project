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

package org.apache.james.mailbox.store.mail.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Date;

import jakarta.mail.Flags;

import org.apache.james.core.Username;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.ByteContent;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.mailbox.model.UidValidity;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.MessageMapper.FetchType;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.junit.Assume;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public abstract class MessageMoveTest {

    private static final char DELIMITER = '.';
    private static final int LIMIT = 10;
    private static final int BODY_START = 16;
    private static final UidValidity UID_VALIDITY = UidValidity.of(42);

    private MapperProvider mapperProvider;
    private MessageMapper messageMapper;
    private MailboxMapper mailboxMapper;

    private Mailbox benwaInboxMailbox;
    private Mailbox benwaWorkMailbox;

    private MailboxMessage message1;

    protected abstract MapperProvider createMapperProvider();

    @BeforeEach
    void setUp() throws Exception {
        this.mapperProvider = createMapperProvider();
        Assume.assumeTrue(mapperProvider.getSupportedCapabilities().contains(MapperProvider.Capabilities.MOVE));
        this.messageMapper = mapperProvider.createMessageMapper();
        Assume.assumeNotNull(messageMapper);
        this.mailboxMapper = mapperProvider.createMailboxMapper();
        Assume.assumeNotNull(mailboxMapper);

        Username benwa = Username.of("benwa");
        benwaInboxMailbox = createMailbox(MailboxPath.forUser(benwa, "INBOX"));
        benwaWorkMailbox = createMailbox(MailboxPath.forUser(benwa, "INBOX" + DELIMITER + "work"));
        message1 = createMessage(benwaInboxMailbox, mapperProvider.generateMessageId(), "Subject: Test1 \n\nBody1\n.\n", BODY_START, new PropertyBuilder());
    }

    @Test
    void movingAMessageShouldWork() throws Exception {
        messageMapper.add(benwaInboxMailbox, message1);
        message1.setModSeq(messageMapper.getHighestModSeq(benwaInboxMailbox));

        MailboxMessage messageToMove = messageMapper.findInMailbox(benwaInboxMailbox, MessageRange.one(message1.getUid()), FetchType.METADATA, 1).next();
        messageMapper.move(benwaWorkMailbox, messageToMove);
        
        assertThat(retrieveMessageFromStorage(benwaWorkMailbox, message1).getUid()).isEqualTo(message1.getUid());
    }

    @Test
    void movingAMessageShouldReturnCorrectMetadata() throws Exception {
        messageMapper.add(benwaInboxMailbox, message1);
        message1.setModSeq(messageMapper.getHighestModSeq(benwaInboxMailbox));

        MailboxMessage messageToMove = messageMapper.findInMailbox(benwaInboxMailbox, MessageRange.one(message1.getUid()), FetchType.METADATA, 1).next();
        MessageMetaData messageMetaData = messageMapper.move(benwaWorkMailbox, messageToMove);

        Flags expectedFlags = message1.createFlags();
        expectedFlags.add(Flags.Flag.RECENT);
        assertThat(messageMetaData.getFlags()).isEqualTo(expectedFlags);
        assertThat(messageMetaData.getUid()).isEqualTo(messageMapper.getLastUid(benwaWorkMailbox).get());
        assertThat(messageMetaData.getModSeq()).isEqualTo(messageMapper.getHighestModSeq(benwaWorkMailbox));
    }

    @Test
    void movingAMessageShouldNotViolateMessageCount() throws Exception {
        messageMapper.add(benwaInboxMailbox, message1);
        message1.setModSeq(messageMapper.getHighestModSeq(benwaInboxMailbox));

        MailboxMessage messageToMove = messageMapper.findInMailbox(benwaInboxMailbox, MessageRange.one(message1.getUid()), FetchType.METADATA, 1).next();
        messageMapper.move(benwaWorkMailbox, messageToMove);

        assertThat(messageMapper.countMessagesInMailbox(benwaInboxMailbox)).isEqualTo(0);
        assertThat(messageMapper.countMessagesInMailbox(benwaWorkMailbox)).isEqualTo(1);
    }

    @Test
    void movingAMessageShouldNotViolateUnseenMessageCount() throws Exception {
        messageMapper.add(benwaInboxMailbox, message1);
        message1.setModSeq(messageMapper.getHighestModSeq(benwaInboxMailbox));

        MailboxMessage messageToMove = messageMapper.findInMailbox(benwaInboxMailbox, MessageRange.one(message1.getUid()), FetchType.METADATA, 1).next();
        messageMapper.move(benwaWorkMailbox, messageToMove);

        assertThat(messageMapper.getMailboxCounters(benwaInboxMailbox).getUnseen()).isEqualTo(0);
        assertThat(messageMapper.getMailboxCounters(benwaWorkMailbox).getUnseen()).isEqualTo(1);
    }

    @Test
    void movingASeenMessageShouldNotIncrementUnseenMessageCount() throws Exception {
        message1.setFlags(new Flags(Flags.Flag.SEEN));
        messageMapper.add(benwaInboxMailbox, message1);
        message1.setModSeq(messageMapper.getHighestModSeq(benwaInboxMailbox));

        MailboxMessage messageToMove = messageMapper.findInMailbox(benwaInboxMailbox, MessageRange.one(message1.getUid()), FetchType.METADATA, 1).next();
        messageMapper.move(benwaWorkMailbox, messageToMove);

        assertThat(messageMapper.getMailboxCounters(benwaInboxMailbox).getUnseen()).isEqualTo(0);
        assertThat(messageMapper.getMailboxCounters(benwaWorkMailbox).getUnseen()).isEqualTo(0);
    }

    private Mailbox createMailbox(MailboxPath mailboxPath) {
        return mailboxMapper.create(mailboxPath, UID_VALIDITY).block();
    }

    private MailboxMessage retrieveMessageFromStorage(Mailbox mailbox, MailboxMessage message) throws MailboxException {
        return messageMapper.findInMailbox(mailbox, MessageRange.one(message.getUid()), FetchType.METADATA, LIMIT).next();
    }
    
    private MailboxMessage createMessage(Mailbox mailbox, MessageId messageId, String content, int bodyStart, PropertyBuilder propertyBuilder) {
        return new SimpleMailboxMessage(messageId, ThreadId.fromBaseMessageId(messageId),new Date(), content.length(), bodyStart, new ByteContent(content.getBytes()), new Flags(), propertyBuilder.build(), mailbox.getMailboxId());
    }
}
