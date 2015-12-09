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

package org.apache.james.transport.mailets.delivery;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.Lists;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.rrt.api.RecipientRewriteTable;
import org.apache.james.transport.mailets.LocalDelivery;
import org.apache.james.user.api.UsersRepository;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;

import javax.activation.DataHandler;
import javax.mail.Flags;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.Properties;

public class LocalDeliveryTest {

    private UsersRepository usersRepository;
    private FileSystem fileSystem;
    private RecipientRewriteTable recipientRewriteTable;
    private MailboxManager mailboxManager;
    private DomainList domainList;
    private LocalDelivery localDelivery;

    @Before
    public void setUp() throws Exception {
        usersRepository = mock(UsersRepository.class);
        fileSystem = mock(FileSystem.class);
        recipientRewriteTable = mock(RecipientRewriteTable.class);
        mailboxManager = mock(MailboxManager.class);
        domainList = mock(DomainList.class);

        localDelivery = new LocalDelivery();
        localDelivery.setDomainList(domainList);
        localDelivery.setFileSystem(fileSystem);
        localDelivery.setMailboxManager(mailboxManager);
        localDelivery.setRrt(recipientRewriteTable);
        localDelivery.setUsersRepository(usersRepository);
    }

    @Test
    public void mailShouldBeWellDeliveredByDefaultToUserWhenvirtualHostingIsTurnedOn() throws Exception {
        when(usersRepository.supportVirtualHosting()).thenAnswer(new Answer<Boolean>() {
            public Boolean answer(InvocationOnMock invocationOnMock) throws Throwable {
                return true;
            }
        });
        when(fileSystem.getFile(any(String.class))).thenThrow(FileNotFoundException.class);
        MailboxPath inbox = new MailboxPath("#private", "receiver@domain.com", "INBOX");
        final MessageManager messageManager = mock(MessageManager.class);
        when(mailboxManager.getMailbox(eq(inbox), any(MailboxSession.class))).thenAnswer(new Answer<MessageManager>() {
            public MessageManager answer(InvocationOnMock invocationOnMock) throws Throwable {
                return messageManager;
            }
        });
        final MailboxSession session = mock(MailboxSession.class);
        when(session.getPathDelimiter()).thenAnswer(new Answer<Character>() {
            @Override
            public Character answer(InvocationOnMock invocationOnMock) throws Throwable {
                return '.';
            }
        });
        when(mailboxManager.createSystemSession(any(String.class), any(Logger.class))).thenAnswer(new Answer<MailboxSession>() {
            @Override
            public MailboxSession answer(InvocationOnMock invocationOnMock) throws Throwable {
                return session;
            }
        });

        Mail mail = createMail();

        localDelivery.init(new FakeMailetConfig());
        localDelivery.service(mail);

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void mailShouldBeWellDeliveredByDefaultToUserWhenvirtualHostingIsTurnedOff() throws Exception {
        when(usersRepository.supportVirtualHosting()).thenAnswer(new Answer<Boolean>() {
            public Boolean answer(InvocationOnMock invocationOnMock) throws Throwable {
                return false;
            }
        });
        when(fileSystem.getFile(any(String.class))).thenThrow(FileNotFoundException.class);
        MailboxPath inbox = new MailboxPath("#private", "receiver", "INBOX");
        final MessageManager messageManager = mock(MessageManager.class);
        when(mailboxManager.getMailbox(eq(inbox), any(MailboxSession.class))).thenAnswer(new Answer<MessageManager>() {
            public MessageManager answer(InvocationOnMock invocationOnMock) throws Throwable {
                return messageManager;
            }
        });
        final MailboxSession session = mock(MailboxSession.class);
        when(session.getPathDelimiter()).thenAnswer(new Answer<Character>() {
            @Override
            public Character answer(InvocationOnMock invocationOnMock) throws Throwable {
                return '.';
            }
        });
        when(mailboxManager.createSystemSession(any(String.class), any(Logger.class))).thenAnswer(new Answer<MailboxSession>() {
            @Override
            public MailboxSession answer(InvocationOnMock invocationOnMock) throws Throwable {
                return session;
            }
        });

        Mail mail = createMail();

        localDelivery.init(new FakeMailetConfig());
        localDelivery.service(mail);

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    private Mail createMail() throws MessagingException, IOException {
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        message.setSubject("Subject");
        message.setSender(new InternetAddress("sender@any.com"));
        message.setRecipient(MimeMessage.RecipientType.TO, new InternetAddress("receiver@domain.com"));
        MimeMultipart multipart = new MimeMultipart();
        MimeBodyPart scriptPart = new MimeBodyPart();
        scriptPart.setDataHandler(
            new DataHandler(
                new ByteArrayDataSource(
                    "toto",
                    "application/sieve; charset=UTF-8")
            ));
        scriptPart.setDisposition(MimeBodyPart.ATTACHMENT);
        scriptPart.setHeader("Content-Type", "application/sieve; charset=UTF-8");
        scriptPart.setFileName("file.txt");
        multipart.addBodyPart(scriptPart);
        message.setContent(multipart);
        message.saveChanges();
        Mail mail = new FakeMail(message);
        mail.setState(Mail.DEFAULT);
        mail.setRecipients(Lists.newArrayList(new MailAddress("receiver@domain.com")));
        return mail;
    }

}