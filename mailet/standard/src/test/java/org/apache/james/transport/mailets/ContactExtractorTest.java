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

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.util.MimeMessageUtil;
import org.apache.mailet.AttributeName;
import org.apache.mailet.MailetContext;
import org.apache.mailet.MailetException;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ContactExtractorTest {

    private static final AttributeName ATTRIBUTE_NAME = AttributeName.of("ExtractedContacts");
    private static final String SENDER = "sender@james.org";
    private static final String TO = "to@james.org";

    private ContactExtractor mailet;
    private MailetContext mailetContext;
    private FakeMailetConfig mailetConfig;

    @BeforeEach
    public void setUp() throws Exception {
        mailet = new ContactExtractor();
        mailetContext = FakeMailContext.builder()
                .build();
        mailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .mailetContext(mailetContext)
                .setProperty(ContactExtractor.Configuration.ATTRIBUTE, ATTRIBUTE_NAME.asString())
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
        FakeMail mail = FakeMail.builder()
                .name("mail")
                .mimeMessage(MimeMessageUtil.defaultMimeMessage())
                .sender(SENDER)
                .recipient(TO)
                .build();

        ObjectMapper objectMapper = mock(ObjectMapper.class);
        JsonGenerator jsonGenerator = null;
        when(objectMapper.writeValueAsString(any(ContactExtractor.ExtractedContacts.class)))
            .thenThrow(new JsonGenerationException("", jsonGenerator));

        mailet.init(mailetConfig);
        mailet.objectMapper = objectMapper;

        mailet.service(mail);
    }

    @Test
    public void serviceShouldAddTheAttribute() throws Exception {
        MimeMessageBuilder message = MimeMessageBuilder.mimeMessageBuilder()
                .setSender(SENDER)
                .addToRecipient(TO)
                .setSubject("Contact collection Rocks")
                .setText("This is my email");
        FakeMail mail = FakeMail.builder()
            .name("mail")
            .mimeMessage(message)
            .sender(SENDER)
            .recipient(TO)
            .build();
        mailet.init(mailetConfig);

        String expectedMessage = "{\"userEmail\" : \"" + SENDER + "\", \"emails\" : [ \"" + TO + "\" ]}";
        mailet.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE_NAME)).hasValueSatisfying(json ->
            assertThatJson(json.getValue().value().toString()).isEqualTo(expectedMessage));
    }

    @Test
    public void serviceShouldPreserveRecipientsEmailAddress() throws Exception {
        MimeMessageBuilder message = MimeMessageBuilder.mimeMessageBuilder()
            .setSender(SENDER)
            .addToRecipient("To <" + TO + ">")
            .setSubject("Contact collection Rocks")
            .setText("This is my email");
        FakeMail mail = FakeMail.builder()
            .name("mail")
            .mimeMessage(message)
            .sender(SENDER)
            .recipient(TO)
            .build();
        mailet.init(mailetConfig);

        String expectedMessage = "{\"userEmail\" : \"" + SENDER + "\", \"emails\" : [ \"To <" + TO + ">\" ]}";
        mailet.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE_NAME)).hasValueSatisfying(json ->
                assertThatJson(json.getValue().value().toString()).isEqualTo(expectedMessage));
    }

    @Test
    public void serviceShouldUnscrambleRecipients() throws Exception {
        MimeMessageBuilder message = MimeMessageBuilder.mimeMessageBuilder()
            .setSender(SENDER)
            .addToRecipient("=?ISO-8859-1?Q?Beno=EEt_TELLIER?= <tellier@linagora.com>")
            .setSubject("Contact collection Rocks")
            .setText("This is my email");
        FakeMail mail = FakeMail.builder()
            .name("mail")
            .mimeMessage(message)
            .sender(SENDER)
            .recipient(TO)
            .build();
        mailet.init(mailetConfig);

        String expectedMessage = "{\"userEmail\" : \"" + SENDER + "\", \"emails\" : [ \"Benoît TELLIER <tellier@linagora.com>\" ]}";
        mailet.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE_NAME)).hasValueSatisfying(json ->
                assertThatJson(json.getValue().value().toString()).isEqualTo(expectedMessage));
    }

    @Test
    public void serviceShouldUnscrambleRecipientsWhenNameContainsSuperiors() throws Exception {
        String rawMessage = "From: sender@example.com\r\n"
            + "To: =?UTF-8?Q?recip_>>_Fr=c3=a9d=c3=a9ric_RECIPIENT?= <frecipient@example.com>\r\n"
            + "Subject: extract this recipient please\r\n"
            + "\r\n"
            + "Please!";
        MimeMessage message = MimeMessageUtil.mimeMessageFromString(rawMessage);
        FakeMail mail = FakeMail.builder()
            .name("mail")
            .mimeMessage(message)
            .sender(SENDER)
            .recipient("recipient@example.com")
            .build();
        mailet.init(mailetConfig);

        String expectedMessage = "{\"userEmail\" : \"" + SENDER + "\", \"emails\" : [ \"\\\"recip >> Frédéric RECIPIENT\\\" <frecipient@example.com>\" ]}";
        mailet.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE_NAME)).hasValueSatisfying(json ->
                assertThatJson(json.getValue().value().toString()).isEqualTo(expectedMessage));
    }

    @Test
    public void serviceShouldParseMultipleRecipients() throws Exception {
        String rawMessage = "From: sender@example.com\r\n"
            + "To: User 1 <user1@example.com>, =?UTF-8?Q?recip_>>_Fr=c3=a9d=c3=a9ric_RECIPIENT?= <frecipient@example.com>\r\n"
            + "Subject: extract this recipient please\r\n"
            + "\r\n"
            + "Please!";
        MimeMessage message = MimeMessageUtil.mimeMessageFromString(rawMessage);
        FakeMail mail = FakeMail.builder()
            .name("mail")
            .mimeMessage(message)
            .sender(SENDER)
            .recipient("recipient@example.com")
            .build();
        mailet.init(mailetConfig);

        String expectedMessage = "{\"userEmail\" : \"" + SENDER + "\", \"emails\" : [ \"User 1 <user1@example.com>\", \"\\\"recip >> Frédéric RECIPIENT\\\" <frecipient@example.com>\" ]}";
        mailet.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE_NAME)).hasValueSatisfying(json ->
                assertThatJson(json.getValue().value().toString()).isEqualTo(expectedMessage));
    }

    @Test
    public void serviceShouldParseRecipientWithCommaInName() throws Exception {
        String rawMessage = "From: sender@example.com\r\n"
            + "To: \"User, the first one\" <user1@example.com>\r\n"
            + "Subject: extract this recipient please\r\n"
            + "\r\n"
            + "Please!";
        MimeMessage message = MimeMessageUtil.mimeMessageFromString(rawMessage);
        FakeMail mail = FakeMail.builder()
            .name("mail")
            .mimeMessage(message)
            .sender(SENDER)
            .recipient("recipient@example.com")
            .build();
        mailet.init(mailetConfig);

        String expectedMessage = "{\"userEmail\" : \"" + SENDER + "\", \"emails\" : [ \"\\\"User, the first one\\\" <user1@example.com>\" ]}";
        mailet.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE_NAME)).hasValueSatisfying(json ->
                assertThatJson(json.getValue().value().toString()).isEqualTo(expectedMessage));
    }

    @Test
    public void serviceShouldNotOverwriteSenderWhenDifferentFromField() throws Exception {
        MimeMessageBuilder message = MimeMessageBuilder.mimeMessageBuilder()
            .addFrom("other@sender.org")
            .addToRecipient("To <" + TO + ">")
            .setSubject("Contact collection Rocks")
            .setText("This is my email");
        FakeMail mail = FakeMail.builder()
            .name("mail").mimeMessage(message)
            .sender(SENDER)
            .recipient(TO)
            .build();
        mailet.init(mailetConfig);

        String expectedMessage = "{\"userEmail\" : \"" + SENDER + "\", \"emails\" : [ \"To <" + TO + ">\" ]}";
        mailet.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE_NAME)).hasValueSatisfying(json ->
                assertThatJson(json.getValue().value().toString()).isEqualTo(expectedMessage));
    }

    @Test
    public void serviceShouldNotOverwriteSenderWhenDifferentSenderField() throws Exception {
        MimeMessageBuilder message = MimeMessageBuilder.mimeMessageBuilder()
            .setSender("other@sender.org")
            .addToRecipient("To <" + TO + ">")
            .setSubject("Contact collection Rocks")
            .setText("This is my email");
        FakeMail mail = FakeMail.builder()
            .name("mail")
            .mimeMessage(message)
            .sender(SENDER)
            .recipient(TO)
            .build();
        mailet.init(mailetConfig);

        String expectedMessage = "{\"userEmail\" : \"" + SENDER + "\", \"emails\" : [ \"To <" + TO + ">\" ]}";
        mailet.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE_NAME)).hasValueSatisfying(json ->
                assertThatJson(json.getValue().value().toString()).isEqualTo(expectedMessage));
    }

    @Test
    public void serviceShouldSkipMessagesWithoutSenderEnvelope() throws Exception {
        MimeMessageBuilder message = MimeMessageBuilder.mimeMessageBuilder()
            .addToRecipient("To <" + TO + ">")
            .setSubject("Contact collection Rocks")
            .setText("This is my email");
        FakeMail mail = FakeMail.builder()
            .name("mail")
            .mimeMessage(message)
            .recipient(TO)
            .build();
        mailet.init(mailetConfig);

        mailet.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE_NAME))
            .isEmpty();
    }

    @Test
    public void serviceShouldNotAddTheAttributeWhenNoRecipient() throws Exception {
        MimeMessageBuilder message = MimeMessageBuilder.mimeMessageBuilder()
                .setSender(SENDER)
                .setSubject("Contact collection Rocks")
                .setText("This is my email");
        FakeMail mail = FakeMail.builder()
            .name("mail")
            .mimeMessage(message)
            .sender(SENDER)
            .build();
        mailet.init(mailetConfig);

        mailet.service(mail);

        assertThat(mail.getAttribute(ATTRIBUTE_NAME))
            .isEmpty();
    }

    @Test
    public void extractContactsShouldNotThrowWhenNoRecipient() throws Exception {
        MimeMessageBuilder message = MimeMessageBuilder.mimeMessageBuilder()
                .setSender(SENDER)
                .setSubject("Contact collection Rocks")
                .setText("This is my email");
        FakeMail mail = FakeMail.builder()
            .name("mail")
            .mimeMessage(message)
            .sender(SENDER)
            .build();

        mailet.extractContacts(mail);
    }
}