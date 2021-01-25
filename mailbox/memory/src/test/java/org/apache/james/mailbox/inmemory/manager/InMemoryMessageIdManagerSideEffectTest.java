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

package org.apache.james.mailbox.inmemory.manager;

import java.util.Set;

import org.apache.james.events.EventBus;
import org.apache.james.mailbox.extension.PreDeletionHook;
import org.apache.james.mailbox.inmemory.InMemoryMessageId;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.store.AbstractMessageIdManagerSideEffectTest;
import org.apache.james.mailbox.store.MessageIdManagerTestSystem;

class InMemoryMessageIdManagerSideEffectTest extends AbstractMessageIdManagerSideEffectTest {

    @Override
    protected MessageIdManagerTestSystem createTestSystem(QuotaManager quotaManager, EventBus eventBus, Set<PreDeletionHook> preDeletionHooks) {
        InMemoryMessageId.Factory messageIdFactory = new InMemoryMessageId.Factory();

        InMemoryIntegrationResources resources = InMemoryIntegrationResources.builder()
            .preProvisionnedFakeAuthenticator()
            .fakeAuthorizator()
            .eventBus(eventBus)
            .defaultAnnotationLimits()
            .defaultMessageParser()
            .scanningSearchIndex()
            .preDeletionHooks(preDeletionHooks)
            .quotaManager(quotaManager)
            .build();

        return new MessageIdManagerTestSystem(resources.getMessageIdManager(),
            messageIdFactory,
            resources.getMailboxManager().getMapperFactory(),
            resources.getMailboxManager());
    }
}
