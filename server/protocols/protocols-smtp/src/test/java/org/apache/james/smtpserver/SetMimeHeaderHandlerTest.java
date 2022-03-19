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
package org.apache.james.smtpserver;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.ThreadLocalRandom;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.utils.BaseFakeSMTPSession;
import org.apache.james.server.core.MailImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SetMimeHeaderHandlerTest {

    private static final String HEADER_NAME = "JUNIT";
    private static final String HEADER_VALUE = "test-value";

    private SMTPSession fakeSMTPSession;

    @BeforeEach
    void setUp() {
        fakeSMTPSession = new BaseFakeSMTPSession() {

            @Override
            public int getRcptCount() {
                return 0;
            }
        };
    }

    @Test
    void setMimeHeaderHandlerShouldAddSpecifiedHeader() throws MessagingException {
        MimeMessage mimeMessage = MimeMessageBuilder.mimeMessageBuilder()
            .setSubject("testmail")
            .setText("testtext")
            .addToRecipient("test2@james.apache.org")
            .addFrom("test@james.apache.org")
            .build();

        MailImpl mail = MailImpl.builder()
            .name("ID=" + ThreadLocalRandom.current().nextLong())
            .mimeMessage(mimeMessage)
            .addRecipients("test@james.apache.org", "test2@james.apache.org")
            .build();

        SetMimeHeaderHandler header = new SetMimeHeaderHandler();
        header.setHeaderName(HEADER_NAME);
        header.setHeaderValue(HEADER_VALUE);

        header.onMessage(fakeSMTPSession, mail);

        assertThat(mail.getMessage().getHeader(HEADER_NAME)).containsOnly(HEADER_VALUE);
    }

    @Test
    void setMimeHeaderHandlerShouldReplaceSpecifiedHeader() throws MessagingException {

        MimeMessage mimeMessage = MimeMessageBuilder.mimeMessageBuilder()
            .addHeader(HEADER_NAME, "defaultHeaderValue")
            .setSubject("testmail")
            .setText("testtext")
            .addToRecipient("test2@james.apache.org")
            .addFrom("test@james.apache.org")
            .build();
        MailImpl mail = MailImpl.builder()
            .name("ID=" + ThreadLocalRandom.current().nextLong())
            .mimeMessage(mimeMessage)
            .addRecipients("test@james.apache.org", "test2@james.apache.org")
            .build();

        SetMimeHeaderHandler header = new SetMimeHeaderHandler();
        header.setHeaderName(HEADER_NAME);
        header.setHeaderValue(HEADER_VALUE);

        header.onMessage(fakeSMTPSession, mail);

        assertThat(mail.getMessage().getHeader(HEADER_NAME)).containsOnly(HEADER_VALUE);
    }

}
