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

import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.inmemory.InMemoryMessageId;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.store.search.AbstractMessageSearchIndexTest;
import org.apache.lucene.store.RAMDirectory;
import org.junit.Ignore;

import com.github.fge.lambdas.Throwing;

public class LuceneMessageSearchIndexTest extends AbstractMessageSearchIndexTest {

    @Override
    protected void await() {
    }

    @Override
    protected void initializeMailboxManager() {
        InMemoryIntegrationResources resources = new InMemoryIntegrationResources.Factory()
            .withListeningSearchIndex(Throwing.function(preInstanciationStage -> new LuceneMessageSearchIndex(
                preInstanciationStage.getMapperFactory(), new InMemoryId.Factory(), new RAMDirectory(),
                new InMemoryMessageId.Factory(),
                preInstanciationStage.getSessionProvider())))
            .create();

        storeMailboxManager = resources.getMailboxManager();
        messageIdManager = resources.getMessageIdManager();
        messageSearchIndex = resources.getSearchIndex();
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
