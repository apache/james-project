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

package org.apache.james.transport.matchers;

import static org.apache.mailet.base.MailAddressFixture.ANY_AT_JAMES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;

import org.apache.james.util.MimeMessageUtil;
import org.apache.mailet.Mail;
import org.apache.mailet.base.test.FakeMail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class HasAttachmentTest {

    private HasAttachment testee;
    private MimeMessage mimeMessage;
    private Mail mail;

    @BeforeEach
    public void setUp() throws Exception {
        testee = new HasAttachment();

        mimeMessage = MimeMessageUtil.defaultMimeMessage();
        mail = FakeMail.builder()
            .name("mail")
            .recipient(ANY_AT_JAMES)
            .mimeMessage(mimeMessage)
            .build();
    }

    @Test
    public void textMailsShouldNotBeMatched() throws Exception {
        mimeMessage.setText("A simple text message");

        assertThat(testee.match(mail)).isNull();
    }

    @Test
    public void emptyMultipartShouldNotBeMatched() throws Exception {
        mimeMessage.setContent(new MimeMultipart());

        assertThat(testee.match(mail)).isNull();
    }

    @Test
    public void inlinedOnlyMultipartShouldNotBeMatched() throws Exception {
        MimeMultipart mimeMultipart = new MimeMultipart();
        MimeBodyPart part = new MimeBodyPart();
        part.setDisposition(MimeMessage.INLINE);
        part.setFileName("bahamas.png");
        mimeMultipart.addBodyPart(part);
        mimeMessage.setContent(mimeMultipart);

        assertThat(testee.match(mail)).isNull();
    }

    @Test
    public void multipartWithOneAttachmentShouldBeMatched() throws Exception {
        MimeMultipart mimeMultipart = new MimeMultipart();
        MimeBodyPart textPart = new MimeBodyPart();
        textPart.setDisposition(MimeMessage.INLINE);
        mimeMultipart.addBodyPart(textPart);
        MimeBodyPart attachmentPart = new MimeBodyPart();
        attachmentPart.setDisposition(MimeMessage.ATTACHMENT);
        mimeMultipart.addBodyPart(attachmentPart);
        mimeMessage.setContent(mimeMultipart);

        assertThat(testee.match(mail)).containsAll(mail.getRecipients());
    }

    @Test
    public void attachmentMailsShouldBeMatched() throws Exception {
        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mimeMessage.getContent()).thenReturn(new Object());
        when(mimeMessage.getDisposition()).thenReturn(MimeMessage.ATTACHMENT);
        when(mimeMessage.getContentType()).thenReturn("application/json");
        mail.setMessage(mimeMessage);

        assertThat(testee.match(mail)).containsAll(mail.getRecipients());
    }

}
