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
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.mock.MockMailboxSession;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageAttachment;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.store.mail.AttachmentMapper;
import org.apache.james.mailbox.store.mail.AttachmentMapperFactory;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.MessageMapper.FetchType;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.junit.Test;

public abstract class AbstractMailboxManagerAttachmentTest {
    private static final String USERNAME = "user@domain.tld";

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
    
    protected abstract AttachmentMapperFactory getAttachmentMapperFactory();
    
    public void setUp() throws Exception {
        mailboxSession = new MockMailboxSession(USERNAME);
        messageMapper = getMailboxSessionMapperFactory().getMessageMapper(mailboxSession);
        mailboxMapper = getMailboxSessionMapperFactory().getMailboxMapper(mailboxSession);
        inboxPath = MailboxPath.forUser(USERNAME, "INBOX");
        mailboxManager = getMailboxManager();
        mailboxManager.createMailbox(inboxPath, mailboxSession);
        inbox = mailboxMapper.findMailboxByPath(inboxPath);
        inboxMessageManager = mailboxManager.getMailbox(inboxPath, mailboxSession);
        attachmentMapper = getAttachmentMapperFactory().createAttachmentMapper(mailboxSession);
    }

    @Test
    public void appendMessageShouldStoreWithoutAttachmentWhenMailWithoutAttachment() throws Exception {
        String mail = "Subject: Test\n\nBody";
        InputStream mailInputStream = new ByteArrayInputStream(mail.getBytes());
        inboxMessageManager.appendMessage(MessageManager.AppendCommand.builder()
            .build(mailInputStream), mailboxSession);
        
        Iterator<MailboxMessage> messages = messageMapper.findInMailbox(inbox, MessageRange.all(), FetchType.Full, 1);
        assertThat(messages.hasNext()).isTrue();
        assertThat(messages.next().getAttachments()).isEmpty();
    }

    @Test
    public void appendMessageShouldStoreAttachmentWhenMailWithOneAttachment() throws Exception {
        InputStream mailInputStream = ClassLoader.getSystemResourceAsStream("eml/oneAttachmentAndSomeTextInlined.eml");
        inboxMessageManager.appendMessage(MessageManager.AppendCommand.builder()
            .build(mailInputStream), mailboxSession);

        Iterator<MailboxMessage> messages = messageMapper.findInMailbox(inbox, MessageRange.all(), FetchType.Full, 1);
        assertThat(messages.hasNext()).isTrue();
        assertThat(messages.next().getAttachments()).hasSize(1);
    }

    @Test
    public void appendMessageShouldStoreAttachmentNameWhenMailWithOneAttachment() throws Exception {
        InputStream mailInputStream = ClassLoader.getSystemResourceAsStream("eml/oneAttachmentAndSomeTextInlined.eml");
        inboxMessageManager.appendMessage(MessageManager.AppendCommand.builder()
            .build(mailInputStream), mailboxSession);

        Optional<String> expectedName = Optional.of("exploits_of_a_mom.png");

        Iterator<MailboxMessage> messages = messageMapper.findInMailbox(inbox, MessageRange.all(), FetchType.Full, 1);
        List<MessageAttachment> attachments = messages.next().getAttachments();
        assertThat(attachments.get(0).getName()).isEqualTo(expectedName);
    }

    @Test
    public void appendMessageShouldStoreARetrievableAttachmentWhenMailWithOneAttachment() throws Exception {
        InputStream mailInputStream = ClassLoader.getSystemResourceAsStream("eml/oneAttachmentAndSomeTextInlined.eml");

        inboxMessageManager.appendMessage(MessageManager.AppendCommand.builder()
            .build(mailInputStream), mailboxSession);
        
        Iterator<MailboxMessage> messages = messageMapper.findInMailbox(inbox, MessageRange.all(), FetchType.Full, 1);
        assertThat(messages.hasNext()).isTrue();
        List<MessageAttachment> attachments = messages.next().getAttachments();
        assertThat(attachments).hasSize(1);
        assertThat(attachmentMapper.getAttachment(attachments.get(0).getAttachmentId()).getStream())
            .hasSameContentAs(ClassLoader.getSystemResourceAsStream("eml/gimp.png"));
    }

    @Test
    public void appendMessageShouldStoreAttachmentsWhenMailWithTwoAttachment() throws Exception {
        InputStream mailInputStream = ClassLoader.getSystemResourceAsStream("eml/twoAttachments.eml");

        inboxMessageManager.appendMessage(MessageManager.AppendCommand.builder()
            .build(mailInputStream), mailboxSession);
        
        Iterator<MailboxMessage> messages = messageMapper.findInMailbox(inbox, MessageRange.all(), FetchType.Full, 1);
        assertThat(messages.hasNext()).isTrue();
        assertThat(messages.next().getAttachments()).hasSize(2);
    }

    @Test
    public void appendMessageShouldStoreTwoRetrievableAttachmentsWhenMailWithTwoAttachment() throws Exception {
        InputStream mailInputStream = ClassLoader.getSystemResourceAsStream("eml/twoAttachments.eml");

        inboxMessageManager.appendMessage(MessageManager.AppendCommand.builder()
            .build(mailInputStream), mailboxSession);
        
        Iterator<MailboxMessage> messages = messageMapper.findInMailbox(inbox, MessageRange.all(), FetchType.Full, 1);
        assertThat(messages.hasNext()).isTrue();
        List<MessageAttachment> attachments = messages.next().getAttachments();
        assertThat(attachments).hasSize(2);
        assertThat(attachmentMapper.getAttachment(attachments.get(0).getAttachmentId()).getStream())
            .hasSameContentAs(ClassLoader.getSystemResourceAsStream("eml/4037_014.jpg"));
        assertThat(attachmentMapper.getAttachment(attachments.get(1).getAttachmentId()).getStream())
            .hasSameContentAs(ClassLoader.getSystemResourceAsStream("eml/4037_015.jpg"));
    }

    @Test
    public void appendMessageShouldStoreEmbeddedMailAsAttachmentWhenMailWithEmbeddedAttachment() throws Exception {
        InputStream mailInputStream = ClassLoader.getSystemResourceAsStream("eml/embeddedAttachmentWithAttachment.eml");

        inboxMessageManager.appendMessage(MessageManager.AppendCommand.builder()
            .build(mailInputStream), mailboxSession);
        
        Iterator<MailboxMessage> messages = messageMapper.findInMailbox(inbox, MessageRange.all(), FetchType.Full, 1);
        assertThat(messages.hasNext()).isTrue();
        assertThat(messages.next().getAttachments()).hasSize(1);
    }

    @Test
    public void appendMessageShouldNotStoreAnyAttachmentWhenUnparsableMail() throws Exception {
        MailboxManager parseFailingMailboxManager = getParseFailingMailboxManager();
        MessageManager parseFailingInboxMessageManager = parseFailingMailboxManager.getMailbox(inboxPath, mailboxSession);
        InputStream mailInputStream = new ByteArrayInputStream("content".getBytes());

        parseFailingInboxMessageManager.appendMessage(MessageManager.AppendCommand.builder()
            .build(mailInputStream), mailboxSession);

        Iterator<MailboxMessage> messages = messageMapper.findInMailbox(inbox, MessageRange.all(), FetchType.Full, 1);
        assertThat(messages.hasNext()).isTrue();
        List<MessageAttachment> attachments = messages.next().getAttachments();
        assertThat(attachments).hasSize(0);
    }

    @Test
    public void appendMessageShouldStoreOnceWhenDuplicateAttachment() throws Exception {
        InputStream mailInputStream = ClassLoader.getSystemResourceAsStream("eml/oneAttachmentAndSomeTextInlined.eml");
        InputStream mailInputStream2 = ClassLoader.getSystemResourceAsStream("eml/oneAttachmentAndSomeTextInlined.eml");
        String user2 = "user2@domain.tld";
        MailboxSession user2MailboxSession = new MockMailboxSession(user2);
        MessageMapper user2MessageMapper = getMailboxSessionMapperFactory().getMessageMapper(user2MailboxSession);
        MailboxMapper user2MailboxMapper = getMailboxSessionMapperFactory().getMailboxMapper(user2MailboxSession);
        MailboxPath user2InboxPath = MailboxPath.forUser(user2, "INBOX");
        mailboxManager.createMailbox(user2InboxPath, user2MailboxSession);
        Mailbox user2Inbox = user2MailboxMapper.findMailboxByPath(user2InboxPath);
        MessageManager user2InboxMessageManager = mailboxManager.getMailbox(user2InboxPath, user2MailboxSession);

        inboxMessageManager.appendMessage(MessageManager.AppendCommand.builder()
            .build(mailInputStream),
            mailboxSession);
        user2InboxMessageManager.appendMessage(MessageManager.AppendCommand.builder()
                .build(mailInputStream2),
            user2MailboxSession);
        Iterator<MailboxMessage> messages = messageMapper.findInMailbox(inbox, MessageRange.all(), FetchType.Full, 1);
        Iterator<MailboxMessage> user2Messages = user2MessageMapper.findInMailbox(user2Inbox, MessageRange.all(), FetchType.Full, 1);
        assertThat(messages.hasNext()).isTrue();
        List<MessageAttachment> attachments = messages.next().getAttachments();
        assertThat(attachments).hasSize(1);
        assertThat(user2Messages.hasNext()).isTrue();
        List<MessageAttachment> user2Attachments = user2Messages.next().getAttachments();
        assertThat(attachments.equals(user2Attachments)).isTrue();
    }
}

