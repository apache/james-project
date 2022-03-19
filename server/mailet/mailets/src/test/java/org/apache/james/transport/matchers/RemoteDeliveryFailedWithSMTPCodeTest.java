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

import static org.apache.james.transport.mailets.remote.delivery.Bouncer.DELIVERY_ERROR_CODE;
import static org.apache.mailet.base.MailAddressFixture.RECIPIENT1;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collection;

import jakarta.mail.MessagingException;

import org.apache.james.core.MailAddress;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.Mail;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMatcherConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RemoteDeliveryFailedWithSMTPCodeTest {

    private static final String CONDITION = "521";
    public static final int SMTP_ERROR_CODE_521 = 521;

    private RemoteDeliveryFailedWithSMTPCode testee;

    private Mail createMail() throws MessagingException {
        return FakeMail.builder()
            .name("test-message")
            .recipient(RECIPIENT1)
            .build();
    }

    @BeforeEach
    void setUp() throws Exception {
        testee = new RemoteDeliveryFailedWithSMTPCode();

        FakeMatcherConfig matcherConfig = FakeMatcherConfig.builder()
            .matcherName("RemoteDeliveryFailedWithSMTPCode")
            .condition(CONDITION)
            .build();

        testee.init(matcherConfig);
    }

    @Test
    void shouldMatchWhenErrorCodeIsEqual() throws Exception {
        Mail mail = createMail();
        mail.setAttribute(new Attribute(DELIVERY_ERROR_CODE, AttributeValue.of(SMTP_ERROR_CODE_521)));

        Collection<MailAddress> actual = testee.match(mail);

        assertThat(actual).containsOnly(RECIPIENT1);
    }

    @Test
    void shouldNotMatchWhenErrorCodeIsNotEqual() throws Exception {
        Mail mail = createMail();
        mail.setAttribute(new Attribute(DELIVERY_ERROR_CODE, AttributeValue.of(522)));

        Collection<MailAddress> actual = testee.match(mail);

        assertThat(actual).isEmpty();
    }

    @Test
    void shouldNotMatchWhenErrorCodeIsMissing() throws Exception {
        Mail mail = createMail();

        Collection<MailAddress> actual = testee.match(mail);

        assertThat(actual).isEmpty();
    }

    @Test
    void shouldNotMatchWhenErrorCodeIsInvalid() throws Exception {
        Mail mail = createMail();
        mail.setAttribute(new Attribute(DELIVERY_ERROR_CODE, AttributeValue.of("abc")));

        Collection<MailAddress> actual = testee.match(mail);

        assertThat(actual).isEmpty();
    }

    @Test
    void shouldThrowWhenConditionIsEmpty() {
        FakeMatcherConfig matcherConfig = FakeMatcherConfig.builder()
            .matcherName("RemoteDeliveryFailedWithSMTPCode")
            .build();

        assertThatThrownBy(() -> testee.init(matcherConfig))
            .isInstanceOf(MessagingException.class);
    }

    @Test
    void shouldThrowWhenConditionIsInvalid() {
        FakeMatcherConfig matcherConfig = FakeMatcherConfig.builder()
            .matcherName("RemoteDeliveryFailedWithSMTPCode")
            .condition("abc")
            .build();

        assertThatThrownBy(() -> testee.init(matcherConfig))
            .isInstanceOf(MessagingException.class);
    }

    @Test
    void shouldThrowWhenConditionIsNegative() {
        FakeMatcherConfig matcherConfig = FakeMatcherConfig.builder()
            .matcherName("RemoteDeliveryFailedWithSMTPCode")
            .condition("-1")
            .build();

        assertThatThrownBy(() -> testee.init(matcherConfig))
            .isInstanceOf(MessagingException.class);
    }

    @Test
    void shouldThrowWhenConditionIsZero() {
        FakeMatcherConfig matcherConfig = FakeMatcherConfig.builder()
            .matcherName("RemoteDeliveryFailedWithSMTPCode")
            .condition("0")
            .build();

        assertThatThrownBy(() -> testee.init(matcherConfig))
            .isInstanceOf(MessagingException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"99", "555"})
    void shouldThrowWhenInvalidErrorCode(String condition) {
        FakeMatcherConfig matcherConfig = FakeMatcherConfig.builder()
            .matcherName("RemoteDeliveryFailedWithSMTPCode")
            .condition(condition)
            .build();

        assertThatThrownBy(() -> testee.init(matcherConfig))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"101", "554"})
    void shouldNotThrowWhenValidErrorCode(String condition) {
        FakeMatcherConfig matcherConfig = FakeMatcherConfig.builder()
            .matcherName("RemoteDeliveryFailedWithSMTPCode")
            .condition(condition)
            .build();

        assertThatCode(() -> testee.init(matcherConfig)).doesNotThrowAnyException();
    }
}
