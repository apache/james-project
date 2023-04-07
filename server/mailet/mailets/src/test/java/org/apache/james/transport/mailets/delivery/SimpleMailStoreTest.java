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

import javax.mail.internet.MimeMessage;

import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.metrics.api.Metric;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.mailet.base.MailAddressFixture;
import org.apache.mailet.base.test.FakeMail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Mono;

class SimpleMailStoreTest {
    public static final String FOLDER = "FOLDER";
    private SimpleMailStore testee;
    private MailboxAppenderImpl mailboxAppender;
    private UsersRepository usersRepository;
    private MimeMessage mimeMessage;

    @BeforeEach
    void setUp() throws Exception {
        mailboxAppender = mock(MailboxAppenderImpl.class);
        when(mailboxAppender.append(any(), any(), any(), any())).thenReturn(Mono.empty());
        usersRepository = mock(UsersRepository.class);
        testee = SimpleMailStore.builder()
            .usersRepository(usersRepository)
            .mailboxAppender(mailboxAppender)
            .folder(FOLDER)
            .metric(mock(Metric.class))
            .build();

        mimeMessage = MimeMessageBuilder.mimeMessageBuilder()
            .setMultipartWithBodyParts(
                MimeMessageBuilder.bodyPartBuilder()
                    .data("toto"))
            .build();
    }

    @Test
    void storeMailShouldUseFullMailAddressWhenSupportsVirtualHosting() throws Exception {
        MailAddress recipient = MailAddressFixture.OTHER_AT_JAMES;
        when(usersRepository.getUsername(recipient)).thenReturn(Username.of(recipient.asString()));
        FakeMail mail = FakeMail.builder()
            .name("name")
            .mimeMessage(mimeMessage)
            .build();
        testee.storeMail(recipient, mail);

        verify(mailboxAppender).append(any(MimeMessage.class), eq(Username.of(recipient.asString())), eq(FOLDER), any());
    }

    @Test
    void storeMailShouldUseLocalPartWhenSupportsVirtualHosting() throws Exception {
        MailAddress recipient = MailAddressFixture.OTHER_AT_JAMES;
        when(usersRepository.getUsername(recipient)).thenReturn(Username.of(recipient.getLocalPart()));
        FakeMail mail = FakeMail.builder()
            .name("name")
            .mimeMessage(mimeMessage)
            .build();
        testee.storeMail(recipient, mail);

        verify(mailboxAppender).append(any(MimeMessage.class), eq(Username.of(recipient.getLocalPart())), eq(FOLDER), any());
    }

    @Test
    void storeMailShouldUseFullMailAddressWhenErrorReadingUsersRepository() throws Exception {
        MailAddress recipient = MailAddressFixture.OTHER_AT_JAMES;
        when(usersRepository.getUsername(recipient)).thenThrow(new UsersRepositoryException("Any message"));
        FakeMail mail = FakeMail.builder()
            .name("name")
            .mimeMessage(mimeMessage)
            .build();
        testee.storeMail(recipient, mail);

        verify(mailboxAppender).append(any(MimeMessage.class), eq(Username.of(recipient.toString())), eq(FOLDER), any());
    }
}
