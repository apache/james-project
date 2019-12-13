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
import org.apache.james.transport.mailets.LocalDelivery;
import org.apache.james.user.api.UsersRepository;
import org.apache.mailet.Mail;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;

public class LocalDeliveryTest {

    public static final String RECEIVER_DOMAIN_COM = "receiver@domain.com";
    private UsersRepository usersRepository;
    private MailboxManager mailboxManager;
    private FakeMailetConfig config;
    private LocalDelivery testee;
    private MailboxSession session;

    @Before
    public void setUp() throws Exception {
        usersRepository = mock(UsersRepository.class);
        mailboxManager = mock(MailboxManager.class);

        MetricFactory metricFactory = new RecordingMetricFactory();
        testee = new LocalDelivery(usersRepository, mailboxManager, metricFactory);

        session = mock(MailboxSession.class);
        when(session.getPathDelimiter()).thenReturn('.');
        when(mailboxManager.createSystemSession(any(Username.class))).thenReturn(session);


        config = FakeMailetConfig.builder()
            .mailetName("Local delivery")
            .mailetContext(FakeMailContext.builder().logger(mock(Logger.class)))
            .build();
    }

    @Test
    public void mailShouldBeWellDeliveredByDefaultToUserWhenVirtualHostingIsTurnedOn() throws Exception {
        // Given
        Username username = Username.of("receiver@domain.com");
        MailboxPath inbox = MailboxPath.inbox(username);
        MessageManager messageManager = mock(MessageManager.class);

        when(usersRepository.supportVirtualHosting()).thenReturn(true);
        when(usersRepository.getUser(new MailAddress(username.asString()))).thenReturn(username);
        when(mailboxManager.getMailbox(eq(inbox), any(MailboxSession.class))).thenReturn(messageManager);
        when(session.getUser()).thenReturn(username);

        // When
        Mail mail = createMail();
        testee.init(config);
        testee.service(mail);

        // Then
        verify(messageManager).appendMessage(any(MessageManager.AppendCommand.class), any(MailboxSession.class));
    }

    @Test
    public void mailShouldBeWellDeliveredByDefaultToUserWhenVirtualHostingIsTurnedOff() throws Exception {
        // Given
        Username username = Username.of("receiver");
        MailboxPath inbox = MailboxPath.inbox(username);
        MessageManager messageManager = mock(MessageManager.class);
        when(usersRepository.supportVirtualHosting()).thenReturn(false);
        when(usersRepository.getUser(new MailAddress("receiver@localhost"))).thenReturn(username);
        when(usersRepository.getUser(new MailAddress(RECEIVER_DOMAIN_COM))).thenReturn(username);
        when(mailboxManager.getMailbox(eq(inbox), any(MailboxSession.class))).thenReturn(messageManager);
        when(session.getUser()).thenReturn(username);

        // When
        Mail mail = createMail();
        testee.init(config);
        testee.service(mail);

        // Then
        verify(messageManager).appendMessage(any(MessageManager.AppendCommand.class), any(MailboxSession.class));
    }

    private Mail createMail() throws MessagingException, IOException {
        return FakeMail.builder()
                .name("name")
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
                .recipient("receiver@domain.com")
                .build();
    }

}