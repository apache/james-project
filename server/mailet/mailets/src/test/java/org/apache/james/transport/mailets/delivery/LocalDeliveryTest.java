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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeBodyPart;

import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.transport.mailets.LocalDelivery;
import org.apache.james.user.api.UsersRepository;
import org.apache.mailet.Mail;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

class LocalDeliveryTest {
    public static final String RECEIVER_DOMAIN_COM = "receiver@domain.com";

    private UsersRepository usersRepository;
    private MailboxManager mailboxManager;
    private FakeMailetConfig config;
    private LocalDelivery testee;

    @BeforeEach
    void setUp() {
        usersRepository = mock(UsersRepository.class);
        mailboxManager = InMemoryIntegrationResources.defaultResources().getMailboxManager();

        MetricFactory metricFactory = new RecordingMetricFactory();
        testee = new LocalDelivery(usersRepository, mailboxManager, metricFactory);

        config = FakeMailetConfig.builder()
            .mailetName("Local delivery")
            .mailetContext(FakeMailContext.builder().logger(mock(Logger.class)))
            .build();
    }

    @Test
    void mailShouldBeWellDeliveredByDefaultToUserWhenVirtualHostingIsTurnedOn() throws Exception {
        // Given
        Username username = Username.of("receiver@domain.com");
        MailboxPath inbox = MailboxPath.inbox(username);
        MailboxSession session = mailboxManager.createSystemSession(username);
        mailboxManager.createMailbox(inbox, mailboxManager.createSystemSession(username));
        when(usersRepository.supportVirtualHosting()).thenReturn(true);
        when(usersRepository.getUsername(new MailAddress(username.asString()))).thenReturn(username);

        // When
        Mail mail = createMail();
        testee.init(config);
        testee.service(mail);

        // Then
        assertThat(mailboxManager.getMailbox(inbox, session).getMailboxCounters(session).getCount())
            .isEqualTo(1L);
    }

    @Test
    void mailShouldBeWellDeliveredByDefaultToUserWhenVirtualHostingIsTurnedOff() throws Exception {
        // Given
        Username username = Username.of("receiver");
        MailboxPath inbox = MailboxPath.inbox(username);
        MailboxSession session = mailboxManager.createSystemSession(username);
        mailboxManager.createMailbox(inbox, session);
        when(usersRepository.supportVirtualHosting()).thenReturn(false);
        when(usersRepository.getUsername(new MailAddress("receiver@localhost"))).thenReturn(username);
        when(usersRepository.getUsername(new MailAddress(RECEIVER_DOMAIN_COM))).thenReturn(username);

        // When
        Mail mail = createMail();
        testee.init(config);
        testee.service(mail);

        // Then
        assertThat(mailboxManager.getMailbox(inbox, session).getMailboxCounters(session).getCount())
            .isEqualTo(1L);
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