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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import javax.mail.Flags;
import javax.mail.Flags.Flag;

import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.mock.MockMailboxSession;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.store.mail.AttachmentMapper;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.MessageMapper.FetchType;
import org.apache.james.mailbox.store.mail.model.AttachmentId;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public abstract class AbstractMailboxManagerAttachmentTest {
    private static final String USERNAME = "user@domain.tld";
    private static final Date SUN_SEP_9TH_2001 = new Date(1000000000000L);

    private MailboxManager mailboxManager;
    private MessageMapper messageMapper;
    private MailboxMapper mailboxMapper;
    private MailboxPath inboxPath;
    private MailboxSession mailboxSession;
    private Mailbox inbox;
    private MessageManager inboxMessageManager;
    private AttachmentMapper attachmentMapper;

    protected abstract MailboxManager getMailboxManager();
    protected abstract MailboxManager getParseFailingMailboxManager();
    protected abstract MailboxSessionMapperFactory getMailboxSessionMapperFactory();
    
    protected void clean() {
    }

    @Before
    public void setUp() throws Exception {
        mailboxSession = new MockMailboxSession(USERNAME);
        messageMapper = getMailboxSessionMapperFactory().getMessageMapper(mailboxSession);
        mailboxMapper = getMailboxSessionMapperFactory().getMailboxMapper(mailboxSession);
        inboxPath = new MailboxPath(MailboxConstants.USER_NAMESPACE, USERNAME, "INBOX");
        mailboxManager = getMailboxManager();
        mailboxManager.createMailbox(inboxPath, mailboxSession);
        inbox = mailboxMapper.findMailboxByPath(inboxPath);
        inboxMessageManager = mailboxManager.getMailbox(inboxPath, mailboxSession);
        attachmentMapper = getMailboxSessionMapperFactory().getAttachmentMapper(mailboxSession);
    }

    @After
    public void tearDown() {
        clean();
    }

    @Test
    public void appendMessageShouldStoreWithoutAttachmentWhenMailWithoutAttachment() throws Exception {
        String mail = "Subject: Test\n\nBody";
        InputStream mailInputStream = new ByteArrayInputStream(mail.getBytes());
        inboxMessageManager.appendMessage(mailInputStream, SUN_SEP_9TH_2001, mailboxSession, true, new Flags(Flags.Flag.RECENT));
        
        Iterator<MailboxMessage> messages = messageMapper.findInMailbox(inbox, MessageRange.all(), FetchType.Full, 1);
        assertThat(messages.hasNext()).isTrue();
        assertThat(messages.next().getAttachmentsIds()).isEmpty();
    }

    @Test
    public void appendMessageShouldStoreAttachmentWhenMailWithOneAttachment() throws Exception {
        InputStream mailInputStream = ClassLoader.getSystemResourceAsStream("eml/oneAttachmentAndSomeInlined.eml");
        inboxMessageManager.appendMessage(mailInputStream, SUN_SEP_9TH_2001, mailboxSession, true, new Flags(Flags.Flag.RECENT));
        
        Iterator<MailboxMessage> messages = messageMapper.findInMailbox(inbox, MessageRange.all(), FetchType.Full, 1);
        assertThat(messages.hasNext()).isTrue();
        assertThat(messages.next().getAttachmentsIds()).hasSize(1);
    }

    @Test
    public void appendMessageShouldStoreARetrievableAttachmentWhenMailWithOneAttachment() throws Exception {
        InputStream mailInputStream = ClassLoader.getSystemResourceAsStream("eml/oneAttachmentAndSomeInlined.eml");
        inboxMessageManager.appendMessage(mailInputStream, SUN_SEP_9TH_2001, mailboxSession, true, new Flags(Flags.Flag.RECENT));
        
        Iterator<MailboxMessage> messages = messageMapper.findInMailbox(inbox, MessageRange.all(), FetchType.Full, 1);
        assertThat(messages.hasNext()).isTrue();
        List<AttachmentId> attachmentsIds = messages.next().getAttachmentsIds();
        assertThat(attachmentsIds).hasSize(1);
        assertThat(attachmentMapper.getAttachment(attachmentsIds.get(0)).getStream())
            .hasContentEqualTo(ClassLoader.getSystemResourceAsStream("eml/gimp.png"));
    }

    @Test
    public void appendMessageShouldStoreAttachmentsWhenMailWithTwoAttachment() throws Exception {
        InputStream mailInputStream = ClassLoader.getSystemResourceAsStream("eml/twoAttachments.eml");
        inboxMessageManager.appendMessage(mailInputStream, SUN_SEP_9TH_2001, mailboxSession, true, new Flags(Flags.Flag.RECENT));
        
        Iterator<MailboxMessage> messages = messageMapper.findInMailbox(inbox, MessageRange.all(), FetchType.Full, 1);
        assertThat(messages.hasNext()).isTrue();
        assertThat(messages.next().getAttachmentsIds()).hasSize(2);
    }

    @Test
    public void appendMessageShouldStoreTwoRetrievableAttachmentsWhenMailWithTwoAttachment() throws Exception {
        InputStream mailInputStream = ClassLoader.getSystemResourceAsStream("eml/twoAttachments.eml");
        inboxMessageManager.appendMessage(mailInputStream, SUN_SEP_9TH_2001, mailboxSession, true, new Flags(Flags.Flag.RECENT));
        
        Iterator<MailboxMessage> messages = messageMapper.findInMailbox(inbox, MessageRange.all(), FetchType.Full, 1);
        assertThat(messages.hasNext()).isTrue();
        List<AttachmentId> attachmentsIds = messages.next().getAttachmentsIds();
        assertThat(attachmentsIds).hasSize(2);
        assertThat(attachmentMapper.getAttachment(attachmentsIds.get(0)).getStream())
            .hasContentEqualTo(ClassLoader.getSystemResourceAsStream("eml/4037_014.jpg"));
        assertThat(attachmentMapper.getAttachment(attachmentsIds.get(1)).getStream())
            .hasContentEqualTo(ClassLoader.getSystemResourceAsStream("eml/4037_015.jpg"));
    }

    @Test
    public void appendMessageShouldStoreEmbeddedMailAsAttachmentWhenMailWithEmbeddedAttachment() throws Exception {
        InputStream mailInputStream = ClassLoader.getSystemResourceAsStream("eml/embeddedAttachmentWithAttachment.eml");
        inboxMessageManager.appendMessage(mailInputStream, SUN_SEP_9TH_2001, mailboxSession, true, new Flags(Flags.Flag.RECENT));
        
        Iterator<MailboxMessage> messages = messageMapper.findInMailbox(inbox, MessageRange.all(), FetchType.Full, 1);
        assertThat(messages.hasNext()).isTrue();
        assertThat(messages.next().getAttachmentsIds()).hasSize(1);
    }

    @Test
    public void appendMessageShouldNotStoreAnyAttachmentWhenUnparsableMail() throws Exception {
        MailboxManager parseFailingMailboxManager = getParseFailingMailboxManager();
        MessageManager parseFailingInboxMessageManager = parseFailingMailboxManager.getMailbox(inboxPath, mailboxSession);
        InputStream mailInputStream = new ByteArrayInputStream("content".getBytes());
        parseFailingInboxMessageManager.appendMessage(mailInputStream, SUN_SEP_9TH_2001, mailboxSession, true, new Flags(Flag.RECENT));

        Iterator<MailboxMessage> messages = messageMapper.findInMailbox(inbox, MessageRange.all(), FetchType.Full, 1);
        assertThat(messages.hasNext()).isTrue();
        List<AttachmentId> attachmentsIds = messages.next().getAttachmentsIds();
        assertThat(attachmentsIds).hasSize(0);
    }

    @Test
    public void appendMessageShouldStoreOnceWhenDuplicateAttachment() throws Exception {
        InputStream mailInputStream = ClassLoader.getSystemResourceAsStream("eml/oneAttachmentAndSomeInlined.eml");
        InputStream mailInputStream2 = ClassLoader.getSystemResourceAsStream("eml/oneAttachmentAndSomeInlined.eml");
        String user2 = "user2@domain.tld";
        MailboxSession user2MailboxSession = new MockMailboxSession(user2);
        MessageMapper user2MessageMapper = getMailboxSessionMapperFactory().getMessageMapper(user2MailboxSession);
        MailboxMapper user2MailboxMapper = getMailboxSessionMapperFactory().getMailboxMapper(user2MailboxSession);
        MailboxPath user2InboxPath = new MailboxPath(MailboxConstants.USER_NAMESPACE, user2, "INBOX");
        mailboxManager.createMailbox(user2InboxPath, user2MailboxSession);
        Mailbox user2Inbox = user2MailboxMapper.findMailboxByPath(user2InboxPath);
        MessageManager user2InboxMessageManager = mailboxManager.getMailbox(user2InboxPath, user2MailboxSession);

        inboxMessageManager.appendMessage(mailInputStream, SUN_SEP_9TH_2001, mailboxSession, true, new Flags(Flags.Flag.RECENT));
        user2InboxMessageManager.appendMessage(mailInputStream2, SUN_SEP_9TH_2001, user2MailboxSession, true, new Flags(Flags.Flag.RECENT));

        Iterator<MailboxMessage> messages = messageMapper.findInMailbox(inbox, MessageRange.all(), FetchType.Full, 1);
        Iterator<MailboxMessage> user2Messages = user2MessageMapper.findInMailbox(user2Inbox, MessageRange.all(), FetchType.Full, 1);
        assertThat(messages.hasNext()).isTrue();
        List<AttachmentId> attachmentsIds = messages.next().getAttachmentsIds();
        assertThat(attachmentsIds).hasSize(1);
        assertThat(user2Messages.hasNext()).isTrue();
        List<AttachmentId> user2AttachmentsIds = user2Messages.next().getAttachmentsIds();
        assertThat(attachmentsIds.equals(user2AttachmentsIds)).isTrue();
    }
}

