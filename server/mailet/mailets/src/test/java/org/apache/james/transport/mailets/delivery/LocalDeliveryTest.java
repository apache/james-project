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
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;

import javax.mail.Flags;
import javax.mail.MessagingException;
import javax.mail.internet.MimeBodyPart;

import org.apache.james.core.MailAddress;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.metrics.api.Metric;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.transport.mailets.LocalDelivery;
import org.apache.james.user.api.UsersRepository;
import org.apache.mailet.Mail;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.apache.mailet.base.test.MimeMessageBuilder;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;

public class LocalDeliveryTest {

    public static final String RECEIVER_DOMAIN_COM = "receiver@domain.com";
    private UsersRepository usersRepository;
    private MailboxManager mailboxManager;
    private MailboxSession.User user;
    private FakeMailetConfig config;
    private LocalDelivery testee;

    @Before
    public void setUp() throws Exception {
        usersRepository = mock(UsersRepository.class);
        mailboxManager = mock(MailboxManager.class);

        MetricFactory metricFactory = mock(MetricFactory.class);
        when(metricFactory.generate(anyString())).thenReturn(mock(Metric.class));
        testee = new LocalDelivery(usersRepository, mailboxManager, metricFactory);

        user = mock(MailboxSession.User.class);
        MailboxSession session = mock(MailboxSession.class);
        when(session.getPathDelimiter()).thenReturn('.');
        when(mailboxManager.createSystemSession(any(String.class))).thenReturn(session);
        when(session.getUser()).thenReturn(user);

        config = FakeMailetConfig.builder()
            .mailetName("Local delivery")
            .mailetContext(FakeMailContext.builder().logger(mock(Logger.class)))
            .build();
    }

    @Test
    public void mailShouldBeWellDeliveredByDefaultToUserWhenVirtualHostingIsTurnedOn() throws Exception {
        // Given
        String username = "receiver@domain.com";
        MailboxPath inbox = MailboxPath.forUser(username, "INBOX");
        MessageManager messageManager = mock(MessageManager.class);

        when(usersRepository.supportVirtualHosting()).thenReturn(true);
        when(usersRepository.getUser(new MailAddress(username))).thenReturn(username);
        when(mailboxManager.getMailbox(eq(inbox), any(MailboxSession.class))).thenReturn(messageManager);
        when(user.getUserName()).thenReturn(username);

        // When
        Mail mail = createMail();
        testee.init(config);
        testee.service(mail);

        // Then
        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    @Test
    public void mailShouldBeWellDeliveredByDefaultToUserWhenVirtualHostingIsTurnedOff() throws Exception {
        // Given
        String username = "receiver";
        MailboxPath inbox = MailboxPath.forUser(username, "INBOX");
        MessageManager messageManager = mock(MessageManager.class);
        when(usersRepository.supportVirtualHosting()).thenReturn(false);
        when(usersRepository.getUser(new MailAddress("receiver@localhost"))).thenReturn(username);
        when(usersRepository.getUser(new MailAddress(RECEIVER_DOMAIN_COM))).thenReturn(username);
        when(mailboxManager.getMailbox(eq(inbox), any(MailboxSession.class))).thenReturn(messageManager);
        when(user.getUserName()).thenReturn(username);

        // When
        Mail mail = createMail();
        testee.init(config);
        testee.service(mail);

        // Then
        verify(messageManager).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), eq(true), any(Flags.class));
    }

    private Mail createMail() throws MessagingException, IOException {
        return FakeMail.builder()
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setSender("sender@any.com")
                    .setSubject("Subject")
                    .addToRecipient(RECEIVER_DOMAIN_COM)
                    .setMultipartWithBodyParts(
                        MimeMessageBuilder.bodyPartBuilder()
                            .data("toto")
                            .disposition(MimeBodyPart.ATTACHMENT)
                            .filename("file.txt")
                            .addHeader("Content-Type", "application/sieve; charset=UTF-8")))
                .state(Mail.DEFAULT)
                .recipient(new MailAddress("receiver@domain.com"))
                .build();
    }

}