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
package org.apache.james.transport.mailets;

import static net.javacrumbs.jsonunit.fluent.JsonFluentAssert.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.james.core.MailAddress;
import org.apache.mailet.MailetContext;
import org.apache.mailet.MailetException;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.apache.mailet.base.test.MimeMessageBuilder;
import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ContactExtractorTest {

    private static final String ATTRIBUTE = "ExtractedContacts";
    private static final String SENDER = "sender@james.org";
    private static final String TO = "to@james.org";

    private ContactExtractor mailet;
    private MailetContext mailetContext;
    private FakeMailetConfig mailetConfig;

    @Before
    public void setUp() throws Exception {
        mailet = new ContactExtractor();
        mailetContext = FakeMailContext.builder()
                .build();
        mailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .mailetContext(mailetContext)
                .setProperty(ContactExtractor.Configuration.ATTRIBUTE, ATTRIBUTE)
                .build();
    }

    @Test
    public void initShouldThrowWhenNoAttributeParameter() throws MessagingException {
        FakeMailetConfig customMailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .mailetContext(mailetContext)
                .build();
        
        assertThatThrownBy(() -> mailet.init(customMailetConfig))
            .isInstanceOf(MailetException.class);
    }

    @Test
    public void initShouldNotThrowWithAllParameters() throws MessagingException {
        mailet.init(mailetConfig);
    }

    @Test
    public void getMailetInfoShouldReturnInfo() {
        assertThat(mailet.getMailetInfo()).isEqualTo("ContactExtractor Mailet");
    }

    @Test
    public void serviceShouldNotThrowWhenJsonProcessingFails() throws Exception {
        FakeMail mail = FakeMail.builder().mimeMessage(MimeMessageBuilder.defaultMimeMessage())
                .sender(new MailAddress(SENDER))
                .recipient(new MailAddress(TO))
                .build();

        ObjectMapper objectMapper = mock(ObjectMapper.class);
        when(objectMapper.writeValueAsString(any(ContactExtractor.ExtractedContacts.class)))
            .thenThrow(new JsonGenerationException(""));

        mailet.init(mailetConfig);
        mailet.objectMapper = objectMapper;

        mailet.service(mail);
    }

    @Test
    public void serviceShouldAddTheAttribute() throws Exception {
        MimeMessage message = MimeMessageBuilder.mimeMessageBuilder()
                .setSender(SENDER)
                .addToRecipient(TO)
                .setSubject("Contact collection Rocks")
                .setText("This is my email")
                .build();
        FakeMail mail = FakeMail.builder().mimeMessage(message)
            .sender(new MailAddress(SENDER))
            .recipient(new MailAddress(TO))
            .build();
        mailet.init(mailetConfig);

        String expectedMessage = "{\"userEmail\" : \"" + SENDER + "\", \"emails\" : [ \"" + TO + "\" ]}";
        mailet.service(mail);

        assertThatJson(mail.getAttribute(ATTRIBUTE).toString()).isEqualTo(expectedMessage);
    }

    @Test
    public void serviceShouldPreserveRecipientsEmailAddress() throws Exception {
        MimeMessage message = MimeMessageBuilder.mimeMessageBuilder()
            .setSender(SENDER)
            .addToRecipient("To <" + TO + ">")
            .setSubject("Contact collection Rocks")
            .setText("This is my email")
            .build();
        FakeMail mail = FakeMail.builder().mimeMessage(message)
            .sender(new MailAddress(SENDER))
            .recipient(new MailAddress(TO))
            .build();
        mailet.init(mailetConfig);

        String expectedMessage = "{\"userEmail\" : \"" + SENDER + "\", \"emails\" : [ \"To <" + TO + ">\" ]}";
        mailet.service(mail);

        assertThatJson(mail.getAttribute(ATTRIBUTE).toString()).isEqualTo(expectedMessage);
    }

    @Test
    public void serviceShouldUnscrambleRecipients() throws Exception {
        MimeMessage message = MimeMessageBuilder.mimeMessageBuilder()
            .setSender(SENDER)
            .addToRecipient("=?ISO-8859-1?Q?Beno=EEt_TELLIER?= <tellier@linagora.com>")
            .setSubject("Contact collection Rocks")
            .setText("This is my email")
            .build();
        FakeMail mail = FakeMail.builder().mimeMessage(message)
            .sender(new MailAddress(SENDER))
            .recipient(new MailAddress(TO))
            .build();
        mailet.init(mailetConfig);

        String expectedMessage = "{\"userEmail\" : \"" + SENDER + "\", \"emails\" : [ \"Benoît TELLIER <tellier@linagora.com>\" ]}";
        mailet.service(mail);

        assertThatJson(mail.getAttribute(ATTRIBUTE).toString()).isEqualTo(expectedMessage);
    }

    @Test
    public void serviceShouldNotOverwriteSenderWhenDifferentFromField() throws Exception {
        MimeMessage message = MimeMessageBuilder.mimeMessageBuilder()
            .addFrom("other@sender.org")
            .addToRecipient("To <" + TO + ">")
            .setSubject("Contact collection Rocks")
            .setText("This is my email")
            .build();
        FakeMail mail = FakeMail.builder().mimeMessage(message)
            .sender(new MailAddress(SENDER))
            .recipient(new MailAddress(TO))
            .build();
        mailet.init(mailetConfig);

        String expectedMessage = "{\"userEmail\" : \"" + SENDER + "\", \"emails\" : [ \"To <" + TO + ">\" ]}";
        mailet.service(mail);

        assertThatJson(mail.getAttribute(ATTRIBUTE).toString()).isEqualTo(expectedMessage);
    }

    @Test
    public void serviceShouldNotOverwriteSenderWhenDifferentSenderField() throws Exception {
        MimeMessage message = MimeMessageBuilder.mimeMessageBuilder()
            .setSender("other@sender.org")
            .addToRecipient("To <" + TO + ">")
            .setSubject("Contact collection Rocks")
            .setText("This is my email")
            .build();
        FakeMail mail = FakeMail.builder().mimeMessage(message)
            .sender(new MailAddress(SENDER))
            .recipient(new MailAddress(TO))
            .build();
        mailet.init(mailetConfig);

        String expectedMessage = "{\"userEmail\" : \"" + SENDER + "\", \"emails\" : [ \"To <" + TO + ">\" ]}";
        mailet.service(mail);

        assertThatJson(mail.getAttribute(ATTRIBUTE).toString()).isEqualTo(expectedMessage);
    }

    @Test
    public void serviceShouldSkipMessagesWithoutSenderEnvelope() throws Exception {
        MimeMessage message = MimeMessageBuilder.mimeMessageBuilder()
            .addToRecipient("To <" + TO + ">")
            .setSubject("Contact collection Rocks")
            .setText("This is my email")
            .build();
        FakeMail mail = FakeMail.builder().mimeMessage(message)
            .recipient(new MailAddress(TO))
            .build();
        mailet.init(mailetConfig);

        mailet.service(mail);

        assertThatJson(mail.getAttribute(ATTRIBUTE)).isEqualTo(null);
    }

    @Test
    public void serviceShouldNotAddTheAttributeWhenNoRecipient() throws Exception {
        MimeMessage message = MimeMessageBuilder.mimeMessageBuilder()
                .setSender(SENDER)
                .setSubject("Contact collection Rocks")
                .setText("This is my email")
                .build();
        FakeMail mail = FakeMail.builder().mimeMessage(message)
            .sender(new MailAddress(SENDER))
            .build();
        mailet.init(mailetConfig);

        mailet.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE)).isNull();

    }
}