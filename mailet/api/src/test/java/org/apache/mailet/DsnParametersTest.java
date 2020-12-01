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
package org.apache.mailet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumSet;
import java.util.Optional;

import org.apache.james.core.MailAddress;
import org.apache.mailet.DsnParameters.EnvId;
import org.apache.mailet.DsnParameters.Notify;
import org.apache.mailet.DsnParameters.RecipientDsnParameters;
import org.apache.mailet.DsnParameters.Ret;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableMap;

import nl.jqno.equalsverifier.EqualsVerifier;

class DsnParametersTest {
    @Test
    void dsnParametersShouldRespectBeanContract() {
        EqualsVerifier.forClass(DsnParameters.class).verify();
    }

    @Test
    void recipientDsnParametersShouldRespectBeanContract() {
        EqualsVerifier.forClass(RecipientDsnParameters.class).verify();
    }

    @Test
    void envIdShouldRespectBeanContract() {
        EqualsVerifier.forClass(EnvId.class).verify();
    }

    @Nested
    class RetTest {
        @Test
        void parseShouldReturnEmptyOnUnknownValue() {
            assertThat(Ret.parse("unknown")).isEmpty();
        }

        @Test
        void parseShouldReturnEmptyOnEmptyValue() {
            assertThat(Ret.parse("")).isEmpty();
        }

        @Test
        void parseShouldThrowOnNullValue() {
            assertThatThrownBy(() -> Ret.parse(null))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void parseShouldRecogniseHDRS() {
            assertThat(Ret.parse("HDRS")).contains(Ret.HDRS);
        }

        @Test
        void parseShouldRecogniseFull() {
            assertThat(Ret.parse("FULL")).contains(Ret.FULL);
        }

        @Test
        void parseShouldIgnoreCase() {
            assertThat(Ret.parse("HdrS")).contains(Ret.HDRS);
        }

        @Test
        void fromSMTPArgLineShouldReturnEmptyWhenNoParameters() {
            assertThat(Ret.fromSMTPArgLine(ImmutableMap.of()))
                .isEmpty();
        }

        @Test
        void fromSMTPArgLineShouldReturnEmptyWhenOtherParameters() {
            assertThat(Ret.fromSMTPArgLine(ImmutableMap.of("OTHER", "value")))
                .isEmpty();
        }

        @Test
        void fromSMTPArgLineShouldRecogniseValidValues() {
            assertThat(Ret.fromSMTPArgLine(ImmutableMap.of("RET", "HDRS")))
                .contains(Ret.HDRS);
        }

        @Test
        void fromSMTPArgLineShouldThrowOnInvalidValue() {
            assertThatThrownBy(() -> Ret.fromSMTPArgLine(ImmutableMap.of("RET", "invalid")))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class EnvIdTest {
        @Test
        void parseShouldReturnEmptyOnUnknownValue() {
            assertThat(Ret.parse("unknown")).isEmpty();
        }

        @Test
        void parseShouldReturnEmptyOnEmptyValue() {
            assertThat(EnvId.of("").asString()).isEqualTo("");
        }

        @Test
        void ofShouldThrowOnNullValue() {
            assertThatThrownBy(() -> EnvId.of(null))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void fromSMTPArgLineShouldReturnEmptyWhenNoParameters() {
            assertThat(EnvId.fromSMTPArgLine(ImmutableMap.of()))
                .isEmpty();
        }

        @Test
        void fromSMTPArgLineShouldReturnEmptyWhenOtherParameters() {
            assertThat(EnvId.fromSMTPArgLine(ImmutableMap.of("OTHER", "value")))
                .isEmpty();
        }

        @Test
        void fromSMTPArgLineShouldRecogniseValidValues() {
            assertThat(EnvId.fromSMTPArgLine(ImmutableMap.of("ENVID", "valueee")))
                .contains(EnvId.of("valueee"));
        }
    }

    @Nested
    class NotifyTest {
        @Test
        void parseShouldThrowOnUnknownValue() {
            assertThatThrownBy(() -> Notify.parse("unknown"))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void parseShouldThrowOnNullValue() {
            assertThatThrownBy(() -> Notify.parse(null))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void parseShouldThrowOnEmptyValue() {
            assertThatThrownBy(() -> Notify.parse(""))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void parseShouldThrowOnBlankValue() {
            assertThatThrownBy(() -> Notify.parse("  "))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void parseShouldThrowComasValue() {
            assertThatThrownBy(() -> Notify.parse(" , , "))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void parseShouldRecogniseNever() {
            assertThat(Notify.parse("NEVER")).contains(Notify.NEVER);
        }

        @Test
        void parseShouldRecogniseDelay() {
            assertThat(Notify.parse("DELAY")).contains(Notify.DELAY);
        }

        @Test
        void parseShouldRecogniseSuccess() {
            assertThat(Notify.parse("SUCCESS")).contains(Notify.SUCCESS);
        }

        @Test
        void parseShouldRecogniseFailure() {
            assertThat(Notify.parse("FAILURE")).contains(Notify.FAILURE);
        }

        @Test
        void parseShouldIgnoreCase() {
            assertThat(Notify.parse("FaiLurE")).contains(Notify.FAILURE);
        }

        @Test
        void parseShouldAcceptAllValues() {
            assertThat(Notify.parse("SUCCESS,FAILURE,DELAY")).contains(Notify.FAILURE, Notify.SUCCESS, Notify.DELAY);
        }

        @Test
        void parseShouldTrimValues() {
            assertThat(Notify.parse("SUCCESS, FAILURE, DELAY")).contains(Notify.FAILURE, Notify.SUCCESS, Notify.DELAY);
        }

        @Test
        void parseShouldIgnoreExtraComas() {
            assertThat(Notify.parse("SUCCESS, ,FAILURE, DELAY")).contains(Notify.FAILURE, Notify.SUCCESS, Notify.DELAY);
        }

        @Test
        void parseShouldThrowWhenNeverIsCombinedWithAnotherValue() {
            assertThatThrownBy(() -> Notify.parse("NEVER,SUCCESS"))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class RecipientDsnParametersTest {
        @Test
        void fromSMTPArgLineShouldReturnEmptyWhenNoParameters() {
            assertThat(RecipientDsnParameters.fromSMTPArgLine(ImmutableMap.of()))
                .isEmpty();
        }

        @Test
        void fromSMTPArgLineShouldReturnEmptyWhenOtherParameters() {
            assertThat(RecipientDsnParameters.fromSMTPArgLine(ImmutableMap.of("OTHER", "value")))
                .isEmpty();
        }

        @Test
        void fromSMTPArgLineShouldAcceptOrcpt() throws Exception {
            assertThat(RecipientDsnParameters.fromSMTPArgLine(ImmutableMap.of("ORCPT", "rfc822;bob@apache.org")))
                .contains(new RecipientDsnParameters(Optional.empty(), Optional.of(new MailAddress("bob@apache.org"))));
        }

        @Test
        void fromSMTPArgShouldLineRejectUnknownOrcptAddressScheme() {
            assertThatThrownBy(() -> RecipientDsnParameters.fromSMTPArgLine(ImmutableMap.of("ORCPT", "other;bob@apache.org")))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void fromSMTPArgLineShouldRejectNoOrcptAddressScheme() {
            assertThatThrownBy(() -> RecipientDsnParameters.fromSMTPArgLine(ImmutableMap.of("ORCPT", "bob@apache.org")))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void fromSMTPArgLineShouldRejectInvalidOrcptAddress() {
            assertThatThrownBy(() -> RecipientDsnParameters.fromSMTPArgLine(ImmutableMap.of("ORCPT", "bob@apache@oups.org")))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void fromSMTPArgLineShouldRejectInvalidNotifyArgument() {
            assertThatThrownBy(() -> RecipientDsnParameters.fromSMTPArgLine(ImmutableMap.of("NOTIFY", "NEVER,DELAY")))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void fromSMTPArgLineAcceptNotifyArgument() {
            assertThat(RecipientDsnParameters.fromSMTPArgLine(ImmutableMap.of("NOTIFY", "SUCCESS")))
                .contains(new RecipientDsnParameters(Optional.of(EnumSet.of(Notify.SUCCESS)), Optional.empty()));
        }
    }

    @Test
    void ofShouldReturnEmptyWhenAllParamsAreEmpty() {
        assertThat(DsnParameters.of(Optional.empty(), Optional.empty(), ImmutableMap.of()))
            .isEmpty();
    }
}
