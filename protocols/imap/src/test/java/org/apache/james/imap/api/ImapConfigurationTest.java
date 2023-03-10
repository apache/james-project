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

package org.apache.james.imap.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.TimeUnit;

import org.apache.james.imap.api.message.Capability;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableSet;

import nl.jqno.equalsverifier.EqualsVerifier;

class ImapConfigurationTest {
    @Test
    void shouldRespectBeanContract() {
        EqualsVerifier.forClass(ImapConfiguration.class).verify();
    }

    @Test
    void idleKeepAliveShouldBeDefaultValueWhenNoSetting() {
        ImapConfiguration imapConfiguration = ImapConfiguration.builder().build();

        assertThat(imapConfiguration.getIdleTimeInterval()).isEqualTo(ImapConfiguration.DEFAULT_HEARTBEAT_INTERVAL_IN_SECONDS);
    }

    @Test
    void idleKeepAliveShouldReturnSetValue() {
        long idleValue  = 1;
        ImapConfiguration imapConfiguration = ImapConfiguration.builder()
                .idleTimeInterval(idleValue)
                .build();

        assertThat(imapConfiguration.getIdleTimeInterval()).isEqualTo(idleValue);
    }

    @Test
    void idleKeepAliveShouldThrowWhenRezo() {
        assertThatThrownBy(() ->
            ImapConfiguration.builder()
                .idleTimeInterval(0L)
                .build())
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void idleKeepAliveShouldThrowWhenNegative() {
        assertThatThrownBy(() ->
            ImapConfiguration.builder()
                .idleTimeInterval(-1)
                .build())
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void millisecondsShouldBeDefaultValueWhenNoSetting() {
        ImapConfiguration imapConfiguration = ImapConfiguration.builder().build();

        assertThat(imapConfiguration.getIdleTimeIntervalUnit()).isEqualTo(ImapConfiguration.DEFAULT_HEARTBEAT_INTERVAL_UNIT);
    }

    @Test
    void millisecondsShouldReturnSetValue() {
        ImapConfiguration imapConfiguration = ImapConfiguration.builder()
                .idleTimeIntervalUnit(TimeUnit.MINUTES)
                .build();

        assertThat(imapConfiguration.getIdleTimeIntervalUnit()).isEqualTo(TimeUnit.MINUTES);
    }

    @Test
    void disabledCapsShouldBeEmptyAsDefault() {
        ImapConfiguration imapConfiguration = ImapConfiguration.builder()
                .build();

        assertThat(imapConfiguration.getDisabledCaps()).isEmpty();
    }

    @Test
    void disabledCapsShouldReturnSetValue() {
        ImapConfiguration imapConfiguration = ImapConfiguration.builder()
                .disabledCaps(ImmutableSet.of("AnyValue"))
                .build();

        assertThat(imapConfiguration.getDisabledCaps()).containsExactly(Capability.of("AnyValue"));
    }

    @Test
    void disabledCapsShouldReturnMultipleSetValues() {
        ImapConfiguration imapConfiguration = ImapConfiguration.builder()
                .disabledCaps(ImmutableSet.of("AnyValue", "OtherValue"))
                .build();

        assertThat(imapConfiguration.getDisabledCaps()).containsExactly(Capability.of("AnyValue"), Capability.of("OtherValue"));
    }

    @Test
    void disabledCapsShouldReturnMultipleSetValuesWithNormalizeValue() {
        ImapConfiguration imapConfiguration = ImapConfiguration.builder()
                .disabledCaps(ImmutableSet.of("   AnyValue   ", "  OtherValue   "))
                .build();

        assertThat(imapConfiguration.getDisabledCaps()).containsExactly(Capability.of("AnyValue"), Capability.of("OtherValue"));
    }

    @Test
    void disabledCapsFromStringArrayShouldReturnMultipleSetValuesWithNormalizeValue() {
        ImapConfiguration imapConfiguration = ImapConfiguration.builder()
                .disabledCaps("   AnyValue   ", "  OtherValue   ")
                .build();

        assertThat(imapConfiguration.getDisabledCaps()).containsExactly(Capability.of("AnyValue"), Capability.of("OtherValue"));
    }

    @Test
    void disabledCapShouldReturnMultipleStringWithNormalizeValue() {
        ImapConfiguration imapConfiguration = ImapConfiguration.builder()
                .disabledCap("   AnyValue   ")
                .build();

        assertThat(imapConfiguration.getDisabledCaps()).containsExactly(Capability.of("AnyValue"));
    }

    @Test
    void idleShouldEnableByDefault() {
        ImapConfiguration imapConfiguration = ImapConfiguration.builder()
                .build();

        assertThat(imapConfiguration.isEnableIdle()).isTrue();
    }

    @Test
    void idleShouldBeDisable() {
        ImapConfiguration imapConfiguration = ImapConfiguration.builder()
                .enableIdle(false)
                .build();

        assertThat(imapConfiguration.isEnableIdle()).isFalse();
    }

    @Test
    void isCondstoreEnableShouldBeFalseWhenNoSetting() {
        ImapConfiguration imapConfiguration = ImapConfiguration.builder().build();

        assertThat(imapConfiguration.isCondstoreEnable()).isFalse();
   }

    @Test
    void isCondstoreEnableShouldBeTrueWhenValueIsTrue() {
        ImapConfiguration imapConfiguration = ImapConfiguration.builder()
                .isCondstoreEnable(true)
                .build();

        assertThat(imapConfiguration.isCondstoreEnable()).isTrue();
   }

    @Test
    void isCondstoreEnableShouldBeFalseWhenValueIsFalse() {
        ImapConfiguration imapConfiguration = ImapConfiguration.builder()
                .isCondstoreEnable(false)
                .build();

        assertThat(imapConfiguration.isCondstoreEnable()).isFalse();
    }

    @Test
    void isProvisionDefaultMailboxesShouldBeTrueWhenNoSetting() {
        ImapConfiguration imapConfiguration = ImapConfiguration.builder().build();

        assertThat(imapConfiguration.isProvisionDefaultMailboxes()).isTrue();
   }

    @Test
    void isProvisionDefaultMailboxesShouldBeTrueWhenValueIsTrue() {
        ImapConfiguration imapConfiguration = ImapConfiguration.builder()
                .isProvisionDefaultMailboxes(true)
                .build();

        assertThat(imapConfiguration.isProvisionDefaultMailboxes()).isTrue();
   }

    @Test
    void isProvisionDefaultMailboxesShouldBeFalseWhenValueIsFalse() {
        ImapConfiguration imapConfiguration = ImapConfiguration.builder()
                .isProvisionDefaultMailboxes(false)
                .build();

        assertThat(imapConfiguration.isProvisionDefaultMailboxes()).isFalse();
   }
}