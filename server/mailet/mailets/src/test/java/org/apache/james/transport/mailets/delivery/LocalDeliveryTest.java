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
import static org.mockito.Mockito.doThrow;
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

import org.apache.james.domainlist.api.DomainList;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.rrt.api.RecipientRewriteTable;
import org.apache.james.sieverepository.api.SieveRepository;
import org.apache.james.sieverepository.api.exception.ScriptNotFoundException;
import org.apache.james.transport.mailets.LocalDelivery;
import org.apache.james.transport.mailets.SieveLocalDelivery;
import org.apache.james.user.api.UsersRepository;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.apache.mailet.Mailet;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.slf4j.Logger;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;

@RunWith(Parameterized.class)
public class LocalDeliveryTest {

    public static class Parameter {
        private final UsersRepository usersRepository;
        private final MailboxManager mailboxManager;
        private final SieveRepository sieveRepository;
        private final Mailet mailet;
        private final MailboxSession.User user;

        public Parameter(UsersRepository usersRepository, MailboxManager mailboxManager, SieveRepository sieveRepository,
                         Mailet mailet, MailboxSession.User user) {
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

        public Mailet getMailet() {
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
        RecipientRewriteTable recipientRewriteTable = mock(RecipientRewriteTable.class);
        DomainList domainList = mock(DomainList.class);

        SieveLocalDelivery sieveLocalDelivery = new SieveLocalDelivery();
        sieveLocalDelivery.setDomainList(domainList);
        sieveLocalDelivery.setMailboxManager(mailboxManager);
        sieveLocalDelivery.setRrt(recipientRewriteTable);
        sieveLocalDelivery.setUsersRepository(usersRepository);
        sieveLocalDelivery.setSieveRepository(sieveRepository);

        LocalDelivery localDelivery = new LocalDelivery();
        localDelivery.setDomainList(domainList);
        localDelivery.setRrt(recipientRewriteTable);
        localDelivery.setMailboxManager(mailboxManager);
        localDelivery.setUsersRepository(usersRepository);

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
            new Object[]{new Parameter(usersRepository, mailboxManager, sieveRepository, sieveLocalDelivery, user)},
            new Object[]{new Parameter(usersRepository, mailboxManager, sieveRepository, localDelivery, user)}
        );
    }

    @Parameterized.Parameter
    public Parameter parameter;
    private FakeMailetConfig config;

    @Before
    public void setUp() {
        config = new FakeMailetConfig("Local delivery", FakeMailContext.builder().logger(mock(Logger.class)).build());
    }

    @Test
    public void mailShouldBeWellDeliveredByDefaultToUserWhenVirtualHostingIsTurnedOn() throws Exception {
        // Given
        String username = "receiver@domain.com";
        MailboxPath inbox = new MailboxPath("#private", username, "INBOX");
        MessageManager messageManager = mock(MessageManager.class);

        when(parameter.getUsersRepository().supportVirtualHosting()).thenReturn(true);
        doThrow(new ScriptNotFoundException()).when(parameter.getSieveRepository()).getActive(username);
        when(parameter.getMailboxManager().getMailbox(eq(inbox), any(MailboxSession.class))).thenReturn(messageManager);
        when(parameter.getUser().getUserName()).thenReturn(username);

        // When
        Mail mail = createMail();
        parameter.getMailet().init(config);
        parameter.getMailet().service(mail);

        // Then
        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void mailShouldBeWellDeliveredByDefaultToUserWhenVirtualHostingIsTurnedOff() throws Exception {
        // Given
        String username = "receiver";
        MailboxPath inbox = new MailboxPath("#private", username, "INBOX");
        MessageManager messageManager = mock(MessageManager.class);
        when(parameter.getUsersRepository().supportVirtualHosting()).thenReturn(false);
        doThrow(new ScriptNotFoundException()).when(parameter.getSieveRepository()).getActive(username);
        when(parameter.getMailboxManager().getMailbox(eq(inbox), any(MailboxSession.class))).thenReturn(messageManager);
        when(parameter.getUser().getUserName()).thenReturn(username);

        // When
        Mail mail = createMail();
        parameter.getMailet().init(config);
        parameter.getMailet().service(mail);

        // Then
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
        return FakeMail.builder()
                .mimeMessage(message)
                .state(Mail.DEFAULT)
                .recipient(new MailAddress("receiver@domain.com"))
                .build();
    }

}