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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.Properties;

import javax.activation.DataHandler;
import javax.mail.Flags;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;

import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.sieverepository.api.SieveRepository;
import org.apache.james.transport.mailets.ToRecipientFolder;
import org.apache.james.user.api.UsersRepository;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;

import com.google.common.collect.Lists;

public class ToRecipientFolderTest {

    public static final String USER = "receiver@domain.com";
    public static final MailboxPath INBOX = new MailboxPath("#private", USER, "INBOX");
    public static final MailboxPath JUNK = new MailboxPath("#private", "receiver", "Junk");
    public static final MailboxPath JUNK_VIRTUAL_HOSTING = new MailboxPath("#private", USER, "Junk");
    private UsersRepository usersRepository;
    private MailboxManager mailboxManager;
    private ToRecipientFolder recipientFolder;
    private FakeMailetConfig mailetConfig;

    @Before
    public void setUp() throws Exception {
        usersRepository = mock(UsersRepository.class);
        mailboxManager = mock(MailboxManager.class);

        mailetConfig = new FakeMailetConfig("RecipientFolderTest", FakeMailContext.defaultContext());

        recipientFolder = new ToRecipientFolder();
        recipientFolder.setMailboxManager(mailboxManager);
        recipientFolder.setUsersRepository(usersRepository);
        recipientFolder.setSieveRepository(mock(SieveRepository.class));
    }

    @Test
    public void initParameterTesting() throws Exception {
        mailetConfig.setProperty(ToRecipientFolder.FOLDER_PARAMETER, "Junk");
        recipientFolder.init(mailetConfig);

        Assert.assertEquals("Junk", recipientFolder.getInitParameter(ToRecipientFolder.FOLDER_PARAMETER));
    }

    @Test
    public void consumeOptionShouldGhostTheMail() throws Exception {
        mailetConfig.setProperty(ToRecipientFolder.CONSUME_PARAMETER, "true");
        recipientFolder.init(mailetConfig);

        Mail mail = createMail();
        recipientFolder.service(mail);

        assertThat(mail.getState()).isEqualTo(Mail.GHOST);
    }

    @Test
    public void consumeOptionShouldNotGhostTheMailByDefault() throws Exception {
        recipientFolder.init(mailetConfig);

        Mail mail = createMail();
        recipientFolder.service(mail);

        assertThat(mail.getState()).isEqualTo(Mail.DEFAULT);
    }

    @Test
    public void folderParameterShouldIndicateDestinationFolder() throws Exception {
        MessageManager messageManager = mock(MessageManager.class);
        MailboxSession session = mock(MailboxSession.class);

        when(usersRepository.supportVirtualHosting()).thenReturn(true);
        when(mailboxManager.getMailbox(eq(JUNK_VIRTUAL_HOSTING), any(MailboxSession.class))).thenReturn(messageManager);
        when(session.getPathDelimiter()).thenReturn('.');
        when(mailboxManager.createSystemSession(any(String.class), any(Logger.class))).thenReturn(session);

        mailetConfig.setProperty(ToRecipientFolder.FOLDER_PARAMETER, "Junk");
        recipientFolder.init(mailetConfig);
        recipientFolder.service(createMail());

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void folderParameterShouldBeInboxByDefault() throws Exception {
        MessageManager messageManager = mock(MessageManager.class);
        MailboxSession session = mock(MailboxSession.class);

        when(usersRepository.supportVirtualHosting()).thenReturn(true);
        when(mailboxManager.getMailbox(eq(INBOX), any(MailboxSession.class))).thenReturn(messageManager);
        when(session.getPathDelimiter()).thenReturn('.');
        when(mailboxManager.createSystemSession(any(String.class), any(Logger.class))).thenReturn(session);

        recipientFolder.init(mailetConfig);
        recipientFolder.service(createMail());

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void folderParameterShouldWorkWhenVirtualHostingIsTurnedOff() throws Exception {
        MessageManager messageManager = mock(MessageManager.class);
        MailboxSession session = mock(MailboxSession.class);

        when(usersRepository.supportVirtualHosting()).thenReturn(false);
        when(mailboxManager.getMailbox(eq(JUNK), any(MailboxSession.class))).thenReturn(messageManager);
        when(session.getPathDelimiter()).thenReturn('.');
        when(mailboxManager.createSystemSession(any(String.class), any(Logger.class))).thenReturn(session);

        mailetConfig.setProperty(ToRecipientFolder.FOLDER_PARAMETER, "Junk");
        recipientFolder.init(mailetConfig);
        recipientFolder.service(createMail());

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    private Mail createMail() throws MessagingException, IOException {
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        message.setSubject("Subject");
        message.setSender(new InternetAddress("sender@any.com"));
        message.setRecipient(MimeMessage.RecipientType.TO, new InternetAddress(USER));
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
        mail.setRecipients(Lists.newArrayList(new MailAddress(USER)));
        return mail;
    }

}
