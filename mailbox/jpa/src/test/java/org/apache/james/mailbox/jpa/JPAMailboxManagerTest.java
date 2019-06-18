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
package org.apache.james.mailbox.jpa;

import java.util.Optional;

import org.apache.james.backends.jpa.JpaTestCluster;
import org.apache.james.mailbox.MailboxManagerTest;
import org.apache.james.mailbox.events.EventBus;
import org.apache.james.mailbox.jpa.openjpa.OpenJPAMailboxManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public class JPAMailboxManagerTest extends MailboxManagerTest<OpenJPAMailboxManager> {

    @Disabled("JPAMailboxManager is using DefaultMessageId which doesn't support full feature of a messageId, which is an essential" +
        " element of the Vault")
    @Nested
    class HookTests {
    }

    private static final JpaTestCluster JPA_TEST_CLUSTER = JpaTestCluster.create(JPAMailboxFixture.MAILBOX_PERSISTANCE_CLASSES);
    private Optional<OpenJPAMailboxManager> openJPAMailboxManager = Optional.empty();
    
    @Override
    protected OpenJPAMailboxManager provideMailboxManager() {
        if (!openJPAMailboxManager.isPresent()) {
            openJPAMailboxManager = Optional.of(JpaMailboxManagerProvider.provideMailboxManager(JPA_TEST_CLUSTER));
        }
        return openJPAMailboxManager.get();
    }

    @AfterEach
    void tearDownJpa() {
        JPA_TEST_CLUSTER.clear(JPAMailboxFixture.MAILBOX_TABLE_NAMES);
    }

    @Disabled("MAILBOX-353 Creating concurrently mailboxes with the same parents with JPA")
    @Test
    @Override
    public void creatingConcurrentlyMailboxesWithSameParentShouldNotFail() {

    }

    @Override
    protected EventBus retrieveEventBus(OpenJPAMailboxManager mailboxManager) {
        return mailboxManager.getEventBus();
    }
}
