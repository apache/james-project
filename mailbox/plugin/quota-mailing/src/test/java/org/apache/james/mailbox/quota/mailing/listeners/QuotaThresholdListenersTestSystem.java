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

package org.apache.james.mailbox.quota.mailing.listeners;

import org.apache.james.eventsourcing.eventstore.EventStore;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.mailbox.Event;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.mock.MockMailboxSession;
import org.apache.james.mailbox.quota.mailing.QuotaMailingListenerConfiguration;
import org.apache.james.mailbox.store.event.DefaultDelegatingMailboxListener;
import org.apache.james.server.core.JamesServerResourceLoader;
import org.apache.james.server.core.filesystem.FileSystemImpl;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.apache.mailet.MailetContext;

public class QuotaThresholdListenersTestSystem {

    private final DefaultDelegatingMailboxListener delegatingListener;

    public QuotaThresholdListenersTestSystem(MailetContext mailetContext, EventStore eventStore, QuotaMailingListenerConfiguration configuration) throws MailboxException {
        delegatingListener = new DefaultDelegatingMailboxListener();

        FileSystem fileSystem = new FileSystemImpl(new JamesServerResourceLoader("."));

        QuotaThresholdCrossingListener thresholdCrossingListener =
            new QuotaThresholdCrossingListener(mailetContext, MemoryUsersRepository.withVirtualHosting(), fileSystem, eventStore, configuration);

        MockMailboxSession mailboxSession = new MockMailboxSession("system");
        delegatingListener.addGlobalListener(thresholdCrossingListener, mailboxSession);
    }

    public void event(Event event) {
        delegatingListener.event(event);
    }
}
