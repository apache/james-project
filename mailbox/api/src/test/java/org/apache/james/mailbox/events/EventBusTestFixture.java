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

package org.apache.james.mailbox.events;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.apache.james.core.User;
import org.apache.james.mailbox.Event;
import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.TestId;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public interface EventBusTestFixture {

    class GroupA extends Group {}
    class GroupB extends Group {}

    MailboxListener.MailboxEvent EVENT = new MailboxListener.MailboxAdded(
        MailboxSession.SessionId.of(42),
        User.fromUsername("user"),
        new MailboxPath(MailboxConstants.USER_NAMESPACE, "user", "mailboxName"),
        TestId.of(18),
        Event.EventId.random());

    int ONE_SECOND = 1000;
    int FIVE_HUNDRED_MS = 500;
    MailboxId ID_1 = TestId.of(18);
    MailboxId ID_2 = TestId.of(24);
    ImmutableSet<RegistrationKey> NO_KEYS = ImmutableSet.of();
    MailboxIdRegistrationKey KEY_1 = new MailboxIdRegistrationKey(ID_1);
    MailboxIdRegistrationKey KEY_2 = new MailboxIdRegistrationKey(ID_2);
    List<Class<? extends Group>> ALL_GROUPS = ImmutableList.of(GroupA.class, GroupB.class);

    static MailboxListener newListener() {
        MailboxListener listener = mock(MailboxListener.class);
        when(listener.getExecutionMode()).thenReturn(MailboxListener.ExecutionMode.SYNCHRONOUS);
        return listener;
    }
}
