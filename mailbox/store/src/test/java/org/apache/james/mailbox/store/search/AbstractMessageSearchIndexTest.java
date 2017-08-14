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

import java.util.Date;
import java.util.List;

import javax.mail.Flags;

import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MultimailboxesSearchQuery;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.model.SearchQuery.AddressType;
import org.apache.james.mailbox.model.SearchQuery.DateResolution;
import org.apache.james.mailbox.model.SearchQuery.Sort;
import org.apache.james.mailbox.model.SearchQuery.Sort.Order;
import org.apache.james.mailbox.model.SearchQuery.Sort.SortClause;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.mailbox.store.StoreMessageManager;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableList;

public abstract class AbstractMessageSearchIndexTest {

    protected static final String INBOX = "INBOX";
    protected static final String OTHERUSER = "otheruser";
    protected static final String USERNAME = "benwa";

    public static final long LIMIT = 100L;
    public static final boolean RECENT = true;
    public static final boolean NOT_RECENT = false;

    protected MessageSearchIndex messageSearchIndex;
    protected StoreMailboxManager storeMailboxManager;
    protected MessageIdManager messageIdManager;
    private Mailbox mailbox;
    private Mailbox mailbox2;
    private Mailbox otherMailbox;
    private MailboxSession session;
    private MailboxSession otherSession;

    private ComposedMessageId m1;
    private ComposedMessageId m2;
    private ComposedMessageId m3;
    private ComposedMessageId m4;
    private ComposedMessageId m5;
    private ComposedMessageId m6;
    private ComposedMessageId m7;
    private ComposedMessageId m8;
    private ComposedMessageId m9;
    private ComposedMessageId mOther;
    private ComposedMessageId mailWithAttachment;
    private ComposedMessageId mailWithInlinedAttachment;
    private ComposedMessageId m10;
    private StoreMessageManager myFolderMessageManager;


    @Before
    public void setUp() throws Exception {
        initializeMailboxManager();

        session = storeMailboxManager.createSystemSession(USERNAME);
        otherSession = storeMailboxManager.createSystemSession(OTHERUSER);

        MailboxPath inboxPath = new MailboxPath(MailboxConstants.USER_NAMESPACE, USERNAME, INBOX);
        MailboxPath otherInboxPath = new MailboxPath(MailboxConstants.USER_NAMESPACE, OTHERUSER, INBOX);

        storeMailboxManager.createMailbox(inboxPath, session);
        storeMailboxManager.createMailbox(otherInboxPath, otherSession);

        StoreMessageManager inboxMessageManager = (StoreMessageManager) storeMailboxManager.getMailbox(inboxPath, session);
        StoreMessageManager otherInboxMessageManager = (StoreMessageManager) storeMailboxManager.getMailbox(otherInboxPath, otherSession);

        MailboxPath myFolderPath = new MailboxPath(MailboxConstants.USER_NAMESPACE, USERNAME, "MyFolder");
        storeMailboxManager.createMailbox(myFolderPath, session);
        myFolderMessageManager = (StoreMessageManager) storeMailboxManager.getMailbox(myFolderPath, session);
        mailbox = inboxMessageManager.getMailboxEntity();
        mailbox2 = myFolderMessageManager.getMailboxEntity();
        otherMailbox = otherInboxMessageManager.getMailboxEntity();

        m1 = inboxMessageManager.appendMessage(
            ClassLoader.getSystemResourceAsStream("eml/spamMail.eml"),
            new Date(1388617200000L),
            session,
            RECENT,
            new Flags(Flags.Flag.DELETED));
        // sentDate: Thu, 4 Jun 2015 09:23:37 +0000
        // Internal date : 2014/02/02 00:00:00.000
        m2 = inboxMessageManager.appendMessage(
            ClassLoader.getSystemResourceAsStream("eml/mail1.eml"),
            new Date(1391295600000L),
            session,
            RECENT,
            new Flags(Flags.Flag.ANSWERED));
        // sentDate: Thu, 4 Jun 2015 09:27:37 +0000
        // Internal date : 2014/03/02 00:00:00.000
        m3 = inboxMessageManager.appendMessage(
            ClassLoader.getSystemResourceAsStream("eml/mail2.eml"),
            new Date(1393714800000L),
            session,
            RECENT,
            new Flags(Flags.Flag.DRAFT));
        // sentDate: Tue, 2 Jun 2015 08:16:19 +0000
        // Internal date : 2014/05/02 00:00:00.000
        m4 = inboxMessageManager.appendMessage(
            ClassLoader.getSystemResourceAsStream("eml/mail3.eml"),
            new Date(1398981600000L),
            session,
            RECENT,
            new Flags(Flags.Flag.RECENT));
        // sentDate: Fri, 15 May 2015 06:35:59 +0000
        // Internal date : 2014/04/02 00:00:00.000
        m5 = inboxMessageManager.appendMessage(
            ClassLoader.getSystemResourceAsStream("eml/mail4.eml"),
            new Date(1396389600000L),
            session,
            RECENT,
            new Flags(Flags.Flag.FLAGGED));
        // sentDate: Wed, 03 Jun 2015 19:14:32 +0000
        // Internal date : 2014/06/02 00:00:00.000
        m6 = inboxMessageManager.appendMessage(
            ClassLoader.getSystemResourceAsStream("eml/pgpSignedMail.eml"),
            new Date(1401660000000L),
            session,
            RECENT,
            new Flags(Flags.Flag.SEEN));
        // sentDate: Thu, 04 Jun 2015 07:36:08 +0000
        // Internal date : 2014/07/02 00:00:00.000
        m7 = inboxMessageManager.appendMessage(
            ClassLoader.getSystemResourceAsStream("eml/htmlMail.eml"),
            new Date(1404252000000L),
            session,
            NOT_RECENT,
            new Flags());
        // sentDate: Thu, 4 Jun 2015 06:08:41 +0200
        // Internal date : 2014/08/02 00:00:00.000
        m8 = inboxMessageManager.appendMessage(
            ClassLoader.getSystemResourceAsStream("eml/mail.eml"),
            new Date(1406930400000L),
            session,
            RECENT,
            new Flags("Hello"));
        // sentDate: Thu, 4 Jun 2015 06:08:41 +0200
        // Internal date : 2014/08/02 00:00:00.000
        mOther = myFolderMessageManager.appendMessage(
            ClassLoader.getSystemResourceAsStream("eml/mail.eml"),
            new Date(1406930400000L),
            session,
            RECENT,
            new Flags(Flags.Flag.SEEN));
        m9 = inboxMessageManager.appendMessage(
            ClassLoader.getSystemResourceAsStream("eml/frnog.eml"),
            new Date(1409608800000L),
            session,
            RECENT,
            new Flags("Hello you"));

        mailWithAttachment = myFolderMessageManager.appendMessage(
            ClassLoader.getSystemResourceAsStream("eml/oneAttachmentAndSomeTextInlined.eml"),
            new Date(1409608900000L),
            session,
            RECENT,
            new Flags("Hello you"));

        mailWithInlinedAttachment = myFolderMessageManager.appendMessage(
            ClassLoader.getSystemResourceAsStream("eml/oneInlinedAttachment.eml"),
            new Date(1409608900000L),
            session,
            RECENT,
            new Flags("Hello you"));

        m10 = otherInboxMessageManager.appendMessage(
            ClassLoader.getSystemResourceAsStream("eml/mail1.eml"),
            new Date(1391295600000L),
            otherSession,
            RECENT,
            new Flags());
        await();
    }

    protected abstract void await();
    protected abstract void initializeMailboxManager() throws Exception;

    @Test
    public void searchingMessageInMultipleMailboxShouldNotReturnTwiceTheSameMessage() throws MailboxException {
        Assume.assumeTrue(messageIdManager != null);

        messageIdManager.setInMailboxes(m4.getMessageId(),
            ImmutableList.of(mailbox.getMailboxId(), mailbox2.getMailboxId()),
            session);

        await();

        SearchQuery searchQuery = new SearchQuery();

        assertThat(messageSearchIndex.search(session,
            MultimailboxesSearchQuery.from(searchQuery)
                .inMailboxes(mailbox.getMailboxId(), mailbox2.getMailboxId())
                .build(), 20))
            .hasSize(12)
            .containsOnly(m1.getMessageId(),
                m2.getMessageId(),
                m3.getMessageId(),
                m4.getMessageId(),
                m5.getMessageId(),
                m6.getMessageId(),
                m7.getMessageId(),
                m8.getMessageId(),
                m9.getMessageId(),
                mOther.getMessageId(),
                mailWithAttachment.getMessageId(),
                mailWithInlinedAttachment.getMessageId());
    }

    @Test
    public void searchingMessageInMultipleMailboxShouldUnionOfTheTwoMailbox() throws MailboxException {
        Assume.assumeTrue(messageIdManager != null);
        messageIdManager.setInMailboxes(m4.getMessageId(),
            ImmutableList.of(mailbox2.getMailboxId()),
            session);

        await();

        SearchQuery searchQuery = new SearchQuery();

        assertThat(messageSearchIndex.search(session,
            MultimailboxesSearchQuery.from(searchQuery)
                .inMailboxes(mailbox.getMailboxId(), mailbox2.getMailboxId())
                .build(), 20))
            .containsOnly(m1.getMessageId(),
                m2.getMessageId(),
                m3.getMessageId(),
                m4.getMessageId(),
                m5.getMessageId(),
                m6.getMessageId(),
                m7.getMessageId(),
                m8.getMessageId(),
                m9.getMessageId(),
                mOther.getMessageId(),
                mailWithAttachment.getMessageId(),
                mailWithInlinedAttachment.getMessageId());
    }

    @Test
    public void searchingMessageInMultipleMailboxShouldNotReturnLessMessageThanLimitArgument() throws MailboxException {
        Assume.assumeTrue(messageIdManager != null);
        messageIdManager.setInMailboxes(m1.getMessageId(), ImmutableList.of(mailbox.getMailboxId(), mailbox2.getMailboxId()), session);
        messageIdManager.setInMailboxes(m2.getMessageId(), ImmutableList.of(mailbox.getMailboxId(), mailbox2.getMailboxId()), session);
        messageIdManager.setInMailboxes(m3.getMessageId(), ImmutableList.of(mailbox.getMailboxId(), mailbox2.getMailboxId()), session);
        messageIdManager.setInMailboxes(m4.getMessageId(), ImmutableList.of(mailbox.getMailboxId(), mailbox2.getMailboxId()), session);
        messageIdManager.setInMailboxes(m5.getMessageId(), ImmutableList.of(mailbox.getMailboxId(), mailbox2.getMailboxId()), session);
        messageIdManager.setInMailboxes(m6.getMessageId(), ImmutableList.of(mailbox.getMailboxId(), mailbox2.getMailboxId()), session);
        messageIdManager.setInMailboxes(m7.getMessageId(), ImmutableList.of(mailbox.getMailboxId(), mailbox2.getMailboxId()), session);

        await();

        SearchQuery searchQuery = new SearchQuery();

        assertThat(messageSearchIndex.search(session,
            MultimailboxesSearchQuery.from(searchQuery)
                .inMailboxes(mailbox2.getMailboxId(), mailbox.getMailboxId())
                .build(), 10))
            .hasSize(10);
    }

    @Test
    public void searchingMessageInMultipleMailboxShouldNotReturnLessMessageThanLimitArgumentEvenIfDuplicatedMessageAreBeforeLegitimeMessage() throws MailboxException {
        Assume.assumeTrue(messageIdManager != null);
        messageIdManager.setInMailboxes(m1.getMessageId(), ImmutableList.of(mailbox.getMailboxId(), mailbox2.getMailboxId()), session);

        SearchQuery searchQuery = new SearchQuery();

        myFolderMessageManager.appendMessage(
                ClassLoader.getSystemResourceAsStream("eml/mail.eml"),
                new Date(1406930400000L),
                session,
                true,
                new Flags(Flags.Flag.SEEN));

        await();

        assertThat(messageSearchIndex.search(session,
                MultimailboxesSearchQuery.from(searchQuery)
                        .inMailboxes(mailbox2.getMailboxId(), mailbox.getMailboxId())
                        .build(), 13))
                .hasSize(13);
    }

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
        MailboxSession otherUserSession = storeMailboxManager.createSystemSession("otherUser");
        SearchQuery searchQuery = new SearchQuery();
        assertThat(messageSearchIndex.search(otherUserSession, mailbox, searchQuery))
            .isEmpty();
    }

    @Test
    public void emptySearchQueryShouldReturnAllUids() throws MailboxException {
        SearchQuery searchQuery = new SearchQuery();
        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(m1.getUid(), m2.getUid(), m3.getUid(), m4.getUid(), m5.getUid(), m6.getUid(), m7.getUid(), m8.getUid(), m9.getUid());
    }

    @Test
    public void allShouldReturnAllUids() throws MailboxException {
        SearchQuery searchQuery = new SearchQuery(SearchQuery.all());

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(m1.getUid(), m2.getUid(), m3.getUid(), m4.getUid(), m5.getUid(), m6.getUid(), m7.getUid(), m8.getUid(), m9.getUid());
    }

    @Test
    public void bodyContainsShouldReturnUidOfMessageContainingTheGivenText() throws MailboxException {
        /* Only mail4.eml contains word MAILET-94 */
        SearchQuery searchQuery = new SearchQuery(SearchQuery.bodyContains("MAILET-94"));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(m5.getUid());
    }

    @Test
    public void bodyContainsShouldReturnUidOfMessageContainingTheApproximativeText() throws MailboxException {
        /* mail1.eml contains words created AND summary
           mail.eml contains created and thus matches the query with a low score */
        SearchQuery searchQuery = new SearchQuery(SearchQuery.bodyContains("created summary"));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(m2.getUid(), m8.getUid());
    }

    @Test
    public void hasAttachmentShouldOnlyReturnMessageThatHasAttachmentWhichAreNotInline() throws MailboxException {
        SearchQuery searchQuery = new SearchQuery(SearchQuery.hasAttachment());

        assertThat(messageSearchIndex.search(session, mailbox2, searchQuery))
            .containsOnly(mailWithAttachment.getUid());
    }

    @Test
    public void hasNoAttachmenShouldOnlyReturnMessageThatHasNoAttachmentWhichAreNotInline() throws MailboxException {
        SearchQuery searchQuery = new SearchQuery(SearchQuery.hasNoAttachment());

        assertThat(messageSearchIndex.search(session, mailbox2, searchQuery))
            .containsOnly(mOther.getUid(), mailWithInlinedAttachment.getUid());
    }

    @Test
    public void flagIsSetShouldReturnUidOfMessageMarkedAsDeletedWhenUsedWithFlagDeleted() throws MailboxException {
        SearchQuery searchQuery = new SearchQuery(SearchQuery.flagIsSet(Flags.Flag.DELETED));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(m1.getUid());
    }

    @Test
    public void flagIsSetShouldReturnUidOfMessageMarkedAsAnsweredWhenUsedWithFlagAnswered() throws MailboxException {
        SearchQuery searchQuery = new SearchQuery(SearchQuery.flagIsSet(Flags.Flag.ANSWERED));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(m2.getUid());
    }

    @Test
    public void flagIsSetShouldReturnUidOfMessageMarkedAsDraftWhenUsedWithFlagDraft() throws MailboxException {
        SearchQuery searchQuery = new SearchQuery(SearchQuery.flagIsSet(Flags.Flag.DRAFT));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(m3.getUid());
    }

    @Test
    public void flagIsSetShouldReturnUidOfMessageMarkedAsRecentWhenUsedWithFlagRecent() throws MailboxException {
        // Only message 7 is not marked as RECENT
        SearchQuery searchQuery = new SearchQuery(SearchQuery.flagIsSet(Flags.Flag.RECENT));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(m1.getUid(), m2.getUid(), m3.getUid(), m4.getUid(), m5.getUid(), m6.getUid(), m8.getUid(), m9.getUid());
    }

    @Test
    public void flagIsSetShouldReturnUidOfMessageMarkedAsFlaggedWhenUsedWithFlagFlagged() throws MailboxException {
        SearchQuery searchQuery = new SearchQuery(SearchQuery.flagIsSet(Flags.Flag.FLAGGED));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(m5.getUid());
    }

    @Test
    public void flagIsSetShouldReturnUidOfMessageMarkedAsSeenWhenUsedWithFlagSeen() throws MailboxException {
        // Only message 6 is marked as read.
        SearchQuery searchQuery = new SearchQuery(SearchQuery.flagIsSet(Flags.Flag.SEEN));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(m6.getUid());
    }
    
    @Test
    public void multimailboxSearchShouldReturnUidOfMessageMarkedAsSeenInAllMailboxes() throws MailboxException {
        SearchQuery searchQuery = new SearchQuery(SearchQuery.flagIsSet(Flags.Flag.SEEN));

        List<MessageId> actual = messageSearchIndex.search(session, MultimailboxesSearchQuery.from(searchQuery).build(), LIMIT);

        assertThat(actual).containsOnly(mOther.getMessageId(), m6.getMessageId());
    }

    @Test
    public void multimailboxSearchShouldReturnUidOfMessageMarkedAsSeenInOneMailbox() throws MailboxException {
        SearchQuery searchQuery = new SearchQuery(SearchQuery.flagIsSet(Flags.Flag.SEEN));
        MultimailboxesSearchQuery query = 
                MultimailboxesSearchQuery
                    .from(searchQuery)
                    .inMailboxes(mailbox.getMailboxId())
                    .build();

        List<MessageId> actual = messageSearchIndex.search(session, query, LIMIT);

        assertThat(actual).containsOnly(m6.getMessageId());
    }

    @Test
    public void multimailboxSearchShouldReturnUidOfMessageWithExpectedFromInTwoMailboxes() throws MailboxException {
        SearchQuery searchQuery = new SearchQuery(SearchQuery.address(AddressType.From, "murari"));
        MultimailboxesSearchQuery query = 
                MultimailboxesSearchQuery
                    .from(searchQuery)
                    .inMailboxes(mailbox.getMailboxId(), mailbox2.getMailboxId())
                    .build();
        List<MessageId> actual = messageSearchIndex.search(session, query, LIMIT);

        assertThat(actual).containsOnly(mOther.getMessageId(), m8.getMessageId());
    }

    @Test
    public void multimailboxSearchShouldReturnUidOfMessageWithExpectedFromInAllMailboxes() throws MailboxException {
        SearchQuery searchQuery = new SearchQuery(SearchQuery.address(AddressType.From, "murari"));
        MultimailboxesSearchQuery query = 
                MultimailboxesSearchQuery
                    .from(searchQuery)
                    .build();

        List<MessageId> actual = messageSearchIndex.search(session, query, LIMIT);

        assertThat(actual).containsOnly(mOther.getMessageId(), m8.getMessageId());
    }

    @Test
    public void multimailboxSearchShouldReturnUidOfMessageMarkedAsSeenInTwoMailboxes() throws MailboxException {
        SearchQuery searchQuery = new SearchQuery(SearchQuery.flagIsSet(Flags.Flag.SEEN));
        MultimailboxesSearchQuery query = 
                MultimailboxesSearchQuery
                    .from(searchQuery)
                    .inMailboxes(mailbox.getMailboxId(), mailbox2.getMailboxId())
                    .build();

        List<MessageId> actual = messageSearchIndex.search(session, query, LIMIT);

        assertThat(actual).containsOnly(mOther.getMessageId(), m6.getMessageId());
    }

    @Test
    public void multimailboxSearchShouldLimitTheSize() throws MailboxException {
        SearchQuery searchQuery = new SearchQuery(SearchQuery.flagIsSet(Flags.Flag.SEEN));
        MultimailboxesSearchQuery query =
            MultimailboxesSearchQuery
                .from(searchQuery)
                .inMailboxes(mailbox.getMailboxId(), mailbox2.getMailboxId())
                .build();

        long limit = 1;
        List<MessageId> actual = messageSearchIndex.search(session, query, limit);
        // Two messages matches this query : mOther and m6

        assertThat(actual).hasSize(1);
    }


    @Test
    public void flagIsSetShouldReturnUidsOfMessageContainingAGivenUserFlag() throws MailboxException {
        SearchQuery searchQuery = new SearchQuery(SearchQuery.flagIsSet("Hello"));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(m8.getUid());
    }

    @Test
    public void userFlagsShouldBeMatchedExactly() throws MailboxException {
        SearchQuery searchQuery = new SearchQuery(SearchQuery.flagIsSet("Hello bonjour"));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .isEmpty();
    }

    @Test
    public void flagIsUnSetShouldReturnUidOfMessageNotMarkedAsDeletedWhenUsedWithFlagDeleted() throws MailboxException {
        SearchQuery searchQuery = new SearchQuery(SearchQuery.flagIsUnSet(Flags.Flag.DELETED));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(m2.getUid(), m3.getUid(), m4.getUid(), m5.getUid(), m6.getUid(), m7.getUid(), m8.getUid(), m9.getUid());
    }

    @Test
    public void flagIsUnSetShouldReturnUidOfMessageNotMarkedAsAnsweredWhenUsedWithFlagAnswered() throws MailboxException {
        SearchQuery searchQuery = new SearchQuery(SearchQuery.flagIsUnSet(Flags.Flag.ANSWERED));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(m1.getUid(), m3.getUid(), m4.getUid(), m5.getUid(), m6.getUid(), m7.getUid(), m8.getUid(), m9.getUid());
    }

    @Test
    public void flagIsUnSetShouldReturnUidOfMessageNotMarkedAsDraftWhenUsedWithFlagDraft() throws MailboxException {
        SearchQuery searchQuery = new SearchQuery(SearchQuery.flagIsUnSet(Flags.Flag.DRAFT));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(m1.getUid(), m2.getUid(), m4.getUid(), m5.getUid(), m6.getUid(), m7.getUid(), m8.getUid(), m9.getUid());
    }

    @Test
    public void flagIsUnSetShouldReturnUidOfMessageNotMarkedAsRecentWhenUsedWithFlagRecent() throws MailboxException {
        // Only message 7 is not marked as RECENT
        SearchQuery searchQuery = new SearchQuery(SearchQuery.flagIsUnSet(Flags.Flag.RECENT));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(m7.getUid());
    }

    @Test
    public void flagIsUnSetShouldReturnUidOfMessageNotMarkedAsFlaggedWhenUsedWithFlagFlagged() throws MailboxException {
        SearchQuery searchQuery = new SearchQuery(SearchQuery.flagIsUnSet(Flags.Flag.FLAGGED));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(m1.getUid(), m2.getUid(), m3.getUid(), m4.getUid(), m6.getUid(), m7.getUid(), m8.getUid(), m9.getUid());
    }

    @Test
    public void flagIsUnSetShouldReturnUidOfMessageNotMarkedAsSeendWhenUsedWithFlagSeen() throws MailboxException {
        // Only message 6 is marked as read.
        SearchQuery searchQuery = new SearchQuery(SearchQuery.flagIsUnSet(Flags.Flag.SEEN));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(m1.getUid(), m2.getUid(), m3.getUid(), m4.getUid(), m5.getUid(), m7.getUid(), m8.getUid(), m9.getUid());
    }

    @Test
    public void flagIsUnSetShouldReturnUidsOfMessageNotContainingAGivenUserFlag() throws MailboxException {
        SearchQuery searchQuery = new SearchQuery(SearchQuery.flagIsUnSet("Hello"));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(m1.getUid(), m2.getUid(), m3.getUid(), m4.getUid(), m5.getUid(), m6.getUid(), m7.getUid(),  m9.getUid());
    }

    @Test
    public void internalDateAfterShouldReturnMessagesAfterAGivenDate() throws Exception {
        SearchQuery searchQuery = new SearchQuery(SearchQuery.internalDateAfter(
            new Date(1404252000000L),
            DateResolution.Day));
        // Date : 2014/07/02 00:00:00.000 ( Paris time zone )

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(m7.getUid(), m8.getUid(), m9.getUid());
    }

    @Test
    public void internalDateBeforeShouldReturnMessagesBeforeAGivenDate() throws Exception {
        SearchQuery searchQuery = new SearchQuery(SearchQuery.internalDateBefore(
            new Date(1391295600000L),
            DateResolution.Day));
        // Date : 2014/02/02 00:00:00.000 ( Paris time zone )

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(m1.getUid(), m2.getUid());
    }

    @Test
    public void internalDateOnShouldReturnMessagesOfTheGivenDate() throws Exception {
        SearchQuery searchQuery = new SearchQuery(SearchQuery.internalDateOn(
            new Date(1393714800000L),
            DateResolution.Day));
        // Date : 2014/03/02 00:00:00.000 ( Paris time zone )

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(m3.getUid());
    }

    @Test
    public void sentDateAfterShouldReturnMessagesAfterAGivenDate() throws Exception {
        SearchQuery searchQuery = new SearchQuery(SearchQuery.sentDateAfter(
            new Date(1433408400000L),
            DateResolution.Second));
        // Date : 2015/06/04 11:00:00.000 ( Paris time zone )
        searchQuery.setSorts(ImmutableList.of(new Sort(SortClause.Arrival, Order.REVERSE)));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(m3.getUid(), m2.getUid());
    }

    @Test
    public void sentDateBeforeShouldReturnMessagesBeforeAGivenDate() throws Exception {
        SearchQuery searchQuery = new SearchQuery(SearchQuery.sentDateBefore(
            new Date(1433109600000L),
            DateResolution.Day));
        // Date : 2015/06/01 00:00:00.000 ( Paris time zone )
        searchQuery.setSorts(ImmutableList.of(new Sort(SortClause.Arrival, Order.REVERSE)));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(m5.getUid());
    }

    @Test
    public void sentDateOnShouldReturnMessagesOfTheGivenDate() throws Exception {
        SearchQuery searchQuery = new SearchQuery(SearchQuery.sentDateOn(
            new Date(1433224800000L),
            DateResolution.Day));
        // Date : 2015/06/02 08:00:00.000 ( Paris time zone )
        searchQuery.setSorts(ImmutableList.of(new Sort(SortClause.Arrival, Order.REVERSE)));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(m4.getUid(), m9.getUid());
    }

    @Test
    public void modSeqEqualsShouldReturnUidsOfMessageHavingAGivenModSeq() throws Exception {
        SearchQuery searchQuery = new SearchQuery(SearchQuery.modSeqEquals(2L));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(m2.getUid());
    }

    @Test
    public void modSeqGreaterThanShouldReturnUidsOfMessageHavingAGreaterModSeq() throws Exception {
        SearchQuery searchQuery = new SearchQuery(SearchQuery.modSeqGreaterThan(7L));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(m7.getUid(), m8.getUid(), m9.getUid());
    }

    @Test
    public void modSeqLessThanShouldReturnUidsOfMessageHavingAGreaterModSeq() throws Exception {
        SearchQuery searchQuery = new SearchQuery(SearchQuery.modSeqLessThan(3L));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(m1.getUid(), m2.getUid(), m3.getUid());
    }

    @Test
    public void sizeGreaterThanShouldReturnUidsOfMessageExceedingTheSpecifiedSize() throws Exception {
        // Only message 6 is over 6.8 KB
        SearchQuery searchQuery = new SearchQuery(SearchQuery.sizeGreaterThan(6800L));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(m6.getUid());
    }

    @Test
    public void sizeLessThanShouldReturnUidsOfMessageNotExceedingTheSpecifiedSize() throws Exception {
        // Only message 2 3 4 5 7 9 are under 5 KB
        SearchQuery searchQuery = new SearchQuery(SearchQuery.sizeLessThan(5000L));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(m2.getUid(), m3.getUid(), m4.getUid(), m5.getUid(), m7.getUid(), m9.getUid());
    }

    @Test
    public void headerContainsShouldReturnUidsOfMessageHavingThisHeaderWithTheSpecifiedValue() throws Exception {
        SearchQuery searchQuery = new SearchQuery(SearchQuery.headerContains("Precedence", "list"));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(m1.getUid(), m6.getUid(), m8.getUid(), m9.getUid());
    }

    @Test
    public void headerExistsShouldReturnUidsOfMessageHavingThisHeader() throws Exception {
        SearchQuery searchQuery = new SearchQuery(SearchQuery.headerExists("Precedence"));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(m1.getUid(), m2.getUid(), m3.getUid(), m4.getUid(), m5.getUid(), m6.getUid(), m8.getUid(), m9.getUid());
    }

    @Test
    public void addressShouldReturnUidHavingRightExpeditorWhenFromIsSpecified() throws Exception {
        SearchQuery searchQuery = new SearchQuery(SearchQuery.address(AddressType.From, "murari.ksr@gmail.com"));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(m8.getUid());
    }

    @Test
    public void addressShouldReturnUidHavingRightRecipientWhenToIsSpecified() throws Exception {
        SearchQuery searchQuery = new SearchQuery(SearchQuery.address(AddressType.To, "root@listes.minet.net"));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(m1.getUid());
    }

    @Test
    public void addressShouldReturnUidHavingRightRecipientWhenCcIsSpecified() throws Exception {
        SearchQuery searchQuery = new SearchQuery(SearchQuery.address(AddressType.Cc, "any@any.com"));
        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(m5.getUid());
    }

    @Test
    public void addressShouldReturnUidHavingRightRecipientWhenBccIsSpecified() throws Exception {
        SearchQuery searchQuery = new SearchQuery(SearchQuery.address(AddressType.Bcc, "no@no.com"));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(m9.getUid());
    }

    @Test
    public void uidShouldreturnExistingUidsOnTheGivenRanges() throws Exception {
        SearchQuery.UidRange[] numericRanges = {new SearchQuery.UidRange(m2.getUid(), m4.getUid()), new SearchQuery.UidRange(m6.getUid(), m7.getUid())};
        SearchQuery searchQuery = new SearchQuery(SearchQuery.uid(numericRanges));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(m2.getUid(), m3.getUid(), m4.getUid(), m6.getUid(), m7.getUid());
    }

    @Test
    public void uidShouldreturnEveryThing() throws Exception {
        SearchQuery.UidRange[] numericRanges = {};
        SearchQuery searchQuery = new SearchQuery(SearchQuery.uid(numericRanges));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(m1.getUid(), m2.getUid(), m3.getUid(), m4.getUid(), m5.getUid(), m6.getUid(), m7.getUid(), m8.getUid(), m9.getUid());
    }

    @Test
    public void youShouldBeAbleToSpecifySeveralCriterionOnASingleQuery() throws Exception {
        SearchQuery searchQuery = new SearchQuery();
        searchQuery.andCriteria(SearchQuery.headerExists("Precedence"));
        searchQuery.andCriteria(SearchQuery.modSeqGreaterThan(6L));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(m6.getUid(), m8.getUid(), m9.getUid());
    }

    @Test
    public void andShouldReturnResultsMatchingBothRequests() throws Exception {
        SearchQuery searchQuery = new SearchQuery(SearchQuery.and(
            SearchQuery.headerExists("Precedence"),
            SearchQuery.modSeqGreaterThan(6L)));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(m6.getUid(), m8.getUid(), m9.getUid());
    }

    @Test
    public void orShouldReturnResultsMatchinganyRequests() throws Exception {
        SearchQuery.UidRange[] numericRanges = {new SearchQuery.UidRange(m2.getUid(), m4.getUid())};
        SearchQuery searchQuery = new SearchQuery(SearchQuery.or(
            SearchQuery.uid(numericRanges),
            SearchQuery.modSeqGreaterThan(6L)));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(m2.getUid(), m3.getUid(), m4.getUid(), m6.getUid(), m7.getUid(), m8.getUid(), m9.getUid());
    }

    @Test
    public void notShouldReturnResultsThatDoNotMatchAQuery() throws Exception {
        SearchQuery searchQuery = new SearchQuery(
            SearchQuery.not(SearchQuery.headerExists("Precedence")));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(m7.getUid());
    }

    @Test
    public void sortShouldOrderMessages() throws Exception {
        SearchQuery searchQuery = new SearchQuery(SearchQuery.all());
        searchQuery.setSorts(ImmutableList.of(new Sort(SortClause.Arrival)));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsExactly(m1.getUid(), m2.getUid(), m3.getUid(), m5.getUid(), m4.getUid(), m6.getUid(), m7.getUid(), m8.getUid(), m9.getUid());
    }

    @Test
    public void revertSortingShouldReturnElementsInAReversedOrder() throws Exception {
        SearchQuery searchQuery = new SearchQuery(SearchQuery.all());
        searchQuery.setSorts(ImmutableList.of(new Sort(SortClause.Arrival, Order.REVERSE)));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsExactly(m9.getUid(), m8.getUid(), m7.getUid(), m6.getUid(), m4.getUid(), m5.getUid(), m3.getUid(), m2.getUid(), m1.getUid());
    }

    @Test
    public void headerDateAfterShouldWork() throws Exception {
        SearchQuery searchQuery = new SearchQuery(
            SearchQuery.headerDateAfter("sentDate", new Date(1433408400000L), DateResolution.Second));
        // Date : 2015/06/04 11:00:00.000 ( Paris time zone )
        searchQuery.setSorts(ImmutableList.of(new Sort(SortClause.Arrival, Order.REVERSE)));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(m3.getUid(), m2.getUid());
    }

    @Test
    public void headerDateBeforeShouldWork() throws Exception {
        SearchQuery searchQuery = new SearchQuery(
            SearchQuery.headerDateBefore("sentDate", new Date(1433109600000L), DateResolution.Day));
        // Date : 2015/06/01 00:00:00.000 ( Paris time zone )
        searchQuery.setSorts(ImmutableList.of(new Sort(SortClause.Arrival, Order.REVERSE)));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(m5.getUid());
    }

    @Test
    public void headerDateOnShouldWork() throws Exception {
        SearchQuery searchQuery = new SearchQuery(
            SearchQuery.headerDateOn("sentDate", new Date(1433224800000L), DateResolution.Day));
        // Date : 2015/06/02 08:00:00.000 ( Paris time zone )
        searchQuery.setSorts(ImmutableList.of(new Sort(SortClause.Arrival, Order.REVERSE)));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(m4.getUid(), m9.getUid());
    }

    @Test
    public void mailsContainsShouldIncludeMailHavingAttachmentsMatchingTheRequest() throws Exception {
        SearchQuery searchQuery = new SearchQuery(SearchQuery.mailContains("root mailing list"));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(m1.getUid(), m6.getUid());
    }

    @Test
    public void sortOnCcShouldWork() throws Exception {
        SearchQuery.UidRange[] numericRanges = {new SearchQuery.UidRange(m2.getUid(), m5.getUid())};
        SearchQuery searchQuery = new SearchQuery(SearchQuery.uid(numericRanges));
        searchQuery.setSorts(ImmutableList.of(new Sort(SortClause.MailboxCc)));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsExactly(m3.getUid(), m5.getUid(), m4.getUid(), m2.getUid());
        // 2 : No cc
        // 3 : Cc : abc@abc.org
        // 4 : zzz@bcd.org
        // 5 : any@any.com
    }

    @Test
    public void sortOnFromShouldWork() throws Exception {
        SearchQuery.UidRange[] numericRanges = {new SearchQuery.UidRange(m2.getUid(), m5.getUid())};
        SearchQuery searchQuery = new SearchQuery(SearchQuery.uid(numericRanges));
        searchQuery.setSorts(ImmutableList.of(new Sort(SortClause.MailboxFrom)));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsExactly(m3.getUid(), m2.getUid(), m4.getUid(), m5.getUid());
        // 2 : jira2@apache.org
        // 3 : jira1@apache.org
        // 4 : jira@apache.org
        // 5 : mailet-api@james.apache.org
    }

    @Test
    public void sortOnToShouldWork() throws Exception {
        SearchQuery.UidRange[] numericRanges = {new SearchQuery.UidRange(m2.getUid(), m5.getUid())};
        SearchQuery searchQuery = new SearchQuery(SearchQuery.uid(numericRanges));
        searchQuery.setSorts(ImmutableList.of(new Sort(SortClause.MailboxTo)));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsExactly(m5.getUid(), m2.getUid(), m3.getUid(), m4.getUid());
        // 2 : server-dev@james.apache.org
        // 3 : server-dev@james.apache.org
        // 4 : server-dev@james.apache.org
        // 5 : mailet-api@james.apache.org
    }

    @Test
    public void sortOnSubjectShouldWork() throws Exception {
        SearchQuery.UidRange[] numericRanges = {new SearchQuery.UidRange(m2.getUid(), m5.getUid())};
        SearchQuery searchQuery = new SearchQuery(SearchQuery.uid(numericRanges));
        searchQuery.setSorts(ImmutableList.of(new Sort(SortClause.BaseSubject)));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsExactly(m4.getUid(), m3.getUid(), m2.getUid(), m5.getUid());
        // 2 : [jira] [Created] (MAILBOX-234) Convert Message into JSON
        // 3 : [jira] [Closed] (MAILBOX-217) We should index attachment in elastic search
        // 4 : [jira] [Closed] (MAILBOX-11) MailboxQuery ignore namespace
        // 5 : [jira] [Resolved] (MAILET-94) James Mailet should use latest version of other James subprojects
    }

    @Test
    public void sortOnSizeShouldWork() throws Exception {
        SearchQuery.UidRange[] numericRanges = {new SearchQuery.UidRange(m2.getUid(), m5.getUid())};
        SearchQuery searchQuery = new SearchQuery(SearchQuery.uid(numericRanges));
        searchQuery.setSorts(ImmutableList.of(new Sort(SortClause.Size)));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsExactly(m2.getUid(), m3.getUid(), m5.getUid(), m4.getUid());
        // 2 : 3210 o
        // 3 : 3647 o
        // 4 : 4360 o
        // 5 : 3653 o
    }

    @Test
    public void sortOnDisplayFromShouldWork() throws Exception {
        SearchQuery.UidRange[] numericRanges = {new SearchQuery.UidRange(m2.getUid(), m5.getUid())};
        SearchQuery searchQuery = new SearchQuery(SearchQuery.uid(numericRanges));
        searchQuery.setSorts(ImmutableList.of(new Sort(SortClause.DisplayFrom)));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsExactly(m4.getUid(), m3.getUid(), m5.getUid(), m2.getUid());
        // 2 : Tellier Benoit (JIRA)
        // 3 : efij
        // 4 : abcd
        // 5 : Eric Charles (JIRA)
    }

    @Test
    public void sortOnDisplayToShouldWork() throws Exception {
        SearchQuery.UidRange[] numericRanges = {new SearchQuery.UidRange(m2.getUid(), m5.getUid())};
        SearchQuery searchQuery = new SearchQuery(SearchQuery.uid(numericRanges));
        searchQuery.setSorts(ImmutableList.of(new Sort(SortClause.DisplayTo)));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsExactly(m3.getUid(), m2.getUid(), m4.getUid(), m5.getUid());
        // 2 : abc
        // 3 : aaa
        // 4 : server
        // 5 : zzz
    }

    @Test
    public void sortOnSentDateShouldWork() throws Exception {
        SearchQuery.UidRange[] numericRanges = {new SearchQuery.UidRange(m2.getUid(), m5.getUid())};
        SearchQuery searchQuery = new SearchQuery(SearchQuery.uid(numericRanges));
        searchQuery.setSorts(ImmutableList.of(new Sort(SortClause.SentDate)));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsExactly(m5.getUid(), m4.getUid(), m2.getUid(), m3.getUid());
        // 2 : 4 Jun 2015 09:23:37
        // 3 : 4 Jun 2015 09:27:37
        // 4 : 2 Jun 2015 08:16:19
        // 5 : 15 May 2015 06:35:59
    }

    @Test
    public void sortOnIdShouldWork() throws Exception {
        SearchQuery.UidRange[] numericRanges = {new SearchQuery.UidRange(m2.getUid(), m5.getUid())};
        SearchQuery searchQuery = new SearchQuery(SearchQuery.uid(numericRanges));
        searchQuery.setSorts(ImmutableList.of(new Sort(SortClause.Uid)));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsExactly(m2.getUid(), m3.getUid(), m4.getUid(), m5.getUid());
    }

    @Test
    public void searchWithFullTextShouldReturnNoMailWhenNotMatching() throws Exception {
        Assume.assumeTrue(storeMailboxManager.getSupportedSearchCapabilities().contains(MailboxManager.SearchCapabilities.Text));
        SearchQuery searchQuery = new SearchQuery(SearchQuery.textContains("unmatching"));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .isEmpty();
    }

    @Test
    public void searchWithFullTextShouldReturnMailsWhenFromMatches() throws Exception {
        Assume.assumeTrue(storeMailboxManager.getSupportedSearchCapabilities().contains(MailboxManager.SearchCapabilities.Text));
        SearchQuery searchQuery = new SearchQuery(SearchQuery.textContains("spam.minet.net"));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsExactly(m1.getUid());
    }

    @Test
    public void searchWithFullTextShouldReturnMailsWhenToMatches() throws Exception {
        Assume.assumeTrue(storeMailboxManager.getSupportedSearchCapabilities().contains(MailboxManager.SearchCapabilities.Text));
        SearchQuery searchQuery = new SearchQuery(SearchQuery.textContains("listes.minet.net"));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsExactly(m1.getUid());
    }

    @Test
    public void searchWithFullTextShouldReturnMailsWhenCcMatches() throws Exception {
        Assume.assumeTrue(storeMailboxManager.getSupportedSearchCapabilities().contains(MailboxManager.SearchCapabilities.Text));
        SearchQuery searchQuery = new SearchQuery(SearchQuery.textContains("abc.org"));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsExactly(m3.getUid());
    }

    @Test
    public void searchWithFullTextShouldReturnMailsWhenBccMatches() throws Exception {
        Assume.assumeTrue(storeMailboxManager.getSupportedSearchCapabilities().contains(MailboxManager.SearchCapabilities.Text));
        SearchQuery searchQuery = new SearchQuery(SearchQuery.textContains("any.com"));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsExactly(m5.getUid());
    }

    @Test
    public void searchWithFullTextShouldReturnMailsWhenTextBodyMatches() throws Exception {
        Assume.assumeTrue(storeMailboxManager.getSupportedSearchCapabilities().contains(MailboxManager.SearchCapabilities.Text));
        SearchQuery searchQuery = new SearchQuery(SearchQuery.textContains("reviewing work"));

        // text/plain contains: "We are reviewing work I did for this feature."
        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsExactly(m3.getUid());
    }

    @Test
    public void searchWithFullTextShouldReturnMailsWhenTextBodyMatchesAndNonContinuousWords() throws Exception {
        Assume.assumeTrue(storeMailboxManager.getSupportedSearchCapabilities().contains(MailboxManager.SearchCapabilities.Text));
        SearchQuery searchQuery = new SearchQuery(SearchQuery.textContains("reviewing feature"));
        // 2: text/plain contains: "Issue Type: New Feature"
        // 3: text/plain contains: "We are reviewing work I did for this feature."

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsExactly(m2.getUid(), m3.getUid());
    }

    @Test
    public void searchWithFullTextShouldReturnMailsWhenTextBodyMatchesInsensitiveWords() throws Exception {
        Assume.assumeTrue(storeMailboxManager.getSupportedSearchCapabilities().contains(MailboxManager.SearchCapabilities.Text));
        SearchQuery searchQuery = new SearchQuery(SearchQuery.textContains("reVieWing"));
        // text/plain contains: "We are reviewing work I did for this feature."

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsExactly(m3.getUid());
    }

    @Test
    public void searchWithFullTextShouldReturnMailsWhenTextBodyWithExtraUnindexedWords() throws Exception {
        Assume.assumeTrue(storeMailboxManager.getSupportedSearchCapabilities().contains(MailboxManager.SearchCapabilities.Text));
        SearchQuery searchQuery = new SearchQuery(SearchQuery.textContains("a reviewing of the work"));
        // text/plain contains: "We are reviewing work I did for this feature."

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsExactly(m3.getUid());
    }

    @Test
    public void searchWithFullTextShouldReturnMailsWhenHtmlBodyMatches() throws Exception {
        Assume.assumeTrue(storeMailboxManager.getSupportedSearchCapabilities().contains(MailboxManager.SearchCapabilities.Text));
        SearchQuery searchQuery = new SearchQuery(SearchQuery.textContains("contains a banana"));
        // text/html contains: "This is a mail with beautifull html content which contains a banana."

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsExactly(m7.getUid());
    }

    @Test
    public void searchWithFullTextShouldReturnMailsWhenHtmlBodyMatchesWithStemming() throws Exception {
        Assume.assumeTrue(storeMailboxManager.getSupportedSearchCapabilities().contains(MailboxManager.SearchCapabilities.Text));
        SearchQuery searchQuery = new SearchQuery(SearchQuery.textContains("contain banana"));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsExactly(m7.getUid());
    }

    @Test
    public void searchWithFullTextShouldReturnMailsWhenHtmlBodyMatchesAndNonContinuousWords() throws Exception {
        Assume.assumeTrue(storeMailboxManager.getSupportedSearchCapabilities().contains(MailboxManager.SearchCapabilities.Text));
        SearchQuery searchQuery = new SearchQuery(SearchQuery.textContains("beautifull banana"));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsExactly(m7.getUid());
    }

    @Test
    public void sortShouldNotDiscardResultWhenSearchingFieldIsIdentical() throws Exception {
        SearchQuery searchQuery = new SearchQuery(SearchQuery.all());
        searchQuery.setSorts(ImmutableList.of(new Sort(SortClause.Arrival)));

        List<MessageId> actual = messageSearchIndex.search(session, MultimailboxesSearchQuery.from(searchQuery).build(), LIMIT);

        assertThat(actual).containsOnly(m1.getMessageId(), m2.getMessageId(), m3.getMessageId(), m4.getMessageId(), m5.getMessageId(),
            m6.getMessageId(), m7.getMessageId(), m8.getMessageId(), m9.getMessageId(), mOther.getMessageId(), mailWithAttachment.getMessageId(), mailWithInlinedAttachment.getMessageId());
    }

    @Test
    public void searchInMultiMailboxShouldReturnMessagesBelongingToUserSession() throws Exception {
        SearchQuery query = new SearchQuery(SearchQuery.all());

        MultimailboxesSearchQuery multiMailboxesQuery = MultimailboxesSearchQuery.from(query).build();

        assertThat(messageSearchIndex.search(otherSession, multiMailboxesQuery, LIMIT))
            .hasSize(1)
            .containsOnly(m10.getMessageId());
    }

    @Test
    public void searchInMultiMailboxShouldNotReturnMessagesBelongingToAnotherUserSession() throws Exception {
        SearchQuery query = new SearchQuery(SearchQuery.all());

        MultimailboxesSearchQuery multiMailboxesQuery = MultimailboxesSearchQuery.from(query).build();

        assertThat(messageSearchIndex.search(session, multiMailboxesQuery, LIMIT))
            .doesNotContain(m10.getMessageId());
    }

    @Test
    public void searchShouldFilterMailboxBelongingToMailboxSession() throws Exception {
        SearchQuery searchQuery = new SearchQuery();

        assertThat(messageSearchIndex.search(otherSession, otherMailbox, searchQuery)).containsOnly(m10.getUid());
    }

    @Test
    public void searchShouldReturnEmptyWhenMailboxBelongingToAnotherMailboxSession() throws Exception {
        SearchQuery searchQuery = new SearchQuery();

        assertThat(messageSearchIndex.search(otherSession, mailbox, searchQuery)).isEmpty();
    }
}
