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

package org.apache.james.protocols.api.sasl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

class SaslMechanismRegistryTest {
    private static final SaslSessionContext IMAP_CONTEXT = new FakeSaslSessionContext(SaslProtocol.IMAP);
    private static final SaslSessionContext SMTP_CONTEXT = new FakeSaslSessionContext(SaslProtocol.SMTP);

    @Test
    void findShouldBeCaseInsensitive() {
        FakeSaslMechanism plain = new FakeSaslMechanism("PLAIN", Set.of(SaslProtocol.IMAP), true);
        SaslMechanismRegistry testee = new SaslMechanismRegistry(ImmutableList.of(plain));

        assertThat(testee.find("plain", SaslProtocol.IMAP)).contains(plain);
    }

    @Test
    void findShouldFilterByProtocol() {
        FakeSaslMechanism plain = new FakeSaslMechanism("PLAIN", Set.of(SaslProtocol.IMAP), true);
        SaslMechanismRegistry testee = new SaslMechanismRegistry(ImmutableList.of(plain));

        assertThat(testee.find("PLAIN", SaslProtocol.SMTP)).isEmpty();
    }

    @Test
    void availableForShouldFilterUnavailableMechanisms() {
        FakeSaslMechanism available = new FakeSaslMechanism("AVAILABLE", Set.of(SaslProtocol.IMAP), true);
        FakeSaslMechanism unavailable = new FakeSaslMechanism("UNAVAILABLE", Set.of(SaslProtocol.IMAP), false);
        SaslMechanismRegistry testee = new SaslMechanismRegistry(ImmutableList.of(available, unavailable));

        assertThat(testee.availableFor(SaslProtocol.IMAP, IMAP_CONTEXT)).containsExactly(available);
    }

    @Test
    void constructorShouldDeduplicateSameProtocolAndMechanismName() {
        FakeSaslMechanism firstPlain = new FakeSaslMechanism("PLAIN", Set.of(SaslProtocol.IMAP), true);
        FakeSaslMechanism secondPlain = new FakeSaslMechanism("plain", Set.of(SaslProtocol.IMAP), true);
        SaslMechanismRegistry testee = new SaslMechanismRegistry(ImmutableList.of(firstPlain, secondPlain));

        assertThat(testee.availableFor(SaslProtocol.IMAP, IMAP_CONTEXT)).containsExactly(firstPlain);
        assertThat(testee.find("PLAIN", SaslProtocol.IMAP)).contains(firstPlain);
    }

    @Test
    void constructorShouldDeduplicateIndependentlyPerProtocol() {
        FakeSaslMechanism imapPlain = new FakeSaslMechanism("PLAIN", Set.of(SaslProtocol.IMAP), true);
        FakeSaslMechanism smtpPlain = new FakeSaslMechanism("PLAIN", Set.of(SaslProtocol.SMTP), true);
        SaslMechanismRegistry testee = new SaslMechanismRegistry(ImmutableList.of(imapPlain, smtpPlain));

        assertThat(testee.availableFor(SaslProtocol.IMAP, IMAP_CONTEXT)).containsExactly(imapPlain);
        assertThat(testee.availableFor(SaslProtocol.SMTP, SMTP_CONTEXT)).containsExactly(smtpPlain);
    }

    private static class FakeSaslMechanism implements SaslMechanism {
        private final String name;
        private final Set<SaslProtocol> supportedProtocols;
        private final boolean available;

        private FakeSaslMechanism(String name, Set<SaslProtocol> supportedProtocols, boolean available) {
            this.name = name;
            this.supportedProtocols = supportedProtocols;
            this.available = available;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public boolean supports(SaslProtocol protocol) {
            return supportedProtocols.contains(protocol);
        }

        @Override
        public boolean isAvailable(SaslSessionContext context) {
            return available;
        }

        @Override
        public SaslExchange start(SaslInitialRequest request, SaslSessionContext context) {
            throw new UnsupportedOperationException();
        }
    }

    private record FakeSaslSessionContext(SaslProtocol protocol) implements SaslSessionContext {
        @Override
        public boolean isTlsStarted() {
            return true;
        }

        @Override
        public <T> Optional<T> configuration(Class<T> configurationType) {
            return Optional.empty();
        }

        @Override
        public <T> Optional<T> service(Class<T> serviceType) {
            return Optional.empty();
        }
    }
}
