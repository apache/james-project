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

package org.apache.james.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Objects;

import org.apache.james.events.EventBusTestFixture.TestRegistrationKey;
import org.apache.james.events.EventBusTestFixture.TestRegistrationKeyFactory;
import org.junit.jupiter.api.Test;

class RoutingKeyConverterTest {
    static class OtherTestRegistrationKey implements RegistrationKey {
        static class Factory implements RegistrationKey.Factory {
            @Override
            public Class<? extends RegistrationKey> forClass() {
                return OtherTestRegistrationKey.class;
            }

            @Override
            public RegistrationKey fromString(String asString) {
                return new OtherTestRegistrationKey(asString);
            }
        }

        private final String value;

        OtherTestRegistrationKey(String value) {
            this.value = value;
        }

        @Override
        public String asString() {
            return value;
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof OtherTestRegistrationKey) {
                OtherTestRegistrationKey that = (OtherTestRegistrationKey) o;

                return Objects.equals(this.value, that.value);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(value);
        }
    }

    private static final RegistrationKey REGISTRATION_KEY_1 = new TestRegistrationKey("a");
    private static final String ROUTING_KEY_1 = "org.apache.james.events.EventBusTestFixture$TestRegistrationKey:a";

    private RoutingKeyConverter testee = RoutingKeyConverter.forFactories(
        new OtherTestRegistrationKey.Factory(),
        new TestRegistrationKeyFactory());

    @Test
    void toRoutingKeyShouldTransformAKeyIntoAString() {
        assertThat(RoutingKeyConverter.RoutingKey.of(REGISTRATION_KEY_1).asString())
            .isEqualTo(ROUTING_KEY_1);
    }

    @Test
    void toRegistrationKeyShouldReturnCorrespondingRoutingKey() {
        assertThat(testee.toRegistrationKey(ROUTING_KEY_1))
            .isEqualTo(REGISTRATION_KEY_1);
    }

    @Test
    void toRoutingKeyShouldAcceptSeparator() {
        assertThat(RoutingKeyConverter.RoutingKey.of(new OtherTestRegistrationKey("a:b")).asString())
            .isEqualTo("org.apache.james.events.RoutingKeyConverterTest$OtherTestRegistrationKey:a:b");
    }

    @Test
    void toRegistrationKeyShouldAcceptSeparator() {
        assertThat(testee.toRegistrationKey("org.apache.james.events.RoutingKeyConverterTest$OtherTestRegistrationKey:a:b"))
            .isEqualTo(new OtherTestRegistrationKey("a:b"));
    }

    @Test
    void toRoutingKeyShouldAcceptEmptyValue() {
        assertThat(RoutingKeyConverter.RoutingKey.of(new OtherTestRegistrationKey("")).asString())
            .isEqualTo("org.apache.james.events.RoutingKeyConverterTest$OtherTestRegistrationKey:");
    }

    @Test
    void toRegistrationKeyShouldAcceptEmptyValue() {
        assertThat(testee.toRegistrationKey("org.apache.james.events.RoutingKeyConverterTest$OtherTestRegistrationKey:"))
            .isEqualTo(new OtherTestRegistrationKey(""));
    }

    @Test
    void toRegistrationKeyShouldRejectNull() {
        assertThatThrownBy(() -> testee.toRegistrationKey(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void toRegistrationKeyShouldRejectEmptyString() {
        assertThatThrownBy(() -> testee.toRegistrationKey(""))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void toRegistrationKeyShouldRejectNoSeparator() {
        assertThatThrownBy(() -> testee.toRegistrationKey("noSeparator"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void toRegistrationKeyShouldRejectUnknownRegistrationKeyClass() {
        assertThatThrownBy(() -> testee.toRegistrationKey("unknown:"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void toRegistrationKeyShouldRejectInvalidValue() {
        assertThatThrownBy(() -> testee.toRegistrationKey("org.apache.james.mailbox.events.MailboxIdRegistrationKey:invalid"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
