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

package org.apache.james.imapserver.netty;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Optional;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.imapserver.netty.IMAPCommandsThrottler.ThrottlerConfiguration;
import org.apache.james.imapserver.netty.IMAPCommandsThrottler.ThrottlerConfigurationEntry;
import org.apache.james.protocols.lib.mock.ConfigLoader;
import org.apache.james.util.ClassLoaderUtils;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableMap;

class IMAPCommandsThrottlerTest {
    @Nested
    class ConfigTest {
        @Test
        void shouldLoad() throws Exception {
            HierarchicalConfiguration<ImmutableNode> config = ConfigLoader.getConfig(ClassLoaderUtils.getSystemResourceAsSharedStream("commandsThrottling.xml"));

            var selectEntry = new ThrottlerConfigurationEntry(Optional.of("APPEND"), 25, Duration.ofMillis(2), Duration.ofMinutes(10), Duration.ofSeconds(1));
            var appendEntry = new ThrottlerConfigurationEntry(Optional.empty(), 5, Duration.ofMillis(10), Duration.ofMinutes(5), Duration.ofSeconds(2));

            assertThat(ThrottlerConfiguration.from(config))
                .isEqualTo(ThrottlerConfiguration.from(
                    ImmutableMap.of(
                        "SELECT", selectEntry,
                        "APPEND", appendEntry)));
        }
    }

    @Nested
    class DelayTest {
        @Test
        void shouldNotDelayWhenBelowThreshold() {
            var selectEntry = new ThrottlerConfigurationEntry(Optional.empty(), 25, Duration.ofMillis(2), Duration.ofMinutes(10), Duration.ofSeconds(1));

            assertThat(selectEntry.delayMSFor(24)).isZero();
        }

        @Test
        void shouldDelayWhenThreshold() {
            var selectEntry = new ThrottlerConfigurationEntry(Optional.empty(), 25, Duration.ofMillis(2), Duration.ofMinutes(10), Duration.ofSeconds(1));

            assertThat(selectEntry.delayMSFor(25)).isEqualTo(50);
        }

        @Test
        void shouldAdditionalDelayWhenAboveThreshold() {
            var selectEntry = new ThrottlerConfigurationEntry(Optional.empty(), 25, Duration.ofMillis(2), Duration.ofMinutes(10), Duration.ofSeconds(1));

            assertThat(selectEntry.delayMSFor(26)).isEqualTo(52);
        }

        @Test
        void shouldNotExceedMaximumDelay() {
            var selectEntry = new ThrottlerConfigurationEntry(Optional.empty(), 25, Duration.ofMillis(2), Duration.ofMinutes(10), Duration.ofSeconds(1));

            assertThat(selectEntry.delayMSFor(2600)).isEqualTo(1000);
        }
    }
}