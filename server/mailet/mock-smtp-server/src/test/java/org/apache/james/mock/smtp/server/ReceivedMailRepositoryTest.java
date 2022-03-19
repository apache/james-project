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

package org.apache.james.mock.smtp.server;

import static org.apache.james.mock.smtp.server.Fixture.ALICE;
import static org.apache.james.mock.smtp.server.Fixture.BOB;
import static org.apache.james.mock.smtp.server.Fixture.JACK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import jakarta.mail.internet.MimeMessage;

import org.apache.james.core.MailAddress;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.mock.smtp.server.model.Mail;
import org.apache.james.util.MimeMessageUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReceivedMailRepositoryTest {
    private ReceivedMailRepository testee;
    private Mail mail;
    private Mail mail2;

    @BeforeEach
    void setUp() throws Exception {
        testee = new ReceivedMailRepository();
        MimeMessage message = MimeMessageBuilder.mimeMessageBuilder()
            .setSubject("test")
            .setText("any text")
            .build();
        mail = new Mail(
            Mail.Envelope.ofAddresses(
                new MailAddress(BOB),
                new MailAddress(ALICE), new MailAddress(JACK)),
                MimeMessageUtil.asString(message));
        mail2 = new Mail(
            Mail.Envelope.ofAddresses(
                new MailAddress(ALICE),
                new MailAddress(BOB), new MailAddress(JACK)),
                MimeMessageUtil.asString(message));
    }

    @Test
    void listShouldBeEmptyWhenNoMailStored() {
        assertThat(testee.list()).isEmpty();
    }

    @Test
    void listShouldReturnStoredMail() {
        testee.store(mail);

        assertThat(testee.list()).containsExactly(mail);
    }

    @Test
    void listShouldReturnStoredMails() {
        testee.store(mail);
        testee.store(mail2);

        assertThat(testee.list()).containsExactly(mail, mail2);
    }

    @Test
    void listShouldPreserveDuplicates() {
        testee.store(mail);
        testee.store(mail);

        assertThat(testee.list()).containsExactly(mail, mail);
    }

    @Test
    void listShouldNotReturnClearedMails() {
        testee.store(mail);

        testee.clear();

        assertThat(testee.list()).isEmpty();
    }

    @Test
    void clearShouldNotFailWhenNoElementStored() {
        assertThatCode(() -> testee.clear())
            .doesNotThrowAnyException();
    }
}