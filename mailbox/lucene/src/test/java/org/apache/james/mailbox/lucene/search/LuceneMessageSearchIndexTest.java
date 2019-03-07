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

package org.apache.james.mailbox.lucene.search;

import org.apache.james.mailbox.acl.SimpleGroupMembershipResolver;
import org.apache.james.mailbox.acl.UnionMailboxACLResolver;
import org.apache.james.mailbox.events.InVMEventBus;
import org.apache.james.mailbox.events.delivery.InVmEventDelivery;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.InMemoryMailboxSessionMapperFactory;
import org.apache.james.mailbox.inmemory.InMemoryMessageId;
import org.apache.james.mailbox.manager.ManagerTestResources;
import org.apache.james.mailbox.store.FakeAuthenticator;
import org.apache.james.mailbox.store.FakeAuthorizator;
import org.apache.james.mailbox.store.JVMMailboxPathLocker;
import org.apache.james.mailbox.store.PreDeletionHooks;
import org.apache.james.mailbox.store.SessionProvider;
import org.apache.james.mailbox.store.StoreMailboxAnnotationManager;
import org.apache.james.mailbox.store.StoreMessageIdManager;
import org.apache.james.mailbox.store.StoreRightManager;
import org.apache.james.mailbox.store.mail.model.impl.MessageParser;
import org.apache.james.mailbox.store.quota.QuotaComponents;
import org.apache.james.mailbox.store.search.AbstractMessageSearchIndexTest;
import org.apache.james.metrics.api.NoopMetricFactory;
import org.apache.lucene.store.RAMDirectory;
import org.junit.Ignore;

public class LuceneMessageSearchIndexTest extends AbstractMessageSearchIndexTest {

    @Override
    protected void await() {
    }

    @Override
    protected void initializeMailboxManager() throws Exception {
        FakeAuthenticator fakeAuthenticator = new FakeAuthenticator();
        fakeAuthenticator.addUser(ManagerTestResources.USER, ManagerTestResources.USER_PASS);
        fakeAuthenticator.addUser(ManagerTestResources.OTHER_USER, ManagerTestResources.OTHER_USER_PASS);
        InMemoryMailboxSessionMapperFactory mailboxSessionMapperFactory = new InMemoryMailboxSessionMapperFactory();
        InVMEventBus eventBus = new InVMEventBus(new InVmEventDelivery(new NoopMetricFactory()));
        StoreRightManager storeRightManager = new StoreRightManager(mailboxSessionMapperFactory, new UnionMailboxACLResolver(), new SimpleGroupMembershipResolver(), eventBus);
        StoreMailboxAnnotationManager annotationManager = new StoreMailboxAnnotationManager(mailboxSessionMapperFactory, storeRightManager);

        SessionProvider sessionProvider = new SessionProvider(fakeAuthenticator, FakeAuthorizator.defaultReject());
        QuotaComponents quotaComponents = QuotaComponents.disabled(sessionProvider, mailboxSessionMapperFactory);
        InMemoryMessageId.Factory messageIdFactory = new InMemoryMessageId.Factory();
        LuceneMessageSearchIndex luceneMessageSearchIndex = new LuceneMessageSearchIndex(
            mailboxSessionMapperFactory, new InMemoryId.Factory(), new RAMDirectory(),
            messageIdFactory,
            new SessionProvider(new FakeAuthenticator(), FakeAuthorizator.defaultReject()));

        storeMailboxManager = new InMemoryMailboxManager(
            mailboxSessionMapperFactory,
            sessionProvider,
            new JVMMailboxPathLocker(),
            new MessageParser(),
            messageIdFactory,
            eventBus,
            annotationManager,
            storeRightManager,
            quotaComponents,
            luceneMessageSearchIndex,
            PreDeletionHooks.NO_PRE_DELETION_HOOK);

        messageIdManager = new StoreMessageIdManager(
            storeMailboxManager,
            storeMailboxManager.getMapperFactory(),
            eventBus,
            storeMailboxManager.getMessageIdFactory(),
            quotaComponents.getQuotaManager(),
            quotaComponents.getQuotaRootResolver(),
            PreDeletionHooks.NO_PRE_DELETION_HOOK);

        eventBus.register(luceneMessageSearchIndex);
        this.messageSearchIndex = luceneMessageSearchIndex;
    }

    @Ignore
    @Override
    public void uidShouldreturnEveryThing() throws Exception {
    }

    @Ignore
    @Override
    public void sortOnCcShouldWork() throws Exception {
    }

    @Ignore
    @Override
    public void sortOnFromShouldWork() throws Exception {
    }

    @Ignore
    @Override
    public void orShouldReturnResultsMatchinganyRequests() throws Exception {
    }

    @Ignore
    @Override
    public void internalDateBeforeShouldReturnMessagesBeforeAGivenDate() throws Exception {
    }

    @Ignore
    @Override
    public void internalDateAfterShouldReturnMessagesAfterAGivenDate() throws Exception {
    }

    @Ignore
    @Override
    public void youShouldBeAbleToSpecifySeveralCriterionOnASingleQuery() throws Exception {
    }

    @Ignore
    @Override
    public void modSeqLessThanShouldReturnUidsOfMessageHavingAGreaterModSeq() throws Exception {
    }

    @Ignore
    @Override
    public void andShouldReturnResultsMatchingBothRequests() throws Exception {
    }

    @Ignore
    @Override
    public void addressShouldReturnUidHavingRightExpeditorWhenFromIsSpecified() throws Exception {
    }

    @Ignore
    @Override
    public void bodyContainsShouldReturnUidOfMessageContainingTheApproximativeText() throws MailboxException {
    }

    @Ignore
    @Override
    public void sortOnDisplayFromShouldWork() throws Exception {
    }

    @Ignore
    @Override
    public void mailsContainsShouldIncludeMailHavingAttachmentsMatchingTheRequest() throws Exception {
    }

    @Ignore
    @Override
    public void modSeqGreaterThanShouldReturnUidsOfMessageHavingAGreaterModSeq() throws Exception {
    }

    @Ignore
    @Override
    public void modSeqEqualsShouldReturnUidsOfMessageHavingAGivenModSeq() throws Exception {
    }

    @Ignore
    @Override
    public void multimailboxSearchShouldReturnUidOfMessageMarkedAsSeenInTwoMailboxes() throws MailboxException {
    }

    @Ignore
    @Override
    public void multimailboxSearchShouldReturnUidOfMessageMarkedAsSeenInAllMailboxes() throws MailboxException {
    }

    @Ignore("Lucene implementation is not handling mail addresses with names")
    @Override
    public void sortOnToShouldWork() {
    }
}
