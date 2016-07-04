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
package org.apache.james.jmap.send;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.util.Date;
import java.util.Iterator;

import javax.mail.Flags;

import org.apache.james.jmap.exceptions.MailboxRoleNotFoundException;
import org.apache.james.jmap.model.MessageId;
import org.apache.james.jmap.send.exception.MailShouldBeInOutboxException;
import org.apache.james.jmap.send.exception.MessageIdNotFoundException;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.acl.GroupMembershipResolver;
import org.apache.james.mailbox.acl.MailboxACLResolver;
import org.apache.james.mailbox.acl.SimpleGroupMembershipResolver;
import org.apache.james.mailbox.acl.UnionMailboxACLResolver;
import org.apache.james.mailbox.inmemory.InMemoryMailboxSessionMapperFactory;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.store.MockAuthenticator;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.impl.MessageParser;
import org.apache.james.queue.api.MailQueue.MailQueueItem;
import org.apache.mailet.Mail;
import org.apache.mailet.base.test.FakeMail;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PostDequeueDecoratorTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(PostDequeueDecoratorTest.class);
    private static final String OUTBOX = "OUTBOX";
    private static final String SENT = "SENT";
    private static final String USERNAME = "username@domain.tld";
    private static final long UID = 1;
    private static final MailboxPath OUTBOX_MAILBOX_PATH = new MailboxPath(MailboxConstants.USER_NAMESPACE, USERNAME, OUTBOX);
    private static final MailboxPath SENT_MAILBOX_PATH = new MailboxPath(MailboxConstants.USER_NAMESPACE, USERNAME, SENT);
    private static final String MESSAGE_ID = USERNAME + "|" + OUTBOX_MAILBOX_PATH.getName() + "|" + UID;
    
    private InMemoryMailboxSessionMapperFactory mailboxSessionMapperFactory;
    private StoreMailboxManager mailboxManager;
    private MailQueueItem mockedMailQueueItem;
    private Mail mail;
    private PostDequeueDecorator testee;

    @Before
    public void init() throws Exception {
        mailboxSessionMapperFactory = new InMemoryMailboxSessionMapperFactory();
        MailboxACLResolver aclResolver = new UnionMailboxACLResolver();
        GroupMembershipResolver groupMembershipResolver = new SimpleGroupMembershipResolver();
        MessageParser messageParser = new MessageParser();
        mailboxManager = new StoreMailboxManager(mailboxSessionMapperFactory, new MockAuthenticator(), aclResolver, groupMembershipResolver, messageParser);
        mailboxManager.init();

        mockedMailQueueItem = mock(MailQueueItem.class);
        mail = new FakeMail();
        when(mockedMailQueueItem.getMail()).thenReturn(mail);
        testee = new PostDequeueDecorator(mockedMailQueueItem, mailboxManager, mailboxSessionMapperFactory, mailboxSessionMapperFactory);
    }
    
    @Test
    public void doneShouldCallDecoratedDone() throws Exception {
        try {
            testee.done(true);
        } catch (Exception e) {
            //Ignore
        }

        verify(mockedMailQueueItem).done(true);
    }
    
    @Test(expected=MessageIdNotFoundException.class)
    public void doneShouldThrowWhenMetadataHasNotAnExistingMessageId() throws Exception {
        MailboxSession mailboxSession = mailboxManager.createSystemSession(USERNAME, LOGGER);
        mailboxManager.createMailbox(OUTBOX_MAILBOX_PATH, mailboxSession);
        mail.setAttribute(MailMetadata.MAIL_METADATA_MESSAGE_ID_ATTRIBUTE, MESSAGE_ID);
        mail.setAttribute(MailMetadata.MAIL_METADATA_USERNAME_ATTRIBUTE, USERNAME);

        testee.done(true);
    }
    
    @Test(expected=MailShouldBeInOutboxException.class)
    public void doneShouldThrowWhenMessageIsNotInOutbox() throws Exception {
        MailboxSession mailboxSession = mailboxManager.createSystemSession(USERNAME, LOGGER);
        mailboxManager.createMailbox(SENT_MAILBOX_PATH, mailboxSession);
        MessageManager messageManager = mailboxManager.getMailbox(SENT_MAILBOX_PATH, mailboxSession);
        messageManager.appendMessage(new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), mailboxSession, false, new Flags());
        MessageId inSentMessageId = MessageId.of(USERNAME + "|" + SENT_MAILBOX_PATH.getName() + "|" + UID);
        mail.setAttribute(MailMetadata.MAIL_METADATA_MESSAGE_ID_ATTRIBUTE, inSentMessageId.serialize());
        mail.setAttribute(MailMetadata.MAIL_METADATA_USERNAME_ATTRIBUTE, USERNAME);
        
        testee.done(true);
    }
    
    @Test(expected=MailboxRoleNotFoundException.class)
    public void doneShouldThrowWhenSentDoesNotExist() throws Exception {
        MailboxSession mailboxSession = mailboxManager.createSystemSession(USERNAME, LOGGER);
        mailboxManager.createMailbox(OUTBOX_MAILBOX_PATH, mailboxSession);
        MessageManager messageManager = mailboxManager.getMailbox(OUTBOX_MAILBOX_PATH, mailboxSession);
        messageManager.appendMessage(new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), mailboxSession, false, new Flags());
        mail.setAttribute(MailMetadata.MAIL_METADATA_MESSAGE_ID_ATTRIBUTE, MESSAGE_ID);
        mail.setAttribute(MailMetadata.MAIL_METADATA_USERNAME_ATTRIBUTE, USERNAME);

        testee.done(true);
    }
    
    @Test
    public void doneShouldCopyMailFromOutboxToSentWhenSuccess() throws Exception {
        MailboxSession mailboxSession = mailboxManager.createSystemSession(USERNAME, LOGGER);
        mailboxManager.createMailbox(OUTBOX_MAILBOX_PATH, mailboxSession);
        mailboxManager.createMailbox(SENT_MAILBOX_PATH, mailboxSession);
        MessageManager messageManager = mailboxManager.getMailbox(OUTBOX_MAILBOX_PATH, mailboxSession);
        messageManager.appendMessage(new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), mailboxSession, false, new Flags());
        mail.setAttribute(MailMetadata.MAIL_METADATA_MESSAGE_ID_ATTRIBUTE, MESSAGE_ID);
        mail.setAttribute(MailMetadata.MAIL_METADATA_USERNAME_ATTRIBUTE, USERNAME);
        
        testee.done(true);
        
        Mailbox sentMailbox = mailboxSessionMapperFactory.getMailboxMapper(mailboxSession).findMailboxByPath(SENT_MAILBOX_PATH);
        MessageMapper messageMapper = mailboxSessionMapperFactory.getMessageMapper(mailboxSession);
        Iterator<MailboxMessage> resultIterator = messageMapper.findInMailbox(sentMailbox, MessageRange.one(UID), MessageMapper.FetchType.Full, 1);
        assertThat(resultIterator).hasSize(1);
    }
    
    @Test
    public void doneShouldDeleteMailFromOutboxWhenSuccess() throws Exception {
        MailboxSession mailboxSession = mailboxManager.createSystemSession(USERNAME, LOGGER);
        mailboxManager.createMailbox(OUTBOX_MAILBOX_PATH, mailboxSession);
        mailboxManager.createMailbox(SENT_MAILBOX_PATH, mailboxSession);
        MessageManager messageManager = mailboxManager.getMailbox(OUTBOX_MAILBOX_PATH, mailboxSession);
        messageManager.appendMessage(new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), mailboxSession, false, new Flags());
        mail.setAttribute(MailMetadata.MAIL_METADATA_MESSAGE_ID_ATTRIBUTE, MESSAGE_ID);
        mail.setAttribute(MailMetadata.MAIL_METADATA_USERNAME_ATTRIBUTE, USERNAME);
        
        testee.done(true);
        
        Mailbox mailbox = mailboxSessionMapperFactory.getMailboxMapper(mailboxSession).findMailboxByPath(OUTBOX_MAILBOX_PATH);
        MessageMapper messageMapper = mailboxSessionMapperFactory.getMessageMapper(mailboxSession);
        Iterator<MailboxMessage> resultIterator = messageMapper.findInMailbox(mailbox, MessageRange.one(UID), MessageMapper.FetchType.Full, 1);
        assertThat(resultIterator).hasSize(0);
    }
    
    @Test
    public void doneShouldNotMoveMailFromOutboxToSentWhenSuccessIsFalse() throws Exception {
        MailboxSession mailboxSession = mailboxManager.createSystemSession(USERNAME, LOGGER);
        mailboxManager.createMailbox(OUTBOX_MAILBOX_PATH, mailboxSession);
        mailboxManager.createMailbox(SENT_MAILBOX_PATH, mailboxSession);
        MessageManager messageManager = mailboxManager.getMailbox(OUTBOX_MAILBOX_PATH, mailboxSession);
        messageManager.appendMessage(new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), mailboxSession, false, new Flags());
        mail.setAttribute(MailMetadata.MAIL_METADATA_MESSAGE_ID_ATTRIBUTE, MESSAGE_ID);
        mail.setAttribute(MailMetadata.MAIL_METADATA_USERNAME_ATTRIBUTE, USERNAME);
        
        testee.done(false);
        
        Mailbox mailbox = mailboxSessionMapperFactory.getMailboxMapper(mailboxSession).findMailboxByPath(OUTBOX_MAILBOX_PATH);
        MessageMapper messageMapper = mailboxSessionMapperFactory.getMessageMapper(mailboxSession);
        Iterator<MailboxMessage> resultIterator = messageMapper.findInMailbox(mailbox, MessageRange.one(UID), MessageMapper.FetchType.Full, 1);
        assertThat(resultIterator).hasSize(1);
    }
    
    @Test
    public void doneShouldNotMoveMailFromOutboxToSentWhenNoMetadataProvided() throws Exception {
        MailboxSession mailboxSession = mailboxManager.createSystemSession(USERNAME, LOGGER);
        mailboxManager.createMailbox(OUTBOX_MAILBOX_PATH, mailboxSession);
        mailboxManager.createMailbox(SENT_MAILBOX_PATH, mailboxSession);
        MessageManager messageManager = mailboxManager.getMailbox(OUTBOX_MAILBOX_PATH, mailboxSession);
        messageManager.appendMessage(new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), mailboxSession, false, new Flags());
        
        testee.done(true);
        
        Mailbox mailbox = mailboxSessionMapperFactory.getMailboxMapper(mailboxSession).findMailboxByPath(OUTBOX_MAILBOX_PATH);
        MessageMapper messageMapper = mailboxSessionMapperFactory.getMessageMapper(mailboxSession);
        Iterator<MailboxMessage> resultIterator = messageMapper.findInMailbox(mailbox, MessageRange.one(UID), MessageMapper.FetchType.Full, 1);
        assertThat(resultIterator).hasSize(1);
    }
    
    @Test
    public void doneShouldNotMoveMailFromOutboxToSentWhenUsernameNotProvided() throws Exception {
        MailboxSession mailboxSession = mailboxManager.createSystemSession(USERNAME, LOGGER);
        mailboxManager.createMailbox(OUTBOX_MAILBOX_PATH, mailboxSession);
        mailboxManager.createMailbox(SENT_MAILBOX_PATH, mailboxSession);
        MessageManager messageManager = mailboxManager.getMailbox(OUTBOX_MAILBOX_PATH, mailboxSession);
        messageManager.appendMessage(new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), mailboxSession, false, new Flags());
        mail.setAttribute(MailMetadata.MAIL_METADATA_MESSAGE_ID_ATTRIBUTE, MESSAGE_ID);
        
        testee.done(true);
        
        Mailbox mailbox = mailboxSessionMapperFactory.getMailboxMapper(mailboxSession).findMailboxByPath(OUTBOX_MAILBOX_PATH);
        MessageMapper messageMapper = mailboxSessionMapperFactory.getMessageMapper(mailboxSession);
        Iterator<MailboxMessage> resultIterator = messageMapper.findInMailbox(mailbox, MessageRange.one(UID), MessageMapper.FetchType.Full, 1);
        assertThat(resultIterator).hasSize(1);
    }
    
    @Test
    public void doneShouldNotMoveMailFromOutboxToSentWhenMessageIdNotProvided() throws Exception {
        MailboxSession mailboxSession = mailboxManager.createSystemSession(USERNAME, LOGGER);
        mailboxManager.createMailbox(OUTBOX_MAILBOX_PATH, mailboxSession);
        mailboxManager.createMailbox(SENT_MAILBOX_PATH, mailboxSession);
        MessageManager messageManager = mailboxManager.getMailbox(OUTBOX_MAILBOX_PATH, mailboxSession);
        messageManager.appendMessage(new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), mailboxSession, false, new Flags());
        mail.setAttribute(MailMetadata.MAIL_METADATA_USERNAME_ATTRIBUTE, USERNAME);
        
        testee.done(true);
        
        Mailbox mailbox = mailboxSessionMapperFactory.getMailboxMapper(mailboxSession).findMailboxByPath(OUTBOX_MAILBOX_PATH);
        MessageMapper messageMapper = mailboxSessionMapperFactory.getMessageMapper(mailboxSession);
        Iterator<MailboxMessage> resultIterator = messageMapper.findInMailbox(mailbox, MessageRange.one(UID), MessageMapper.FetchType.Full, 1);
        assertThat(resultIterator).hasSize(1);
    }
    
    @Test
    public void doneShouldNotMoveMailFromOutboxToSentWhenInvalidMessageIdProvided() throws Exception {
        MailboxSession mailboxSession = mailboxManager.createSystemSession(USERNAME, LOGGER);
        mailboxManager.createMailbox(OUTBOX_MAILBOX_PATH, mailboxSession);
        mailboxManager.createMailbox(SENT_MAILBOX_PATH, mailboxSession);
        MessageManager messageManager = mailboxManager.getMailbox(OUTBOX_MAILBOX_PATH, mailboxSession);
        messageManager.appendMessage(new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), mailboxSession, false, new Flags());
        mail.setAttribute(MailMetadata.MAIL_METADATA_USERNAME_ATTRIBUTE, USERNAME);
        mail.setAttribute(MailMetadata.MAIL_METADATA_MESSAGE_ID_ATTRIBUTE, "invalid");
        
        testee.done(true);
        
        Mailbox mailbox = mailboxSessionMapperFactory.getMailboxMapper(mailboxSession).findMailboxByPath(OUTBOX_MAILBOX_PATH);
        MessageMapper messageMapper = mailboxSessionMapperFactory.getMessageMapper(mailboxSession);
        Iterator<MailboxMessage> resultIterator = messageMapper.findInMailbox(mailbox, MessageRange.one(UID), MessageMapper.FetchType.Full, 1);
        assertThat(resultIterator).hasSize(1);
    }
}
