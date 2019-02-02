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
 m10 = otherInboxMessageManager.appendMessage(
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

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import javax.mail.Flags;

import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.model.SearchQuery.AddressType;
import org.apache.james.mailbox.model.SearchQuery.DateResolution;
import org.apache.james.mailbox.model.SearchQuery.Sort;
import org.apache.james.mailbox.model.SearchQuery.Sort.Order;
import org.apache.james.mailbox.model.SearchQuery.Sort.SortClause;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.mailbox.store.StoreMessageManager;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.dom.Multipart;
import org.apache.james.mime4j.message.BodyPartBuilder;
import org.apache.james.mime4j.message.MultipartBuilder;
import org.apache.james.mime4j.message.SingleBodyBuilder;
import org.apache.james.util.ClassLoaderUtils;
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
    private MailboxPath inboxPath;
    private MailboxPath otherInboxPath;
    private StoreMessageManager inboxMessageManager;


    @Before
    public void setUp() throws Exception {
        initializeMailboxManager();

        session = storeMailboxManager.createSystemSession(USERNAME);
        otherSession = storeMailboxManager.createSystemSession(OTHERUSER);

        inboxPath = MailboxPath.forUser(USERNAME, INBOX);
        otherInboxPath = MailboxPath.forUser(OTHERUSER, INBOX);

        storeMailboxManager.createMailbox(inboxPath, session);
        storeMailboxManager.createMailbox(otherInboxPath, otherSession);

        inboxMessageManager = (StoreMessageManager) storeMailboxManager.getMailbox(inboxPath, session);
        StoreMessageManager otherInboxMessageManager = (StoreMessageManager) storeMailboxManager.getMailbox(otherInboxPath, otherSession);

        MailboxPath myFolderPath = MailboxPath.forUser(USERNAME, "MyFolder");
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

        List<MessageId> result = messageSearchIndex.search(session,
            ImmutableList.of(mailbox.getMailboxId(), mailbox2.getMailboxId()),
            searchQuery,
            LIMIT);

        assertThat(result)
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

        List<MessageId> result = messageSearchIndex.search(session,
            ImmutableList.of(mailbox.getMailboxId(), mailbox2.getMailboxId()),
            searchQuery,
            LIMIT);

        assertThat(result)
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

        int limit = 10;
        List<MessageId> result = messageSearchIndex.search(session,
            ImmutableList.of(mailbox2.getMailboxId(), mailbox.getMailboxId()),
            searchQuery,
            limit);

        assertThat(result)
            .hasSize(limit);
    }

    @Test
    public void whenEmptyListOfMailboxGivenSearchShouldReturnEmpty() throws MailboxException {
        SearchQuery searchQuery = new SearchQuery();

        List<MessageId> result = messageSearchIndex.search(session,
            ImmutableList.of(),
            searchQuery,
            LIMIT);

        assertThat(result)
            .isEmpty();
    }

    @Test
    public void searchingMessageInMultipleMailboxShouldNotReturnLessMessageThanLimitArgumentEvenIfDuplicatedMessageAreBeforeLegitimeMessage() throws MailboxException {
        Assume.assumeTrue(messageIdManager != null);
        messageIdManager.setInMailboxes(m1.getMessageId(), ImmutableList.of(mailbox.getMailboxId(), mailbox2.getMailboxId()), session);

        SearchQuery searchQuery = new SearchQuery();

        myFolderMessageManager.appendMessage(MessageManager.AppendCommand.builder()
            .withFlags(new Flags(Flags.Flag.SEEN))
            .build(ClassLoader.getSystemResourceAsStream("eml/mail.eml")),
            session);

        await();

        int limit = 13;
        List<MessageId> result = messageSearchIndex.search(session,
            ImmutableList.of(mailbox2.getMailboxId(), mailbox.getMailboxId()),
            searchQuery,
            limit);

        assertThat(result)
                .hasSize(limit);
    }

    @Test(expected = IllegalArgumentException.class)
    public void searchShouldThrowWhenSessionIsNull() throws MailboxException {
        SearchQuery searchQuery = new SearchQuery();
        MailboxSession session = null;
        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
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
    public void messageWithDotsInHeaderShouldBeIndexed() throws MailboxException {

        ComposedMessageId mailWithDotsInHeader = myFolderMessageManager.appendMessage(
            MessageManager.AppendCommand.builder()
                .build(ClassLoader.getSystemResourceAsStream("eml/headerWithDot.eml")),
            session);
        await();
        
        SearchQuery searchQuery = new SearchQuery(SearchQuery.all());

        assertThat(messageSearchIndex.search(session, mailbox2, searchQuery))
            .contains(mailWithDotsInHeader.getUid());
    }

    @Test
    public void searchShouldBeExactOnEmailAddresses() throws MailboxException {
        Assume.assumeTrue(storeMailboxManager.getSupportedSearchCapabilities().contains(MailboxManager.SearchCapabilities.Text));

        ComposedMessageId m11 = inboxMessageManager.appendMessage(
            MessageManager.AppendCommand.builder()
            .build(ClassLoader.getSystemResourceAsStream("eml/mail5.eml")),
            session);

        String emailToSearch = "luc.duzan@james.apache.org";

        await();

        SearchQuery searchQuery = new SearchQuery(SearchQuery.or(ImmutableList.of(
            SearchQuery.address(AddressType.From, emailToSearch),
            SearchQuery.address(AddressType.To, emailToSearch),
            SearchQuery.address(AddressType.Cc, emailToSearch),
            SearchQuery.address(AddressType.Bcc, emailToSearch),
            SearchQuery.headerContains("Subject", emailToSearch),
            SearchQuery.attachmentContains(emailToSearch),
            SearchQuery.bodyContains(emailToSearch))));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(m11.getUid());
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

        List<MessageId> actual = messageSearchIndex.search(
            session,
            ImmutableList.of(mailbox.getMailboxId(), mailbox2.getMailboxId()),
            searchQuery,
            LIMIT);

        assertThat(actual).containsOnly(mOther.getMessageId(), m6.getMessageId());
    }

    @Test
    public void multimailboxSearchShouldReturnUidOfMessageMarkedAsSeenInOneMailbox() throws MailboxException {
        SearchQuery searchQuery = new SearchQuery(SearchQuery.flagIsSet(Flags.Flag.SEEN));

        List<MessageId> actual = messageSearchIndex.search(session, ImmutableList.of(mailbox.getMailboxId()), searchQuery, LIMIT);

        assertThat(actual).containsOnly(m6.getMessageId());
    }

    @Test
    public void multimailboxSearchShouldReturnUidOfMessageWithExpectedFromInTwoMailboxes() throws MailboxException {
        SearchQuery searchQuery = new SearchQuery(SearchQuery.address(AddressType.From, "murari"));

        List<MessageId> actual = messageSearchIndex.search(
            session,
            ImmutableList.of(mailbox.getMailboxId(), mailbox2.getMailboxId()),
            searchQuery,
            LIMIT);

        assertThat(actual).containsOnly(mOther.getMessageId(), m8.getMessageId());
    }

    @Test
    public void multimailboxSearchShouldReturnUidOfMessageMarkedAsSeenInTwoMailboxes() throws MailboxException {
        SearchQuery searchQuery = new SearchQuery(SearchQuery.flagIsSet(Flags.Flag.SEEN));

        List<MessageId> actual = messageSearchIndex.search(
            session,
            ImmutableList.of(mailbox.getMailboxId(), mailbox2.getMailboxId()),
            searchQuery,
            LIMIT);

        assertThat(actual).containsOnly(mOther.getMessageId(), m6.getMessageId());
    }

    @Test
    public void multimailboxSearchShouldLimitTheSize() throws MailboxException {
        SearchQuery searchQuery = new SearchQuery(SearchQuery.flagIsSet(Flags.Flag.SEEN));

        long limit = 1;
        List<MessageId> actual = messageSearchIndex.search(
            session,
            ImmutableList.of(mailbox.getMailboxId(), mailbox2.getMailboxId()),
            searchQuery,
            limit);
        // Two messages matches this query : mOther and m6

        assertThat(actual).hasSize(1);
    }

    @Test
    public void multimailboxSearchShouldWorkWithOtherUserMailbox() throws  MailboxException {
        Assume.assumeTrue(storeMailboxManager.hasCapability(MailboxManager.MailboxCapabilities.ACL));
        SearchQuery searchQuery = new SearchQuery();

        long limit = 256;
        List<MessageId> actual = messageSearchIndex.search(
            session,
            ImmutableList.of(otherMailbox.getMailboxId()),
            searchQuery,
            limit);

        assertThat(actual).contains(m10.getMessageId());
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
    public void flagIsUnSetShouldReturnUidOfMessageNotMarkedAsSeenWhenUsedWithFlagSeen() throws MailboxException {
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
    public void addressShouldReturnUidHavingRightExpeditorWhenFromIsSpecifiedWithOnlyUserPartOfEmail() throws Exception {
        Assume.assumeTrue(storeMailboxManager
            .getSupportedSearchCapabilities()
            .contains(MailboxManager.SearchCapabilities.PartialEmailMatch));

        SearchQuery searchQuery = new SearchQuery(SearchQuery.address(AddressType.From, "murari"));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(m8.getUid());
    }

    @Test
    public void addressShouldReturnUidHavingRightExpeditorWhenFromIsSpecifiedWithDomainPartOfEmail() throws Exception {
        Assume.assumeTrue(storeMailboxManager
            .getSupportedSearchCapabilities()
            .contains(MailboxManager.SearchCapabilities.PartialEmailMatch));

        SearchQuery searchQuery = new SearchQuery(SearchQuery.address(AddressType.From, "gmail.com"));

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
    public void addressShouldReturnUidHavingRightRecipientWhenToIsSpecifiedWithOnlyEmailUserPart() throws Exception {
        Assume.assumeTrue(storeMailboxManager
            .getSupportedSearchCapabilities()
            .contains(MailboxManager.SearchCapabilities.PartialEmailMatch));

        SearchQuery searchQuery = new SearchQuery(SearchQuery.address(AddressType.To, "root"));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(m1.getUid());
    }

    @Test
    public void addressShouldReturnUidHavingRightRecipientWhenToIsSpecifiedWithOnlyDomainPartSpecified() throws Exception {
        Assume.assumeTrue(storeMailboxManager
            .getSupportedSearchCapabilities()
            .contains(MailboxManager.SearchCapabilities.PartialEmailMatch));

        SearchQuery searchQuery = new SearchQuery(SearchQuery.address(AddressType.To, "listes.minet.net"));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(m1.getUid());
    }

    @Test
    public void addressShouldReturnUidHavingRightRecipientWhenCcIsSpecified() throws Exception {
        Assume.assumeTrue(storeMailboxManager
            .getSupportedSearchCapabilities()
            .contains(MailboxManager.SearchCapabilities.PartialEmailMatch));

        SearchQuery searchQuery = new SearchQuery(SearchQuery.address(AddressType.Cc, "monkey@any.com"));
        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(m5.getUid());
    }

    @Test
    public void addressShouldReturnUidHavingRightRecipientWhenCcIsSpecifiedWithOnlyUserPartOfTheEmail() throws Exception {
        Assume.assumeTrue(storeMailboxManager
            .getSupportedSearchCapabilities()
            .contains(MailboxManager.SearchCapabilities.PartialEmailMatch));

        SearchQuery searchQuery = new SearchQuery(SearchQuery.address(AddressType.Cc, "monkey"));
        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(m5.getUid());
    }

    @Test
    public void addressShouldReturnUidHavingRightRecipientWhenCcIsSpecifiedWithOnlyDomainPartOfTheEmail() throws Exception {
        Assume.assumeTrue(storeMailboxManager
            .getSupportedSearchCapabilities()
            .contains(MailboxManager.SearchCapabilities.PartialEmailMatch));

        SearchQuery searchQuery = new SearchQuery(SearchQuery.address(AddressType.Cc, "any.com"));
        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(m5.getUid());
    }

    @Test
    public void addressShouldReturnUidHavingRightRecipientWhenBccIsSpecifiedWithOnlyUserPartOfTheEmail() throws Exception {
        Assume.assumeTrue(storeMailboxManager
            .getSupportedSearchCapabilities()
            .contains(MailboxManager.SearchCapabilities.PartialEmailMatch));

        SearchQuery searchQuery = new SearchQuery(SearchQuery.address(AddressType.Bcc, "monkey"));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(m5.getUid());
    }

    @Test
    public void addressShouldReturnUidHavingRightRecipientWhenBccIsSpecifiedWithOnlyDomainPartOfTheEmail() throws Exception {
        Assume.assumeTrue(storeMailboxManager
            .getSupportedSearchCapabilities()
            .contains(MailboxManager.SearchCapabilities.PartialEmailMatch));

        SearchQuery searchQuery = new SearchQuery(SearchQuery.address(AddressType.Bcc, "any.com"));
        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(m5.getUid());
    }

    @Test
    public void addressShouldReturnUidHavingRightRecipientWhenBccIsSpecified() throws Exception {
        Assume.assumeTrue(storeMailboxManager
            .getSupportedSearchCapabilities()
            .contains(MailboxManager.SearchCapabilities.PartialEmailMatch));

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
        // 5 : monkey@any.com
    }

    @Test
    public void sortOnFromShouldWork() throws Exception {
        SearchQuery.UidRange[] numericRanges = {new SearchQuery.UidRange(m2.getUid(), m5.getUid())};
        SearchQuery searchQuery = new SearchQuery(SearchQuery.uid(numericRanges));
        searchQuery.setSorts(ImmutableList.of(new Sort(SortClause.MailboxFrom)));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsExactly(m3.getUid(), m2.getUid(), m4.getUid(), m5.getUid());
        // m3 : jira1@apache.org
        // m2 : jira2@apache.org
        // m4 : jira@apache.org
        // m5 : mailet-api@james.apache.org
    }

    @Test
    public void sortOnToShouldWork() throws Exception {
        SearchQuery.UidRange[] numericRanges = {new SearchQuery.UidRange(m2.getUid(), m5.getUid())};
        SearchQuery searchQuery = new SearchQuery(SearchQuery.uid(numericRanges));
        searchQuery.setSorts(ImmutableList.of(new Sort(SortClause.MailboxTo)));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsExactly(m5.getUid(), m3.getUid(), m2.getUid(), m4.getUid());
        // 5 : "zzz" <mailet-api@james.apache.org>
        // 3 : "aaa" <server-dev@james.apache.org>
        // 2 : "abc" <server-dev@james.apache.org>
        // 4 : "server" <server-dev@james.apache.org>
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
    public void searchWithTextShouldReturnNoMailWhenNotMatching() throws Exception {
        Assume.assumeTrue(storeMailboxManager.getSupportedSearchCapabilities().contains(MailboxManager.SearchCapabilities.Text));
        SearchQuery searchQuery = new SearchQuery(SearchQuery.textContains("unmatching"));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .isEmpty();
    }

    @Test
    public void searchWithTextShouldReturnMailsWhenFromMatches() throws Exception {
        Assume.assumeTrue(storeMailboxManager.getSupportedSearchCapabilities().contains(MailboxManager.SearchCapabilities.Text));
        SearchQuery searchQuery = new SearchQuery(SearchQuery.textContains("spam.minet.net"));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsExactly(m1.getUid());
    }

    @Test
    public void searchWithTextShouldReturnMailsWhenMatches() throws Exception {
        Assume.assumeTrue(storeMailboxManager.getSupportedSearchCapabilities().contains(MailboxManager.SearchCapabilities.Text));
        SearchQuery searchQuery = new SearchQuery(SearchQuery.textContains("root@listes.minet.net"));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsExactly(m1.getUid());
    }

    @Test
    public void searchWithTextShouldReturnMailsWhenToIsNotAnExactMatches() throws Exception {
        Assume.assumeTrue(storeMailboxManager.getSupportedSearchCapabilities().contains(MailboxManager.SearchCapabilities.Text));
        SearchQuery searchQuery = new SearchQuery(SearchQuery.textContains("root"));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsExactly(m1.getUid());
    }

    @Test
    public void searchWithTextShouldReturnMailsWhenCcMatches() throws Exception {
        Assume.assumeTrue(storeMailboxManager.getSupportedSearchCapabilities().contains(MailboxManager.SearchCapabilities.Text));
        SearchQuery searchQuery = new SearchQuery(SearchQuery.textContains("abc@abc.org"));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsExactly(m3.getUid());
    }

    @Test
    public void searchWithTextShouldReturnMailsWhenCcIsNotAExactMatch() throws Exception {
        Assume.assumeTrue(storeMailboxManager.getSupportedSearchCapabilities().contains(MailboxManager.SearchCapabilities.Text));
        SearchQuery searchQuery = new SearchQuery(SearchQuery.textContains("monkey"));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsExactly(m5.getUid());
    }

    @Test
    public void searchWithTextShouldReturnMailsWhenBccMatches() throws Exception {
        Assume.assumeTrue(storeMailboxManager.getSupportedSearchCapabilities().contains(MailboxManager.SearchCapabilities.Text));
        SearchQuery searchQuery = new SearchQuery(SearchQuery.textContains("monkey@any.com"));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsExactly(m5.getUid());
    }

    @Test
    public void searchWithTextShouldReturnMailsWhenBccIsNotAExactMatch() throws Exception {
        Assume.assumeTrue(storeMailboxManager.getSupportedSearchCapabilities().contains(MailboxManager.SearchCapabilities.Text));
        SearchQuery searchQuery = new SearchQuery(SearchQuery.textContains("monkey"));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsExactly(m5.getUid());
    }

    @Test
    public void searchWithTextShouldReturnMailsWhenTextBodyMatches() throws Exception {
        Assume.assumeTrue(storeMailboxManager.getSupportedSearchCapabilities().contains(MailboxManager.SearchCapabilities.Text));
        SearchQuery searchQuery = new SearchQuery(SearchQuery.textContains("reviewing work"));

        // text/plain contains: "We are reviewing work I did for this feature."
        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsExactly(m3.getUid());
    }

    @Test
    public void searchWithTextShouldReturnMailsWhenTextBodyMatchesAndNonContinuousWords() throws Exception {
        Assume.assumeTrue(storeMailboxManager.getSupportedSearchCapabilities().contains(MailboxManager.SearchCapabilities.Text));
        SearchQuery searchQuery = new SearchQuery(SearchQuery.textContains("reviewing feature"));
        // 2: text/plain contains: "Issue Type: New Feature"
        // 3: text/plain contains: "We are reviewing work I did for this feature."

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsExactly(m2.getUid(), m3.getUid());
    }

    @Test
    public void searchWithTextShouldReturnMailsWhenTextBodyMatchesInsensitiveWords() throws Exception {
        Assume.assumeTrue(storeMailboxManager.getSupportedSearchCapabilities().contains(MailboxManager.SearchCapabilities.Text));
        SearchQuery searchQuery = new SearchQuery(SearchQuery.textContains("reVieWing"));
        // text/plain contains: "We are reviewing work I did for this feature."

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsExactly(m3.getUid());
    }

    @Test
    public void searchWithTextShouldReturnMailsWhenTextBodyWithExtraUnindexedWords() throws Exception {
        Assume.assumeTrue(storeMailboxManager.getSupportedSearchCapabilities().contains(MailboxManager.SearchCapabilities.Text));
        SearchQuery searchQuery = new SearchQuery(SearchQuery.textContains("a reviewing of the work"));
        // text/plain contains: "We are reviewing work I did for this feature."

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsExactly(m3.getUid());
    }

    @Test
    public void searchWithTextShouldReturnMailsWhenHtmlBodyMatches() throws Exception {
        Assume.assumeTrue(storeMailboxManager.getSupportedSearchCapabilities().contains(MailboxManager.SearchCapabilities.Text));
        SearchQuery searchQuery = new SearchQuery(SearchQuery.textContains("contains a banana"));
        // text/html contains: "This is a mail with beautifull html content which contains a banana."

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsExactly(m7.getUid());
    }

    @Test
    public void searchWithTextShouldReturnMailsWhenHtmlBodyMatchesWithStemming() throws Exception {
        Assume.assumeTrue(storeMailboxManager.getSupportedSearchCapabilities().contains(MailboxManager.SearchCapabilities.Text));
        SearchQuery searchQuery = new SearchQuery(SearchQuery.textContains("contain banana"));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsExactly(m7.getUid());
    }

    @Test
    public void searchWithTextShouldReturnMailsWhenHtmlBodyMatchesAndNonContinuousWords() throws Exception {
        Assume.assumeTrue(storeMailboxManager.getSupportedSearchCapabilities().contains(MailboxManager.SearchCapabilities.Text));
        SearchQuery searchQuery = new SearchQuery(SearchQuery.textContains("beautifull banana"));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsExactly(m7.getUid());
    }

    @Test
    public void searchWithFullTextShouldReturnMailsWhenNotAPerfectMatch() throws Exception {
        Assume.assumeTrue(storeMailboxManager.getSupportedSearchCapabilities().contains(MailboxManager.SearchCapabilities.FullText));
        ComposedMessageId messageWithBeautifulBananaAsTextAttachment = myFolderMessageManager.appendMessage(
            MessageManager.AppendCommand.builder()
            .build(ClassLoader.getSystemResourceAsStream("eml/emailWithTextAttachment.eml")),
            session);
        await();

        SearchQuery searchQuery = new SearchQuery(SearchQuery.mailContains("User message banana"));

        assertThat(messageSearchIndex.search(session, mailbox2, searchQuery))
            .containsExactly(messageWithBeautifulBananaAsTextAttachment.getUid());
    }

    @Test
    public void searchWithTextAttachmentShouldReturnMailsWhenAttachmentContentMatches() throws Exception {
        Assume.assumeTrue(storeMailboxManager.getSupportedSearchCapabilities().contains(MailboxManager.SearchCapabilities.Attachment));
        ComposedMessageId messageWithBeautifulBananaAsTextAttachment = myFolderMessageManager.appendMessage(
            MessageManager.AppendCommand.builder()
                .build(ClassLoader.getSystemResourceAsStream("eml/emailWithTextAttachment.eml")),
            session);
        await();

        SearchQuery searchQuery = new SearchQuery(SearchQuery.attachmentContains("beautiful banana"));

        assertThat(messageSearchIndex.search(session, mailbox2, searchQuery))
            .containsExactly(messageWithBeautifulBananaAsTextAttachment.getUid());
    }

    @Test
    public void searchWithPDFAttachmentShouldReturnMailsWhenAttachmentContentMatches() throws Exception {
        Assume.assumeTrue(storeMailboxManager.getSupportedSearchCapabilities().contains(MailboxManager.SearchCapabilities.Attachment));
        byte[] attachmentContent = ClassLoaderUtils.getSystemResourceAsByteArray("eml/attachment.pdf");
        Multipart multipart = MultipartBuilder.create("mixed")
                .addBodyPart(BodyPartBuilder.create()
                    .setBody(attachmentContent, "application/pdf")
                    .setContentDisposition("attachment"))
                .addBodyPart(BodyPartBuilder.create()
                    .setBody("The message has a PDF attachment.", "plain", StandardCharsets.UTF_8))
                .build();
        Message message = Message.Builder.of()
                .setBody(multipart)
                .build();
        ComposedMessageId messageWithBeautifulBananaAsPDFAttachment = myFolderMessageManager
            .appendMessage(MessageManager.AppendCommand.from(message), session);
        await();

        SearchQuery searchQuery = new SearchQuery(SearchQuery.attachmentContains("beautiful banana"));

        assertThat(messageSearchIndex.search(session, mailbox2, searchQuery))
            .containsExactly(messageWithBeautifulBananaAsPDFAttachment.getUid());
    }

    @Test
    public void sortShouldNotDiscardResultWhenSearchingFieldIsIdentical() throws Exception {
        SearchQuery searchQuery = new SearchQuery(SearchQuery.all());
        searchQuery.setSorts(ImmutableList.of(new Sort(SortClause.Arrival)));

        List<MessageId> actual = messageSearchIndex.search(
            session,
            ImmutableList.of(mailbox.getMailboxId(), mailbox2.getMailboxId()),
            searchQuery,
            LIMIT);

        assertThat(actual).containsOnly(m1.getMessageId(), m2.getMessageId(), m3.getMessageId(), m4.getMessageId(), m5.getMessageId(),
            m6.getMessageId(), m7.getMessageId(), m8.getMessageId(), m9.getMessageId(), mOther.getMessageId(), mailWithAttachment.getMessageId(), mailWithInlinedAttachment.getMessageId());
    }

    @Test
    public void searchShouldOrderByInternalDateWhenSortOnSentDateAndNoCorrespondingHeader() throws Exception {
        MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, "sentDate");
        storeMailboxManager.createMailbox(mailboxPath, session);

        MessageManager messageManager = storeMailboxManager.getMailbox(mailboxPath, session);
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
        Date date1 = simpleDateFormat.parse("2017-08-24");
        Date date2 = simpleDateFormat.parse("2017-08-23");
        Date date3 = simpleDateFormat.parse("2017-08-25");
        ComposedMessageId message1 = messageManager.appendMessage(MessageManager.AppendCommand.builder()
            .withInternalDate(date1)
            .build(Message.Builder.of()
                .setSubject("test")
                .setBody("testmail", StandardCharsets.UTF_8)),
            session);
        ComposedMessageId message2 = messageManager.appendMessage(MessageManager.AppendCommand.builder()
            .withInternalDate(date2)
            .build(Message.Builder.of()
                .setSubject("test")
                .setBody("testmail", StandardCharsets.UTF_8)),
            session);
        ComposedMessageId message3 = messageManager.appendMessage(MessageManager.AppendCommand.builder()
            .withInternalDate(date3)
            .build(Message.Builder.of()
                .setSubject("test")
                .setBody("testmail", StandardCharsets.UTF_8)),
            session);

        await();

        SearchQuery searchQuery = new SearchQuery();
        searchQuery.setSorts(ImmutableList.of(new Sort(SortClause.SentDate)));

        assertThat(messageManager.search(searchQuery, session))
            .containsExactly(message2.getUid(),
                message1.getUid(),
                message3.getUid());
    }

    @Test
    public void searchShouldOrderBySentDateThenInternalDateWhenSortOnSentDateAndNonHomogeneousCorrespondingHeader() throws Exception {
        MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, "sentDate");
        storeMailboxManager.createMailbox(mailboxPath, session);

        MessageManager messageManager = storeMailboxManager.getMailbox(mailboxPath, session);
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
        Date date1 = simpleDateFormat.parse("2017-08-24");
        Date date2 = simpleDateFormat.parse("2017-08-26");
        Date date3 = simpleDateFormat.parse("2017-08-25");
        ComposedMessageId message1 = messageManager.appendMessage(MessageManager.AppendCommand.builder()
            .withInternalDate(date1)
            .build(Message.Builder.of()
                .setSubject("test")
                .setBody("testmail", StandardCharsets.UTF_8)), session);
        ComposedMessageId message2 = messageManager.appendMessage(MessageManager.AppendCommand.builder()
            .withInternalDate(date2)
            .build(Message.Builder.of()
                .setSubject("test")
                .setDate(new SimpleDateFormat("yyyy/MM/dd HH:mm:ss")
                    .parse("2017/08/23 00:00:00 "), TimeZone.getTimeZone(ZoneId.of("+0200")))
                .setBody("testmail", StandardCharsets.UTF_8)),
            session);
        ComposedMessageId message3 = messageManager.appendMessage(MessageManager.AppendCommand.builder()
            .withInternalDate(date3)
            .build(Message.Builder.of()
                .setSubject("test")
                .setBody("testmail", StandardCharsets.UTF_8)), session);

        await();

        SearchQuery searchQuery = new SearchQuery();
        searchQuery.setSorts(ImmutableList.of(new Sort(SortClause.SentDate)));

        assertThat(messageManager.search(searchQuery, session))
            .containsExactly(message2.getUid(),
                message1.getUid(),
                message3.getUid());
    }

    @Test
    public void searchShouldRetrieveExactlyMatchingMimeMessageID() throws Exception {
        SearchQuery searchQuery = new SearchQuery();
        searchQuery.andCriteria(SearchQuery.mimeMessageID("<JIRA.12781874.1426269127000.9353.1433410057953@Atlassian.JIRA>"));
        // Correspond to mail.eml

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(m3.getUid());
    }

    @Test
    public void searchShouldRetrieveMailByAttachmentFileName() throws Exception {
        Assume.assumeTrue(messageSearchIndex.getSupportedCapabilities(storeMailboxManager.getSupportedMessageCapabilities())
            .contains(MailboxManager.SearchCapabilities.AttachmentFileName));

        String fileName = "matchme.txt";
        ComposedMessageId mWithFileName = inboxMessageManager.appendMessage(MessageManager.AppendCommand.builder()
                .build(Message.Builder.of()
                    .setBody(
                        MultipartBuilder.create("alternative")
                            .addBodyPart(BodyPartBuilder.create()
                                .setContentDisposition("attachment", fileName)
                                .setBody(SingleBodyBuilder.create()
                                    .setText("this is the file content...")
                                    .setCharset(StandardCharsets.UTF_8)
                                    .build())
                                .build())
                            .build())),
            session);

        await();

        SearchQuery searchQuery = new SearchQuery(SearchQuery.attachmentFileName(fileName));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery))
            .containsOnly(mWithFileName.getUid());
    }
}
