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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;

import javax.mail.MessagingException;
import javax.mail.internet.MimeBodyPart;

import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.transport.mailets.ToRecipientFolder;
import org.apache.james.user.api.UsersRepository;
import org.apache.mailet.Mail;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;

@Deprecated
public class ToRecipientFolderTest {

    public static final String USER_LOCAL_PART = "receiver";
    public static final String USER = USER_LOCAL_PART + "@domain.com";
    public static final Username USERNAME = Username.of(USER);
    public static final Username USERNAME_LOCAL_PART = Username.of(USER_LOCAL_PART);
    public static final MailboxPath INBOX = MailboxPath.inbox(USERNAME);
    public static final MailboxPath JUNK = MailboxPath.forUser(USERNAME_LOCAL_PART, "Junk");
    public static final MailboxPath JUNK_VIRTUAL_HOSTING = MailboxPath.forUser(USERNAME, "Junk");
    public static final String MAILET_NAME = "RecipientFolderTest";

    private MessageManager messageManager;
    private UsersRepository usersRepository;
    private MailboxManager mailboxManager;
    private ToRecipientFolder testee;
    private FakeMailContext mailetContext;
    private MailboxSession session;

    @Before
    public void setUp() throws Exception {
        mailetContext = FakeMailContext.builder().logger(mock(Logger.class)).build();
        messageManager = mock(MessageManager.class);
        usersRepository = mock(UsersRepository.class);
        mailboxManager = mock(MailboxManager.class);


        MetricFactory metricFactory = new RecordingMetricFactory();
        testee = new ToRecipientFolder(mailboxManager, usersRepository, metricFactory);

        session = mock(MailboxSession.class);
        when(session.getPathDelimiter()).thenReturn('.');
        when(mailboxManager.createSystemSession(any(Username.class))).thenReturn(session);
        when(session.getUser()).thenReturn(Username.of(USER));
    }

    @Test
    public void initParameterTesting() throws Exception {
        testee.init(FakeMailetConfig.builder()
            .mailetName(MAILET_NAME)
            .mailetContext(mailetContext)
            .setProperty(ToRecipientFolder.FOLDER_PARAMETER, "Junk")
            .build());

        assertThat(testee.getInitParameter(ToRecipientFolder.FOLDER_PARAMETER)).isEqualTo("Junk");
    }

    @Test
    public void consumeOptionShouldGhostTheMail() throws Exception {
        testee.init(FakeMailetConfig.builder()
            .mailetName(MAILET_NAME)
            .mailetContext(mailetContext)
            .setProperty(ToRecipientFolder.CONSUME_PARAMETER, "true")
            .build());

        Mail mail = createMail();
        testee.service(mail);

        assertThat(mail.getState()).isEqualTo(Mail.GHOST);
    }

    @Test
    public void consumeOptionShouldNotGhostTheMailByDefault() throws Exception {
        testee.init(FakeMailetConfig.builder()
            .mailetName(MAILET_NAME)
            .mailetContext(mailetContext)
            .build());

        Mail mail = createMail();
        testee.service(mail);

        assertThat(mail.getState()).isEqualTo(Mail.DEFAULT);
    }

    @Test
    public void folderParameterShouldIndicateDestinationFolder() throws Exception {
        when(usersRepository.supportVirtualHosting()).thenReturn(true);
        when(usersRepository.getUsername(new MailAddress(USER))).thenReturn(USERNAME);
        when(mailboxManager.getMailbox(eq(JUNK_VIRTUAL_HOSTING), any(MailboxSession.class))).thenReturn(messageManager);

        testee.init(FakeMailetConfig.builder()
            .mailetName(MAILET_NAME)
            .mailetContext(mailetContext)
            .setProperty(ToRecipientFolder.FOLDER_PARAMETER, "Junk")
            .build());
        testee.service(createMail());

        verify(messageManager).appendMessage(any(MessageManager.AppendCommand.class), any(MailboxSession.class));
    }

    @Test
    public void folderParameterShouldBeInboxByDefault() throws Exception {
        when(usersRepository.supportVirtualHosting()).thenReturn(true);
        when(usersRepository.getUsername(new MailAddress(USER))).thenReturn(USERNAME);
        when(mailboxManager.getMailbox(eq(INBOX), any(MailboxSession.class))).thenReturn(messageManager);

        testee.init(FakeMailetConfig.builder()
            .mailetName(MAILET_NAME)
            .mailetContext(mailetContext)
            .build());
        testee.service(createMail());

        verify(messageManager).appendMessage(any(MessageManager.AppendCommand.class), any(MailboxSession.class));
    }

    @Test
    public void folderParameterShouldWorkWhenVirtualHostingIsTurnedOff() throws Exception {
        when(usersRepository.supportVirtualHosting()).thenReturn(false);
        when(usersRepository.getUsername(new MailAddress(USER_LOCAL_PART + "@localhost"))).thenReturn(USERNAME_LOCAL_PART);
        when(usersRepository.getUsername(new MailAddress(USER))).thenReturn(USERNAME_LOCAL_PART);
        when(mailboxManager.getMailbox(eq(JUNK), any(MailboxSession.class))).thenReturn(messageManager);
        when(session.getUser()).thenReturn(Username.of(USER_LOCAL_PART));

        testee.init(FakeMailetConfig.builder()
            .mailetName(MAILET_NAME)
            .mailetContext(mailetContext)
            .setProperty(ToRecipientFolder.FOLDER_PARAMETER, "Junk")
            .setProperty(ToRecipientFolder.CONSUME_PARAMETER, "true")
            .build());
        testee.service(createMail());

        verify(messageManager).appendMessage(any(MessageManager.AppendCommand.class), any(MailboxSession.class));
    }

    private Mail createMail() throws MessagingException, IOException {
        return FakeMail.builder()
            .name("name")
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .setSender("sender@any.com")
                .setSubject("Subject")
                .addToRecipient(USER)
                .setMultipartWithBodyParts(
                    MimeMessageBuilder.bodyPartBuilder()
                        .data("toto")
                        .disposition(MimeBodyPart.ATTACHMENT)
                        .filename("file.txt")
                        .addHeader("Content-Type", "application/sieve; charset=UTF-8")))
            .state(Mail.DEFAULT)
            .recipient("receiver@domain.com")
            .build();
    }

}
