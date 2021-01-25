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

import java.io.File;

import org.apache.james.events.EventBusTestFixture;
import org.apache.james.events.InVMEventBus;
import org.apache.james.events.MemoryEventDeadLetters;
import org.apache.james.events.delivery.InVmEventDelivery;
import org.apache.james.mailbox.Authenticator;
import org.apache.james.mailbox.Authorizator;
import org.apache.james.mailbox.acl.GroupMembershipResolver;
import org.apache.james.mailbox.acl.MailboxACLResolver;
import org.apache.james.mailbox.acl.SimpleGroupMembershipResolver;
import org.apache.james.mailbox.acl.UnionMailboxACLResolver;
import org.apache.james.mailbox.store.JVMMailboxPathLocker;
import org.apache.james.mailbox.store.MailboxManagerConfiguration;
import org.apache.james.mailbox.store.PreDeletionHooks;
import org.apache.james.mailbox.store.SessionProviderImpl;
import org.apache.james.mailbox.store.StoreMailboxAnnotationManager;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.mailbox.store.StoreRightManager;
import org.apache.james.mailbox.store.extractor.DefaultTextExtractor;
import org.apache.james.mailbox.store.mail.model.DefaultMessageId;
import org.apache.james.mailbox.store.mail.model.impl.MessageParser;
import org.apache.james.mailbox.store.quota.QuotaComponents;
import org.apache.james.mailbox.store.search.MessageSearchIndex;
import org.apache.james.mailbox.store.search.SimpleMessageSearchIndex;
import org.apache.james.metrics.tests.RecordingMetricFactory;

public class MaildirMailboxManagerProvider {

    public static StoreMailboxManager createMailboxManager(String configuration, File tempFile) {
        MaildirStore store = new MaildirStore(tempFile.getPath() + configuration, new JVMMailboxPathLocker());
        MaildirMailboxSessionMapperFactory mf = new MaildirMailboxSessionMapperFactory(store);

        MailboxACLResolver aclResolver = new UnionMailboxACLResolver();
        GroupMembershipResolver groupMembershipResolver = new SimpleGroupMembershipResolver();
        MessageParser messageParser = new MessageParser();

        InVMEventBus eventBus = new InVMEventBus(new InVmEventDelivery(new RecordingMetricFactory()), EventBusTestFixture.RETRY_BACKOFF_CONFIGURATION, new MemoryEventDeadLetters());
        StoreRightManager storeRightManager = new StoreRightManager(mf, aclResolver, groupMembershipResolver, eventBus);

        Authenticator noAuthenticator = null;
        Authorizator noAuthorizator = null;
        SessionProviderImpl sessionProvider = new SessionProviderImpl(noAuthenticator, noAuthorizator);

        StoreMailboxAnnotationManager annotationManager = new StoreMailboxAnnotationManager(mf, storeRightManager);
        QuotaComponents quotaComponents = QuotaComponents.disabled(sessionProvider, mf);
        MessageSearchIndex index = new SimpleMessageSearchIndex(mf, mf, new DefaultTextExtractor(), new MaildirAttachmentContentLoader());

        StoreMailboxManager manager = new StoreMailboxManager(mf, sessionProvider, new JVMMailboxPathLocker(),
            messageParser, new DefaultMessageId.Factory(), annotationManager, eventBus, storeRightManager,
            quotaComponents, index, MailboxManagerConfiguration.DEFAULT, PreDeletionHooks.NO_PRE_DELETION_HOOK);

        return manager;
    }
}
