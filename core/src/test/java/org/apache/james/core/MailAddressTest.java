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
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.stream.Stream;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import nl.jqno.equalsverifier.EqualsVerifier;

class MailAddressTest {

    private static final String GOOD_LOCAL_PART = "\"quoted@local part\"";
    private static final String GOOD_QUOTED_LOCAL_PART = "\"quoted@local part\"@james.apache.org";
    private static final String GOOD_ADDRESS = "server-dev@james.apache.org";
    private static final Domain GOOD_DOMAIN = Domain.of("james.apache.org");

    private static Stream<Arguments> goodAddresses() {
        return Stream.of(
                GOOD_ADDRESS,
                GOOD_QUOTED_LOCAL_PART,
                "server-dev@james-apache.org",
                "server-dev@[127.0.0.1]",
                "server.dev@james.apache.org",
                "\\.server-dev@james.apache.org",
                "Abc@10.42.0.1",
                "Abc.123@example.com",
                "user+mailbox/department=shipping@example.com",
                "user+mailbox@example.com",
                "\"Abc@def\"@example.com",
                "\"Fred Bloggs\"@example.com",
                "\"Joe.\\Blow\"@example.com",
                "!#$%&'*+-/=?^_`.{|}~@example.com")
            .map(Arguments::of);
    }

    private static Stream<Arguments> badAddresses() {
        return Stream.of(
                "",
                "server-dev",
                "server-dev@",
                "[]",
                "server-dev@[]",
                "server-dev@#",
                "quoted local-part@james.apache.org",
                "quoted@local-part@james.apache.org",
                "local-part.@james.apache.org",
                ".local-part@james.apache.org",
                "local-part@.james.apache.org",
                "local-part@james.apache.org.",
                "local-part@james.apache..org",
                "server-dev@-james.apache.org",
                "server-dev@james.apache.org-",
                "server-dev@#james.apache.org",
                "server-dev@#123james.apache.org",
                "server-dev@#-123.james.apache.org",
                "server-dev@james. apache.org",
                "server-dev@james\\.apache.org",
                "server-dev@[300.0.0.1]",
                "server-dev@[127.0.1]",
                "server-dev@[0127.0.0.1]",
                "server-dev@[127.0.1.1a]",
                "server-dev@[127\\.0.1.1]",
                "server-dev@#123",
                "server-dev@#123.apache.org",
                "server-dev@[127.0.1.1.1]",
                "server-dev@[127.0.1.-1]",
                "\"a..b\"@domain.com", // Javax.mail is unable to handle this so we better reject it
                "server-dev\\.@james.apache.org", // Javax.mail is unable to handle this so we better reject it
                "a..b@domain.com",
                // According to wikipedia these addresses are valid but as javax.mail is unable
                // to work with them we shall rather reject them (note that this is not breaking retro-compatibility)
                "Loïc.Accentué@voilà.fr8",
                "pelé@exemple.com",
                "δοκιμή@παράδειγμα.δοκιμή",
                "我買@屋企.香港",
                "二ノ宮@黒川.日本",
                "медведь@с-балалайкой.рф",
                "संपर्क@डाटामेल.भारत")
            .map(Arguments::of);
    }

    @ParameterizedTest
    @MethodSource("goodAddresses")
    void testGoodMailAddressString(String mailAddress) {
        assertThatCode(() -> new MailAddress(mailAddress))
            .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @MethodSource("goodAddresses")
    void toInternetAddressShouldNoop(String mailAddress) throws Exception {
        assertThat(new MailAddress(mailAddress).toInternetAddress())
            .isNotEmpty();
    }

    @ParameterizedTest
    @MethodSource("badAddresses")
    void testBadMailAddressString(String mailAddress) {
        Assertions.assertThatThrownBy(() -> new MailAddress(mailAddress))
            .isInstanceOf(AddressException.class);
    }

    @Test
    void testGoodMailAddressWithLocalPartAndDomain() {
        assertThatCode(() -> new MailAddress("local-part", "domain"))
            .doesNotThrowAnyException();
    }

    @Test
    void testBadMailAddressWithLocalPartAndDomain() {
        Assertions.assertThatThrownBy(() -> new MailAddress("local-part", "-domain"))
            .isInstanceOf(AddressException.class);
    }

    @Test
    void testMailAddressInternetAddress() {
        assertThatCode(() -> new MailAddress(new InternetAddress(GOOD_QUOTED_LOCAL_PART)))
            .doesNotThrowAnyException();
    }

    @Test
    void testGetDomain() throws AddressException {
        MailAddress a = new MailAddress(new InternetAddress(GOOD_ADDRESS));

        assertThat(a.getDomain()).isEqualTo(GOOD_DOMAIN);
    }

    @Test
    void testGetLocalPart() throws AddressException {
        MailAddress a = new MailAddress(new InternetAddress(GOOD_QUOTED_LOCAL_PART));

        assertThat(a.getLocalPart()).isEqualTo(GOOD_LOCAL_PART);
    }

    @Test
    void testToString() throws AddressException {
        MailAddress a = new MailAddress(new InternetAddress(GOOD_ADDRESS));

        assertThat(a.toString()).isEqualTo(GOOD_ADDRESS);
    }

    @Test
    void testToInternetAddress() throws AddressException {
        InternetAddress b = new InternetAddress(GOOD_ADDRESS);
        MailAddress a = new MailAddress(b);

        assertThat(a.toInternetAddress()).contains(b);
        assertThat(a.toString()).isEqualTo(GOOD_ADDRESS);
    }

    @Test
    void testEqualsObject() throws AddressException {
        MailAddress a = new MailAddress(GOOD_ADDRESS);
        MailAddress b = new MailAddress(GOOD_ADDRESS);

        assertThat(a).isNotNull().isEqualTo(b);
    }

    @Test
    void equalsShouldReturnTrueWhenBothNullSender() {
        assertThat(MailAddress.nullSender())
            .isEqualTo(MailAddress.nullSender());
    }

    @SuppressWarnings("deprecation")
    @Test
    void getMailSenderShouldReturnNullSenderWhenNullSender() {
        assertThat(MailAddress.getMailSender(MailAddress.NULL_SENDER_AS_STRING))
            .isEqualTo(MailAddress.nullSender());
    }

    @SuppressWarnings("deprecation")
    @Test
    void getMailSenderShouldReturnParsedAddressWhenNotNullAddress() throws Exception {
        assertThat(MailAddress.getMailSender(GOOD_ADDRESS))
            .isEqualTo(new MailAddress(GOOD_ADDRESS));
    }

    @SuppressWarnings("deprecation")
    @Test
    void equalsShouldReturnFalseWhenOnlyFirstMemberIsANullSender() {
        assertThat(MailAddress.getMailSender(GOOD_ADDRESS))
            .isNotEqualTo(MailAddress.nullSender());
    }

    @SuppressWarnings("deprecation")
    @Test
    void equalsShouldReturnFalseWhenOnlySecondMemberIsANullSender() {
        assertThat(MailAddress.nullSender())
            .isNotEqualTo(MailAddress.getMailSender(GOOD_ADDRESS));
    }

    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(MailAddress.class)
            .verify();
    }
}
