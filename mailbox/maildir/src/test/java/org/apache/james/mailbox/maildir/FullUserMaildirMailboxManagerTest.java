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
package org.apache.james.mailbox.maildir;

import java.util.Optional;

import org.apache.james.events.EventBus;
import org.apache.james.junit.TemporaryFolderExtension;
import org.apache.james.mailbox.MailboxManagerTest;
import org.apache.james.mailbox.SubscriptionManager;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.mailbox.store.StoreSubscriptionManager;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.RegisterExtension;

class FullUserMaildirMailboxManagerTest extends MailboxManagerTest<StoreMailboxManager> {

    @Disabled("Maildir is using DefaultMessageId which doesn't support full feature of a messageId, which is an essential" +
        " element of the Vault")
    @Nested
    class HookTests {
    }

    @RegisterExtension
    TemporaryFolderExtension temporaryFolder = new TemporaryFolderExtension();
    Optional<StoreMailboxManager> mailboxManager = Optional.empty();

    @Override
    protected StoreMailboxManager provideMailboxManager() {
        if (!mailboxManager.isPresent()) {
            mailboxManager = Optional.of(createMailboxManager());
        }
        return mailboxManager.get();
    }

    @Override
    protected SubscriptionManager provideSubscriptionManager() {
        return new StoreSubscriptionManager(provideMailboxManager().getMapperFactory());
    }

    private StoreMailboxManager createMailboxManager() {
        try {
            return MaildirMailboxManagerProvider.createMailboxManager("/%fulluser", temporaryFolder.getTemporaryFolder().getTempDir());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected EventBus retrieveEventBus(StoreMailboxManager mailboxManager) {
        return mailboxManager.getEventBus();
    }
}
