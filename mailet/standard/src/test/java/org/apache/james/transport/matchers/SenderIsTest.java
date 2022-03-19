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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import jakarta.mail.MessagingException;

import org.apache.james.core.MailAddress;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMatcherConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SenderIsTest {

    private static final String SENDER_NAME = "test@james.apache.org";

    private SenderIs matcher;
    private MailAddress recipient;

    @BeforeEach
    void setUp() throws Exception {
        matcher = new SenderIs();
        recipient = new MailAddress("recipient@james.apache.org");
    }

    @Test
    void shouldMatchWhenGoodSender() throws Exception {
        matcher.init(FakeMatcherConfig.builder()
                .matcherName("SenderIs")
                .condition(SENDER_NAME)
                .build());

        FakeMail fakeMail = FakeMail.builder()
            .name("mail")
            .recipient(recipient)
            .sender(SENDER_NAME)
            .build();

        assertThat(matcher.match(fakeMail)).containsExactly(recipient);
    }

    @Test
    void shouldMatchNotMatchWhenNullSender() throws Exception {
        matcher.init(FakeMatcherConfig.builder()
                .matcherName("SenderIs")
                .condition(SENDER_NAME)
                .build());

        FakeMail fakeMail = FakeMail.builder()
            .name("mail")
            .recipient(recipient)
            .sender(MailAddress.nullSender())
            .build();

        assertThat(matcher.match(fakeMail)).isNull();
    }

    @Test
    void shouldMatchNotMatchWhenNoSender() throws Exception {
        matcher.init(FakeMatcherConfig.builder()
                .matcherName("SenderIs")
                .condition(SENDER_NAME)
                .build());

        FakeMail fakeMail = FakeMail.builder()
            .name("mail")
            .recipient(recipient)
            .build();

        assertThat(matcher.match(fakeMail)).isNull();
    }

    @Test
    void shouldMatchMatchWhenNullSenderWhenConfigured() throws Exception {
        matcher.init(FakeMatcherConfig.builder()
                .matcherName("SenderIs")
                .condition(MailAddress.NULL_SENDER_AS_STRING)
                .build());

        FakeMail fakeMail = FakeMail.builder()
            .name("mail")
            .recipient(recipient)
            .sender(MailAddress.nullSender())
            .build();

        assertThat(matcher.match(fakeMail)).containsExactly(recipient);
    }

    @Test
    void shouldMatchMatchWhenNoSenderWhenConfigured() throws Exception {
        matcher.init(FakeMatcherConfig.builder()
                .matcherName("SenderIs")
                .condition(MailAddress.NULL_SENDER_AS_STRING)
                .build());

        FakeMail fakeMail = FakeMail.builder()
            .name("mail")
            .recipient(recipient)
            .build();

        assertThat(matcher.match(fakeMail)).containsExactly(recipient);
    }

    @Test
    void shouldNotMatchWhenWrongSender() throws Exception {
        matcher.init(FakeMatcherConfig.builder()
                .matcherName("SenderIs")
                .condition(SENDER_NAME)
                .build());

        FakeMail fakeMail = FakeMail.builder()
            .name("mail")
            .recipient(recipient)
            .sender("other@james.apache.org")
            .build();

        assertThat(matcher.match(fakeMail)).isNull();
    }

    @Test
    void shouldNotMatchWhenNullSender() throws Exception {
        matcher.init(FakeMatcherConfig.builder()
                .matcherName("SenderIs")
                .condition(SENDER_NAME)
                .build());

        FakeMail fakeMail = FakeMail.builder()
            .name("mail")
            .recipient(recipient)
            .build();

        assertThat(matcher.match(fakeMail)).isNull();
    }

    @Test
    void senderIsShouldBeConfigurableWithSeveralAddresses() throws Exception {
        String mailAddress = "any@apache.org";
        matcher.init(FakeMatcherConfig.builder()
                .matcherName("SenderIs")
                .condition(mailAddress + ", " + SENDER_NAME)
                .build());

        assertThat(matcher.getSenders()).containsExactly(Optional.of(new MailAddress(mailAddress)),
            Optional.of(new MailAddress(SENDER_NAME)));
    }

    @Test
    void senderIsShouldThrowWhenNoAddressesPassedByConfiguration() {
        assertThatThrownBy(() ->
            matcher.init(FakeMatcherConfig.builder()
                .matcherName("SenderIs")
                .build()))
        .isInstanceOf(MessagingException.class);
    }

    @Test
    void senderIsShouldThrowWhenNoConfiguration() {
        assertThatThrownBy(() ->
            matcher.init(FakeMatcherConfig.builder()
                .matcherName("SenderIs")
                .build()))
            .isInstanceOf(MessagingException.class);
    }
}
