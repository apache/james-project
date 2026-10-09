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

package org.apache.james.protocols.smtp.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.apache.james.core.Username;
import org.apache.james.protocols.api.ProtocolSession.AttachmentKey;
import org.apache.james.protocols.api.ProtocolSession.State;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.utils.BaseFakeSMTPSession;
import org.junit.jupiter.api.Test;

/**
 * Covers RFC 6531 §4.3 — the UTF8SMTP / UTF8SMTPA / UTF8SMTPS / UTF8SMTPSA
 * trace keywords used in the Received header when the transaction asserted
 * SMTPUTF8.
 */
class ReceivedHeaderGeneratorTest {

    private static final ReceivedHeaderGenerator generator = new ReceivedHeaderGenerator();

    private static String serviceTypeFor(String heloMode, boolean tls, boolean authenticated, boolean smtpUtf8) {
        SMTPSession session = new FakeSession(tls, authenticated, smtpUtf8);
        // getServiceType is protected; we exercise it through a thin
        // subclass that exposes it.
        return new ReceivedHeaderGenerator() {
            String invoke() {
                return getServiceType(session, heloMode);
            }
        }.invoke();
    }

    // --- HELO (no extensions can have been negotiated) ---

    @Test
    void heloShouldYieldSmtp() {
        assertThat(serviceTypeFor("HELO", false, false, false)).isEqualTo("SMTP");
    }

    @Test
    void heloShouldYieldSmtpEvenWhenSmtpUtf8FlagIsSet() {
        // The flag should only ever be set after EHLO + an SMTPUTF8
        // parameter, but we double-check the HELO branch ignores it.
        assertThat(serviceTypeFor("HELO", false, false, true)).isEqualTo("SMTP");
    }

    // --- EHLO without SMTPUTF8: existing RFC 3848 keywords ---

    @Test
    void ehloShouldYieldEsmtp() {
        assertThat(serviceTypeFor("EHLO", false, false, false)).isEqualTo("ESMTP");
    }

    @Test
    void ehloAuthenticatedShouldYieldEsmtpa() {
        assertThat(serviceTypeFor("EHLO", false, true, false)).isEqualTo("ESMTPA");
    }

    @Test
    void ehloOverTlsShouldYieldEsmtps() {
        assertThat(serviceTypeFor("EHLO", true, false, false)).isEqualTo("ESMTPS");
    }

    @Test
    void ehloOverTlsAuthenticatedShouldYieldEsmtpsa() {
        assertThat(serviceTypeFor("EHLO", true, true, false)).isEqualTo("ESMTPSA");
    }

    // --- EHLO with SMTPUTF8: RFC 6531 §4.3 keywords ---

    @Test
    void ehloWithSmtpUtf8ShouldYieldUtf8Smtp() {
        assertThat(serviceTypeFor("EHLO", false, false, true)).isEqualTo("UTF8SMTP");
    }

    @Test
    void ehloAuthenticatedWithSmtpUtf8ShouldYieldUtf8Smtpa() {
        assertThat(serviceTypeFor("EHLO", false, true, true)).isEqualTo("UTF8SMTPA");
    }

    @Test
    void ehloOverTlsWithSmtpUtf8ShouldYieldUtf8Smtps() {
        assertThat(serviceTypeFor("EHLO", true, false, true)).isEqualTo("UTF8SMTPS");
    }

    @Test
    void ehloOverTlsAuthenticatedWithSmtpUtf8ShouldYieldUtf8Smtpsa() {
        assertThat(serviceTypeFor("EHLO", true, true, true)).isEqualTo("UTF8SMTPSA");
    }

    private static class FakeSession extends BaseFakeSMTPSession {
        private final boolean tls;
        private final Username username;
        private final boolean smtpUtf8;

        FakeSession(boolean tls, boolean authenticated, boolean smtpUtf8) {
            this.tls = tls;
            this.username = authenticated ? Username.of("alice@example.com") : null;
            this.smtpUtf8 = smtpUtf8;
        }

        @Override
        public boolean isTLSStarted() {
            return tls;
        }

        @Override
        public Username getUsername() {
            return username;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> Optional<T> getAttachment(AttachmentKey<T> key, State state) {
            if (key == SMTPSession.SMTPUTF8_REQUESTED) {
                return (Optional<T>) Optional.of(smtpUtf8);
            }
            return Optional.empty();
        }
    }
}
