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
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.util.Properties;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.AddressException;
import javax.mail.internet.MimeMessage;

import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.slf4j.Logger;

import com.google.common.base.Charsets;

public class LogMessageTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private LogMessage mailet;
    private FakeMailContext mailContext;
    private Logger logger;


    @Before
    public void setup() {
        logger = mock(Logger.class);
        mailContext = FakeMailContext.builder()
                .logger(logger)
                .build();
        mailet = new LogMessage(logger);
    }

    @Test
    public void getMailetInfoShouldReturnValue() {
        assertThat(mailet.getMailetInfo()).isEqualTo("LogHeaders Mailet");
    }

    @Test
    public void initShouldIgnoreExceptions() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("LogContext")
                .mailetContext(mailContext)
                .setProperty("maxBody", "comment")
                .build();
        mailet.init(mailetConfig);
    }

    @Test
    public void serviceShouldFailWhenMailHasNoStream() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("LogContext")
                .mailetContext(mailContext)
                .build();
        mailet.init(mailetConfig);

        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        message.setSubject("subject");
        message.setText("This is a fake mail");
        mailet.service(FakeMail.builder()
                .mimeMessage(message)
                .build());

        verify(logger).info("Logging mail null");
        verify(logger).info("\n");
        verify(logger).info("Subject: subject\n");
        verify(logger).error(eq("Error logging message."), any(MessagingException.class));
        verifyNoMoreInteractions(logger);
    }

    @Test
    public void serviceShouldLog() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("LogContext")
                .mailetContext(mailContext)
                .build();
        mailet.init(mailetConfig);

        mailet.service(createMail());

        verify(logger).info("Logging mail name");
        verify(logger).info("\n");
        verify(logger).info("Subject: subject\n");
        verify(logger).info("Content-Type: text/plain\n");
        verify(logger).info("This is a fake mail");
        verifyNoMoreInteractions(logger);
    }

    @Test
    public void serviceShouldLogWhenExceptionOccured() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("LogContext")
                .mailetContext(mailContext)
                .build();
        mailet.init(mailetConfig);

        Mail mail = mock(Mail.class);
        when(mail.getName())
            .thenReturn("name");
        MessagingException messagingException = new MessagingException("exception message");
        when(mail.getMessage())
            .thenThrow(messagingException);

        mailet.service(mail);

        verify(logger).info("Logging mail name");
        verify(logger).error("Error logging message.", messagingException);
        verifyNoMoreInteractions(logger);
    }

    @Test
    public void serviceShouldSetTheMailStateWhenPassThroughIsFalse() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("LogContext")
                .mailetContext(mailContext)
                .setProperty("passThrough", "false")
                .build();
        mailet.init(mailetConfig);

        FakeMail mail = createMail();
        mailet.service(mail);

        assertThat(mail.getState()).isEqualTo(Mail.GHOST);
    }

    @Test
    public void serviceShouldNotChangeTheMailStateWhenPassThroughIsTrue() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("LogContext")
                .mailetContext(mailContext)
                .setProperty("passThrough", "true")
                .build();
        mailet.init(mailetConfig);

        FakeMail mail = createMail();
        String initialState = mail.getState();
        mailet.service(mail);

        assertThat(mail.getState()).isEqualTo(initialState);
    }

    @Test
    public void serviceShouldNotLogHeadersWhenFalse() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("LogContext")
                .mailetContext(mailContext)
                .setProperty("headers", "false")
                .build();
        mailet.init(mailetConfig);

        mailet.service(createMail());

        verify(logger).info("Logging mail name");
        verify(logger).info("This is a fake mail");
        verifyNoMoreInteractions(logger);
    }

    @Test
    public void serviceShouldNotLogBodyWhenFalse() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("LogContext")
                .mailetContext(mailContext)
                .setProperty("body", "false")
                .build();
        mailet.init(mailetConfig);

        mailet.service(createMail());

        verify(logger).info("Logging mail name");
        verify(logger).info("\n");
        verify(logger).info("Subject: subject\n");
        verify(logger).info("Content-Type: text/plain\n");
        verifyNoMoreInteractions(logger);
    }

    @Test
    public void serviceShouldNotLogFullBodyWhenBodyMaxIsSet() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("LogContext")
                .mailetContext(mailContext)
                .setProperty("maxBody", "2")
                .build();
        mailet.init(mailetConfig);

        mailet.service(createMail());

        verify(logger).info("Logging mail name");
        verify(logger).info("\n");
        verify(logger).info("Subject: subject\n");
        verify(logger).info("Content-Type: text/plain\n");
        verify(logger).info("Th");
        verifyNoMoreInteractions(logger);
    }

    @Test
    public void serviceShouldLogAdditionalCommentWhenCommentIsSet() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("LogContext")
                .mailetContext(mailContext)
                .setProperty("comment", "comment")
                .build();
        mailet.init(mailetConfig);

        mailet.service(createMail());

        verify(logger).info("Logging mail name");
        verify(logger).info("comment");
        verify(logger).info("\n");
        verify(logger).info("Subject: subject\n");
        verify(logger).info("Content-Type: text/plain\n");
        verify(logger).info("This is a fake mail");
        verifyNoMoreInteractions(logger);
    }

    private FakeMail createMail() throws MessagingException, AddressException {
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()),
                new ByteArrayInputStream("Subject: subject\r\nContent-Type: text/plain\r\n\r\nThis is a fake mail".getBytes(Charsets.UTF_8)));
        return FakeMail.builder()
                .mimeMessage(message)
                .name("name")
                .state(Mail.DEFAULT)
                .recipient(new MailAddress("receiver@domain.com"))
                .sender(new MailAddress("sender@any.com"))
                .build();
    }
}
