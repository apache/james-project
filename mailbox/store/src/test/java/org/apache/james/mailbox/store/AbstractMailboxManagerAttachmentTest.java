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

import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import jakarta.mail.util.SharedByteArrayInputStream;

import org.apache.commons.io.IOUtils;
import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.model.AttachmentMetadata;
import org.apache.james.mailbox.model.ByteContent;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageAttachmentMetadata;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.store.mail.AttachmentMapper;
import org.apache.james.mailbox.store.mail.AttachmentMapperFactory;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.MessageMapper.FetchType;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.util.ClassLoaderUtils;
import org.junit.jupiter.api.Test;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;

public abstract class AbstractMailboxManagerAttachmentTest {
    private static final Username USERNAME = Username.of("user@domain.tld");

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

    protected void setUp() throws Exception {
        mailboxSession = MailboxSessionUtil.create(USERNAME);
        messageMapper = getMailboxSessionMapperFactory().getMessageMapper(mailboxSession);
        mailboxMapper = getMailboxSessionMapperFactory().getMailboxMapper(mailboxSession);
        inboxPath = MailboxPath.forUser(USERNAME, "INBOX");
        mailboxManager = getMailboxManager();
        mailboxManager.createMailbox(inboxPath, mailboxSession);
        inbox = mailboxMapper.findMailboxByPath(inboxPath).block();
        inboxMessageManager = mailboxManager.getMailbox(inboxPath, mailboxSession);
        attachmentMapper = getAttachmentMapperFactory().createAttachmentMapper(mailboxSession);
    }

    @Test
    void appendMessageShouldStoreWithoutAttachmentWhenMailWithoutAttachment() throws Exception {
        String mail = "Subject: Test\n\nBody";
        inboxMessageManager.appendMessage(MessageManager.AppendCommand.builder()
            .build(new ByteContent(mail.getBytes())), mailboxSession);
        
        Iterator<MailboxMessage> messages = messageMapper.findInMailbox(inbox, MessageRange.all(), FetchType.FULL, 1);
        assertThat(messages.hasNext()).isTrue();
        assertThat(messages.next().getAttachments()).isEmpty();
    }

    @Test
    void appendMessageShouldStoreAttachmentWhenMailWithOneAttachment() throws Exception {
        SharedByteArrayInputStream mailInputStream = ClassLoaderUtils.getSystemResourceAsSharedStream("eml/oneAttachmentAndSomeTextInlined.eml");
        inboxMessageManager.appendMessage(MessageManager.AppendCommand.builder()
            .build(mailInputStream), mailboxSession);

        Iterator<MailboxMessage> messages = messageMapper.findInMailbox(inbox, MessageRange.all(), FetchType.FULL, 1);
        assertThat(messages.hasNext()).isTrue();
        assertThat(messages.next().getAttachments()).hasSize(1);
    }

    @Test
    void appendMessageShouldStoreAttachmentNameWhenMailWithOneAttachment() throws Exception {
        SharedByteArrayInputStream mailInputStream = ClassLoaderUtils.getSystemResourceAsSharedStream("eml/oneAttachmentAndSomeTextInlined.eml");
        inboxMessageManager.appendMessage(MessageManager.AppendCommand.builder()
            .build(mailInputStream), mailboxSession);

        Optional<String> expectedName = Optional.of("exploits_of_a_mom.png");

        Iterator<MailboxMessage> messages = messageMapper.findInMailbox(inbox, MessageRange.all(), FetchType.FULL, 1);
        List<MessageAttachmentMetadata> attachments = messages.next().getAttachments();
        assertThat(attachments.get(0).getName()).isEqualTo(expectedName);
    }

    @Test
    void appendMessageShouldStoreARetrievableAttachmentWhenMailWithOneAttachment() throws Exception {
        SharedByteArrayInputStream mailInputStream = ClassLoaderUtils.getSystemResourceAsSharedStream("eml/oneAttachmentAndSomeTextInlined.eml");

        inboxMessageManager.appendMessage(MessageManager.AppendCommand.builder()
            .build(mailInputStream), mailboxSession);
        
        Iterator<MailboxMessage> messages = messageMapper.findInMailbox(inbox, MessageRange.all(), FetchType.FULL, 1);
        assertThat(messages.hasNext()).isTrue();
        List<MessageAttachmentMetadata> attachments = messages.next().getAttachments();
        assertThat(attachments).hasSize(1);
        assertThat(attachmentMapper.loadAttachmentContent(attachments.get(0).getAttachmentId()))
            .hasSameContentAs(ClassLoader.getSystemResourceAsStream("eml/gimp.png"));
    }

    @Test
    void appendMessageShouldStoreAttachmentsWhenMailWithTwoAttachment() throws Exception {
        SharedByteArrayInputStream mailInputStream = ClassLoaderUtils.getSystemResourceAsSharedStream("eml/twoAttachments.eml");

        inboxMessageManager.appendMessage(MessageManager.AppendCommand.builder()
            .build(mailInputStream), mailboxSession);
        
        Iterator<MailboxMessage> messages = messageMapper.findInMailbox(inbox, MessageRange.all(), FetchType.FULL, 1);
        assertThat(messages.hasNext()).isTrue();
        assertThat(messages.next().getAttachments()).hasSize(2);
    }

    @Test
    void appendMessageShouldStoreTwoRetrievableAttachmentsWhenMailWithTwoAttachment() throws Exception {
        SharedByteArrayInputStream mailInputStream = ClassLoaderUtils.getSystemResourceAsSharedStream("eml/twoAttachments.eml");

        inboxMessageManager.appendMessage(MessageManager.AppendCommand.builder()
            .build(mailInputStream), mailboxSession);
        
        Iterator<MailboxMessage> messages = messageMapper.findInMailbox(inbox, MessageRange.all(), FetchType.FULL, 1);
        assertThat(messages.hasNext()).isTrue();
        List<MessageAttachmentMetadata> attachments = messages.next().getAttachments();
        assertThat(attachments).hasSize(2);
        ImmutableList<byte[]> attachmentContents = attachments
            .stream()
            .map(MessageAttachmentMetadata::getAttachmentId)
            .map(Throwing.function(attachmentMapper::getAttachment))
            .map(AttachmentMetadata::getAttachmentId)
            .map(Throwing.function(attachmentMapper::loadAttachmentContent))
            .map(Throwing.function(IOUtils::toByteArray))
            .collect(ImmutableList.toImmutableList());

        ImmutableList<byte[]> files = Stream.of("eml/4037_014.jpg", "eml/4037_015.jpg")
                .map(ClassLoader::getSystemResourceAsStream)
                .map(Throwing.function(IOUtils::toByteArray))
                .collect(ImmutableList.toImmutableList());

        assertThat(attachmentContents)
            .containsExactlyInAnyOrder(files.get(0), files.get(1));
    }

    @Test
    void appendMessageShouldStoreEmbeddedMailAsAttachmentWhenMailWithEmbeddedAttachment() throws Exception {
        SharedByteArrayInputStream mailInputStream = ClassLoaderUtils.getSystemResourceAsSharedStream("eml/embeddedAttachmentWithAttachment.eml");

        inboxMessageManager.appendMessage(MessageManager.AppendCommand.builder()
            .build(mailInputStream), mailboxSession);
        
        Iterator<MailboxMessage> messages = messageMapper.findInMailbox(inbox, MessageRange.all(), FetchType.FULL, 1);
        assertThat(messages.hasNext()).isTrue();
        assertThat(messages.next().getAttachments()).hasSize(1);
    }

    @Test
    void appendMessageShouldNotStoreAnyAttachmentWhenUnparsableMail() throws Exception {
        MailboxManager parseFailingMailboxManager = getParseFailingMailboxManager();
        MessageManager parseFailingInboxMessageManager = parseFailingMailboxManager.getMailbox(inboxPath, mailboxSession);

        parseFailingInboxMessageManager.appendMessage(MessageManager.AppendCommand.builder()
            .build(new ByteContent("content".getBytes())), mailboxSession);

        Iterator<MailboxMessage> messages = messageMapper.findInMailbox(inbox, MessageRange.all(), FetchType.FULL, 1);
        assertThat(messages.hasNext()).isTrue();
        List<MessageAttachmentMetadata> attachments = messages.next().getAttachments();
        assertThat(attachments).hasSize(0);
    }
}

