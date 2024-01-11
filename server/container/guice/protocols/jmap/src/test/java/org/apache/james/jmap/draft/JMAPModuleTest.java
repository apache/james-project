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

package org.apache.james.jmap.draft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.EnumSet;

import org.apache.james.jmap.draft.JMAPModule.RequiredCapabilitiesStartUpCheck;
import org.apache.james.mailbox.MailboxManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class JMAPModuleTest {

    @Nested
    class RequiredCapabilitiesStartUpCheckTest {

        private RequiredCapabilitiesStartUpCheck testee;
        private MailboxManager mockMailboxManager;

        @SuppressWarnings("unchecked")
        @BeforeEach
        void beforeEach() {
            mockMailboxManager = mock(MailboxManager.class);

            testee = new RequiredCapabilitiesStartUpCheck(mockMailboxManager);
        }

        @Test
        void checkShouldReturnGoodWhenAllChecksSatisfy() {
            when(mockMailboxManager.hasCapability(any()))
                .thenReturn(true);


            when(mockMailboxManager.getSupportedMessageCapabilities())
                .thenReturn(EnumSet.allOf(MailboxManager.MessageCapabilities.class));
            when(mockMailboxManager.getSupportedSearchCapabilities())
                .thenReturn(EnumSet.allOf(MailboxManager.SearchCapabilities.class));

            assertThat(testee.check().isGood())
                .isTrue();
        }

        @Test
        void checkShouldReturnBadWhenMailboxManagerDoesntHaveCapabilities() {
            when(mockMailboxManager.hasCapability(any()))
                .thenReturn(false);
            when(mockMailboxManager.getSupportedMessageCapabilities())
                .thenReturn(EnumSet.allOf(MailboxManager.MessageCapabilities.class));
            when(mockMailboxManager.getSupportedSearchCapabilities())
                .thenReturn(EnumSet.allOf(MailboxManager.SearchCapabilities.class));

            assertThat(testee.check().isBad())
                .isTrue();
        }

        @Test
        void checkShouldReturnBadWhenMailboxManagerDoesntHaveMessagesCapabilities() {
            when(mockMailboxManager.hasCapability(any()))
                .thenReturn(true);
            when(mockMailboxManager.getSupportedMessageCapabilities())
                .thenReturn(EnumSet.noneOf(MailboxManager.MessageCapabilities.class));
            when(mockMailboxManager.getSupportedSearchCapabilities())
                .thenReturn(EnumSet.allOf(MailboxManager.SearchCapabilities.class));

            assertThat(testee.check().isBad())
                .isTrue();
        }

        @Test
        void checkShouldReturnBadWhenMailboxManagerDoesntHaveSearchCapabilities() {
            when(mockMailboxManager.hasCapability(any()))
                .thenReturn(true);
            when(mockMailboxManager.getSupportedMessageCapabilities())
                .thenReturn(EnumSet.allOf(MailboxManager.MessageCapabilities.class));
            when(mockMailboxManager.getSupportedSearchCapabilities())
                .thenReturn(EnumSet.noneOf(MailboxManager.SearchCapabilities.class));

            assertThat(testee.check().isBad())
                .isTrue();
        }
    }
}