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

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.apache.mailet.Mailet;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.apache.mailet.base.test.MailUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AddSubjectPrefixTest {

    private Mailet mailet;

    @BeforeEach
    void setup() {
        mailet = new AddSubjectPrefix();
    }
    
    @Test
    void shouldAddPrefixToSubject() throws MessagingException {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("subjectPrefix", "JUNIT")
                .build();
        mailet.init(mailetConfig);

        MimeMessage mimeMessage = MailUtil.createMimeMessageWithSubject("test");
        FakeMail mail = MailUtil.createMockMail2Recipients(mimeMessage);
        
        mailet.service(mail);

        assertThat(mail.getMessage().getSubject()).isEqualTo("JUNIT test");
    }

    
    @Test
    void shouldAddPrefixToEncodedSubject() throws MessagingException {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("subjectPrefix", "Русский")
                .build();
        mailet.init(mailetConfig);

        String subject = 
                "=?iso8859-15?Q?Beno=EEt_TELLIER_vous_a_d=E9pos=E9_des_fichiers?=";
        MimeMessage mimeMessage = MailUtil.createMimeMessageWithSubject(subject);
        FakeMail mail = MailUtil.createMockMail2Recipients(mimeMessage);
        
        mailet.service(mail);

        assertThat(mail.getMessage().getSubject()).startsWith("Русский").endsWith("Benoît TELLIER vous a déposé des fichiers");
    }

    
    @Test
    void shouldDefinePrefixAsSubjectWhenNoSubject() throws MessagingException {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("subjectPrefix", "JUNIT")
                .build();
        mailet.init(mailetConfig);

        String noSubject = null;
        MimeMessage mimeMessage = MailUtil.createMimeMessageWithSubject(noSubject);
        FakeMail mail = MailUtil.createMockMail2Recipients(mimeMessage);

        mailet.service(mail);

        assertThat(mail.getMessage().getSubject()).isEqualTo("JUNIT");
    }

    @Test
    void shouldDefinePrefixAsSubjectWhenEmptySubject() throws MessagingException {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("subjectPrefix", "JUNIT")
                .build();
        mailet.init(mailetConfig);

        MimeMessage mimeMessage = MailUtil.createMimeMessageWithSubject("");
        FakeMail mail = MailUtil.createMockMail2Recipients(mimeMessage);

        mailet.service(mail);

        assertThat(mail.getMessage().getSubject()).isEqualTo("JUNIT");
    }
    
    @Test
    void shouldThrowWhenEmptyPrefix() throws MessagingException {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("subjectPrefix", "")
                .build();

        assertThatThrownBy(() -> mailet.init(mailetConfig))
            .isInstanceOf(MessagingException.class);
    }
}
