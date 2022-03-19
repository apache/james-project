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

import static org.apache.james.transport.mailets.remote.delivery.Bouncer.IS_DELIVERY_PERMANENT_ERROR;
import static org.apache.mailet.base.MailAddressFixture.RECIPIENT1;
import static org.assertj.core.api.Assertions.assertThat;

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

class IsRemoteDeliveryTemporaryErrorTest {
    private IsRemoteDeliveryTemporaryError testee;

    private Mail createMail() throws MessagingException {
        return FakeMail.builder()
            .name("test-message")
            .recipient(RECIPIENT1)
            .build();
    }

    @BeforeEach
    void setUp() throws Exception {
        testee = new IsRemoteDeliveryTemporaryError();

        FakeMatcherConfig matcherConfig = FakeMatcherConfig.builder()
            .matcherName("IsRemoteDeliveryTemporaryError")
            .build();

        testee.init(matcherConfig);
    }

    @Test
    void shouldMatchWhenAttributeIsFalse() throws Exception {
        Mail mail = createMail();
        mail.setAttribute(new Attribute(IS_DELIVERY_PERMANENT_ERROR, AttributeValue.of(false)));

        Collection<MailAddress> actual = testee.match(mail);

        assertThat(actual).containsOnly(RECIPIENT1);
    }

    @Test
    void shouldNotMatchWhenAttributeIsTrue() throws Exception {
        Mail mail = createMail();
        mail.setAttribute(new Attribute(IS_DELIVERY_PERMANENT_ERROR, AttributeValue.of(true)));

        Collection<MailAddress> actual = testee.match(mail);

        assertThat(actual).isEmpty();
    }

    @Test
    void shouldNotMatchWhenAttributeIsMissing() throws Exception {
        Mail mail = createMail();

        Collection<MailAddress> actual = testee.match(mail);

        assertThat(actual).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "1"})
    void shouldNotMatchWhenAttributeIsInvalid(String value) throws Exception {
        Mail mail = createMail();
        mail.setAttribute(new Attribute(IS_DELIVERY_PERMANENT_ERROR, AttributeValue.of(value)));

        Collection<MailAddress> actual = testee.match(mail);

        assertThat(actual).isEmpty();
    }
}
