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

package org.apache.james.jmap.mailet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.mail.BodyPart;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.internet.MimeMessage;

import org.apache.james.util.MimeMessageUtil;
import org.apache.mailet.Mail;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class TextCalendarBodyToAttachmentTest {
    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private TextCalendarBodyToAttachment mailet = new TextCalendarBodyToAttachment();

    private MimeMessage calendarMessage;

    @Before
    public void setUp() throws Exception {
        mailet.init(FakeMailetConfig.builder()
            .mailetName("TextCalendarBodyToAttachment")
            .build());

        calendarMessage = MimeMessageUtil.mimeMessageFromStream(ClassLoader.getSystemResourceAsStream("calendar.eml"));
    }

    @Test
    public void getMailetInformationShouldReturnInformation() throws Exception {
        assertThat(mailet.getMailetInfo()).isEqualTo("Moves body part of content type text/calendar to attachment");
    }

    @Test
    public void serviceShouldThrowWhenCanNotGetMessageFromMail() throws Exception {
        expectedException.expect(MessagingException.class);
        Mail mail = mock(Mail.class);
        when(mail.getMessage())
            .thenThrow(new MessagingException());

        mailet.service(mail);
    }

    @Test
    public void serviceShouldThrowWhenMessageCanNotGetContentType() throws Exception {
        expectedException.expect(MessagingException.class);

        MimeMessage message = mock(MimeMessage.class);
        Mail mail = FakeMail.from(message);

        when(message.isMimeType(anyString())).thenThrow(new MessagingException());

        mailet.service(mail);
    }

    @Test
    public void serviceShouldKeepMessageAsItIsWhenMessageIsNotTextCalendar() throws Exception {
        String messageContent = "Content-type: text/html; method=REPLY; charset=UTF-8\n" +
            "Content-transfer-encoding: 8BIT\n" +
            "\n" +
            "BEGIN:VCALENDAR";
        MimeMessage message = MimeMessageUtil.mimeMessageFromString(messageContent);

        Mail mail = FakeMail.builder()
            .name("name")
            .mimeMessage(message)
            .build();

        mailet.service(mail);

        assertThat(mail.getMessage().isMimeType("text/html")).isTrue();
        assertThat(mail.getMessage().getDisposition()).isNull();
    }

    @Test
    public void serviceShouldChangeMessageContentTypeToMultipartWhenTextCalendarMessage() throws Exception {
        String messageContent = "Content-type: text/calendar; method=REPLY; charset=UTF-8\n" +
            "Content-transfer-encoding: 8BIT\n" +
            "\n" +
            "BEGIN:VCALENDAR";
        MimeMessage message = MimeMessageUtil.mimeMessageFromString(messageContent);

        Mail mail = FakeMail.builder()
            .name("name")
            .mimeMessage(message)
            .build();

        mailet.service(mail);

        assertThat(mail.getMessage().isMimeType("multipart/*")).isTrue();
    }

    @Test
    public void serviceShouldConvertTextBodyOfMessageToAttachmentWhenTextCalendar() throws Exception {
        String messageContent = "Content-type: text/calendar; method=REPLY; charset=UTF-8\n" +
            "Content-transfer-encoding: 8BIT\n" +
            "\n" +
            "BEGIN:VCALENDAR\n" +
            "END:VEVENT\n" +
            "END:VCALENDAR";
        MimeMessage message = MimeMessageUtil.mimeMessageFromString(messageContent);

        Mail mail = FakeMail.builder()
            .name("name")
            .mimeMessage(message)
            .build();

        mailet.service(mail);

        MimeMessage actual = mail.getMessage();
        Multipart multipart = (Multipart)actual.getContent();

        assertThat(multipart.getCount()).isEqualTo(1);
        assertThat(multipart.getBodyPart(0).getDisposition()).isEqualTo("attachment");
    }

    @Test
    public void contentTypeOfAttachmentShouldBeTakenFromOriginalMessage() throws Exception {
        Mail mail = FakeMail.builder()
            .name("name")
            .mimeMessage(calendarMessage)
            .build();

        mailet.service(mail);

        Multipart multipart = (Multipart)mail.getMessage().getContent();

        int firstBodyPartIndex = 0;
        BodyPart firstBodyPart = multipart.getBodyPart(firstBodyPartIndex);
        assertThat(firstBodyPart.getContentType()).isEqualTo("text/calendar; method=REPLY; charset=UTF-8");
    }

    @Test
    public void contentTransferEncodingOfAttachmentShouldBeTakenFromOriginalMessage() throws Exception {
        Mail mail = FakeMail.builder()
            .name("name")
            .mimeMessage(calendarMessage)
            .build();

        mailet.service(mail);

        Multipart multipart = (Multipart)mail.getMessage().getContent();

        int firstBodyPartIndex = 0;
        BodyPart firstBodyPart = multipart.getBodyPart(firstBodyPartIndex);
        assertThat(firstBodyPart.getHeader("Content-transfer-encoding")).containsExactly("8BIT");
    }

    @Test
    public void contentClassOfAttachmentShouldBeTakenFromOriginalMessage() throws Exception {
        Mail mail = FakeMail.builder()
            .name("name")
            .mimeMessage(calendarMessage)
            .build();

        mailet.service(mail);

        Multipart multipart = (Multipart)mail.getMessage().getContent();

        int firstBodyPartIndex = 0;
        BodyPart firstBodyPart = multipart.getBodyPart(firstBodyPartIndex);
        assertThat(firstBodyPart.getHeader("Content-class")).containsExactly("urn:content-classes:calendarmessage");
    }

    @Test
    public void contentClassHeaderShouldBeRemoveFromOriginalMessage() throws Exception {
        Mail mail = FakeMail.builder()
            .name("name")
            .mimeMessage(calendarMessage)
            .build();

        mailet.service(mail);

        assertThat(mail.getMessage().getHeader("Content-class")).isNullOrEmpty();
    }

    @Test
    public void contentDispositionHeaderShouldBeRemoveFromOriginalMessage() throws Exception {
        String messageContent = "Content-type: text/calendar; method=REPLY; charset=UTF-8\n" +
            "Content-Disposition: inline\n" +
            "\n" +
            "BEGIN:VCALENDAR\n" +
            "END:VEVENT\n" +
            "END:VCALENDAR";
        MimeMessage message = MimeMessageUtil.mimeMessageFromString(messageContent);

        Mail mail = FakeMail.builder()
            .name("name")
            .mimeMessage(message)
            .build();

        mailet.service(mail);

        assertThat(mail.getMessage().getHeader("Content-Disposition")).isNullOrEmpty();
    }

    @Test
    public void contentDispositionOfAttachmentShouldBeOverwrittenWhenOriginalMessageHasContentDisposition() throws Exception {
        String messageContent = "Content-type: text/calendar; method=REPLY; charset=UTF-8\n" +
            "Content-Disposition: inline\n" +
            "\n" +
            "BEGIN:VCALENDAR\n" +
            "END:VEVENT\n" +
            "END:VCALENDAR";
        MimeMessage message = MimeMessageUtil.mimeMessageFromString(messageContent);

        Mail mail = FakeMail.builder()
            .name("name")
            .mimeMessage(message)
            .build();

        mailet.service(mail);

        Multipart multipart = (Multipart)mail.getMessage().getContent();

        int firstBodyPartIndex = 0;
        BodyPart firstBodyPart = multipart.getBodyPart(firstBodyPartIndex);
        assertThat(firstBodyPart.getHeader("Content-Disposition")).containsExactly("attachment");
    }
}