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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.apache.james.util.MimeMessageUtil;
import org.apache.mailet.Mail;
import org.apache.mailet.base.RFC2822Headers;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMatcherConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RelayLimitTest {

    private RelayLimit testee;
    private Mail mail;
    private MimeMessage mimeMessage;

    @BeforeEach
    void setUp() throws Exception {
        testee = new RelayLimit();
        mimeMessage = MimeMessageUtil.defaultMimeMessage();
        mail = FakeMail.builder()
                .name("mail")
                .recipient(ANY_AT_JAMES)
                .mimeMessage(mimeMessage)
                .build();
    }

    @Test
    void relayLimitShouldBeANumber() {
        assertThatThrownBy(() ->
            testee.init(FakeMatcherConfig.builder()
                .matcherName("RelayLimit")
                .condition("Abc")
                .build()))
            .isInstanceOf(MessagingException.class);
    }

    @Test
    void relayLimitShouldBeSpecified() {
        assertThatThrownBy(() ->
            testee.init(FakeMatcherConfig.builder()
                .matcherName("RelayLimit")
                .build()))
        .isInstanceOf(MessagingException.class);
    }

    @Test
    void relayLimitShouldNotBeNull() {
        assertThatThrownBy(() ->
            testee.init(FakeMatcherConfig.builder()
                .matcherName("RelayLimit")
                .condition("0")
                .build()))
            .isInstanceOf(MessagingException.class);
    }

    @Test
    void relayLimitShouldThrowWhenConditionLessThanZero() {
        assertThatThrownBy(() ->
            testee.init(FakeMatcherConfig.builder()
                .matcherName("RelayLimit")
                .condition("-1")
                .build()))
            .isInstanceOf(MessagingException.class);
    }
    
    @Test
    void shouldNotMatchWhenNoReceivedHeader() throws Exception {
        testee.init(FakeMatcherConfig.builder()
                .matcherName("RelayLimit")
                .condition("2")
                .build());

        assertThat(testee.match(mail)).isNull();
    }


    @Test
    void shouldNotMatchWhenNotEnoughReceivedHeader() throws Exception {
        testee.init(FakeMatcherConfig.builder()
                .matcherName("RelayLimit")
                .condition("2")
                .build());

        mimeMessage.addHeader(RFC2822Headers.RECEIVED, "any");

        assertThat(testee.match(mail)).isNull();
    }

    @Test
    void shouldMatchWhenEqualToLimit() throws Exception {
        testee.init(FakeMatcherConfig.builder()
                .matcherName("RelayLimit")
                .condition("2")
                .build());

        mimeMessage.addHeader(RFC2822Headers.RECEIVED, "any");
        mimeMessage.addHeader(RFC2822Headers.RECEIVED, "any");

        assertThat(testee.match(mail)).containsExactly(ANY_AT_JAMES);
    }

    @Test
    void shouldMatchWhenWhenOverLimit() throws Exception {
        testee.init(FakeMatcherConfig.builder()
                .matcherName("RelayLimit")
                .condition("2")
                .build());

        mimeMessage.addHeader(RFC2822Headers.RECEIVED, "any");
        mimeMessage.addHeader(RFC2822Headers.RECEIVED, "any");
        mimeMessage.addHeader(RFC2822Headers.RECEIVED, "any");

        assertThat(testee.match(mail)).containsExactly(ANY_AT_JAMES);
    }

}
