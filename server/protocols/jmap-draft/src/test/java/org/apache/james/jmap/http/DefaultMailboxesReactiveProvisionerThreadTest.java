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
package org.apache.james.jmap.http;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.SubscriptionManager;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.util.concurrency.ConcurrentTestRunner;
import org.junit.Before;
import org.junit.Test;

public class DefaultMailboxesReactiveProvisionerThreadTest {

    private static final Username USERNAME = Username.of("username");

    private DefaultMailboxesReactiveProvisioner testee;
    private MailboxSession session;
    private MailboxManager mailboxManager;
    private SubscriptionManager subscriptionManager;

    @Before
    public void before() {
        session = MailboxSessionUtil.create(USERNAME);
        mailboxManager = mock(MailboxManager.class);
        subscriptionManager = mock(SubscriptionManager.class);
        testee = new DefaultMailboxesReactiveProvisioner(mailboxManager, subscriptionManager, new RecordingMetricFactory());
    }

    @Test
    public void testConcurrentAccessToFilterShouldNotThrow() throws Exception {
        doNothing().when(subscriptionManager).subscribe(eq(session), anyString());

        when(mailboxManager.createMailbox(any(MailboxPath.class), eq(session))).thenReturn(Optional.of(TestId.of(18L)));
        when(mailboxManager.mailboxExists(any(MailboxPath.class), eq(session))).thenReturn(false);
        when(mailboxManager.createSystemSession(USERNAME)).thenReturn(session);

        ConcurrentTestRunner
            .builder()
            .operation((threadNumber, step) -> testee.createMailboxesIfNeeded(session).block())
            .threadCount(2)
            .runSuccessfullyWithin(Duration.ofMinutes(1));
    }
}
