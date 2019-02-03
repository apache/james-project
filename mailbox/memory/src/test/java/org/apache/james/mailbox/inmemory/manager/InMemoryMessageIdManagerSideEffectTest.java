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

import org.apache.james.mailbox.acl.SimpleGroupMembershipResolver;
import org.apache.james.mailbox.acl.UnionMailboxACLResolver;
import org.apache.james.mailbox.events.EventBus;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.InMemoryMailboxSessionMapperFactory;
import org.apache.james.mailbox.inmemory.InMemoryMessageId;
import org.apache.james.mailbox.manager.ManagerTestResources;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.store.AbstractMessageIdManagerSideEffectTest;
import org.apache.james.mailbox.store.FakeAuthenticator;
import org.apache.james.mailbox.store.FakeAuthorizator;
import org.apache.james.mailbox.store.JVMMailboxPathLocker;
import org.apache.james.mailbox.store.MessageIdManagerTestSystem;
import org.apache.james.mailbox.store.SessionProvider;
import org.apache.james.mailbox.store.StoreMailboxAnnotationManager;
import org.apache.james.mailbox.store.StoreMessageIdManager;
import org.apache.james.mailbox.store.StoreRightManager;
import org.apache.james.mailbox.store.extractor.DefaultTextExtractor;
import org.apache.james.mailbox.store.mail.model.impl.MessageParser;
import org.apache.james.mailbox.store.quota.DefaultUserQuotaRootResolver;
import org.apache.james.mailbox.store.quota.NoMaxQuotaManager;
import org.apache.james.mailbox.store.quota.NoQuotaUpdater;
import org.apache.james.mailbox.store.quota.QuotaComponents;
import org.apache.james.mailbox.store.search.MessageSearchIndex;
import org.apache.james.mailbox.store.search.SimpleMessageSearchIndex;
import org.junit.Before;

public class InMemoryMessageIdManagerSideEffectTest extends AbstractMessageIdManagerSideEffectTest {

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
    }

    @Override
    protected MessageIdManagerTestSystem createTestSystem(QuotaManager quotaManager, EventBus eventBus) {
        InMemoryMailboxSessionMapperFactory mapperFactory = new InMemoryMailboxSessionMapperFactory();

        FakeAuthenticator fakeAuthenticator = new FakeAuthenticator();
        fakeAuthenticator.addUser(ManagerTestResources.USER, ManagerTestResources.USER_PASS);
        fakeAuthenticator.addUser(ManagerTestResources.OTHER_USER, ManagerTestResources.OTHER_USER_PASS);
        FakeAuthorizator fakeAuthorizator = FakeAuthorizator.defaultReject();

        StoreRightManager rightManager = new StoreRightManager(mapperFactory, new UnionMailboxACLResolver(), new SimpleGroupMembershipResolver(), eventBus);
        JVMMailboxPathLocker locker = new JVMMailboxPathLocker();
        InMemoryMessageId.Factory messageIdFactory = new InMemoryMessageId.Factory();
        SessionProvider sessionProvider = new SessionProvider(fakeAuthenticator, fakeAuthorizator);

        QuotaComponents quotaComponents = new QuotaComponents(new NoMaxQuotaManager(), quotaManager, new DefaultUserQuotaRootResolver(sessionProvider, mapperFactory), new NoQuotaUpdater());
        MessageSearchIndex index = new SimpleMessageSearchIndex(mapperFactory, mapperFactory, new DefaultTextExtractor());

        InMemoryMailboxManager mailboxManager = new InMemoryMailboxManager(mapperFactory,
            sessionProvider,
            locker,
            new MessageParser(),
            messageIdFactory,
            eventBus,
            new StoreMailboxAnnotationManager(mapperFactory, rightManager),
            rightManager,
            quotaComponents,
            index);
        StoreMessageIdManager messageIdManager = new StoreMessageIdManager(
            mailboxManager,
            mapperFactory,
            eventBus,
            messageIdFactory,
            quotaManager,
            quotaComponents.getQuotaRootResolver());
        return new MessageIdManagerTestSystem(messageIdManager, messageIdFactory, mapperFactory, mailboxManager);
    }
}
