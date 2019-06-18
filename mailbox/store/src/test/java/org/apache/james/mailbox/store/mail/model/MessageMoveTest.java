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

import javax.mail.Flags;
import javax.mail.util.SharedByteArrayInputStream;

import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.MessageMapper.FetchType;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public abstract class MessageMoveTest {

    private static final char DELIMITER = '.';
    private static final int LIMIT = 10;
    private static final int BODY_START = 16;
    public static final int UID_VALIDITY = 42;

    private MapperProvider mapperProvider;
    private MessageMapper messageMapper;
    private MailboxMapper mailboxMapper;

    private Mailbox benwaInboxMailbox;
    private Mailbox benwaWorkMailbox;

    private MailboxMessage message1;

    protected abstract MapperProvider createMapperProvider();

    @Rule
    public ExpectedException expected = ExpectedException.none();

    public void setUp() throws Exception {
        this.mapperProvider = createMapperProvider();
        Assume.assumeTrue(mapperProvider.getSupportedCapabilities().contains(MapperProvider.Capabilities.MOVE));
        this.messageMapper = mapperProvider.createMessageMapper();
        Assume.assumeNotNull(messageMapper);
        this.mailboxMapper = mapperProvider.createMailboxMapper();
        Assume.assumeNotNull(mailboxMapper);

        benwaInboxMailbox = createMailbox(MailboxPath.forUser("benwa", "INBOX"));
        benwaWorkMailbox = createMailbox(MailboxPath.forUser("benwa", "INBOX" + DELIMITER + "work"));
        message1 = createMessage(benwaInboxMailbox, mapperProvider.generateMessageId(), "Subject: Test1 \n\nBody1\n.\n", BODY_START, new PropertyBuilder());
    }

    @Test
    public void movingAMessageShouldWork() throws Exception {
        messageMapper.add(benwaInboxMailbox, message1);
        message1.setModSeq(messageMapper.getHighestModSeq(benwaInboxMailbox));

        messageMapper.move(benwaWorkMailbox, message1);
        
        assertThat(retrieveMessageFromStorage(benwaWorkMailbox, message1).getUid()).isEqualTo(message1.getUid());
    }

    @Test
    public void movingAMessageShouldReturnCorrectMetadata() throws Exception {
        messageMapper.add(benwaInboxMailbox, message1);
        message1.setModSeq(messageMapper.getHighestModSeq(benwaInboxMailbox));

        MessageMetaData messageMetaData = messageMapper.move(benwaWorkMailbox, message1);

        Flags expectedFlags = message1.createFlags();
        expectedFlags.add(Flags.Flag.RECENT);
        assertThat(messageMetaData.getFlags()).isEqualTo(expectedFlags);
        assertThat(messageMetaData.getUid()).isEqualTo(messageMapper.getLastUid(benwaWorkMailbox).get());
        assertThat(messageMetaData.getModSeq()).isEqualTo(messageMapper.getHighestModSeq(benwaWorkMailbox));
    }

    @Test
    public void movingAMessageShouldNotViolateMessageCount() throws Exception {
        messageMapper.add(benwaInboxMailbox, message1);
        message1.setModSeq(messageMapper.getHighestModSeq(benwaInboxMailbox));

        messageMapper.move(benwaWorkMailbox, message1);

        assertThat(messageMapper.countMessagesInMailbox(benwaInboxMailbox)).isEqualTo(0);
        assertThat(messageMapper.countMessagesInMailbox(benwaWorkMailbox)).isEqualTo(1);
    }

    @Test
    public void movingAMessageShouldNotViolateUnseenMessageCount() throws Exception {
        messageMapper.add(benwaInboxMailbox, message1);
        message1.setModSeq(messageMapper.getHighestModSeq(benwaInboxMailbox));

        messageMapper.move(benwaWorkMailbox, message1);

        assertThat(messageMapper.countUnseenMessagesInMailbox(benwaInboxMailbox)).isEqualTo(0);
        assertThat(messageMapper.countUnseenMessagesInMailbox(benwaWorkMailbox)).isEqualTo(1);
    }

    @Test
    public void movingASeenMessageShouldNotIncrementUnseenMessageCount() throws Exception {
        message1.setFlags(new Flags(Flags.Flag.SEEN));
        messageMapper.add(benwaInboxMailbox, message1);
        message1.setModSeq(messageMapper.getHighestModSeq(benwaInboxMailbox));

        messageMapper.move(benwaWorkMailbox, message1);

        assertThat(messageMapper.countUnseenMessagesInMailbox(benwaInboxMailbox)).isEqualTo(0);
        assertThat(messageMapper.countUnseenMessagesInMailbox(benwaWorkMailbox)).isEqualTo(0);
    }

    private Mailbox createMailbox(MailboxPath mailboxPath) throws MailboxException {
        Mailbox mailbox = new Mailbox(mailboxPath, UID_VALIDITY);
        MailboxId id = mapperProvider.generateId();
        mailbox.setMailboxId(id);
        mailboxMapper.save(mailbox);
        
        return mailbox;
    }

    private MailboxMessage retrieveMessageFromStorage(Mailbox mailbox, MailboxMessage message) throws MailboxException {
        return messageMapper.findInMailbox(mailbox, MessageRange.one(message.getUid()), FetchType.Metadata, LIMIT).next();
    }
    
    private MailboxMessage createMessage(Mailbox mailbox, MessageId messageId, String content, int bodyStart, PropertyBuilder propertyBuilder) throws MailboxException {
        return new SimpleMailboxMessage(messageId, new Date(), content.length(), bodyStart, new SharedByteArrayInputStream(content.getBytes()), new Flags(), propertyBuilder, mailbox.getMailboxId());
    }
}
