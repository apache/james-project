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
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.metrics.api.Metric;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.mailet.base.MailAddressFixture;
import org.apache.mailet.base.test.FakeMail;
import org.junit.Before;
import org.junit.Test;

public class SimpleMailStoreTest {

    public static final String FOLDER = "FOLDER";
    private SimpleMailStore testee;
    private MailboxAppender mailboxAppender;
    private UsersRepository usersRepository;
    private MimeMessage mimeMessage;

    @Before
    public void setUp() throws Exception {
        mailboxAppender = mock(MailboxAppender.class);
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
    public void storeMailShouldUseFullMailAddressWhenSupportsVirtualHosting() throws Exception {
        MailAddress recipient = MailAddressFixture.OTHER_AT_JAMES;
        when(usersRepository.getUser(recipient)).thenReturn(recipient.asString());
        FakeMail mail = FakeMail.builder()
            .mimeMessage(mimeMessage)
            .build();
        testee.storeMail(recipient, mail);

        verify(mailboxAppender).append(any(MimeMessage.class), eq(recipient.asString()), eq(FOLDER));
    }

    @Test
    public void storeMailShouldUseLocalPartWhenSupportsVirtualHosting() throws Exception {
        MailAddress recipient = MailAddressFixture.OTHER_AT_JAMES;
        when(usersRepository.getUser(recipient)).thenReturn(recipient.getLocalPart());
        FakeMail mail = FakeMail.builder()
            .mimeMessage(mimeMessage)
            .build();
        testee.storeMail(recipient, mail);

        verify(mailboxAppender).append(any(MimeMessage.class), eq(recipient.getLocalPart()), eq(FOLDER));
    }

    @Test
    public void storeMailShouldUseFullMailAddressWhenErrorReadingUsersRepository() throws Exception {
        MailAddress recipient = MailAddressFixture.OTHER_AT_JAMES;
        when(usersRepository.getUser(recipient)).thenThrow(new UsersRepositoryException("Any message"));
        FakeMail mail = FakeMail.builder()
            .mimeMessage(mimeMessage)
            .build();
        testee.storeMail(recipient, mail);

        verify(mailboxAppender).append(any(MimeMessage.class), eq(recipient.toString()), eq(FOLDER));
    }
}
