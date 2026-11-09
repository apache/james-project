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
package org.apache.james.server.core;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.util.Properties;

import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import org.junit.jupiter.api.Test;

class AddressFieldsTest {
    @Test
    void parseShouldReturnTheAddresses() throws Exception {
        assertThat(AddressFields.parse("Alice <alice@domain.tld>, bob@domain.tld"))
            .extracting(InternetAddress::getAddress)
            .containsExactly("alice@domain.tld", "bob@domain.tld");
    }

    @Test
    void parseShouldReturnAnAddressAfterAnAngleAddress() throws Exception {
        assertThat(AddressFields.parse("<alice@domain.tld> bob@domain.tld"))
            .extracting(InternetAddress::getAddress)
            .containsExactly("alice@domain.tld", "bob@domain.tld");
    }

    @Test
    void parseShouldReturnWhatMime4jReadsAfterAComment() throws Exception {
        assertThat(AddressFields.parse("alice@domain.tld (c) bob@domain.tld"))
            .extracting(InternetAddress::getAddress)
            .containsExactly("alice@domain.tld", "alice@domain.tldbob@domain.tld");
    }

    @Test
    void parseShouldReturnTheDomainFollowingAComment() throws Exception {
        assertThat(AddressFields.parse("alice@domain.tld(c).evil"))
            .extracting(InternetAddress::getAddress)
            .contains("alice@domain.tld", "alice@domain.tld.evil");
    }

    @Test
    void parseShouldNotDuplicateAddressesDifferingByCase() throws Exception {
        assertThat(AddressFields.parse("Alice@Domain.tld"))
            .extracting(InternetAddress::getAddress)
            .containsExactly("Alice@Domain.tld");
    }

    @Test
    void parseShouldNotDuplicateGroupMembers() throws Exception {
        assertThat(AddressFields.parse("team: alice@domain.tld, bob@domain.tld;"))
            .hasSize(1)
            .allSatisfy(address -> assertThat(address.isGroup()).isTrue());
    }

    @Test
    void parseShouldReadTheWholeFieldWhenTheDisplayNameIsNotAscii() throws Exception {
        assertThat(AddressFields.parse("Éléonore <eleonore@linagora.com>"))
            .extracting(InternetAddress::getAddress)
            .containsExactly("eleonore@linagora.com");
    }

    @Test
    void parseShouldReturnAnAddressAfterAnAngleAddressWhenTheDisplayNameIsNotAscii() throws Exception {
        assertThat(AddressFields.parse("ầầầầầ <alice@domain.tld> bob@domain.tld"))
            .extracting(InternetAddress::getAddress)
            .containsExactly("alice@domain.tld", "bob@domain.tld");
    }

    @Test
    void parseShouldReadEveryAddressWhenDisplayNamesAreNotAscii() throws Exception {
        assertThat(AddressFields.parse("Éric <eric@linagora.com>, Zoé <zoe@linagora.com>"))
            .extracting(InternetAddress::getAddress)
            .containsExactly("eric@linagora.com", "zoe@linagora.com");
    }

    @Test
    void parseShouldReadTheWholeFieldWhenACommentIsNotAscii() throws Exception {
        assertThat(AddressFields.parse("Eric (ça été) <eric@linagora.com>"))
            .extracting(InternetAddress::getAddress)
            .containsExactly("eric@linagora.com");
    }

    @Test
    void parseShouldReadTheWholeFieldWhenAFoldedDisplayNameIsNotAscii() throws Exception {
        assertThat(AddressFields.parse("Éléonore\r\n <eleonore@linagora.com>"))
            .extracting(InternetAddress::getAddress)
            .containsExactly("eleonore@linagora.com");
    }

    @Test
    void parseShouldNotDuplicateGroupMembersWhenTheGroupNameIsNotAscii() throws Exception {
        assertThat(AddressFields.parse("ầầầầ: alice@domain.tld, bob@domain.tld;"))
            .hasSize(1)
            .allSatisfy(address -> assertThat(address.isGroup()).isTrue());
    }

    @Test
    void parseShouldReadTheWholeFieldWhenItIsRawEightBit() throws Exception {
        assertThat(AddressFields.parse(fromFieldOfARawEightBitMessage("Éric <eric@linagora.com>")))
            .extracting(InternetAddress::getAddress)
            .containsExactly("eric@linagora.com");
    }

    @Test
    void parseShouldReturnAnAddressAfterAnAngleAddressWhenItIsRawEightBit() throws Exception {
        assertThat(AddressFields.parse(fromFieldOfARawEightBitMessage("ầầầầầ <alice@domain.tld> bob@domain.tld")))
            .extracting(InternetAddress::getAddress)
            .containsExactly("alice@domain.tld", "bob@domain.tld");
    }

    // Jakarta Mail reads a raw 8-bit header as one char per byte
    private String fromFieldOfARawEightBitMessage(String from) throws Exception {
        byte[] message = ("From: " + from + "\r\n\r\nbody\r\n").getBytes(UTF_8);
        return new MimeMessage(Session.getInstance(new Properties()), new ByteArrayInputStream(message))
            .getHeader("From", ",");
    }
}
