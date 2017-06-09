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
import java.util.concurrent.TimeUnit;

import org.apache.james.imap.processor.IdleProcessor;

import com.google.common.collect.ImmutableSet;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class ImapConfigurationTest {
    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void shouldRespectBeanContract() {
        EqualsVerifier.forClass(ImapConfiguration.class).verify();
    }

    @Test
    public void idleKeepAliveShouldBeDefaultValueWhenNoSetting() throws Exception {
        ImapConfiguration imapConfiguration = ImapConfiguration.builder().build();

        assertThat(imapConfiguration.getIdleTimeInterval()).isEqualTo(IdleProcessor.DEFAULT_HEARTBEAT_INTERVAL_IN_SECONDS);
    }

    @Test
    public void idleKeepAliveShouldReturnSetValue() throws Exception {
        long idleValue  = 1;
        ImapConfiguration imapConfiguration = ImapConfiguration.builder()
                .idleTimeInterval(idleValue)
                .build();

        assertThat(imapConfiguration.getIdleTimeInterval()).isEqualTo(idleValue);
    }

    @Test
    public void idleKeepAliveShouldThrowWhenRezo() throws Exception {
        expectedException.expect(IllegalArgumentException.class);

        ImapConfiguration.builder()
                .idleTimeInterval(0L)
                .build();
    }

    @Test
    public void idleKeepAliveShouldThrowWhenNegative() throws Exception {
        expectedException.expect(IllegalArgumentException.class);

        ImapConfiguration.builder()
                .idleTimeInterval(-1)
                .build();
    }

    @Test
    public void millisecondsShouldBeDefaultValueWhenNoSetting() throws Exception {
        ImapConfiguration imapConfiguration = ImapConfiguration.builder().build();

        assertThat(imapConfiguration.getIdleTimeIntervalUnit()).isEqualTo(IdleProcessor.DEFAULT_HEARTBEAT_INTERVAL_UNIT);
    }

    @Test
    public void millisecondsShouldReturnSetValue() throws Exception {
        ImapConfiguration imapConfiguration = ImapConfiguration.builder()
                .idleTimeIntervalUnit(TimeUnit.MINUTES)
                .build();

        assertThat(imapConfiguration.getIdleTimeIntervalUnit()).isEqualTo(TimeUnit.MINUTES);
    }

    @Test
    public void disabledCapsShouldBeEmptyAsDefault() throws Exception {
        ImapConfiguration imapConfiguration = ImapConfiguration.builder()
                .build();

        assertThat(imapConfiguration.getDisabledCaps()).isEmpty();
    }

    @Test
    public void disabledCapsShouldReturnSetValue() throws Exception {
        ImapConfiguration imapConfiguration = ImapConfiguration.builder()
                .disabledCaps(ImmutableSet.of("AnyValue"))
                .build();

        assertThat(imapConfiguration.getDisabledCaps()).containsExactly("AnyValue");
    }

    @Test
    public void disabledCapsShouldReturnMultipleSetValues() throws Exception {
        ImapConfiguration imapConfiguration = ImapConfiguration.builder()
                .disabledCaps(ImmutableSet.of("AnyValue", "OtherValue"))
                .build();

        assertThat(imapConfiguration.getDisabledCaps()).containsExactly("AnyValue", "OtherValue");
    }

    @Test
    public void disabledCapsShouldReturnMultipleSetValuesWithNormalizeValue() throws Exception {
        ImapConfiguration imapConfiguration = ImapConfiguration.builder()
                .disabledCaps(ImmutableSet.of("   AnyValue   ", "  OtherValue   "))
                .build();

        assertThat(imapConfiguration.getDisabledCaps()).containsExactly("AnyValue", "OtherValue");
    }

    @Test
    public void disabledCapsFromStringArrayShouldReturnMultipleSetValuesWithNormalizeValue() throws Exception {
        ImapConfiguration imapConfiguration = ImapConfiguration.builder()
                .disabledCaps("   AnyValue   ", "  OtherValue   ")
                .build();

        assertThat(imapConfiguration.getDisabledCaps()).containsExactly("AnyValue", "OtherValue");
    }

    @Test
    public void disabledCapShouldReturnMultipleStringWithNormalizeValue() throws Exception {
        ImapConfiguration imapConfiguration = ImapConfiguration.builder()
                .disabledCap("   AnyValue   ")
                .build();

        assertThat(imapConfiguration.getDisabledCaps()).containsExactly("AnyValue");
    }

    @Test
    public void idleShouldEnableByDefault() throws Exception {
        ImapConfiguration imapConfiguration = ImapConfiguration.builder()
                .build();

        assertThat(imapConfiguration.isEnableIdle()).isTrue();
    }

    @Test
    public void idleShouldBeDisable() throws Exception {
        ImapConfiguration imapConfiguration = ImapConfiguration.builder()
                .enableIdle(false)
                .build();

        assertThat(imapConfiguration.isEnableIdle()).isFalse();
    }
}