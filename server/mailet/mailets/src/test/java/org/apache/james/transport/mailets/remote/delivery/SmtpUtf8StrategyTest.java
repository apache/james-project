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

package org.apache.james.transport.mailets.remote.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import jakarta.mail.internet.InternetAddress;

import org.apache.james.core.MailAddress;
import org.apache.james.core.MaybeSender;
import org.junit.jupiter.api.Test;

class SmtpUtf8StrategyTest {

    private static MaybeSender sender(String addr) throws Exception {
        return MaybeSender.of(new MailAddress(addr));
    }

    private static List<InternetAddress> rcpts(String... addrs) throws Exception {
        List<InternetAddress> out = new java.util.ArrayList<>();
        for (String a : addrs) {
            InternetAddress ia = new InternetAddress();
            ia.setAddress(a);
            out.add(ia);
        }
        return out;
    }

    @Test
    void allAsciiShouldNotNeedUtf8() throws Exception {
        assertThat(SmtpUtf8Strategy.pick(
                sender("arnt@example.com"),
                rcpts("info@example.com"),
                /* remoteSupportsSmtpUtf8 */ false))
            .isEqualTo(SmtpUtf8Strategy.Action.NO_UTF8_NEEDED);
    }

    @Test
    void unicodeDomainWithSmtpUtf8ShouldUseExtension() throws Exception {
        assertThat(SmtpUtf8Strategy.pick(
                sender("arnt@grå.org"),
                rcpts("info@grå.org"),
                true))
            .isEqualTo(SmtpUtf8Strategy.Action.USE_EXTENSION);
    }

    @Test
    void unicodeLocalPartWithSmtpUtf8ShouldUseExtension() throws Exception {
        assertThat(SmtpUtf8Strategy.pick(
                sender("grå@example.com"),
                rcpts("info@example.com"),
                true))
            .isEqualTo(SmtpUtf8Strategy.Action.USE_EXTENSION);
    }

    @Test
    void unicodeDomainWithoutSmtpUtf8ShouldDowngradeDomains() throws Exception {
        // ASCII local parts everywhere — we can ACE-encode the domain(s)
        // and send RFC 5321-clean envelope commands.
        assertThat(SmtpUtf8Strategy.pick(
                sender("arnt@grå.org"),
                rcpts("info@münchen.de"),
                false))
            .isEqualTo(SmtpUtf8Strategy.Action.DOWNGRADE_DOMAINS);
    }

    @Test
    void unicodeLocalPartWithoutSmtpUtf8ShouldFailTransaction() throws Exception {
        assertThat(SmtpUtf8Strategy.pick(
                sender("grå@example.com"),
                rcpts("info@example.com"),
                false))
            .isEqualTo(SmtpUtf8Strategy.Action.CANNOT_DOWNGRADE);
    }

    @Test
    void nonAsciiInOnlyOneRecipientShouldStillTriggerAction() throws Exception {
        assertThat(SmtpUtf8Strategy.pick(
                sender("arnt@example.com"),
                rcpts("info@example.com", "गोरिल@उदाहरण.भारत"),
                false))
            .isEqualTo(SmtpUtf8Strategy.Action.CANNOT_DOWNGRADE);
    }

    @Test
    void nullSenderWithAsciiRecipientShouldNotNeedUtf8() throws Exception {
        // Bounce path: MAIL FROM:<>. Only recipients matter.
        assertThat(SmtpUtf8Strategy.pick(
                MaybeSender.nullSender(),
                rcpts("info@example.com"),
                false))
            .isEqualTo(SmtpUtf8Strategy.Action.NO_UTF8_NEEDED);
    }

    @Test
    void nullSenderWithUnicodeRecipientShouldFollowRecipient() throws Exception {
        assertThat(SmtpUtf8Strategy.pick(
                MaybeSender.nullSender(),
                rcpts("arnt@grå.org"),
                true))
            .isEqualTo(SmtpUtf8Strategy.Action.USE_EXTENSION);
    }

    @Test
    void toAceDomainShouldConvertUnicodeDomain() throws Exception {
        InternetAddress input = new InternetAddress();
        input.setAddress("arnt@grå.org");
        InternetAddress converted = SmtpUtf8Strategy.toAceDomain(input);
        assertThat(converted.getAddress()).isEqualTo("arnt@xn--gr-zia.org");
    }

    @Test
    void toAceDomainShouldLeaveAsciiDomainUntouched() throws Exception {
        InternetAddress input = new InternetAddress();
        input.setAddress("arnt@example.com");
        InternetAddress converted = SmtpUtf8Strategy.toAceDomain(input);
        assertThat(converted.getAddress()).isEqualTo("arnt@example.com");
    }

    @Test
    void aceAddressStringShouldConvertDomainButPreserveLocalPart() {
        // Local part "grå" is kept verbatim — this helper is only for the
        // downgrade path, where callers have already confirmed the local
        // part is ASCII. Preserving whatever local part arrived is the
        // right contract.
        assertThat(SmtpUtf8Strategy.aceAddressString("arnt@grå.org"))
            .isEqualTo("arnt@xn--gr-zia.org");
    }

    @Test
    void aceAddressStringShouldPreserveNullSender() {
        assertThat(SmtpUtf8Strategy.aceAddressString("")).isEqualTo("");
    }
}
