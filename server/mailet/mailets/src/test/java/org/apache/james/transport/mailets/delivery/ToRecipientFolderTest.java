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
import java.util.Collection;
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
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.sieverepository.api.SieveRepository;
import org.apache.james.transport.mailets.SieveToRecipientFolder;
import org.apache.james.transport.mailets.ToRecipientFolder;
import org.apache.james.user.api.UsersRepository;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.apache.mailet.base.GenericMailet;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.slf4j.Logger;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;

@RunWith(Parameterized.class)
public class ToRecipientFolderTest {

    public static final String USER_LOCAL_PART = "receiver";
    public static final String USER = USER_LOCAL_PART + "@domain.com";
    public static final MailboxPath INBOX = new MailboxPath("#private", USER, "INBOX");
    public static final MailboxPath JUNK = new MailboxPath("#private", USER_LOCAL_PART, "Junk");
    public static final MailboxPath JUNK_VIRTUAL_HOSTING = new MailboxPath("#private", USER, "Junk");
    private MessageManager messageManager;

    public static class Parameter {
        private final UsersRepository usersRepository;
        private final MailboxManager mailboxManager;
        private final SieveRepository sieveRepository;
        private final GenericMailet mailet;
        private final MailboxSession.User user;

        public Parameter(UsersRepository usersRepository, MailboxManager mailboxManager, SieveRepository sieveRepository,
                         GenericMailet mailet, MailboxSession.User user) {
            this.usersRepository = usersRepository;
            this.mailboxManager = mailboxManager;
            this.sieveRepository = sieveRepository;
            this.mailet = mailet;
            this.user = user;
        }

        public UsersRepository getUsersRepository() {
            return usersRepository;
        }

        public MailboxManager getMailboxManager() {
            return mailboxManager;
        }

        public SieveRepository getSieveRepository() {
            return sieveRepository;
        }

        public GenericMailet getMailet() {
            return mailet;
        }

        public MailboxSession.User getUser() {
            return user;
        }
    }

    @Parameterized.Parameters
    public static Collection<Object[]> getLocalDeliveryClasses() {
        SieveRepository sieveRepository = mock(SieveRepository.class);
        UsersRepository usersRepository = mock(UsersRepository.class);
        MailboxManager mailboxManager = mock(MailboxManager.class);

        SieveToRecipientFolder sieveToRecipientFolder = new SieveToRecipientFolder();
        sieveToRecipientFolder.setMailboxManager(mailboxManager);
        sieveToRecipientFolder.setUsersRepository(usersRepository);
        sieveToRecipientFolder.setSieveRepository(sieveRepository);

        ToRecipientFolder toRecipientFolder = new ToRecipientFolder();
        toRecipientFolder.setMailboxManager(mailboxManager);
        toRecipientFolder.setUsersRepository(usersRepository);

        MailboxSession.User user = mock(MailboxSession.User.class);
        MailboxSession session = mock(MailboxSession.class);
        when(session.getPathDelimiter()).thenReturn('.');
        try {
            when(mailboxManager.createSystemSession(any(String.class), any(Logger.class))).thenReturn(session);
        } catch (MailboxException e) {
            throw Throwables.propagate(e);
        }
        when(session.getUser()).thenReturn(user);

        return ImmutableList.of(
            new Object[]{new Parameter(usersRepository, mailboxManager, sieveRepository, sieveToRecipientFolder, user)},
            new Object[]{new Parameter(usersRepository, mailboxManager, sieveRepository, toRecipientFolder, user)}
        );
    }

    @Parameterized.Parameter
    public Parameter parameter;
    private FakeMailetConfig mailetConfig;

    @Before
    public void setUp() throws Exception {
        mailetConfig = new FakeMailetConfig("RecipientFolderTest", FakeMailContext.builder().logger(mock(Logger.class)).build());
        messageManager = mock(MessageManager.class);
    }

    @Test
    public void initParameterTesting() throws Exception {
        mailetConfig.setProperty(ToRecipientFolder.FOLDER_PARAMETER, "Junk");
        parameter.getMailet().init(mailetConfig);

        Assert.assertEquals("Junk", parameter.getMailet().getInitParameter(ToRecipientFolder.FOLDER_PARAMETER));
    }

    @Test
    public void consumeOptionShouldGhostTheMail() throws Exception {
        mailetConfig.setProperty(ToRecipientFolder.CONSUME_PARAMETER, "true");
        parameter.getMailet().init(mailetConfig);

        Mail mail = createMail();
        parameter.getMailet().service(mail);

        assertThat(mail.getState()).isEqualTo(Mail.GHOST);
    }

    @Test
    public void consumeOptionShouldNotGhostTheMailByDefault() throws Exception {
        parameter.getMailet().init(mailetConfig);

        Mail mail = createMail();
        parameter.getMailet().service(mail);

        assertThat(mail.getState()).isEqualTo(Mail.DEFAULT);
    }

    @Test
    public void folderParameterShouldIndicateDestinationFolder() throws Exception {
        when(parameter.getUsersRepository().supportVirtualHosting()).thenReturn(true);
        when(parameter.getUsersRepository().getUser(new MailAddress(USER))).thenReturn(USER);
        when(parameter.getMailboxManager().getMailbox(eq(JUNK_VIRTUAL_HOSTING), any(MailboxSession.class))).thenReturn(messageManager);
        when(parameter.getUser().getUserName()).thenReturn(USER);

        mailetConfig.setProperty(ToRecipientFolder.FOLDER_PARAMETER, "Junk");
        parameter.getMailet().init(mailetConfig);
        parameter.getMailet().service(createMail());

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void folderParameterShouldBeInboxByDefault() throws Exception {
        when(parameter.getUsersRepository().supportVirtualHosting()).thenReturn(true);
        when(parameter.getUsersRepository().getUser(new MailAddress(USER))).thenReturn(USER);
        when(parameter.getMailboxManager().getMailbox(eq(INBOX), any(MailboxSession.class))).thenReturn(messageManager);
        when(parameter.getUser().getUserName()).thenReturn(USER);

        parameter.getMailet().init(mailetConfig);
        parameter.getMailet().service(createMail());

        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void folderParameterShouldWorkWhenVirtualHostingIsTurnedOff() throws Exception {
        when(parameter.getUsersRepository().supportVirtualHosting()).thenReturn(false);
        when(parameter.getUsersRepository().getUser(new MailAddress(USER_LOCAL_PART + "@localhost"))).thenReturn(USER_LOCAL_PART);
        when(parameter.getUsersRepository().getUser(new MailAddress(USER))).thenReturn(USER_LOCAL_PART);
        when(parameter.getMailboxManager().getMailbox(eq(JUNK), any(MailboxSession.class))).thenReturn(messageManager);
        when(parameter.getUser().getUserName()).thenReturn(USER_LOCAL_PART);

        mailetConfig.setProperty(ToRecipientFolder.FOLDER_PARAMETER, "Junk");
        parameter.getMailet().init(mailetConfig);
        parameter.getMailet().service(createMail());

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
        return FakeMail.builder()
                .mimeMessage(message)
                .state(Mail.DEFAULT)
                .recipient(new MailAddress(USER))
                .build();
    }

}
