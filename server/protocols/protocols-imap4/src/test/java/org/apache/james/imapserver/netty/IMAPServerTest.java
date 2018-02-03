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

import java.util.concurrent.TimeUnit;

import org.apache.commons.configuration.DefaultConfigurationBuilder;
import org.apache.james.imap.api.ImapConfiguration;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.google.common.collect.ImmutableSet;

public class IMAPServerTest {
    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void getImapConfigurationShouldReturnDefaultValuesWhenEmpty() throws Exception {
        ImapConfiguration imapConfiguration = IMAPServer.getImapConfiguration(new DefaultConfigurationBuilder());

        ImapConfiguration expectImapConfiguration = ImapConfiguration.builder()
                .enableIdle(ImapConfiguration.DEFAULT_ENABLE_IDLE)
                .idleTimeInterval(ImapConfiguration.DEFAULT_HEARTBEAT_INTERVAL_IN_SECONDS)
                .idleTimeIntervalUnit(ImapConfiguration.DEFAULT_HEARTBEAT_INTERVAL_UNIT)
                .disabledCaps(ImmutableSet.<String>of())
                .build();

        assertThat(imapConfiguration).isEqualTo(expectImapConfiguration);
    }

    @Test
    public void getImapConfigurationShouldReturnSetValue() throws Exception {
        DefaultConfigurationBuilder configurationBuilder = new DefaultConfigurationBuilder();
        configurationBuilder.addProperty("enableIdle", "false");
        configurationBuilder.addProperty("idleTimeInterval", "1");
        configurationBuilder.addProperty("idleTimeIntervalUnit", "MINUTES");
        configurationBuilder.addProperty("disabledCaps", "ACL | MOVE");
        ImapConfiguration imapConfiguration = IMAPServer.getImapConfiguration(configurationBuilder);

        ImapConfiguration expectImapConfiguration = ImapConfiguration.builder()
                .enableIdle(false)
                .idleTimeInterval(1)
                .idleTimeIntervalUnit(TimeUnit.MINUTES)
                .disabledCaps(ImmutableSet.of("ACL", "MOVE"))
                .build();

        assertThat(imapConfiguration).isEqualTo(expectImapConfiguration);
    }
}