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

package org.apache.james.mailbox.store.search;

import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.junit.jupiter.api.Disabled;

class SimpleMessageSearchIndexTest extends AbstractMessageSearchIndexTest {

    @Override
    protected void await() {
    }

    @Override
    protected void initializeMailboxManager() {
        InMemoryIntegrationResources resources = InMemoryIntegrationResources.builder()
            .preProvisionnedFakeAuthenticator()
            .fakeAuthorizator()
            .inVmEventBus()
            .defaultAnnotationLimits()
            .defaultMessageParser()
            .searchIndex(preInstanciationStage -> new SimpleMessageSearchIndex(
                preInstanciationStage.getMapperFactory(),
                preInstanciationStage.getMapperFactory(),
                new PDFTextExtractor(),
                preInstanciationStage.getAttachmentContentLoader()))
            .noPreDeletionHooks()
            .storeQuotaManager()
            .build();

        storeMailboxManager = resources.getMailboxManager();
        messageIdManager = resources.getMessageIdManager();
        messageSearchIndex = resources.getSearchIndex();
    }

    @Disabled("JAMES-1799: ignoring failing test after generalizing ElasticSearch test suite to other mailbox search backends")
    @Override
    public void flagIsSetShouldReturnUidOfMessageMarkedAsRecentWhenUsedWithFlagRecent() {
    }

    @Disabled("JAMES-1799: ignoring failing test after generalizing ElasticSearch test suite to other mailbox search backends")
    @Override
    public void uidShouldreturnEveryThing() {
    }

    @Disabled("JAMES-1799: ignoring failing test after generalizing ElasticSearch test suite to other mailbox search backends")
    @Override
    public void sortOnCcShouldWork() {
    }

    @Disabled("JAMES-1799: ignoring failing test after generalizing ElasticSearch test suite to other mailbox search backends")
    @Override
    public void sortOnFromShouldWork() {
    }

    @Disabled("JAMES-1799: ignoring failing test after generalizing ElasticSearch test suite to other mailbox search backends")
    @Override
    public void addressShouldReturnUidHavingRightRecipientWhenBccIsSpecified() {
    }

    @Disabled("JAMES-1799: ignoring failing test after generalizing ElasticSearch test suite to other mailbox search backends")
    @Override
    public void orShouldReturnResultsMatchinganyRequests() {
    }

    @Disabled("JAMES-1799: ignoring failing test after generalizing ElasticSearch test suite to other mailbox search backends")
    @Override
    public void internalDateBeforeShouldReturnMessagesBeforeAGivenDate() {
    }

    @Disabled("JAMES-1799: ignoring failing test after generalizing ElasticSearch test suite to other mailbox search backends")
    @Override
    public void headerContainsShouldReturnUidsOfMessageHavingThisHeaderWithTheSpecifiedValue() {
    }

    @Disabled("JAMES-1799: ignoring failing test after generalizing ElasticSearch test suite to other mailbox search backends")
    @Override
    public void internalDateAfterShouldReturnMessagesAfterAGivenDate() {
    }

    @Disabled("JAMES-1799: ignoring failing test after generalizing ElasticSearch test suite to other mailbox search backends")
    @Override
    public void youShouldBeAbleToSpecifySeveralCriterionOnASingleQuery() {
    }

    @Disabled("JAMES-1799: ignoring failing test after generalizing ElasticSearch test suite to other mailbox search backends")
    @Override
    public void headerExistsShouldReturnUidsOfMessageHavingThisHeader() {
    }

    @Disabled("JAMES-1799: ignoring failing test after generalizing ElasticSearch test suite to other mailbox search backends")
    @Override
    public void modSeqLessThanShouldReturnUidsOfMessageHavingAGreaterModSeq() {
    }

    @Disabled("JAMES-1799: ignoring failing test after generalizing ElasticSearch test suite to other mailbox search backends")
    @Override
    public void addressShouldReturnUidHavingRightRecipientWhenCcIsSpecified() {
    }

    @Disabled("JAMES-1799: ignoring failing test after generalizing ElasticSearch test suite to other mailbox search backends")
    @Override
    public void andShouldReturnResultsMatchingBothRequests() {
    }

    @Disabled("JAMES-1799: ignoring failing test after generalizing ElasticSearch test suite to other mailbox search backends")
    @Override
    public void addressShouldReturnUidHavingRightExpeditorWhenFromIsSpecified() {
    }

    @Disabled("JAMES-1799: ignoring failing test after generalizing ElasticSearch test suite to other mailbox search backends")
    @Override
    public void sortOnDisplayToShouldWork() {
    }

    @Disabled("JAMES-1799: ignoring failing test after generalizing ElasticSearch test suite to other mailbox search backends")
    @Override
    public void flagIsUnSetShouldReturnUidOfMessageNotMarkedAsRecentWhenUsedWithFlagRecent() {
    }

    @Disabled("JAMES-1799: ignoring failing test after generalizing ElasticSearch test suite to other mailbox search backends")
    @Override
    public void bodyContainsShouldReturnUidOfMessageContainingTheApproximativeText() {
    }

    @Disabled("JAMES-1799: ignoring failing test after generalizing ElasticSearch test suite to other mailbox search backends")
    @Override
    public void headerDateBeforeShouldWork() {
    }

    @Disabled("JAMES-1799: ignoring failing test after generalizing ElasticSearch test suite to other mailbox search backends")
    @Override
    public void addressShouldReturnUidHavingRightRecipientWhenToIsSpecified() {
    }

    @Disabled("JAMES-1799: ignoring failing test after generalizing ElasticSearch test suite to other mailbox search backends")
    @Override
    public void sortOnToShouldWork() {
    }

    @Disabled("JAMES-1799: ignoring failing test after generalizing ElasticSearch test suite to other mailbox search backends")
    @Override
    public void sortOnDisplayFromShouldWork() {
    }

    @Disabled("JAMES-1799: ignoring failing test after generalizing ElasticSearch test suite to other mailbox search backends")
    @Override
    public void revertSortingShouldReturnElementsInAReversedOrder() {
    }

    @Disabled("JAMES-1799: ignoring failing test after generalizing ElasticSearch test suite to other mailbox search backends")
    @Override
    public void headerDateAfterShouldWork() {
    }

    @Disabled("JAMES-1799: ignoring failing test after generalizing ElasticSearch test suite to other mailbox search backends")
    @Override
    public void mailsContainsShouldIncludeMailHavingAttachmentsMatchingTheRequest() {
    }

    @Disabled("JAMES-1799: ignoring failing test after generalizing ElasticSearch test suite to other mailbox search backends")
    @Override
    public void sortOnSubjectShouldWork() {
    }

    @Disabled("JAMES-1799: ignoring failing test after generalizing ElasticSearch test suite to other mailbox search backends")
    @Override
    public void modSeqGreaterThanShouldReturnUidsOfMessageHavingAGreaterModSeq() {
    }

    @Disabled("JAMES-1799: ignoring failing test after generalizing ElasticSearch test suite to other mailbox search backends")
    @Override
    public void notShouldReturnResultsThatDoNotMatchAQuery() {
    }

    @Disabled("JAMES-1799: ignoring failing test after generalizing ElasticSearch test suite to other mailbox search backends")
    @Override
    public void headerDateOnShouldWork() {
    }

    @Disabled("JAMES-1799: ignoring failing test after generalizing ElasticSearch test suite to other mailbox search backends")
    @Override
    public void sortOnSizeShouldWork() {
    }

    @Disabled("JAMES-1799: ignoring failing test after generalizing ElasticSearch test suite to other mailbox search backends")
    @Override
    public void sortShouldOrderMessages() {
    }

    @Disabled("MAILBOX-273: failing test on memory (intended for ES)")
    @Override
    public void multimailboxSearchShouldReturnUidOfMessageWithExpectedFromInTwoMailboxes() {
    }

    @Disabled("JAMES-2241: memory does not handle header with dots indexation (intended for ES)")
    @Override
    public void headerWithDotsShouldBeIndexed() {
    }
}
