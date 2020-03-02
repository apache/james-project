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

package org.apache.james.imap.message.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.imap.message.request.AbstractMailboxSelectionRequest.ClientSpecifiedUidValidity;
import org.apache.james.mailbox.model.UidValidity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import nl.jqno.equalsverifier.EqualsVerifier;

class AbstractMailboxSelectionRequestTest {
    private static final ClientSpecifiedUidValidity INVALID = ClientSpecifiedUidValidity.invalid(0);
    private static final UidValidity UID_VALIDITY = UidValidity.of(42);
    private static final ClientSpecifiedUidValidity VALID = ClientSpecifiedUidValidity.valid(UID_VALIDITY);

    @Nested
    class Unknown {
        @Test
        void isUnknownShouldReturnTrue() {
            assertThat(ClientSpecifiedUidValidity.UNKNOWN.isUnknown())
                .isTrue();
        }

        @Test
        void correspondsToShouldReturnFalse() {
            assertThat(ClientSpecifiedUidValidity.UNKNOWN.correspondsTo(UID_VALIDITY))
                .isFalse();
        }

        @Test
        void toStringShouldBeInformative() {
            assertThat(ClientSpecifiedUidValidity.UNKNOWN.toString())
                .isEqualTo("UidValidity{UNKNOWN}");
        }
    }

    @Nested
    class Invalid {
        @Test
        void shouldMatchBeanContract() {
            EqualsVerifier.forClass(ClientSpecifiedUidValidity.Invalid.class)
                .verify();
        }

        @Test
        void invalidShouldThrowOnValidValue() {
            assertThatThrownBy(() -> ClientSpecifiedUidValidity.invalid(42))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void isUnknownShouldReturnFalse() {
            assertThat(INVALID.isUnknown())
                .isFalse();
        }

        @Test
        void correspondsToShouldReturnFalse() {
            assertThat(INVALID.correspondsTo(UID_VALIDITY))
                .isFalse();
        }

        @Test
        void toStringShouldBeInformative() {
            assertThat(INVALID.toString())
                .isEqualTo("Invalid UidValidity{0}");
        }
    }

    @Nested
    class Valid {
        @Test
        void shouldMatchBeanContract() {
            EqualsVerifier.forClass(ClientSpecifiedUidValidity.Valid.class)
                .verify();
        }

        @Test
        void isUnknownShouldReturnFalse() {
            assertThat(VALID.isUnknown())
                .isFalse();
        }

        @Test
        void correspondsToShouldReturnFalseWhenDifferent() {
            assertThat(VALID.correspondsTo(UidValidity.of(40)))
                .isFalse();
        }

        @Test
        void correspondsToShouldReturnTrueWhenSame() {
            assertThat(VALID.correspondsTo(UID_VALIDITY))
                .isTrue();
        }

        @Test
        void toStringShouldBeInformative() {
            assertThat(VALID.toString())
                .isEqualTo("UidValidity{42}");
        }
    }
}