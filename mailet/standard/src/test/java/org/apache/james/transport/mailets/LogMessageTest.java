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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.james.core.MailAddress;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.util.MimeMessageUtil;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeName;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.Mail;
import org.apache.mailet.PerRecipientHeaders;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class LogMessageTest {

    private LogMessage mailet;
    private FakeMailContext mailContext;
    private Logger logger;


    @BeforeEach
    void setup() {
        logger = mock(Logger.class);
        when(logger.isInfoEnabled()).thenReturn(true);
        mailContext = FakeMailContext.builder()
                .logger(logger)
                .build();
        mailet = new LogMessage(logger);

    }

    @Test
    void getMailetInfoShouldReturnValue() {
        assertThat(mailet.getMailetInfo()).isEqualTo("LogHeaders Mailet");
    }

    @Test
    void initShouldIgnoreExceptions() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("LogContext")
                .mailetContext(mailContext)
                .setProperty("comment", "comment")
                .build();
        mailet.init(mailetConfig);
    }

    @Test
    public void serviceShouldNotFailWhenTextContent() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("LogContext")
                .mailetContext(mailContext)
                .build();
        mailet.init(mailetConfig);

        mailet.service(FakeMail.builder()
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                        .addHeader("Date", "Tue, 16 Jan 2018 10:23:03 +0100")
                        .setSubject("subject")
                        .setText("This is a fake mail"))
                .build());

        verify(logger, times(0)).error(anyString(), any(MessagingException.class));
    }

    @Test
    void serviceShouldLogAsInfoWhenLogLevelIsInfo() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("LogContext")
                .mailetContext(mailContext)
                .setProperty("headers", true)
                .setProperty("body", true)
                .setProperty("level", LogLevel.INFO)
                .build();
        mailet.init(mailetConfig);

        mailet.service(createMail());

        verify(logger).info("\nSubject: subject\nContent-Type: text/plain\nThis is a fake mail");
    }

    @Test
    void serviceShouldLogAsWarnWhenLogLevelIsWarn() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("LogContext")
                .mailetContext(mailContext)
                .setProperty("headers", false)
                .setProperty("body", true)
                .setProperty("level", LogLevel.WARN)
                .build();
        mailet.init(mailetConfig);

        mailet.service(createMail());

        verify(logger).warn("This is a fake mail");
    }

    @Test
    void serviceShouldLogAsDebugWhenLogLevelIsDebug() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("LogContext")
                .mailetContext(mailContext)
                .setProperty("headers", false)
                .setProperty("body", true)
                .setProperty("level", LogLevel.DEBUG)
                .build();
        mailet.init(mailetConfig);

        mailet.service(createMail());

        verify(logger).debug("This is a fake mail");
    }

    @Test
    void serviceShouldLogAsErrorWhenLogLevelIsError() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("LogContext")
                .mailetContext(mailContext)
                .setProperty("headers", false)
                .setProperty("body", true)
                .setProperty("level", LogLevel.ERROR)
                .build();
        mailet.init(mailetConfig);

        mailet.service(createMail());

        verify(logger, times(1)).isInfoEnabled();
        verify(logger).error("This is a fake mail");
        verifyNoMoreInteractions(logger);
    }

    @Test
    void serviceShouldLogWhenExceptionOccured() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("LogContext")
                .mailetContext(mailContext)
                .setProperty("headers", true)
                .setProperty("body", true)
                .setProperty("level", LogLevel.INFO)
                .build();
        mailet.init(mailetConfig);

        Mail mail = mock(Mail.class);
        when(mail.getName())
                .thenReturn("name");
        MessagingException messagingException = new MessagingException("exception message");
        when(mail.getMessage())
                .thenThrow(messagingException);

        mailet.service(mail);

        verify(logger).error("Error logging message.", messagingException);
    }

    @Test
    void serviceShouldSetTheMailStateWhenPassThroughIsFalse() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("LogContext")
                .mailetContext(mailContext)
                .setProperty("passThrough", false)
                .setProperty("headers", true)
                .setProperty("body", true)
                .setProperty("level", LogLevel.INFO)
                .build();
        mailet.init(mailetConfig);

        FakeMail mail = createMail();
        mailet.service(mail);

        assertThat(mail.getState()).isEqualTo(Mail.GHOST);
    }

    @Test
    void serviceShouldNotChangeTheMailStateWhenPassThroughIsTrue() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("LogContext")
                .mailetContext(mailContext)
                .setProperty("passThrough", true)
                .setProperty("headers", true)
                .setProperty("body", true)
                .setProperty("level", LogLevel.INFO)
                .build();
        mailet.init(mailetConfig);

        FakeMail mail = createMail();
        String initialState = mail.getState();
        mailet.service(mail);

        assertThat(mail.getState()).isEqualTo(initialState);
    }

    @Test
    void serviceShouldNotLogHeadersWhenFalse() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("LogContext")
                .mailetContext(mailContext)
                .setProperty("headers", false)
                .setProperty("body", true)
                .setProperty("level", LogLevel.INFO)
                .build();
        mailet.init(mailetConfig);

        mailet.service(createMail());

        verify(logger).isInfoEnabled();
        verify(logger).info("This is a fake mail");
        verifyNoMoreInteractions(logger);
    }

    @Test
    void serviceShouldNotLogBodyWhenFalse() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("LogContext")
                .mailetContext(mailContext)
                .setProperty("headers", true)
                .setProperty("body", false)
                .setProperty("level", LogLevel.INFO)
                .build();
        mailet.init(mailetConfig);

        mailet.service(createMail());

        verify(logger).info("\nSubject: subject\nContent-Type: text/plain\n");
    }

    @Test
    void serviceShouldNotLogFullBodyWhenBodyMaxIsSet() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("LogContext")
                .mailetContext(mailContext)
                .setProperty("maxBody", 2)
                .setProperty("headers", false)
                .setProperty("body", true)
                .setProperty("level", LogLevel.INFO)
                .build();
        mailet.init(mailetConfig);

        mailet.service(createMail());

        verify(logger, times(1)).isInfoEnabled();
        verify(logger).info("Th");

    }

    @Test
    void serviceShouldLogAdditionalCommentWhenCommentIsSet() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("LogContext")
                .mailetContext(mailContext)
                .setProperty("comment", "comment")
                .setProperty("headers", true)
                .setProperty("body", true)
                .setProperty("level", LogLevel.INFO)
                .build();
        mailet.init(mailetConfig);

        mailet.service(createMail());

        verify(logger).info("comment\nSubject: subject\nContent-Type: text/plain\nThis is a fake mail");
        verify(logger, times(2)).isInfoEnabled();
    }

    @Test
    void serviceShouldLogSpecificHeadersWhenGivenHeaderList() throws MessagingException {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("LogContext")
                .mailetContext(mailContext)
                .setProperty("headers", true)
                .setProperty("body", true)
                .setProperty("level", LogLevel.INFO)
                .setProperty("specificHeaders", Arrays.asList("isAuthorized"))
                .build();
        mailet.init(mailetConfig);

        mailet.service(createMail());

        verify(logger).info("\nSubject: subject\nContent-Type: text/plain\nRecipient <receiver@domain.com>'s headers are: \nisAuthorized: true\nThis is a fake mail");
        verify(logger, times(2)).isInfoEnabled();
        verifyNoMoreInteractions(logger);
    }

    @Test
    void serviceShouldLogSpecificAttributesWhenGivenAttributesList() throws MessagingException {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("LogContext")
                .mailetContext(mailContext)
                .setProperty("headers", false)
                .setProperty("body", true)
                .setProperty("specificAttributes", Arrays.asList("SpamChecked", "Level"))
                .build();
        mailet.init(mailetConfig);

        mailet.service(createMail());

        verify(logger).info("This is a fake mail\nSpamChecked: true\nLevel: important\n");
        verify(logger, times(1)).isInfoEnabled();
        verifyNoMoreInteractions(logger);
    }

    @Test
    public void aListShouldBeStoredAsObject() {
        Map<String, Object> map = new HashMap<>();
        map.put("list", Arrays.asList("item"));
        assertEquals("item", ((List) map.get("list")).get(0));
    }


    private FakeMail createMail() throws MessagingException {
        MimeMessage message = MimeMessageUtil.mimeMessageFromString(
                "Subject: subject\r\n" +
                        "Content-Type: text/plain\r\n" +
                        "\r\n" +
                        "This is a fake mail");
        Attribute attribute1 = new Attribute(AttributeName.of("SpamChecked"), AttributeValue.of(true));
        Attribute attribute2 = new Attribute(AttributeName.of("Level"), AttributeValue.of("important"));
        List<Attribute> attributeList = Arrays.asList(attribute1, attribute2);
        PerRecipientHeaders.Header recipientHeader = PerRecipientHeaders.Header.builder().name("isAuthorized").value("true").build();
        return FakeMail.builder()
                .mimeMessage(message)
                .name("name")
                .state(Mail.DEFAULT)
                .recipient("receiver@domain.com")
                .addHeaderForRecipient(recipientHeader, new MailAddress("receiver@domain.com"))
                .attributes(attributeList)
                .sender("sender@any.com")
                .build();
    }

}
