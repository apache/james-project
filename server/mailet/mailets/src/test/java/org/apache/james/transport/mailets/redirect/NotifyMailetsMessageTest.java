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

package org.apache.james.transport.mailets.redirect;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.util.Properties;
import java.util.TimeZone;

import javax.mail.Message.RecipientType;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.apache.james.core.MailAddress;
import org.apache.mailet.base.test.FakeMail;
import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableList;

public class NotifyMailetsMessageTest {

    private TimeZone timeZone;

    @Before
    public void setUp() throws Exception {
        timeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @After
    public void tearDown() {
        TimeZone.setDefault(timeZone);
    }

    @Test
    public void generateMessageShouldReturnTheMessageWhenSimpleMimeMessage() throws Exception {
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        FakeMail mail = FakeMail.builder()
                .mimeMessage(message)
                .sender(new MailAddress("user", "james.org"))
                .build();

        String generateMessage = new NotifyMailetsMessage().generateMessage("my message", mail);

        assertThat(generateMessage).isEqualTo("my message\n" +
                "\n" +
                "Message details:\n" +
                "  MAIL FROM: user@james.org\n" +
                "  Size (in bytes): -1\n");
    }

    @Test
    public void generateMessageShouldAddErrorMessageWhenMimeMessageAsSome() throws Exception {
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        FakeMail mail = FakeMail.from(message);
        mail.setErrorMessage("error message");

        String generateMessage = new NotifyMailetsMessage().generateMessage("my message", mail);

        assertThat(generateMessage).isEqualTo("my message\n" +
                "\n" +
                "Error message below:\n" +
                "error message\n" +
                "\n" +
                "Message details:\n" +
                "  MAIL FROM: null\n" +
                "  Size (in bytes): -1\n");
    }

    @Test
    public void generateMessageShouldAddSubjectWhenMimeMessageAsSome() throws Exception {
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        message.setSubject("my subject");
        FakeMail mail = FakeMail.from(message);

        String generateMessage = new NotifyMailetsMessage().generateMessage("my message", mail);

        assertThat(generateMessage).isEqualTo("my message\n" +
                "\n" +
                "Message details:\n" +
                "  Subject: my subject\n" +
                "  MAIL FROM: null\n" +
                "  Size (in bytes): -1\n");
    }

    @Test
    public void generateMessageShouldAddSentDateWhenMimeMessageAsSome() throws Exception {
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        message.setSentDate(DateTime.parse("2016-09-08T14:25:52.000Z").toDate());
        FakeMail mail = FakeMail.from(message);

        String generateMessage = new NotifyMailetsMessage().generateMessage("my message", mail);

        assertThat(generateMessage).isEqualTo("my message\n" +
                "\n" +
                "Message details:\n" +
                "  Sent date: Thu Sep 08 14:25:52 UTC 2016\n" +
                "  MAIL FROM: null\n" +
                "  Size (in bytes): -1\n");
    }

    @Test
    public void generateMessageShouldAddRecipientsWhenMimeMessageAsSome() throws Exception {
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        FakeMail mail = FakeMail.from(message);
        mail.setRecipients(ImmutableList.of(new MailAddress("user", "james.org"), new MailAddress("user2", "james.org")));

        String generateMessage = new NotifyMailetsMessage().generateMessage("my message", mail);

        assertThat(generateMessage).isEqualTo("my message\n" +
                "\n" +
                "Message details:\n" +
                "  MAIL FROM: null\n" +
                "  RCPT TO: user@james.org\n" +
                "           user2@james.org\n" +
                "  Size (in bytes): -1\n");
    }

    @Test
    public void generateMessageShouldAddFromWhenMimeMessageAsSome() throws Exception {
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        message.setFrom(new InternetAddress("user@james.org"));
        FakeMail mail = FakeMail.from(message);

        String generateMessage = new NotifyMailetsMessage().generateMessage("my message", mail);

        assertThat(generateMessage).isEqualTo("my message\n" +
                "\n" +
                "Message details:\n" +
                "  MAIL FROM: null\n" +
                "  From: \n" +
                "user@james.org \n" +
                "\n" +
                "  Size (in bytes): -1\n");
    }

    @Test
    public void generateMessageShouldAddToWhenMimeMessageAsSome() throws Exception {
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        message.setRecipients(RecipientType.TO, new InternetAddress[] { new InternetAddress("user@james.org"), new InternetAddress("user2@james.org") });
        FakeMail mail = FakeMail.from(message);

        String generateMessage = new NotifyMailetsMessage().generateMessage("my message", mail);

        assertThat(generateMessage).isEqualTo("my message\n" +
                "\n" +
                "Message details:\n" +
                "  MAIL FROM: null\n" +
                "  To: \n" +
                "user@james.org \n" +
                "user2@james.org \n" +
                "\n" +
                "  Size (in bytes): -1\n");
    }

    @Test
    public void generateMessageShouldAddCCWhenMimeMessageAsSome() throws Exception {
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        message.setRecipients(RecipientType.CC, new InternetAddress[] { new InternetAddress("user@james.org"), new InternetAddress("user2@james.org") });
        FakeMail mail = FakeMail.from(message);

        String generateMessage = new NotifyMailetsMessage().generateMessage("my message", mail);

        assertThat(generateMessage).isEqualTo("my message\n" +
                "\n" +
                "Message details:\n" +
                "  MAIL FROM: null\n" +
                "  CC: \n" +
                "user@james.org \n" +
                "user2@james.org \n" +
                "\n" +
                "  Size (in bytes): -1\n");
    }

    @Test
    public void generateMessageShouldAddSizeWhenMimeMessageAsSome() throws Exception {
        String content = "MIME-Version: 1.0\r\n" +
                "Content-Type: text/plain; charset=utf-8\r\n" +
                "\r\n" +
                "test\r\n";
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()), new ByteArrayInputStream(content.getBytes()));
        FakeMail mail = FakeMail.from(message);

        String generateMessage = new NotifyMailetsMessage().generateMessage("my message", mail);

        assertThat(generateMessage).isEqualTo("my message\n" +
                "\n" +
                "Message details:\n" +
                "  MAIL FROM: null\n" +
                "  Size (in bytes): 6\n");
    }
}
