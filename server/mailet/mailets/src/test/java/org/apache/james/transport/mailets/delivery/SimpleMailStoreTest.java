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

import java.util.Properties;

import javax.activation.DataHandler;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;

import org.apache.commons.logging.Log;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.mailet.MailAddress;
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
            .log(mock(Log.class))
            .build();

        mimeMessage = new MimeMessage(Session.getDefaultInstance(new Properties()));
        Multipart multipart = new MimeMultipart();
        MimeBodyPart bodyPart = new MimeBodyPart();
        bodyPart.setDataHandler(
            new DataHandler(
                new ByteArrayDataSource(
                    "toto",
                    "text/plain; charset=UTF-8")
            ));
        multipart.addBodyPart(bodyPart);
        mimeMessage.setContent(multipart);
        mimeMessage.saveChanges();
    }

    @Test
    public void storeMailShouldUseFullMailAddressWhenSupportsVirtualHosting() throws Exception {
        MailAddress sender = MailAddressFixture.ANY_AT_JAMES;
        MailAddress recipient = MailAddressFixture.OTHER_AT_JAMES;
        when(usersRepository.getUser(recipient)).thenReturn(recipient.print());
        FakeMail mail = FakeMail.builder()
            .mimeMessage(mimeMessage)
            .build();
        testee.storeMail(sender, recipient, mail);

        verify(mailboxAppender).append(any(MimeMessage.class), eq(recipient.print()), eq(FOLDER));
    }

    @Test
    public void storeMailShouldUseLocalPartWhenSupportsVirtualHosting() throws Exception {
        MailAddress sender = MailAddressFixture.ANY_AT_JAMES;
        MailAddress recipient = MailAddressFixture.OTHER_AT_JAMES;
        when(usersRepository.getUser(recipient)).thenReturn(recipient.getLocalPart());
        FakeMail mail = FakeMail.builder()
            .mimeMessage(mimeMessage)
            .build();
        testee.storeMail(sender, recipient, mail);

        verify(mailboxAppender).append(any(MimeMessage.class), eq(recipient.getLocalPart()), eq(FOLDER));
    }

    @Test
    public void storeMailShouldUseFullMailAddressWhenErrorReadingUsersRepository() throws Exception {
        MailAddress sender = MailAddressFixture.ANY_AT_JAMES;
        MailAddress recipient = MailAddressFixture.OTHER_AT_JAMES;
        when(usersRepository.getUser(recipient)).thenThrow(new UsersRepositoryException("Any message"));
        FakeMail mail = FakeMail.builder()
            .mimeMessage(mimeMessage)
            .build();
        testee.storeMail(sender, recipient, mail);

        verify(mailboxAppender).append(any(MimeMessage.class), eq(recipient.toString()), eq(FOLDER));
    }
}
