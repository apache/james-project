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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import java.util.Date;
import java.util.Map;

import javax.mail.Flags;

import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MultimailboxesSearchQuery;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.model.SearchQuery.AddressType;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.mailbox.store.StoreMessageManager;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

public abstract class AbstractMessageSearchIndexTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractMessageSearchIndexTest.class);

    protected MessageSearchIndex messageSearchIndex;
    protected StoreMailboxManager storeMailboxManager;
    private Mailbox mailbox;
    private Mailbox mailbox2;
    private MailboxSession session;

    @Before
    public void setUp() throws Exception {
        initializeMailboxManager();

        session = storeMailboxManager.createSystemSession("benwa", LOGGER);

        MailboxPath inboxPath = new MailboxPath("#private", "benwa", "INBOX");
        storeMailboxManager.createMailbox(inboxPath, session);
        StoreMessageManager inboxMessageManager = (StoreMessageManager) storeMailboxManager.getMailbox(inboxPath, session);
        MailboxPath myFolderPath = new MailboxPath("#private", "benwa", "MyFolder");
        storeMailboxManager.createMailbox(myFolderPath, session);
        StoreMessageManager myFolderMessageManager = (StoreMessageManager) storeMailboxManager.getMailbox(myFolderPath, session);
        mailbox = inboxMessageManager.getMailboxEntity();
        mailbox2 = myFolderMessageManager.getMailboxEntity();

        // sentDate: Wed, 3 Jun 2015 09:05:46 +0000
        // Internal date : 2014/01/02 00:00:00.000
        inboxMessageManager.appendMessage(
            ClassLoader.getSystemResourceAsStream("eml/spamMail.eml"),
            new Date(1388617200000L),
            session,
            true,
            new Flags(Flags.Flag.DELETED));
        // sentDate: Thu, 4 Jun 2015 09:23:37 +0000
        // Internal date : 2014/02/02 00:00:00.000
        inboxMessageManager.appendMessage(
            ClassLoader.getSystemResourceAsStream("eml/mail1.eml"),
            new Date(1391295600000L),
            session,
            true,
            new Flags(Flags.Flag.ANSWERED));
        // sentDate: Thu, 4 Jun 2015 09:27:37 +0000
        // Internal date : 2014/03/02 00:00:00.000
        inboxMessageManager.appendMessage(
            ClassLoader.getSystemResourceAsStream("eml/mail2.eml"),
            new Date(1393714800000L),
            session,
            true,
            new Flags(Flags.Flag.DRAFT));
        // sentDate: Tue, 2 Jun 2015 08:16:19 +0000
        // Internal date : 2014/05/02 00:00:00.000
        inboxMessageManager.appendMessage(
            ClassLoader.getSystemResourceAsStream("eml/mail3.eml"),
            new Date(1398981600000L),
            session,
            true,
            new Flags(Flags.Flag.RECENT));
        // sentDate: Fri, 15 May 2015 06:35:59 +0000
        // Internal date : 2014/04/02 00:00:00.000
        inboxMessageManager.appendMessage(
            ClassLoader.getSystemResourceAsStream("eml/mail4.eml"),
            new Date(1396389600000L),
            session,
            true,
            new Flags(Flags.Flag.FLAGGED));
        // sentDate: Wed, 03 Jun 2015 19:14:32 +0000
        // Internal date : 2014/06/02 00:00:00.000
        inboxMessageManager.appendMessage(
            ClassLoader.getSystemResourceAsStream("eml/pgpSignedMail.eml"),
            new Date(1401660000000L),
            session,
            true,
            new Flags(Flags.Flag.SEEN));
        // sentDate: Thu, 04 Jun 2015 07:36:08 +0000
        // Internal date : 2014/07/02 00:00:00.000
        inboxMessageManager.appendMessage(
            ClassLoader.getSystemResourceAsStream("eml/htmlMail.eml"),
            new Date(1404252000000L),
            session,
            false,
            new Flags());
        // sentDate: Thu, 4 Jun 2015 06:08:41 +0200
        // Internal date : 2014/08/02 00:00:00.000
        inboxMessageManager.appendMessage(
            ClassLoader.getSystemResourceAsStream("eml/mail.eml"),
            new Date(1406930400000L),
            session,
            true,
            new Flags("Hello"));
        // sentDate: Thu, 4 Jun 2015 06:08:41 +0200
        // Internal date : 2014/08/02 00:00:00.000
        myFolderMessageManager.appendMessage(
            ClassLoader.getSystemResourceAsStream("eml/mail.eml"),
            new Date(1406930400000L),
            session,
            true,
            new Flags(Flags.Flag.SEEN));
        // sentDate: Tue, 2 Jun 2015 12:00:55 +0200
        // Internal date : 2014/09/02 00:00:00.000
        inboxMessageManager.appendMessage(
            ClassLoader.getSystemResourceAsStream("eml/frnog.eml"),
            new Date(1409608800000L),
            session,
            true,
            new Flags("Hello you"));
        await();
    }

    protected abstract void await();
    protected abstract void initializeMailboxManager() throws Exception;

    @Test(expected = IllegalArgumentException.class)
    public void searchShouldThrowWhenSessionIsNull() throws MailboxException {
        SearchQuery searchQuery = new SearchQuery();
        MailboxSession session = null;
        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .isEmpty();
    }

    @Test
    public void searchShouldReturnEmptyWhenUserDontMatch() throws MailboxException {
        Assume.assumeTrue(storeMailboxManager.getSupportedSearchCapabilities().contains(MailboxManager.SearchCapabilities.MultimailboxSearch));
        MailboxSession otherUserSession = storeMailboxManager.createSystemSession("otherUser", LOGGER);
        SearchQuery searchQuery = new SearchQuery();
        assertThat(messageSearchIndex.search(otherUserSession, mailbox, searchQuery))
            .isEmpty();
    }

    @Test
    public void emptySearchQueryShouldReturnAllUids() throws MailboxException {
        SearchQuery searchQuery = new SearchQuery();
        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L);
    }

    @Test
    public void allShouldReturnAllUids() throws MailboxException {
        SearchQuery searchQuery = new SearchQuery();
        searchQuery.andCriteria(SearchQuery.all());
        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L);
    }

    @Test
    public void bodyContainsShouldReturnUidOfMessageContainingTheGivenText() throws MailboxException {
        /*
        Only mail4.eml contains word MAILET-94
         */
        SearchQuery searchQuery = new SearchQuery();
        searchQuery.andCriteria(SearchQuery.bodyContains("MAILET-94"));
        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(5L);
    }

    @Test
    public void bodyContainsShouldReturnUidOfMessageContainingTheApproximativeText() throws MailboxException {
        /*
        mail1.eml contains words created AND summary
        mail.eml contains created and thus matches the query with a low score
         */
        SearchQuery searchQuery = new SearchQuery();
        searchQuery.andCriteria(SearchQuery.bodyContains("created summary"));
        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(2L, 8L);
    }

    @Test
    public void flagIsSetShouldReturnUidOfMessageMarkedAsDeletedWhenUsedWithFlagDeleted() throws MailboxException {
        SearchQuery searchQuery = new SearchQuery();
        searchQuery.andCriteria(SearchQuery.flagIsSet(Flags.Flag.DELETED));
        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(1L);
    }

    @Test
    public void flagIsSetShouldReturnUidOfMessageMarkedAsAnsweredWhenUsedWithFlagAnswered() throws MailboxException {
        SearchQuery searchQuery = new SearchQuery();
        searchQuery.andCriteria(SearchQuery.flagIsSet(Flags.Flag.ANSWERED));
        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(2L);
    }

    @Test
    public void flagIsSetShouldReturnUidOfMessageMarkedAsDraftWhenUsedWithFlagDraft() throws MailboxException {
        SearchQuery searchQuery = new SearchQuery();
        searchQuery.andCriteria(SearchQuery.flagIsSet(Flags.Flag.DRAFT));
        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(3L);
    }

    @Test
    public void flagIsSetShouldReturnUidOfMessageMarkedAsRecentWhenUsedWithFlagRecent() throws MailboxException {
        // Only message 7 is not marked as RECENT
        SearchQuery searchQuery = new SearchQuery();
        searchQuery.andCriteria(SearchQuery.flagIsSet(Flags.Flag.RECENT));
        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(1L, 2L, 3L, 4L, 5L, 6L, 8L, 9L);
    }

    @Test
    public void flagIsSetShouldReturnUidOfMessageMarkedAsFlaggedWhenUsedWithFlagFlagged() throws MailboxException {
        SearchQuery searchQuery = new SearchQuery();
        searchQuery.andCriteria(SearchQuery.flagIsSet(Flags.Flag.FLAGGED));
        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(5L);
    }

    @Test
    public void flagIsSetShouldReturnUidOfMessageMarkedAsSeenWhenUsedWithFlagSeen() throws MailboxException {
        // Only message 6 is marked as read.
        SearchQuery searchQuery = new SearchQuery();
        searchQuery.andCriteria(SearchQuery.flagIsSet(Flags.Flag.SEEN));
        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(6L);
    }
    
    @Test
    public void multimailboxSearchShouldReturnUidOfMessageMarkedAsSeenInAllMailboxes() throws MailboxException {
        SearchQuery searchQuery = new SearchQuery();
        searchQuery.andCriteria(SearchQuery.flagIsSet(Flags.Flag.SEEN));
        Map<MailboxId, Collection<Long>> actual = messageSearchIndex.search(session, MultimailboxesSearchQuery.from(searchQuery).build());
        assertThat(actual.entrySet()).hasSize(2);
        assertThat(actual.get(mailbox.getMailboxId())).containsExactly(6L);
        assertThat(actual.get(mailbox2.getMailboxId())).containsExactly(1L);
    }

    @Test
    public void multimailboxSearchShouldReturnUidOfMessageMarkedAsSeenInOneMailbox() throws MailboxException {
        SearchQuery searchQuery = new SearchQuery();
        searchQuery.andCriteria(SearchQuery.flagIsSet(Flags.Flag.SEEN));
        MultimailboxesSearchQuery query = 
                MultimailboxesSearchQuery
                    .from(searchQuery)
                    .inMailboxes(mailbox.getMailboxId())
                    .build();
        Map<MailboxId, Collection<Long>> actual = messageSearchIndex.search(session, query);
        assertThat(actual.entrySet()).hasSize(1);
        assertThat(actual.get(mailbox.getMailboxId())).containsExactly(6L);
    }

    @Test
    public void multimailboxSearchShouldReturnUidOfMessageWithExpectedFromInTwoMailboxes() throws MailboxException {
        SearchQuery searchQuery = new SearchQuery();
        searchQuery.andCriteria(SearchQuery.address(AddressType.From, "murari"));
        MultimailboxesSearchQuery query = 
                MultimailboxesSearchQuery
                    .from(searchQuery)
                    .inMailboxes(mailbox.getMailboxId(), mailbox2.getMailboxId())
                    .build();
        Map<MailboxId, Collection<Long>> actual = messageSearchIndex.search(session, query);
        assertThat(actual.entrySet()).hasSize(2);
        assertThat(actual.get(mailbox.getMailboxId())).containsExactly(8L);
        assertThat(actual.get(mailbox2.getMailboxId())).containsExactly(1L);
    }

    @Test
    public void multimailboxSearchShouldReturnUidOfMessageWithExpectedFromInAllMailboxes() throws MailboxException {
        SearchQuery searchQuery = new SearchQuery();
        searchQuery.andCriteria(SearchQuery.address(AddressType.From, "murari"));
        MultimailboxesSearchQuery query = 
                MultimailboxesSearchQuery
                    .from(searchQuery)
                    .build();
        Map<MailboxId, Collection<Long>> actual = messageSearchIndex.search(session, query);
        assertThat(actual.entrySet()).hasSize(2);
        assertThat(actual.get(mailbox.getMailboxId())).containsExactly(8L);
        assertThat(actual.get(mailbox2.getMailboxId())).containsExactly(1L);
    }

    @Test
    public void multimailboxSearchShouldReturnUidOfMessageMarkedAsSeenInTwoMailboxes() throws MailboxException {
        SearchQuery searchQuery = new SearchQuery();
        searchQuery.andCriteria(SearchQuery.flagIsSet(Flags.Flag.SEEN));
        MultimailboxesSearchQuery query = 
                MultimailboxesSearchQuery
                    .from(searchQuery)
                    .inMailboxes(mailbox.getMailboxId(), mailbox2.getMailboxId())
                    .build();
        Map<MailboxId, Collection<Long>> actual = messageSearchIndex.search(session, query);
        assertThat(actual.entrySet()).hasSize(2);
        assertThat(actual.get(mailbox.getMailboxId())).containsExactly(6L);
        assertThat(actual.get(mailbox2.getMailboxId())).containsExactly(1L);
    }
    
    @Test
    public void flagIsSetShouldReturnUidsOfMessageContainingAGivenUserFlag() throws MailboxException {
        SearchQuery searchQuery = new SearchQuery();
        searchQuery.andCriteria(SearchQuery.flagIsSet("Hello"));
        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(8L);
    }

    @Test
    public void userFlagsShouldBeMatchedExactly() throws MailboxException {
        SearchQuery searchQuery = new SearchQuery();
        searchQuery.andCriteria(SearchQuery.flagIsSet("Hello bonjour"));
        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .isEmpty();
    }

    @Test
    public void flagIsUnSetShouldReturnUidOfMessageNotMarkedAsDeletedWhenUsedWithFlagDeleted() throws MailboxException {
        SearchQuery searchQuery = new SearchQuery();
        searchQuery.andCriteria(SearchQuery.flagIsUnSet(Flags.Flag.DELETED));
        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L);
    }

    @Test
    public void flagIsUnSetShouldReturnUidOfMessageNotMarkedAsAnsweredWhenUsedWithFlagAnswered() throws MailboxException {
        SearchQuery searchQuery = new SearchQuery();
        searchQuery.andCriteria(SearchQuery.flagIsUnSet(Flags.Flag.ANSWERED));
        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(1L, 3L, 4L, 5L, 6L, 7L, 8L, 9L);
    }

    @Test
    public void flagIsUnSetShouldReturnUidOfMessageNotMarkedAsDraftWhenUsedWithFlagDraft() throws MailboxException {
        SearchQuery searchQuery = new SearchQuery();
        searchQuery.andCriteria(SearchQuery.flagIsUnSet(Flags.Flag.DRAFT));
        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(1L, 2L, 4L, 5L, 6L, 7L, 8L, 9L);
    }

    @Test
    public void flagIsUnSetShouldReturnUidOfMessageNotMarkedAsRecentWhenUsedWithFlagRecent() throws MailboxException {
        // Only message 7 is not marked as RECENT
        SearchQuery searchQuery = new SearchQuery();
        searchQuery.andCriteria(SearchQuery.flagIsUnSet(Flags.Flag.RECENT));
        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(7L);
    }

    @Test
    public void flagIsUnSetShouldReturnUidOfMessageNotMarkedAsFlaggedWhenUsedWithFlagFlagged() throws MailboxException {
        SearchQuery searchQuery = new SearchQuery();
        searchQuery.andCriteria(SearchQuery.flagIsUnSet(Flags.Flag.FLAGGED));
        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(1L, 2L, 3L, 4L, 6L, 7L, 8L, 9L);
    }

    @Test
    public void flagIsUnSetShouldReturnUidOfMessageNotMarkedAsSeendWhenUsedWithFlagSeen() throws MailboxException {
        // Only message 6 is marked as read.
        SearchQuery searchQuery = new SearchQuery();
        searchQuery.andCriteria(SearchQuery.flagIsUnSet(Flags.Flag.SEEN));
        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(1L, 2L, 3L, 4L, 5L, 7L, 8L, 9L);
    }

    @Test
    public void flagIsUnSetShouldReturnUidsOfMessageNotContainingAGivenUserFlag() throws MailboxException {
        SearchQuery searchQuery = new SearchQuery();
        searchQuery.andCriteria(SearchQuery.flagIsUnSet("Hello"));
        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(1L, 2L, 3L, 4L, 5L, 6L, 7L,  9L);
    }

    @Test
    public void internalDateAfterShouldReturnMessagesAfterAGivenDate() throws Exception {
        SearchQuery searchQuery = new SearchQuery();
        // Date : 2014/07/02 00:00:00.000 ( Paris time zone )
        searchQuery.andCriteria(SearchQuery.internalDateAfter(new Date(1404252000000L), SearchQuery.DateResolution.Day));
        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(7L, 8L, 9L);
    }

    @Test
    public void internalDateBeforeShouldReturnMessagesBeforeAGivenDate() throws Exception {
        SearchQuery searchQuery = new SearchQuery();
        // Date : 2014/02/02 00:00:00.000 ( Paris time zone )
        searchQuery.andCriteria(SearchQuery.internalDateBefore(new Date(1391295600000L), SearchQuery.DateResolution.Day));
        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(1L, 2L);
    }

    @Test
    public void internalDateOnShouldReturnMessagesOfTheGivenDate() throws Exception {
        SearchQuery searchQuery = new SearchQuery();
        // Date : 2014/03/02 00:00:00.000 ( Paris time zone )
        searchQuery.andCriteria(SearchQuery.internalDateOn(new Date(1393714800000L), SearchQuery.DateResolution.Day));
        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(3L);
    }

    @Test
    public void modSeqEqualsShouldReturnUidsOfMessageHavingAGivenModSeq() throws Exception {
        SearchQuery searchQuery = new SearchQuery();
        searchQuery.andCriteria(SearchQuery.modSeqEquals(2L));
        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(2L);
    }

    @Test
    public void modSeqGreaterThanShouldReturnUidsOfMessageHavingAGreaterModSeq() throws Exception {
        SearchQuery searchQuery = new SearchQuery();
        searchQuery.andCriteria(SearchQuery.modSeqGreaterThan(7L));
        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(7L, 8L, 9L);
    }

    @Test
    public void modSeqLessThanShouldReturnUidsOfMessageHavingAGreaterModSeq() throws Exception {
        SearchQuery searchQuery = new SearchQuery();
        searchQuery.andCriteria(SearchQuery.modSeqLessThan(3L));
        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(1L, 2L, 3L);
    }

    @Test
    public void sizeGreaterThanShouldReturnUidsOfMessageExceedingTheSpecifiedSize() throws Exception {
        // Only message 7 is over 10 KB
        SearchQuery searchQuery = new SearchQuery();
        searchQuery.andCriteria(SearchQuery.sizeGreaterThan(10000L));
        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(7L);
    }

    @Test
    public void sizeLessThanShouldReturnUidsOfMessageNotExceedingTheSpecifiedSize() throws Exception {
        // Only message 2 3 4 5 9 are under 5 KB
        SearchQuery searchQuery = new SearchQuery();
        searchQuery.andCriteria(SearchQuery.sizeLessThan(5000L));
        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(2L, 3L, 4L, 5L, 9L);
    }

    @Test
    public void headerContainsShouldReturnUidsOfMessageHavingThisHeaderWithTheSpecifiedValue() throws Exception {
        SearchQuery searchQuery = new SearchQuery();
        searchQuery.andCriteria(SearchQuery.headerContains("Precedence", "list"));
        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(1L, 6L, 8L, 9L);
    }

    @Test
    public void headerExistsShouldReturnUidsOfMessageHavingThisHeader() throws Exception {
        SearchQuery searchQuery = new SearchQuery();
        searchQuery.andCriteria(SearchQuery.headerExists("Precedence"));
        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(1L, 2L, 3L, 4L, 5L, 6L, 8L, 9L);
    }

    @Test
    public void addressShouldReturnUidHavingRightExpeditorWhenFromIsSpecified() throws Exception {
        SearchQuery searchQuery = new SearchQuery();
        searchQuery.andCriteria(SearchQuery.address(SearchQuery.AddressType.From, "murari.ksr@gmail.com"));
        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(8L);
    }

    @Test
    public void addressShouldReturnUidHavingRightRecipientWhenToIsSpecified() throws Exception {
        SearchQuery searchQuery = new SearchQuery();
        searchQuery.andCriteria(SearchQuery.address(SearchQuery.AddressType.To, "root@listes.minet.net"));
        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(1L);
    }

    @Test
    public void addressShouldReturnUidHavingRightRecipientWhenCcIsSpecified() throws Exception {
        SearchQuery searchQuery = new SearchQuery();
        searchQuery.andCriteria(SearchQuery.address(SearchQuery.AddressType.Cc, "any@any.com"));
        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(5L);
    }

    @Test
    public void addressShouldReturnUidHavingRightRecipientWhenBccIsSpecified() throws Exception {
        SearchQuery searchQuery = new SearchQuery();
        searchQuery.andCriteria(SearchQuery.address(SearchQuery.AddressType.Bcc, "no@no.com"));
        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(9L);
    }

    @Test
    public void uidShouldreturnExistingUidsOnTheGivenRanges() throws Exception {
        SearchQuery searchQuery = new SearchQuery();
        SearchQuery.NumericRange[] numericRanges = {new SearchQuery.NumericRange(2L, 4L), new SearchQuery.NumericRange(6L, 7L)};
        searchQuery.andCriteria(SearchQuery.uid(numericRanges));
        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(2L, 3L, 4L, 6L, 7L);
    }

    @Test
    public void uidShouldreturnEveryThing() throws Exception {
        SearchQuery searchQuery = new SearchQuery();
        SearchQuery.NumericRange[] numericRanges = {};
        searchQuery.andCriteria(SearchQuery.uid(numericRanges));
        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L);
    }

    @Test
    public void youShouldBeAbleToSpecifySeveralCriterionOnASingleQuery() throws Exception {
        SearchQuery searchQuery = new SearchQuery();
        searchQuery.andCriteria(SearchQuery.headerExists("Precedence"));
        searchQuery.andCriteria(SearchQuery.modSeqGreaterThan(6L));
        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(6L, 8L, 9L);
    }

    @Test
    public void andShouldReturnResultsMatchingBothRequests() throws Exception {
        SearchQuery searchQuery = new SearchQuery();
        searchQuery.andCriteria(
            SearchQuery.and(
                SearchQuery.headerExists("Precedence"),
                SearchQuery.modSeqGreaterThan(6L)));
        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(6L, 8L, 9L);
    }

    @Test
    public void orShouldReturnResultsMatchinganyRequests() throws Exception {
        SearchQuery.NumericRange[] numericRanges = {new SearchQuery.NumericRange(2L, 4L)};
        SearchQuery searchQuery = new SearchQuery();
        searchQuery.andCriteria(
            SearchQuery.or(
                SearchQuery.uid(numericRanges),
                SearchQuery.modSeqGreaterThan(6L)));
        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(2L, 3L, 4L, 6L, 7L, 8L, 9L);
    }

    @Test
    public void notShouldReturnResultsThatDoNotMatchAQuery() throws Exception {
        SearchQuery searchQuery = new SearchQuery();
        searchQuery.andCriteria(
            SearchQuery.not(SearchQuery.headerExists("Precedence")));
        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(7L);
    }

    @Test
    public void sortShouldOrderMessages() throws Exception {
        SearchQuery searchQuery = new SearchQuery();
        searchQuery.andCriteria(SearchQuery.all());
        searchQuery.setSorts(Lists.newArrayList(new SearchQuery.Sort(SearchQuery.Sort.SortClause.Arrival)));
        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsExactly(1L, 2L, 3L, 5L, 4L, 6L, 7L, 8L, 9L);
    }

    @Test
    public void revertSortingShouldReturnElementsInAReversedOrder() throws Exception {
        SearchQuery searchQuery = new SearchQuery();
        searchQuery.andCriteria(SearchQuery.all());
        searchQuery.setSorts(Lists.newArrayList(new SearchQuery.Sort(SearchQuery.Sort.SortClause.Arrival, true)));
        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsExactly(9L, 8L, 7L, 6L, 4L, 5L, 3L, 2L, 1L);
    }

    @Test
    public void headerDateAfterShouldWork() throws Exception {
        SearchQuery searchQuery = new SearchQuery();
        // Date : 2015/06/04 11:00:00.000 ( Paris time zone )
        searchQuery.andCriteria(SearchQuery.headerDateAfter("sentDate", new Date(1433408400000L), SearchQuery.DateResolution.Second));
        searchQuery.setSorts(Lists.newArrayList(new SearchQuery.Sort(SearchQuery.Sort.SortClause.Arrival, true)));
        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(3L, 2L);
    }

    @Test
    public void headerDateBeforeShouldWork() throws Exception {
        SearchQuery searchQuery = new SearchQuery();
        // Date : 2015/06/01 00:00:00.000 ( Paris time zone )
        searchQuery.andCriteria(SearchQuery.headerDateBefore("sentDate", new Date(1433109600000L), SearchQuery.DateResolution.Day));
        searchQuery.setSorts(Lists.newArrayList(new SearchQuery.Sort(SearchQuery.Sort.SortClause.Arrival, true)));
        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(5L);
    }

    @Test
    public void headerDateOnShouldWork() throws Exception {
        SearchQuery searchQuery = new SearchQuery();
        // Date : 2015/06/02 08:00:00.000 ( Paris time zone )
        searchQuery.andCriteria(SearchQuery.headerDateOn("sentDate", new Date(1433224800000L), SearchQuery.DateResolution.Day));
        searchQuery.setSorts(Lists.newArrayList(new SearchQuery.Sort(SearchQuery.Sort.SortClause.Arrival, true)));
        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(4L, 9L);
    }

    @Test
    public void mailsContainsShouldIncludeMailHavingAttachmentsMatchingTheRequest() throws Exception {
        SearchQuery searchQuery = new SearchQuery();
        searchQuery.andCriteria(SearchQuery.mailContains("root mailing list"));
        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(1L, 6L);
    }

    @Test
    public void sortOnCcShouldWork() throws Exception {
        SearchQuery searchQuery = new SearchQuery();
        SearchQuery.NumericRange[] numericRanges = {new SearchQuery.NumericRange(2L, 5L)};
        searchQuery.andCriteria(SearchQuery.uid(numericRanges));
        searchQuery.setSorts(Lists.newArrayList(new SearchQuery.Sort(SearchQuery.Sort.SortClause.MailboxCc)));
        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsExactly(3L, 5L, 4L, 2L);
        // 2 : No cc
        // 3 : Cc : abc@abc.org
        // 4 : zzz@bcd.org
        // 5 : any@any.com
    }

    @Test
    public void sortOnFromShouldWork() throws Exception {
        SearchQuery searchQuery = new SearchQuery();
        SearchQuery.NumericRange[] numericRanges = {new SearchQuery.NumericRange(2L, 5L)};
        searchQuery.andCriteria(SearchQuery.uid(numericRanges));
        searchQuery.setSorts(Lists.newArrayList(new SearchQuery.Sort(SearchQuery.Sort.SortClause.MailboxFrom)));
        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsExactly(3L, 2L, 4L, 5L);
        // 2 : jira2@apache.org
        // 3 : jira1@apache.org
        // 4 : jira@apache.org
        // 5 : mailet-api@james.apache.org
    }

    @Test
    public void sortOnToShouldWork() throws Exception {
        SearchQuery searchQuery = new SearchQuery();
        SearchQuery.NumericRange[] numericRanges = {new SearchQuery.NumericRange(2L, 5L)};
        searchQuery.andCriteria(SearchQuery.uid(numericRanges));
        searchQuery.setSorts(Lists.newArrayList(new SearchQuery.Sort(SearchQuery.Sort.SortClause.MailboxTo)));
        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsExactly(5L, 2L, 3L, 4L);
        // 2 : server-dev@james.apache.org
        // 3 : server-dev@james.apache.org
        // 4 : server-dev@james.apache.org
        // 5 : mailet-api@james.apache.org
    }

    @Test
    public void sortOnSubjectShouldWork() throws Exception {
        SearchQuery searchQuery = new SearchQuery();
        SearchQuery.NumericRange[] numericRanges = {new SearchQuery.NumericRange(2L, 5L)};
        searchQuery.andCriteria(SearchQuery.uid(numericRanges));
        searchQuery.setSorts(Lists.newArrayList(new SearchQuery.Sort(SearchQuery.Sort.SortClause.BaseSubject)));
        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsExactly(4L, 3L, 2L, 5L);
        // 2 : [jira] [Created] (MAILBOX-234) Convert Message into JSON
        // 3 : [jira] [Closed] (MAILBOX-217) We should index attachment in elastic search
        // 4 : [jira] [Closed] (MAILBOX-11) MailboxQuery ignore namespace
        // 5 : [jira] [Resolved] (MAILET-94) James Mailet should use latest version of other James subprojects
    }

    @Test
    public void sortOnSizeShouldWork() throws Exception {
        SearchQuery searchQuery = new SearchQuery();
        SearchQuery.NumericRange[] numericRanges = {new SearchQuery.NumericRange(2L, 5L)};
        searchQuery.andCriteria(SearchQuery.uid(numericRanges));
        searchQuery.setSorts(Lists.newArrayList(new SearchQuery.Sort(SearchQuery.Sort.SortClause.Size)));
        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsExactly(2L, 3L, 5L, 4L);
        // 2 : 3210 o
        // 3 : 3647 o
        // 4 : 4360 o
        // 5 : 3653 o
    }

    @Test
    public void sortOnDisplayFromShouldWork() throws Exception {
        SearchQuery searchQuery = new SearchQuery();
        SearchQuery.NumericRange[] numericRanges = {new SearchQuery.NumericRange(2L, 5L)};
        searchQuery.andCriteria(SearchQuery.uid(numericRanges));
        searchQuery.setSorts(Lists.newArrayList(new SearchQuery.Sort(SearchQuery.Sort.SortClause.DisplayFrom)));
        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsExactly(4L, 3L, 5L, 2L);
        // 2 : Tellier Benoit (JIRA)
        // 3 : efij
        // 4 : abcd
        // 5 : Eric Charles (JIRA)
    }

    @Test
    public void sortOnDisplayToShouldWork() throws Exception {
        SearchQuery searchQuery = new SearchQuery();
        SearchQuery.NumericRange[] numericRanges = {new SearchQuery.NumericRange(2L, 5L)};
        searchQuery.andCriteria(SearchQuery.uid(numericRanges));
        searchQuery.setSorts(Lists.newArrayList(new SearchQuery.Sort(SearchQuery.Sort.SortClause.DisplayTo)));
        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsExactly(3L, 2L, 4L, 5L);
        // 2 : abc
        // 3 : aaa
        // 4 : server
        // 5 : zzz
    }

    @Test
    public void sortOnSentDateShouldWork() throws Exception {
        SearchQuery searchQuery = new SearchQuery();
        SearchQuery.NumericRange[] numericRanges = {new SearchQuery.NumericRange(2L, 5L)};
        searchQuery.andCriteria(SearchQuery.uid(numericRanges));
        searchQuery.setSorts(Lists.newArrayList(new SearchQuery.Sort(SearchQuery.Sort.SortClause.SentDate)));
        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsExactly(5L, 4L, 2L, 3L);
        // 2 : 4 Jun 2015 09:23:37
        // 3 : 4 Jun 2015 09:27:37
        // 4 : 2 Jun 2015 08:16:19
        // 5 : 15 May 2015 06:35:59
    }

    @Test
    public void sortOnIdShouldWork() throws Exception {
        SearchQuery searchQuery = new SearchQuery();
        SearchQuery.NumericRange[] numericRanges = {new SearchQuery.NumericRange(2L, 5L)};
        searchQuery.andCriteria(SearchQuery.uid(numericRanges));
        searchQuery.setSorts(Lists.newArrayList(new SearchQuery.Sort(SearchQuery.Sort.SortClause.Uid)));
        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsExactly(2L, 3L, 4L, 5L);
    }

    @Test
    public void searchWithFullTextShouldReturnNoMailWhenNotMatching() throws Exception {
        Assume.assumeTrue(storeMailboxManager.getSupportedSearchCapabilities().contains(MailboxManager.SearchCapabilities.Text));
        SearchQuery searchQuery = new SearchQuery();
        searchQuery.andCriteria(SearchQuery.textContains("unmatching"));
        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .isEmpty();
    }

    @Test
    public void searchWithFullTextShouldReturnMailsWhenFromMatches() throws Exception {
        Assume.assumeTrue(storeMailboxManager.getSupportedSearchCapabilities().contains(MailboxManager.SearchCapabilities.Text));
        SearchQuery searchQuery = new SearchQuery();
        searchQuery.andCriteria(SearchQuery.textContains("spam.minet.net"));
        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsExactly(1l);
    }

    @Test
    public void searchWithFullTextShouldReturnMailsWhenToMatches() throws Exception {
        Assume.assumeTrue(storeMailboxManager.getSupportedSearchCapabilities().contains(MailboxManager.SearchCapabilities.Text));
        SearchQuery searchQuery = new SearchQuery();
        searchQuery.andCriteria(SearchQuery.textContains("listes.minet.net"));
        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsExactly(1l);
    }

    @Test
    public void searchWithFullTextShouldReturnMailsWhenCcMatches() throws Exception {
        Assume.assumeTrue(storeMailboxManager.getSupportedSearchCapabilities().contains(MailboxManager.SearchCapabilities.Text));
        SearchQuery searchQuery = new SearchQuery();
        searchQuery.andCriteria(SearchQuery.textContains("abc.org"));
        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsExactly(3l);
    }

    @Test
    public void searchWithFullTextShouldReturnMailsWhenBccMatches() throws Exception {
        Assume.assumeTrue(storeMailboxManager.getSupportedSearchCapabilities().contains(MailboxManager.SearchCapabilities.Text));
        SearchQuery searchQuery = new SearchQuery();
        searchQuery.andCriteria(SearchQuery.textContains("any.com"));
        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsExactly(5l);
    }

    @Test
    public void searchWithFullTextShouldReturnMailsWhenTextBodyMatches() throws Exception {
        Assume.assumeTrue(storeMailboxManager.getSupportedSearchCapabilities().contains(MailboxManager.SearchCapabilities.Text));
        SearchQuery searchQuery = new SearchQuery();
        searchQuery.andCriteria(SearchQuery.textContains("reviewing work"));
        // text/plain contains: "We are reviewing work I did for this feature."
        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsExactly(3l);
    }

    @Test
    public void searchWithFullTextShouldReturnMailsWhenTextBodyMatchesAndNonContinuousWords() throws Exception {
        Assume.assumeTrue(storeMailboxManager.getSupportedSearchCapabilities().contains(MailboxManager.SearchCapabilities.Text));
        SearchQuery searchQuery = new SearchQuery();
        // 2: text/plain contains: "Issue Type: New Feature"
        // 3: text/plain contains: "We are reviewing work I did for this feature."
        searchQuery.andCriteria(SearchQuery.textContains("reviewing feature"));
        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsExactly(2l, 3l);
    }
}
