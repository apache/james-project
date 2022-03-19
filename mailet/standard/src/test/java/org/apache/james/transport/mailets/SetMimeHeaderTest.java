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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.apache.mailet.Mail;
import org.apache.mailet.Mailet;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.apache.mailet.base.test.MailUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SetMimeHeaderTest {

    private Mailet mailet;

    @BeforeEach
    void setUp() {
        mailet = new SetMimeHeader();
    }

    @Test
    void shouldAddHeaderToMime() throws MessagingException {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("name", "header-name")
                .setProperty("value", "test-value")
                .build();
        mailet.init(mailetConfig);
        
        MimeMessage mimeMessage = MailUtil.createMimeMessage();
        Mail mail = MailUtil.createMockMail2Recipients(mimeMessage);

        mailet.service(mail);

        assertThat(mail.getMessage().getHeader("header-name")).containsExactly("test-value");
    }

    @Test
    void shouldAddHeaderWhenAlreadyPresent() throws MessagingException {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("name", "header-name")
                .setProperty("value", "test-value")
                .build();
        mailet.init(mailetConfig);
        
        MimeMessage mimeMessage = MailUtil.createMimeMessage();
        mimeMessage.addHeader("header-name", "first-value");
        Mail mail = MailUtil.createMockMail2Recipients(mimeMessage);

        mailet.service(mail);

        assertThat(mail.getMessage().getHeader("header-name")).containsOnly("test-value", "first-value");
    }

    @Test
    void shouldThrowOnMessagingException() throws MessagingException {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("name", "header-name")
                .setProperty("value", "test-value")
                .build();
        mailet.init(mailetConfig);
        
        Mail mail = mock(Mail.class);
        when(mail.getMessage()).thenThrow(new MessagingException());

        assertThatThrownBy(() -> mailet.service(mail))
            .isInstanceOf(MessagingException.class);
    }
    
    @Test
    void shouldThrowWhenNoConfiguration() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .build();
        assertThatThrownBy(() -> mailet.init(mailetConfig)).isInstanceOf(MessagingException.class);
    }
    
    @Test
    void shouldThrowWhenNoValue() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("name", "correct")
                .build();
        assertThatThrownBy(() -> mailet.init(mailetConfig)).isInstanceOf(MessagingException.class);
    }
    
    @Test
    void shouldThrowWhenNoHeader() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("value", "correct")
                .build();
        assertThatThrownBy(() -> mailet.init(mailetConfig)).isInstanceOf(MessagingException.class);
    }
    
    @Test
    void shouldThrowWhenEmptyValue() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("value", "")
                .setProperty("name", "correct")
                .build();
        assertThatThrownBy(() -> mailet.init(mailetConfig)).isInstanceOf(MessagingException.class);
    }
    
    @Test
    void shouldThrowWhenEmptyHeader() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("name", "")
                .setProperty("value", "correct")
                .build();
        assertThatThrownBy(() -> mailet.init(mailetConfig)).isInstanceOf(MessagingException.class);
    }
}
