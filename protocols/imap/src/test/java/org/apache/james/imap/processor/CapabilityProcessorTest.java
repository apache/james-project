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
package org.apache.james.imap.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.EnumSet;
import java.util.Set;

import org.apache.james.imap.api.ImapConfiguration;
import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.message.Capability;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.metrics.api.MetricFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

import reactor.core.publisher.Mono;

class CapabilityProcessorTest {
    private CapabilityProcessor testee;

    @BeforeEach
    void setup() {
        StatusResponseFactory statusResponseFactory = null;
        MailboxManager mailboxManager = mock(MailboxManager.class);
        when(mailboxManager.manageProcessing(any(), any())).thenAnswer((Answer<Mono>) invocation -> {
            Object[] args = invocation.getArguments();
            return (Mono) args[0];
        });
        when(mailboxManager.getSupportedMessageCapabilities()).thenReturn(EnumSet.allOf(MailboxManager.MessageCapabilities.class));
        MetricFactory metricFactory = null;
        testee = new CapabilityProcessor(mailboxManager, statusResponseFactory, metricFactory);
    }

    @Test
    void condstoreShouldBeSupportedWhenSelectedFor() {
        testee.configure(ImapConfiguration.builder().isCondstoreEnable(true).build());

        Set<Capability> supportedCapabilities = testee.getSupportedCapabilities(null);
        assertThat(supportedCapabilities).contains(ImapConstants.SUPPORTS_CONDSTORE);
    }

    @Test
    void condstoreShouldBeSupportedWhenReconfigured() {
        testee.configure(ImapConfiguration.builder().isCondstoreEnable(false).build());
        testee.configure(ImapConfiguration.builder().isCondstoreEnable(true).build());

        Set<Capability> supportedCapabilities = testee.getSupportedCapabilities(null);
        assertThat(supportedCapabilities).contains(ImapConstants.SUPPORTS_CONDSTORE);
    }

    @Test
    void condstoreShouldBeNotSupportedByDefault() {
        testee.configure(ImapConfiguration.builder().build());

        Set<Capability> supportedCapabilities = testee.getSupportedCapabilities(null);
        assertThat(supportedCapabilities).doesNotContain(ImapConstants.SUPPORTS_CONDSTORE);
    }
}
