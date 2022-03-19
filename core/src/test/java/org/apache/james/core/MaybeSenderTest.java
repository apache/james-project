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

package org.apache.james.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.NoSuchElementException;

import jakarta.mail.internet.AddressException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class MaybeSenderTest {
    private static final String GOOD_ADDRESS = "server-dev@james.apache.org";
    private static final String MAIL_ADDRESS_STRING = "any@domain.tld";

    private MailAddress mailAddress;

    @BeforeEach
    void setUp() throws AddressException {
        mailAddress = new MailAddress(MAIL_ADDRESS_STRING);
    }

    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(MaybeSender.class)
            .verify();
    }

    @Test
    void ofShouldSanitizeNull() {
        assertThat(MaybeSender.of(null))
            .isEqualTo(MaybeSender.nullSender());
    }

    @Test
    void ofShouldSanitizeNullSender() {
        assertThat(MaybeSender.of(MailAddress.nullSender()))
            .isEqualTo(MaybeSender.nullSender());
    }

    @Test
    void asOptionalShouldReturnWrappedValue() {
        assertThat(MaybeSender.of(mailAddress).asOptional())
            .contains(mailAddress);
    }

    @Test
    void asOptionalShouldReturnEmptyWhenNullSender() {
        assertThat(MaybeSender.nullSender().asOptional())
            .isEmpty();
    }

    @Test
    void getShouldReturnWrappedValue() {
        assertThat(MaybeSender.of(mailAddress).get())
            .isEqualTo(mailAddress);
    }

    @Test
    void getShouldThrowWhenNullSender() {
        assertThatThrownBy(() -> MaybeSender.nullSender().get())
            .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void asListShouldReturnWrappedValue() {
        assertThat(MaybeSender.of(mailAddress).asList())
            .contains(mailAddress);
    }

    @Test
    void asListShouldReturnEmptyWhenNullSender() {
        assertThat(MaybeSender.nullSender().asList())
            .isEmpty();
    }

    @Test
    void asStreamShouldReturnWrappedValue() {
        assertThat(MaybeSender.of(mailAddress).asStream())
            .contains(mailAddress);
    }

    @Test
    void asStreamShouldReturnEmptyWhenNullSender() {
        assertThat(MaybeSender.nullSender().asStream())
            .isEmpty();
    }

    @Test
    void isNullSenderShouldReturnFalseWhenNotNullSender() {
        assertThat(MaybeSender.of(mailAddress).isNullSender())
            .isFalse();
    }

    @Test
    void isNullSenderShouldReturnTrueWhenNullSender() {
        assertThat(MaybeSender.nullSender().isNullSender())
            .isTrue();
    }

    @Test
    void asStringShouldReturnWrappedValue() {
        assertThat(MaybeSender.of(mailAddress).asString())
            .isEqualTo(MAIL_ADDRESS_STRING);
    }

    @Test
    void asStringShouldReturnDefaultWhenNullSender() {
        assertThat(MaybeSender.nullSender().asString())
            .isEqualTo(MailAddress.NULL_SENDER_AS_STRING);
    }

    @Test
    void asStringWithDefaultShouldReturnWrappedValue() {
        assertThat(MaybeSender.of(mailAddress).asString("default"))
            .isEqualTo(MAIL_ADDRESS_STRING);
    }

    @Test
    void asPrettyStringShouldReturnDefaultWhenNullSender() {
        assertThat(MaybeSender.nullSender().asPrettyString())
            .isEqualTo(MailAddress.NULL_SENDER_AS_STRING);
    }

    @Test
    void asPrettyStringShouldReturnWrappedValue() {
        assertThat(MaybeSender.of(mailAddress).asPrettyString())
            .isEqualTo("<" + MAIL_ADDRESS_STRING + ">");
    }

    @Test
    void asStringWithDefaultShouldReturnDefaultWhenNullSender() {
        assertThat(MaybeSender.nullSender().asString("default"))
            .isEqualTo("default");
    }

    @Test
    void getMailSenderShouldReturnNullSenderWhenNullSender() {
        assertThat(MaybeSender.getMailSender(MailAddress.NULL_SENDER_AS_STRING))
            .isEqualTo(MaybeSender.nullSender());
    }

    @Test
    void getMailSenderShouldReturnParsedAddressWhenNotNullAddress() throws Exception {
        assertThat(MaybeSender.getMailSender(GOOD_ADDRESS))
            .isEqualTo(MaybeSender.of(new MailAddress(GOOD_ADDRESS)));
    }

    @Test
    void getMailSenderShouldReturnNullSenderWhenNull() {
        assertThat(MaybeSender.getMailSender(null))
            .isEqualTo(MaybeSender.nullSender());
    }

    @Test
    void getMailSenderShouldReturnNullSenderWhenEmptyString() {
        assertThat(MaybeSender.getMailSender(""))
            .isEqualTo(MaybeSender.nullSender());
    }

    @Test
    void getMailSenderShouldReturnNullSenderWhenOnlySpaces() {
        assertThat(MaybeSender.getMailSender("   "))
            .isEqualTo(MaybeSender.nullSender());
    }

    @Test
    void getMailSenderShouldReturnNullSenderWhenBadValue() {
        assertThat(MaybeSender.getMailSender("this@is@a@bad@address"))
            .isEqualTo(MaybeSender.nullSender());
    }

    @Test
    void equalsShouldReturnFalseWhenOnlyFirstMemberIsANullSender() {
        assertThat(MaybeSender.getMailSender(GOOD_ADDRESS))
            .isNotEqualTo(MaybeSender.nullSender());
    }

    @Test
    void equalsShouldReturnFalseWhenOnlySecondMemberIsANullSender() {
        assertThat(MaybeSender.nullSender())
            .isNotEqualTo(MaybeSender.getMailSender(GOOD_ADDRESS));
    }
}