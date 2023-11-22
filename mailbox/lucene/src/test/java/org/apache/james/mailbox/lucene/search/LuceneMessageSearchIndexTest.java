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

import java.util.List;

import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.inmemory.InMemoryMessageId;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.store.search.AbstractMessageSearchIndexTest;
import org.apache.lucene.store.RAMDirectory;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.github.fge.lambdas.Throwing;

class LuceneMessageSearchIndexTest extends AbstractMessageSearchIndexTest {

    @Override
    protected void awaitMessageCount(List<MailboxId> mailboxIds, SearchQuery query, long messageCount) {
    }

    @Override
    protected void initializeMailboxManager() {
        InMemoryIntegrationResources resources = InMemoryIntegrationResources.builder()
            .preProvisionnedFakeAuthenticator()
            .fakeAuthorizator()
            .inVmEventBus()
            .defaultAnnotationLimits()
            .defaultMessageParser()
            .listeningSearchIndex(Throwing.function(preInstanciationStage -> new LuceneMessageSearchIndex(
                preInstanciationStage.getMapperFactory(), new InMemoryId.Factory(), new RAMDirectory(),
                new InMemoryMessageId.Factory(),
                preInstanciationStage.getSessionProvider())))
            .noPreDeletionHooks()
            .storeQuotaManager()
            .build();

        storeMailboxManager = resources.getMailboxManager();
        messageIdManager = resources.getMessageIdManager();
        messageSearchIndex = resources.getSearchIndex();
        eventBus = resources.getEventBus();
        messageIdFactory = new InMemoryMessageId.Factory();
    }

    @Override
    protected MessageId initNewBasedMessageId() {
        return InMemoryMessageId.of(100);
    }

    @Override
    protected MessageId initOtherBasedMessageId() {
        return InMemoryMessageId.of(1000);
    }

    @Disabled("JAMES-1799: ignoring failing test after generalizing OpenSearch test suite to other mailbox search backends")
    @Override
    public void uidShouldreturnEveryThing() {
    }

    @Disabled("JAMES-1799: ignoring failing test after generalizing OpenSearch test suite to other mailbox search backends")
    @Override
    public void sortOnCcShouldWork() {
    }

    @Disabled("JAMES-1799: ignoring failing test after generalizing OpenSearch test suite to other mailbox search backends")
    @Override
    public void sortOnFromShouldWork() {
    }

    @Disabled("JAMES-1799: ignoring failing test after generalizing OpenSearch test suite to other mailbox search backends")
    @Override
    public void orShouldReturnResultsMatchinganyRequests() {
    }

    @Disabled("JAMES-1799: ignoring failing test after generalizing OpenSearch test suite to other mailbox search backends")
    @Override
    public void internalDateBeforeShouldReturnMessagesBeforeAGivenDate() {
    }

    @Disabled("JAMES-1799: ignoring failing test after generalizing OpenSearch test suite to other mailbox search backends")
    @Override
    public void internalDateAfterShouldReturnMessagesAfterAGivenDate() {
    }

    @Disabled("JAMES-1799: ignoring failing test after generalizing OpenSearch test suite to other mailbox search backends")
    @Override
    public void saveDateBeforeShouldReturnMessagesBeforeAGivenDate() {
    }

    @Disabled("JAMES-1799: ignoring failing test after generalizing OpenSearch test suite to other mailbox search backends")
    @Override
    public void saveDateAfterShouldReturnMessagesAfterAGivenDate() {
    }

    @Disabled("JAMES-1799: ignoring failing test after generalizing OpenSearch test suite to other mailbox search backends")
    @Override
    public void youShouldBeAbleToSpecifySeveralCriterionOnASingleQuery() {
    }

    @Disabled("JAMES-1799: ignoring failing test after generalizing OpenSearch test suite to other mailbox search backends")
    @Override
    public void modSeqLessThanShouldReturnUidsOfMessageHavingAGreaterModSeq() {
    }

    @Disabled("JAMES-1799: ignoring failing test after generalizing OpenSearch test suite to other mailbox search backends")
    @Override
    public void andShouldReturnResultsMatchingBothRequests() {
    }

    @Disabled("JAMES-1799: ignoring failing test after generalizing OpenSearch test suite to other mailbox search backends")
    @Override
    public void addressShouldReturnUidHavingRightExpeditorWhenFromIsSpecified() {
    }

    @Disabled("JAMES-1799: ignoring failing test after generalizing OpenSearch test suite to other mailbox search backends")
    @Override
    public void bodyContainsShouldReturnUidOfMessageContainingBothTerms() {
    }

    @Disabled("Domain part search is not supported by Lucene")
    @Test
    @Override
    public void textShouldMatchEmailAddressDomainPart() throws Exception {
    }

    @Disabled("JAMES-1799: ignoring failing test after generalizing OpenSearch test suite to other mailbox search backends")
    @Override
    public void modSeqGreaterThanShouldReturnUidsOfMessageHavingAGreaterModSeq() {
    }

    @Disabled("JAMES-1799: ignoring failing test after generalizing OpenSearch test suite to other mailbox search backends")
    @Override
    public void modSeqEqualsShouldReturnUidsOfMessageHavingAGivenModSeq() {
    }

    @Disabled("MAILBOX-273: failing test on Lucene (intended for ES)")
    @Override
    public void multimailboxSearchShouldReturnUidOfMessageMarkedAsSeenInTwoMailboxes() {
    }

    @Disabled("MAILBOX-273: failing test on Lucene (intended for ES)")
    @Override
    public void multimailboxSearchShouldReturnUidOfMessageMarkedAsSeenInAllMailboxes() {
    }

    @Disabled("JAMES-2590: Lucene implementation is not handling mail addresses with names")
    @Override
    public void sortOnToShouldWork() {
    }
}
