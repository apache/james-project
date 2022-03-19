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

import static org.apache.james.mailbox.events.MailboxEvents.Added.IS_APPENDED;
import static org.apache.james.mailbox.events.MailboxEvents.Added.IS_DELIVERY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import jakarta.mail.Flags;

import org.apache.james.core.Username;
import org.apache.james.events.EventBus;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.events.MailboxIdRegistrationKey;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.ByteContent;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.model.SearchQuery.AddressType;
import org.apache.james.mailbox.model.SearchQuery.DateResolution;
import org.apache.james.mailbox.model.SearchQuery.Sort;
import org.apache.james.mailbox.model.SearchQuery.Sort.Order;
import org.apache.james.mailbox.model.SearchQuery.Sort.SortClause;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.mailbox.store.StoreMessageManager;
import org.apache.james.mailbox.store.event.EventFactory;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.dom.Multipart;
import org.apache.james.mime4j.message.BodyPartBuilder;
import org.apache.james.mime4j.message.MultipartBuilder;
import org.apache.james.mime4j.message.SingleBodyBuilder;
import org.apache.james.util.ClassLoaderUtils;
import org.apache.james.utils.UpdatableTickingClock;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;

public abstract class AbstractMessageSearchIndexTest {

    private static final ConditionFactory CALMLY_AWAIT = Awaitility
            .with().pollInterval(ONE_HUNDRED_MILLISECONDS)
            .and().pollDelay(ONE_HUNDRED_MILLISECONDS)
            .await();
    private static final long LIMIT = 100L;
    private static final boolean RECENT = true;
    private static final boolean NOT_RECENT = false;

    protected static final String INBOX = "INBOX";
    protected static final Username OTHERUSER = Username.of("otheruser");
    protected static final Username USERNAME = Username.of("benwa");
    protected static final Username QUAN = USERNAME.of("quan");

    protected MessageSearchIndex messageSearchIndex;
    protected StoreMailboxManager storeMailboxManager;
    protected MessageIdManager messageIdManager;
    protected EventBus eventBus;
    protected MessageId.Factory messageIdFactory;
    private Mailbox mailbox;
    private Mailbox mailbox2;
    private Mailbox otherMailbox;
    private Mailbox quanMailbox;
    protected MailboxSession session;
    private MailboxSession otherSession;
    private MailboxSession quanSession;

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
    private MailboxPath quanInboxPath;
    protected StoreMessageManager inboxMessageManager;
    private StoreMessageManager quanInboxMessageManager;
    private MessageMapper messageMapper;
    private MessageId newBasedMessageId;
    private MessageId otherBasedMessageId;
    private UpdatableTickingClock clock;

    @BeforeEach
    protected void setUp() throws Exception {
        initializeMailboxManager();
        clock = (UpdatableTickingClock) storeMailboxManager.getClock();

        session = storeMailboxManager.createSystemSession(USERNAME);
        otherSession = storeMailboxManager.createSystemSession(OTHERUSER);
        quanSession = storeMailboxManager.createSystemSession(QUAN);

        inboxPath = MailboxPath.inbox(USERNAME);
        otherInboxPath = MailboxPath.inbox(OTHERUSER);
        quanInboxPath = MailboxPath.inbox(QUAN);

        storeMailboxManager.createMailbox(inboxPath, session);
        storeMailboxManager.createMailbox(otherInboxPath, otherSession);
        storeMailboxManager.createMailbox(quanInboxPath, quanSession);

        inboxMessageManager = (StoreMessageManager) storeMailboxManager.getMailbox(inboxPath, session);
        StoreMessageManager otherInboxMessageManager = (StoreMessageManager) storeMailboxManager.getMailbox(otherInboxPath, otherSession);
        quanInboxMessageManager = (StoreMessageManager) storeMailboxManager.getMailbox(quanInboxPath, quanSession);

        MailboxPath myFolderPath = MailboxPath.forUser(USERNAME, "MyFolder");
        storeMailboxManager.createMailbox(myFolderPath, session);
        myFolderMessageManager = (StoreMessageManager) storeMailboxManager.getMailbox(myFolderPath, session);
        mailbox = inboxMessageManager.getMailboxEntity();
        mailbox2 = myFolderMessageManager.getMailboxEntity();
        otherMailbox = otherInboxMessageManager.getMailboxEntity();
        quanMailbox = quanInboxMessageManager.getMailboxEntity();

        messageMapper = storeMailboxManager.getMapperFactory().getMessageMapper(quanSession);
        newBasedMessageId = initNewBasedMessageId();
        otherBasedMessageId = initOtherBasedMessageId();

        clock.setInstant(new Date(1388617200000L).toInstant());
        m1 = inboxMessageManager.appendMessage(
            ClassLoader.getSystemResourceAsStream("eml/spamMail.eml"),
            new Date(1388617200000L),
            session,
            RECENT,
            new Flags(Flags.Flag.DELETED)).getId();
        // sentDate: Thu, 4 Jun 2015 09:23:37 +0000
        // Internal date : 2014/02/02 00:00:00.000
        clock.setInstant(new Date(1391295600000L).toInstant());
        m2 = inboxMessageManager.appendMessage(
            ClassLoader.getSystemResourceAsStream("eml/mail1.eml"),
            new Date(1391295600000L),
            session,
            RECENT,
            new Flags(Flags.Flag.ANSWERED)).getId();
        // sentDate: Thu, 4 Jun 2015 09:27:37 +0000
        // Internal date : 2014/03/02 00:00:00.000
        clock.setInstant(new Date(1393714800000L).toInstant());
        m3 = inboxMessageManager.appendMessage(
            ClassLoader.getSystemResourceAsStream("eml/mail2.eml"),
            new Date(1393714800000L),
            session,
            RECENT,
            new Flags(Flags.Flag.DRAFT)).getId();
        // sentDate: Tue, 2 Jun 2015 08:16:19 +0000
        // Internal date : 2014/05/02 00:00:00.000
        clock.setInstant(new Date(1398981600000L).toInstant());
        m4 = inboxMessageManager.appendMessage(
            ClassLoader.getSystemResourceAsStream("eml/mail3.eml"),
            new Date(1398981600000L),
            session,
            RECENT,
            new Flags(Flags.Flag.RECENT)).getId();
        // sentDate: Fri, 15 May 2015 06:35:59 +0000
        // Internal date : 2014/04/02 00:00:00.000
        clock.setInstant(new Date(1396389600000L).toInstant());
        m5 = inboxMessageManager.appendMessage(
            ClassLoader.getSystemResourceAsStream("eml/mail4.eml"),
            new Date(1396389600000L),
            session,
            RECENT,
            new Flags(Flags.Flag.FLAGGED)).getId();
        // sentDate: Wed, 03 Jun 2015 19:14:32 +0000
        // Internal date : 2014/06/02 00:00:00.000
        clock.setInstant(new Date(1401660000000L).toInstant());
        m6 = inboxMessageManager.appendMessage(
            ClassLoader.getSystemResourceAsStream("eml/pgpSignedMail.eml"),
            new Date(1401660000000L),
            session,
            RECENT,
            new Flags(Flags.Flag.SEEN)).getId();
        // sentDate: Thu, 04 Jun 2015 07:36:08 +0000
        // Internal date : 2014/07/02 00:00:01.000
        clock.setInstant(new Date(1404252001000L).toInstant());
        m7 = inboxMessageManager.appendMessage(
            ClassLoader.getSystemResourceAsStream("eml/htmlMail.eml"),
            new Date(1404252001000L),
            session,
            NOT_RECENT,
            new Flags()).getId();
        // sentDate: Thu, 4 Jun 2015 06:08:41 +0200
        // Internal date : 2014/08/02 00:00:00.000
        clock.setInstant(new Date(1406930400000L).toInstant());
        m8 = inboxMessageManager.appendMessage(
            ClassLoader.getSystemResourceAsStream("eml/mail.eml"),
            new Date(1406930400000L),
            session,
            RECENT,
            new Flags("Hello")).getId();
        // sentDate: Thu, 4 Jun 2015 06:08:41 +0200
        // Internal date : 2014/08/02 00:00:00.000
        mOther = myFolderMessageManager.appendMessage(
            ClassLoader.getSystemResourceAsStream("eml/mail.eml"),
            new Date(1406930400000L),
            session,
            RECENT,
            new Flags(Flags.Flag.SEEN)).getId();
        clock.setInstant(new Date(1409608800000L).toInstant());
        m9 = inboxMessageManager.appendMessage(
            ClassLoader.getSystemResourceAsStream("eml/frnog.eml"),
            new Date(1409608800000L),
            session,
            RECENT,
            new Flags("Hello you")).getId();

        mailWithAttachment = myFolderMessageManager.appendMessage(
            ClassLoader.getSystemResourceAsStream("eml/oneAttachmentAndSomeTextInlined.eml"),
            new Date(1409608900000L),
            session,
            RECENT,
            new Flags("Hello you")).getId();

        mailWithInlinedAttachment = myFolderMessageManager.appendMessage(
            ClassLoader.getSystemResourceAsStream("eml/oneInlinedAttachment.eml"),
            new Date(1409608900000L),
            session,
            RECENT,
            new Flags("Hello you")).getId();

        m10 = otherInboxMessageManager.appendMessage(
            ClassLoader.getSystemResourceAsStream("eml/mail1.eml"),
            new Date(1391295600000L),
            otherSession,
            RECENT,
            new Flags()).getId();

        awaitMessageCount(ImmutableList.of(), SearchQuery.matchAll(), 13);
    }

    protected abstract void awaitMessageCount(List<MailboxId> mailboxIds, SearchQuery query, long messageCount);
    
    protected abstract void initializeMailboxManager() throws Exception;

    protected abstract MessageId initNewBasedMessageId();

    protected abstract MessageId initOtherBasedMessageId();

    @Test
    void searchingMessageInMultipleMailboxShouldNotReturnTwiceTheSameMessage() throws MailboxException {
        assumeTrue(messageIdManager != null);

        messageIdManager.setInMailboxes(m4.getMessageId(),
            ImmutableList.of(mailbox.getMailboxId(), mailbox2.getMailboxId()),
            session);

        awaitMessageCount(ImmutableList.of(), SearchQuery.matchAll(), 14);

        SearchQuery searchQuery = SearchQuery.matchAll();

        List<MessageId> result = messageSearchIndex.search(session,
            ImmutableList.of(mailbox.getMailboxId(), mailbox2.getMailboxId()),
            searchQuery,
            LIMIT)
            .collectList().block();

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
    void searchingMessageInMultipleMailboxShouldUnionOfTheTwoMailbox() throws MailboxException {
        assumeTrue(messageIdManager != null);
        messageIdManager.setInMailboxes(m4.getMessageId(),
            ImmutableList.of(mailbox2.getMailboxId()),
            session);

        SearchQuery searchQuery = SearchQuery.matchAll();

        List<MessageId> result = messageSearchIndex.search(session,
            ImmutableList.of(mailbox.getMailboxId(), mailbox2.getMailboxId()),
            searchQuery,
            LIMIT)
            .collectList().block();

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
    void searchingMessageInMultipleMailboxShouldNotReturnLessMessageThanLimitArgument() throws MailboxException {
        assumeTrue(messageIdManager != null);
        messageIdManager.setInMailboxes(m1.getMessageId(), ImmutableList.of(mailbox.getMailboxId(), mailbox2.getMailboxId()), session);
        messageIdManager.setInMailboxes(m2.getMessageId(), ImmutableList.of(mailbox.getMailboxId(), mailbox2.getMailboxId()), session);
        messageIdManager.setInMailboxes(m3.getMessageId(), ImmutableList.of(mailbox.getMailboxId(), mailbox2.getMailboxId()), session);
        messageIdManager.setInMailboxes(m4.getMessageId(), ImmutableList.of(mailbox.getMailboxId(), mailbox2.getMailboxId()), session);
        messageIdManager.setInMailboxes(m5.getMessageId(), ImmutableList.of(mailbox.getMailboxId(), mailbox2.getMailboxId()), session);
        messageIdManager.setInMailboxes(m6.getMessageId(), ImmutableList.of(mailbox.getMailboxId(), mailbox2.getMailboxId()), session);
        messageIdManager.setInMailboxes(m7.getMessageId(), ImmutableList.of(mailbox.getMailboxId(), mailbox2.getMailboxId()), session);

        awaitMessageCount(ImmutableList.of(), SearchQuery.matchAll(), 20);

        SearchQuery searchQuery = SearchQuery.matchAll();

        int limit = 10;
        List<MessageId> result = messageSearchIndex.search(session,
            ImmutableList.of(mailbox2.getMailboxId(), mailbox.getMailboxId()),
            searchQuery,
            limit)
            .collectList().block();

        assertThat(result)
            .hasSize(limit);
    }

    @Test
    void whenEmptyListOfMailboxGivenSearchShouldReturnEmpty() throws MailboxException {
        SearchQuery searchQuery = SearchQuery.matchAll();

        List<MessageId> result = messageSearchIndex.search(session,
            ImmutableList.of(),
            searchQuery,
            LIMIT)
            .collectList().block();

        assertThat(result)
            .isEmpty();
    }

    @Test
    void searchingMessageInMultipleMailboxShouldNotReturnLessMessageThanLimitArgumentEvenIfDuplicatedMessageAreBeforeLegitimeMessage() throws MailboxException {
        assumeTrue(messageIdManager != null);
        messageIdManager.setInMailboxes(m1.getMessageId(), ImmutableList.of(mailbox.getMailboxId(), mailbox2.getMailboxId()), session);

        myFolderMessageManager.appendMessage(MessageManager.AppendCommand.builder()
            .withFlags(new Flags(Flags.Flag.SEEN))
            .build(ClassLoaderUtils.getSystemResourceAsSharedStream("eml/mail.eml")),
            session);

        awaitMessageCount(ImmutableList.of(), SearchQuery.matchAll(), 15);

        int limit = 13;
        SearchQuery searchQuery = SearchQuery.matchAll();
        List<MessageId> result = messageSearchIndex.search(session,
            ImmutableList.of(mailbox2.getMailboxId(), mailbox.getMailboxId()),
            searchQuery,
            limit)
            .collectList().block();

        assertThat(result)
                .hasSize(limit);
    }

    @Test
    void searchShouldThrowWhenSessionIsNull() {
        SearchQuery searchQuery = SearchQuery.matchAll();
        MailboxSession session = null;
        
        assertThatThrownBy(() -> messageSearchIndex.search(session, mailbox, searchQuery))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void emptySearchQueryShouldReturnAllUids() throws MailboxException {
        SearchQuery searchQuery = SearchQuery.matchAll();
        
        assertThat(messageSearchIndex.search(session, mailbox, searchQuery).toStream())
            .containsOnly(m1.getUid(), m2.getUid(), m3.getUid(), m4.getUid(), m5.getUid(), m6.getUid(), m7.getUid(), m8.getUid(), m9.getUid());
    }

    @Test
    void searchShouldReturnCorrectResultWhenByMessageId() throws MailboxException {
        SearchQuery searchQuery = SearchQuery.of(SearchQuery.hasMessageId(m4.getMessageId()));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery).toStream())
            .containsOnly(m4.getUid());
    }

    @Test
    void allShouldReturnAllUids() throws MailboxException {
        SearchQuery searchQuery = SearchQuery.of(SearchQuery.all());

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery).toStream())
            .containsOnly(m1.getUid(), m2.getUid(), m3.getUid(), m4.getUid(), m5.getUid(), m6.getUid(), m7.getUid(), m8.getUid(), m9.getUid());
    }

    @Test
    void bodyContainsShouldReturnUidOfMessageContainingTheGivenText() throws MailboxException {
        /* Only mail4.eml contains word MAILET-94 */
        SearchQuery searchQuery = SearchQuery.of(SearchQuery.bodyContains("MAILET-94"));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery).toStream())
            .containsOnly(m5.getUid());
    }

    @Test
    protected void bodyContainsShouldReturnUidOfMessageContainingBothTerms() throws MailboxException {
        /* mail1.eml contains words created AND summary
           mail.eml contains created and thus matches the query with a low score */
        SearchQuery searchQuery = SearchQuery.of(SearchQuery.bodyContains("created summary"));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery).toStream())
            .containsOnly(m2.getUid());
    }

    @Test
    void hasAttachmentShouldOnlyReturnMessageThatHasAttachmentWhichAreNotInline() throws MailboxException {
        SearchQuery searchQuery = SearchQuery.of(SearchQuery.hasAttachment());

        assertThat(messageSearchIndex.search(session, mailbox2, searchQuery).toStream())
            .containsOnly(mailWithAttachment.getUid());
    }

    @Test
    void messageWithDotsInHeaderShouldBeIndexed() throws MailboxException {

        ComposedMessageId mailWithDotsInHeader = myFolderMessageManager.appendMessage(
            MessageManager.AppendCommand.builder()
                .build(ClassLoaderUtils.getSystemResourceAsSharedStream("eml/headerWithDot.eml")),
            session).getId();
        awaitMessageCount(ImmutableList.of(), SearchQuery.matchAll(), 14);
        
        SearchQuery searchQuery = SearchQuery.of(SearchQuery.all());

        assertThat(messageSearchIndex.search(session, mailbox2, searchQuery).toStream())
            .contains(mailWithDotsInHeader.getUid());
    }

    @Test
    void headerWithDotsShouldBeIndexed() throws MailboxException {

        ComposedMessageId mailWithDotsInHeader = myFolderMessageManager.appendMessage(
            MessageManager.AppendCommand.builder()
                .build(ClassLoaderUtils.getSystemResourceAsSharedStream("eml/headerWithDot.eml")),
            session).getId();
        awaitMessageCount(ImmutableList.of(), SearchQuery.matchAll(), 14);

        SearchQuery searchQuery = SearchQuery.of(SearchQuery.headerExists("X-header.with.dots"));

        assertThat(messageSearchIndex.search(session, mailbox2, searchQuery).toStream())
            .contains(mailWithDotsInHeader.getUid());
    }

    @Test
    void searchShouldBeExactOnEmailAddresses() throws MailboxException {
        assumeTrue(storeMailboxManager.getSupportedSearchCapabilities().contains(MailboxManager.SearchCapabilities.Text));

        ComposedMessageId m11 = inboxMessageManager.appendMessage(
            MessageManager.AppendCommand.builder()
            .build(ClassLoaderUtils.getSystemResourceAsSharedStream("eml/mail5.eml")),
            session).getId();

        awaitMessageCount(ImmutableList.of(), SearchQuery.matchAll(), 14);

        String emailToSearch = "luc.duzan@james.apache.org";
        SearchQuery searchQuery = SearchQuery.of(SearchQuery.or(ImmutableList.of(
            SearchQuery.address(AddressType.From, emailToSearch),
            SearchQuery.address(AddressType.To, emailToSearch),
            SearchQuery.address(AddressType.Cc, emailToSearch),
            SearchQuery.address(AddressType.Bcc, emailToSearch),
            SearchQuery.headerContains("Subject", emailToSearch))));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery).toStream())
            .containsOnly(m11.getUid());
    }

    @Test
    void textShouldMatchFullEmailAddress() throws Exception {
        MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, INBOX);
        MailboxSession session = MailboxSessionUtil.create(USERNAME);
        MessageManager messageManager = storeMailboxManager.getMailbox(mailboxPath, session);

        ComposedMessageId messageId1 = messageManager.appendMessage(
            MessageManager.AppendCommand.builder().build(
                Message.Builder
                    .of()
                    .setSubject("test")
                    .setBody("benwa@apache.org email address do not exist", StandardCharsets.UTF_8)
                    .build()),
            session).getId();

        awaitMessageCount(ImmutableList.of(), SearchQuery.matchAll(), 14);

        assertThat(Flux.from(messageManager.search(SearchQuery.of(SearchQuery.bodyContains("benwa@apache.org")), session)).toStream())
            .containsOnly(messageId1.getUid());
    }

    @Test
    void subjectShouldMatchSubject() throws Exception {
        assertThat(Flux.from(inboxMessageManager.search(SearchQuery.of(SearchQuery.subject("JSON")), session)).toStream())
            .containsOnly(m2.getUid());
    }

    @Test
    void subjectShouldNotIncludeIrrelevantResults() throws Exception {
        ComposedMessageId m = inboxMessageManager.appendMessage(
            ClassLoader.getSystemResourceAsStream("eml/oneInlinedAttachment.eml"),
            new Date(1409608900000L),
            session,
            RECENT,
            new Flags("Hello you")).getId();

        awaitMessageCount(ImmutableList.of(), SearchQuery.matchAll(), 14);

        assertThat(Flux.from(inboxMessageManager.search(SearchQuery.of(SearchQuery.subject("Inline attachment")), session)).toStream())
            .containsOnly(m.getUid());
    }

    @Test
    void textShouldMatchEmailAddressLocalPart() throws Exception {
        MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, INBOX);
        MailboxSession session = MailboxSessionUtil.create(USERNAME);
        MessageManager messageManager = storeMailboxManager.getMailbox(mailboxPath, session);

        ComposedMessageId messageId1 = messageManager.appendMessage(
            MessageManager.AppendCommand.builder().build(
                Message.Builder
                    .of()
                    .setSubject("test")
                    .setBody("benwa@apache.org email address do not exist", StandardCharsets.UTF_8)
                    .build()),
            session).getId();

        awaitMessageCount(ImmutableList.of(), SearchQuery.matchAll(), 14);

        assertThat(Flux.from(messageManager.search(SearchQuery.of(SearchQuery.bodyContains("benwa")), session)).toStream())
            .containsOnly(messageId1.getUid());
    }

    @Test
    public void textShouldMatchEmailAddressDomainPart() throws Exception {
        MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, INBOX);
        MailboxSession session = MailboxSessionUtil.create(USERNAME);
        MessageManager messageManager = storeMailboxManager.getMailbox(mailboxPath, session);

        ComposedMessageId messageId1 = messageManager.appendMessage(
            MessageManager.AppendCommand.builder().build(
                Message.Builder
                    .of()
                    .setSubject("test")
                    .setBody("benwa@mydomain.org email address do not exist", StandardCharsets.UTF_8)
                    .build()),
            session).getId();

        awaitMessageCount(ImmutableList.of(), SearchQuery.matchAll(), 14);

        assertThat(Flux.from(messageManager.search(SearchQuery.of(SearchQuery.bodyContains("mydomain.org")), session)).toStream())
            .containsOnly(messageId1.getUid());
    }

    @Test
    void textShouldNotMatchOtherAddressesOfTheSameDomain() throws Exception {
        MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, INBOX);
        MailboxSession session = MailboxSessionUtil.create(USERNAME);
        MessageManager messageManager = storeMailboxManager.getMailbox(mailboxPath, session);

        messageManager.appendMessage(
            MessageManager.AppendCommand.builder().build(
                Message.Builder
                    .of()
                    .setSubject("test")
                    .setBody("benwa@apache.org email address do not exist", StandardCharsets.UTF_8)
                    .build()),
            session).getId();

        awaitMessageCount(ImmutableList.of(), SearchQuery.matchAll(), 14);

        assertThat(Flux.from(messageManager.search(SearchQuery.of(SearchQuery.bodyContains("alice@apache.org")), session)).toStream())
            .isEmpty();
    }

    @Test
    void hasNoAttachmenShouldOnlyReturnMessageThatHasNoAttachmentWhichAreNotInline() throws MailboxException {
        SearchQuery searchQuery = SearchQuery.of(SearchQuery.hasNoAttachment());

        assertThat(messageSearchIndex.search(session, mailbox2, searchQuery).toStream())
            .containsOnly(mOther.getUid(), mailWithInlinedAttachment.getUid());
    }

    @Test
    void flagIsSetShouldReturnUidOfMessageMarkedAsDeletedWhenUsedWithFlagDeleted() throws MailboxException {
        SearchQuery searchQuery = SearchQuery.of(SearchQuery.flagIsSet(Flags.Flag.DELETED));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery).toStream())
            .containsOnly(m1.getUid());
    }

    @Test
    void flagIsSetShouldReturnUidOfMessageMarkedAsAnsweredWhenUsedWithFlagAnswered() throws MailboxException {
        SearchQuery searchQuery = SearchQuery.of(SearchQuery.flagIsSet(Flags.Flag.ANSWERED));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery).toStream())
            .containsOnly(m2.getUid());
    }

    @Test
    void flagIsSetShouldReturnUidOfMessageMarkedAsDraftWhenUsedWithFlagDraft() throws MailboxException {
        SearchQuery searchQuery = SearchQuery.of(SearchQuery.flagIsSet(Flags.Flag.DRAFT));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery).toStream())
            .containsOnly(m3.getUid());
    }

    @Test
    void flagIsSetShouldReturnUidOfMessageMarkedAsRecentWhenUsedWithFlagRecent() throws MailboxException {
        // Only message 7 is not marked as RECENT
        SearchQuery searchQuery = SearchQuery.of(SearchQuery.flagIsSet(Flags.Flag.RECENT));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery).toStream())
            .containsOnly(m1.getUid(), m2.getUid(), m3.getUid(), m4.getUid(), m5.getUid(), m6.getUid(), m8.getUid(), m9.getUid());
    }

    @Test
    void flagIsSetShouldReturnUidOfMessageMarkedAsFlaggedWhenUsedWithFlagFlagged() throws MailboxException {
        SearchQuery searchQuery = SearchQuery.of(SearchQuery.flagIsSet(Flags.Flag.FLAGGED));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery).toStream())
            .containsOnly(m5.getUid());
    }

    @Test
    void flagIsSetShouldReturnUidOfMessageMarkedAsSeenWhenUsedWithFlagSeen() throws MailboxException {
        // Only message 6 is marked as read.
        SearchQuery searchQuery = SearchQuery.of(SearchQuery.flagIsSet(Flags.Flag.SEEN));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery).toStream())
            .containsOnly(m6.getUid());
    }

    @Test
    void searchShouldReturnSeenMessagesWhenFlagsGotUpdated() throws MailboxException {
        inboxMessageManager.setFlags(
            new Flags(Flags.Flag.SEEN),
            MessageManager.FlagsUpdateMode.ADD,
            MessageRange.one(m5.getUid()),
            session);

        SearchQuery searchQuery = SearchQuery.of(SearchQuery.flagIsSet(Flags.Flag.SEEN));

        //m5 & m6 now both have SEEN flag in INBOX mailbox
        awaitMessageCount(ImmutableList.of(mailbox.getMailboxId()), searchQuery, 2);

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery).toStream())
            .contains(m5.getUid());
    }
    
    @Test
    protected void multimailboxSearchShouldReturnUidOfMessageMarkedAsSeenInAllMailboxes() throws MailboxException {
        SearchQuery searchQuery = SearchQuery.of(SearchQuery.flagIsSet(Flags.Flag.SEEN));

        List<MessageId> actual = messageSearchIndex.search(
            session,
            ImmutableList.of(mailbox.getMailboxId(), mailbox2.getMailboxId()),
            searchQuery,
            LIMIT)
            .collectList().block();

        assertThat(actual).containsOnly(mOther.getMessageId(), m6.getMessageId());
    }

    @Test
    void multimailboxSearchShouldReturnUidOfMessageMarkedAsSeenInOneMailbox() throws MailboxException {
        SearchQuery searchQuery = SearchQuery.of(SearchQuery.flagIsSet(Flags.Flag.SEEN));

        List<MessageId> actual = messageSearchIndex.search(session, ImmutableList.of(mailbox.getMailboxId()), searchQuery, LIMIT)
            .collectList().block();

        assertThat(actual).containsOnly(m6.getMessageId());
    }

    @Test
    void multimailboxSearchShouldReturnUidOfMessageWithExpectedFromInTwoMailboxes() throws MailboxException {
        SearchQuery searchQuery = SearchQuery.of(SearchQuery.address(AddressType.From, "murari"));

        List<MessageId> actual = messageSearchIndex.search(
            session,
            ImmutableList.of(mailbox.getMailboxId(), mailbox2.getMailboxId()),
            searchQuery,
            LIMIT)
            .collectList().block();

        assertThat(actual).containsOnly(mOther.getMessageId(), m8.getMessageId());
    }

    @Test
    protected void multimailboxSearchShouldReturnUidOfMessageMarkedAsSeenInTwoMailboxes() throws MailboxException {
        SearchQuery searchQuery = SearchQuery.of(SearchQuery.flagIsSet(Flags.Flag.SEEN));

        List<MessageId> actual = messageSearchIndex.search(
            session,
            ImmutableList.of(mailbox.getMailboxId(), mailbox2.getMailboxId()),
            searchQuery,
            LIMIT)
            .collectList().block();

        assertThat(actual).containsOnly(mOther.getMessageId(), m6.getMessageId());
    }

    @Test
    void multimailboxSearchShouldLimitTheSize() throws MailboxException {
        SearchQuery searchQuery = SearchQuery.of(SearchQuery.flagIsSet(Flags.Flag.SEEN));

        long limit = 1;
        List<MessageId> actual = messageSearchIndex.search(
            session,
            ImmutableList.of(mailbox.getMailboxId(), mailbox2.getMailboxId()),
            searchQuery,
            limit)
            .collectList().block();
        // Two messages matches this query : mOther and m6

        assertThat(actual).hasSize(1);
    }

    @Test
    void multimailboxSearchShouldWorkWithOtherUserMailbox() throws  MailboxException {
        assumeTrue(storeMailboxManager.hasCapability(MailboxManager.MailboxCapabilities.ACL));
        SearchQuery searchQuery = SearchQuery.matchAll();

        long limit = 256;
        List<MessageId> actual = messageSearchIndex.search(
            session,
            ImmutableList.of(otherMailbox.getMailboxId()),
            searchQuery,
            limit)
            .collectList().block();

        assertThat(actual).contains(m10.getMessageId());
    }


    @Test
    void flagIsSetShouldReturnUidsOfMessageContainingAGivenUserFlag() {
        SearchQuery searchQuery = SearchQuery.of(SearchQuery.flagIsSet("Hello"));

        CALMLY_AWAIT.untilAsserted(() ->
            assertThat(messageSearchIndex.search(session, mailbox, searchQuery).toStream())
                .containsOnly(m8.getUid()));
    }

    @Test
    void userFlagsShouldBeMatchedExactly() throws MailboxException {
        SearchQuery searchQuery = SearchQuery.of(SearchQuery.flagIsSet("Hello bonjour"));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery).toStream())
            .isEmpty();
    }

    @Test
    void flagIsUnSetShouldReturnUidOfMessageNotMarkedAsDeletedWhenUsedWithFlagDeleted() throws MailboxException {
        SearchQuery searchQuery = SearchQuery.of(SearchQuery.flagIsUnSet(Flags.Flag.DELETED));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery).toStream())
            .containsOnly(m2.getUid(), m3.getUid(), m4.getUid(), m5.getUid(), m6.getUid(), m7.getUid(), m8.getUid(), m9.getUid());
    }

    @Test
    void flagIsUnSetShouldReturnUidOfMessageNotMarkedAsAnsweredWhenUsedWithFlagAnswered() throws MailboxException {
        SearchQuery searchQuery = SearchQuery.of(SearchQuery.flagIsUnSet(Flags.Flag.ANSWERED));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery).toStream())
            .containsOnly(m1.getUid(), m3.getUid(), m4.getUid(), m5.getUid(), m6.getUid(), m7.getUid(), m8.getUid(), m9.getUid());
    }

    @Test
    void flagIsUnSetShouldReturnUidOfMessageNotMarkedAsDraftWhenUsedWithFlagDraft() throws MailboxException {
        SearchQuery searchQuery = SearchQuery.of(SearchQuery.flagIsUnSet(Flags.Flag.DRAFT));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery).toStream())
            .containsOnly(m1.getUid(), m2.getUid(), m4.getUid(), m5.getUid(), m6.getUid(), m7.getUid(), m8.getUid(), m9.getUid());
    }

    @Test
    void flagIsUnSetShouldReturnUidOfMessageNotMarkedAsRecentWhenUsedWithFlagRecent() throws MailboxException {
        // Only message 7 is not marked as RECENT
        SearchQuery searchQuery = SearchQuery.of(SearchQuery.flagIsUnSet(Flags.Flag.RECENT));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery).toStream())
            .containsOnly(m7.getUid());
    }

    @Test
    void flagIsUnSetShouldReturnUidOfMessageNotMarkedAsFlaggedWhenUsedWithFlagFlagged() throws MailboxException {
        SearchQuery searchQuery = SearchQuery.of(SearchQuery.flagIsUnSet(Flags.Flag.FLAGGED));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery).toStream())
            .containsOnly(m1.getUid(), m2.getUid(), m3.getUid(), m4.getUid(), m6.getUid(), m7.getUid(), m8.getUid(), m9.getUid());
    }

    @Test
    void flagIsUnSetShouldReturnUidOfMessageNotMarkedAsSeenWhenUsedWithFlagSeen() throws MailboxException {
        // Only message 6 is marked as read.
        SearchQuery searchQuery = SearchQuery.of(SearchQuery.flagIsUnSet(Flags.Flag.SEEN));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery).toStream())
            .containsOnly(m1.getUid(), m2.getUid(), m3.getUid(), m4.getUid(), m5.getUid(), m7.getUid(), m8.getUid(), m9.getUid());
    }

    @Test
    void flagIsUnSetShouldReturnUidsOfMessageNotContainingAGivenUserFlag() throws MailboxException {
        SearchQuery searchQuery = SearchQuery.of(SearchQuery.flagIsUnSet("Hello"));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery).toStream())
            .containsOnly(m1.getUid(), m2.getUid(), m3.getUid(), m4.getUid(), m5.getUid(), m6.getUid(), m7.getUid(),  m9.getUid());
    }

    @Test
    protected void internalDateAfterShouldReturnMessagesAfterAGivenDate() throws Exception {
        SearchQuery searchQuery = SearchQuery.of(SearchQuery.internalDateAfter(
            new Date(1404252000000L),
            DateResolution.Day));
        // Date : 2014/07/02 00:00:00.000 ( Paris time zone )

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery).toStream())
            .containsOnly(m8.getUid(), m9.getUid());
    }

    @Test
    protected void internalDateBeforeShouldReturnMessagesBeforeAGivenDate() throws Exception {
        SearchQuery searchQuery = SearchQuery.of(SearchQuery.internalDateBefore(
            new Date(1391295600000L),
            DateResolution.Day));
        // Date : 2014/02/02 00:00:00.000 ( Paris time zone )

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery).toStream())
            .containsOnly(m1.getUid());
    }

    @Test
    void internalDateOnShouldReturnMessagesOfTheGivenDate() throws Exception {
        SearchQuery searchQuery = SearchQuery.of(SearchQuery.internalDateOn(
            new Date(1393714800000L),
            DateResolution.Day));
        // Date : 2014/03/02 00:00:00.000 ( Paris time zone )

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery).toStream())
            .containsOnly(m3.getUid());
    }

    @Test
    protected void saveDateAfterShouldReturnMessagesAfterAGivenDate() throws Exception {
        SearchQuery searchQuery = SearchQuery.of(SearchQuery.saveDateAfter(
            new Date(1404252000000L),
            DateResolution.Day));
        // Date : 2014/07/02 00:00:00.000 ( Paris time zone )

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery).toStream())
            .containsOnly(m8.getUid(), m9.getUid());
    }

    @Test
    protected void saveDateBeforeShouldReturnMessagesBeforeAGivenDate() throws Exception {
        SearchQuery searchQuery = SearchQuery.of(SearchQuery.saveDateBefore(
            new Date(1391295600000L),
            DateResolution.Day));
        // Date : 2014/02/02 00:00:00.000 ( Paris time zone )

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery).toStream())
            .containsOnly(m1.getUid());
    }

    @Test
    void saveDateOnShouldReturnMessagesOfTheGivenDate() throws Exception {
        SearchQuery searchQuery = SearchQuery.of(SearchQuery.saveDateOn(
            new Date(1393714800000L),
            DateResolution.Day));
        // Date : 2014/03/02 00:00:00.000 ( Paris time zone )

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery).toStream())
            .containsOnly(m3.getUid());
    }

    @Test
    void saveDateSupportedShouldReturnAllMessagesOfAMailbox() throws Exception {
        SearchQuery searchQuery = SearchQuery.of(SearchQuery.saveDateSupported());

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery).toStream())
            .containsOnly(m1.getUid(), m2.getUid(), m3.getUid(), m4.getUid(), m5.getUid(), m6.getUid(), m7.getUid(), m8.getUid(), m9.getUid());
    }

    @Test
    protected void saveDateSearchShouldBeDifferentFromInternalDateSearch() throws Exception {
        // set a message with internalDate in 2014 and saveDate is now
        Date now = new Date();
        clock.setInstant(now.toInstant());
        ComposedMessageId message = quanInboxMessageManager.appendMessage(ClassLoader.getSystemResourceAsStream("eml/frnog.eml"), new Date(1391295600000L),
            quanSession, RECENT, new Flags("Hello you")).getId();

        awaitMessageCount(ImmutableList.of(), SearchQuery.matchAll(), 14);

        // find message with saveDate between
        SearchQuery searchQuery = SearchQuery.of(SearchQuery.saveDateAfter(Date.from(now.toInstant().minus(2, ChronoUnit.DAYS)), DateResolution.Day));

        assertThat(messageSearchIndex.search(quanSession, quanMailbox, searchQuery).toStream())
            .containsOnly(message.getUid());
    }

    @Test
    void sentDateAfterShouldReturnMessagesAfterAGivenDate() throws Exception {
        // Date : 2015/06/04 11:00:00.000 ( Paris time zone )
        SearchQuery searchQuery = SearchQuery.builder()
            .andCriteria(SearchQuery.sentDateAfter(new Date(1433408400000L), DateResolution.Second))
            .sorts(new Sort(SortClause.Arrival, Order.REVERSE))
            .build();

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery).toStream())
            .containsOnly(m3.getUid(), m2.getUid());
    }

    @Test
    void sentDateBeforeShouldReturnMessagesBeforeAGivenDate() throws Exception {
        // Date : 2015/06/01 00:00:00.000 ( Paris time zone )
        SearchQuery searchQuery = SearchQuery.builder()
            .andCriteria(SearchQuery.sentDateBefore(new Date(1433109600000L), DateResolution.Day))
            .sorts(new Sort(SortClause.Arrival, Order.REVERSE))
            .build();

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery).toStream())
            .containsOnly(m5.getUid());
    }

    @Test
    void sentDateOnShouldReturnMessagesOfTheGivenDate() throws Exception {
        // Date : 2015/06/02 08:00:00.000 ( Paris time zone )
        SearchQuery searchQuery = SearchQuery.builder()
            .andCriteria(SearchQuery.sentDateOn(new Date(1433224800000L), DateResolution.Day))
            .sorts(new Sort(SortClause.Arrival, Order.REVERSE))
            .build();

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery).toStream())
            .containsOnly(m4.getUid(), m9.getUid());
    }

    @Test
    protected void modSeqEqualsShouldReturnUidsOfMessageHavingAGivenModSeq() throws Exception {
        SearchQuery searchQuery = SearchQuery.of(SearchQuery.modSeqEquals(2L));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery).toStream())
            .containsOnly(m2.getUid());
    }

    @Test
    protected void modSeqGreaterThanShouldReturnUidsOfMessageHavingAGreaterModSeq() throws Exception {
        SearchQuery searchQuery = SearchQuery.of(SearchQuery.modSeqGreaterThan(7L));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery).toStream())
            .containsOnly(m8.getUid(), m9.getUid());
    }

    @Test
    protected void modSeqLessThanShouldReturnUidsOfMessageHavingAGreaterModSeq() throws Exception {
        SearchQuery searchQuery = SearchQuery.of(SearchQuery.modSeqLessThan(3L));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery).toStream())
            .containsOnly(m1.getUid(), m2.getUid());
    }

    @Test
    void sizeGreaterThanShouldReturnUidsOfMessageExceedingTheSpecifiedSize() throws Exception {
        // Only message 6 is over 6.8 KB
        SearchQuery searchQuery = SearchQuery.of(SearchQuery.sizeGreaterThan(6800L));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery).toStream())
            .containsOnly(m6.getUid());
    }

    @Test
    void sizeLessThanShouldReturnUidsOfMessageNotExceedingTheSpecifiedSize() throws Exception {
        // Only message 2 3 4 5 7 9 are under 5 KB
        SearchQuery searchQuery = SearchQuery.of(SearchQuery.sizeLessThan(5000L));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery).toStream())
            .containsOnly(m2.getUid(), m3.getUid(), m4.getUid(), m5.getUid(), m7.getUid(), m9.getUid());
    }

    @Test
    void headerContainsShouldReturnUidsOfMessageHavingThisHeaderWithTheSpecifiedValue() throws Exception {
        SearchQuery searchQuery = SearchQuery.of(SearchQuery.headerContains("Precedence", "list"));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery).toStream())
            .containsOnly(m1.getUid(), m6.getUid(), m8.getUid(), m9.getUid());
    }

    @Test
    void headerContainsShouldBeCaseInsensitive() throws Exception {
        SearchQuery searchQuery = SearchQuery.of(SearchQuery.headerContains("Precedence", "LiSt"));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery).toStream())
            .containsOnly(m1.getUid(), m6.getUid(), m8.getUid(), m9.getUid());
    }

    @Test
    void headerExistsShouldReturnUidsOfMessageHavingThisHeader() throws Exception {
        SearchQuery searchQuery = SearchQuery.of(SearchQuery.headerExists("Precedence"));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery).toStream())
            .containsOnly(m1.getUid(), m2.getUid(), m3.getUid(), m4.getUid(), m5.getUid(), m6.getUid(), m8.getUid(), m9.getUid());
    }

    @Test
    protected void addressShouldReturnUidHavingRightExpeditorWhenFromIsSpecified() throws Exception {
        SearchQuery searchQuery = SearchQuery.of(SearchQuery.address(AddressType.From, "murari.ksr@gmail.com"));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery).toStream())
            .containsOnly(m8.getUid());
    }

    @Test
    void addressShouldReturnUidHavingRightExpeditorWhenFromIsSpecifiedWithOnlyUserPartOfEmail() throws Exception {
        assumeTrue(storeMailboxManager
            .getSupportedSearchCapabilities()
            .contains(MailboxManager.SearchCapabilities.PartialEmailMatch));

        SearchQuery searchQuery = SearchQuery.of(SearchQuery.address(AddressType.From, "murari"));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery).toStream())
            .containsOnly(m8.getUid());
    }

    @Test
    void addressShouldReturnUidHavingRightExpeditorWhenFromIsSpecifiedWithDomainPartOfEmail() throws Exception {
        assumeTrue(storeMailboxManager
            .getSupportedSearchCapabilities()
            .contains(MailboxManager.SearchCapabilities.PartialEmailMatch));

        SearchQuery searchQuery = SearchQuery.of(SearchQuery.address(AddressType.From, "gmail.com"));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery).toStream())
            .containsOnly(m8.getUid());
    }

    @Test
    void addressShouldReturnTheRightUidOfTheMessageContainingUTF8EncodingToHeaderName() throws Exception {
        assumeTrue(storeMailboxManager
            .getSupportedSearchCapabilities()
            .contains(MailboxManager.SearchCapabilities.PartialEmailMatch));

        SearchQuery searchQuery = SearchQuery.of(SearchQuery.address(AddressType.To, "Üsteliğhan"));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery).toStream())
            .containsOnly(m8.getUid());
    }

    @Test
    void addressShouldReturnUidHavingRightRecipientWhenToIsSpecified() throws Exception {
        SearchQuery searchQuery = SearchQuery.of(SearchQuery.address(AddressType.To, "root@listes.minet.net"));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery).toStream())
            .containsOnly(m1.getUid());
    }

    @Test
    void addressShouldReturnUidHavingRightRecipientWhenToIsSpecifiedWithOnlyEmailUserPart() throws Exception {
        assumeTrue(storeMailboxManager
            .getSupportedSearchCapabilities()
            .contains(MailboxManager.SearchCapabilities.PartialEmailMatch));

        SearchQuery searchQuery = SearchQuery.of(SearchQuery.address(AddressType.To, "root"));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery).toStream())
            .containsOnly(m1.getUid());
    }

    @Test
    void addressShouldReturnUidHavingRightRecipientWhenToIsSpecifiedWithOnlyDomainPartSpecified() throws Exception {
        assumeTrue(storeMailboxManager
            .getSupportedSearchCapabilities()
            .contains(MailboxManager.SearchCapabilities.PartialEmailMatch));

        SearchQuery searchQuery = SearchQuery.of(SearchQuery.address(AddressType.To, "listes.minet.net"));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery).toStream())
            .containsOnly(m1.getUid());
    }

    @Test
    void addressShouldReturnUidHavingRightRecipientWhenCcIsSpecified() throws Exception {
        assumeTrue(storeMailboxManager
            .getSupportedSearchCapabilities()
            .contains(MailboxManager.SearchCapabilities.PartialEmailMatch));

        SearchQuery searchQuery = SearchQuery.of(SearchQuery.address(AddressType.Cc, "monkey@any.com"));
        assertThat(messageSearchIndex.search(session, mailbox, searchQuery).toStream())
            .containsOnly(m5.getUid());
    }

    @Test
    void addressShouldReturnUidHavingRightRecipientWhenCcIsSpecifiedWithOnlyUserPartOfTheEmail() throws Exception {
        assumeTrue(storeMailboxManager
            .getSupportedSearchCapabilities()
            .contains(MailboxManager.SearchCapabilities.PartialEmailMatch));

        SearchQuery searchQuery = SearchQuery.of(SearchQuery.address(AddressType.Cc, "monkey"));
        assertThat(messageSearchIndex.search(session, mailbox, searchQuery).toStream())
            .containsOnly(m5.getUid());
    }

    @Test
    void addressShouldReturnUidHavingRightRecipientWhenCcIsSpecifiedWithOnlyDomainPartOfTheEmail() throws Exception {
        assumeTrue(storeMailboxManager
            .getSupportedSearchCapabilities()
            .contains(MailboxManager.SearchCapabilities.PartialEmailMatch));

        SearchQuery searchQuery = SearchQuery.of(SearchQuery.address(AddressType.Cc, "any.com"));
        assertThat(messageSearchIndex.search(session, mailbox, searchQuery).toStream())
            .containsOnly(m5.getUid());
    }

    @Test
    void addressShouldReturnUidHavingRightRecipientWhenBccIsSpecifiedWithOnlyUserPartOfTheEmail() throws Exception {
        assumeTrue(storeMailboxManager
            .getSupportedSearchCapabilities()
            .contains(MailboxManager.SearchCapabilities.PartialEmailMatch));

        SearchQuery searchQuery = SearchQuery.of(SearchQuery.address(AddressType.Bcc, "monkey"));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery).toStream())
            .containsOnly(m5.getUid());
    }

    @Test
    void addressShouldReturnUidHavingRightRecipientWhenBccIsSpecifiedWithOnlyDomainPartOfTheEmail() throws Exception {
        assumeTrue(storeMailboxManager
            .getSupportedSearchCapabilities()
            .contains(MailboxManager.SearchCapabilities.PartialEmailMatch));

        SearchQuery searchQuery = SearchQuery.of(SearchQuery.address(AddressType.Bcc, "any.com"));
        assertThat(messageSearchIndex.search(session, mailbox, searchQuery).toStream())
            .containsOnly(m5.getUid());
    }

    @Test
    void addressShouldReturnUidHavingRightRecipientWhenBccIsSpecified() throws Exception {
        assumeTrue(storeMailboxManager
            .getSupportedSearchCapabilities()
            .contains(MailboxManager.SearchCapabilities.PartialEmailMatch));

        SearchQuery searchQuery = SearchQuery.of(SearchQuery.address(AddressType.Bcc, "no@no.com"));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery).toStream())
            .containsOnly(m9.getUid());
    }

    @Test
    void uidShouldreturnExistingUidsOnTheGivenRanges() throws Exception {
        SearchQuery searchQuery = SearchQuery.of(SearchQuery.uid(
            new SearchQuery.UidRange(m2.getUid(), m4.getUid()),
            new SearchQuery.UidRange(m6.getUid(), m7.getUid())));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery).toStream())
            .containsOnly(m2.getUid(), m3.getUid(), m4.getUid(), m6.getUid(), m7.getUid());
    }

    @Test
    protected void uidShouldreturnEveryThing() throws Exception {
        SearchQuery.UidRange[] numericRanges = {};
        SearchQuery searchQuery = SearchQuery.of(SearchQuery.uid(numericRanges));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery).toStream())
            .containsOnly(m1.getUid(), m2.getUid(), m3.getUid(), m4.getUid(), m5.getUid(), m6.getUid(), m7.getUid(), m8.getUid(), m9.getUid());
    }

    @Test
    protected void youShouldBeAbleToSpecifySeveralCriterionOnASingleQuery() throws Exception {
        SearchQuery searchQuery = SearchQuery.of(SearchQuery.headerExists("Precedence"), SearchQuery.modSeqGreaterThan(6L));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery).toStream())
            .containsOnly(m8.getUid(), m9.getUid());
    }

    @Test
    protected void andShouldReturnResultsMatchingBothRequests() throws Exception {
        SearchQuery searchQuery = SearchQuery.of(SearchQuery.and(
            SearchQuery.headerExists("Precedence"),
            SearchQuery.modSeqGreaterThan(6L)));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery).toStream())
            .containsOnly(m8.getUid(), m9.getUid());
    }

    @Test
    protected void orShouldReturnResultsMatchinganyRequests() throws Exception {
        SearchQuery.UidRange[] numericRanges = {new SearchQuery.UidRange(m2.getUid(), m4.getUid())};
        SearchQuery searchQuery = SearchQuery.of(SearchQuery.or(
            SearchQuery.uid(numericRanges),
            SearchQuery.modSeqGreaterThan(6L)));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery).toStream())
            .containsOnly(m2.getUid(), m3.getUid(), m4.getUid(), m7.getUid(), m8.getUid(), m9.getUid());
    }

    @Test
    void notShouldReturnResultsThatDoNotMatchAQuery() throws Exception {
        SearchQuery searchQuery = SearchQuery.of(
            SearchQuery.not(SearchQuery.headerExists("Precedence")));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery).toStream())
            .containsOnly(m7.getUid());
    }

    @Test
    void sortShouldOrderMessages() throws Exception {
        SearchQuery searchQuery = SearchQuery.allSortedWith(new Sort(SortClause.Arrival));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery).toStream())
            .containsExactly(m1.getUid(), m2.getUid(), m3.getUid(), m5.getUid(), m4.getUid(), m6.getUid(), m7.getUid(), m8.getUid(), m9.getUid());
    }

    @Test
    void revertSortingShouldReturnElementsInAReversedOrder() throws Exception {
        SearchQuery searchQuery = SearchQuery.allSortedWith(new Sort(SortClause.Arrival, Order.REVERSE));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery).toStream())
            .containsExactly(m9.getUid(), m8.getUid(), m7.getUid(), m6.getUid(), m4.getUid(), m5.getUid(), m3.getUid(), m2.getUid(), m1.getUid());
    }

    @Test
    void headerDateAfterShouldWork() throws Exception {
        // Date : 2015/06/04 11:00:00.000 ( Paris time zone )
        SearchQuery searchQuery = SearchQuery.builder()
            .andCriteria(SearchQuery.headerDateAfter("sentDate", new Date(1433408400000L), DateResolution.Second))
            .sorts(new Sort(SortClause.Arrival, Order.REVERSE))
            .build();

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery).toStream())
            .containsOnly(m3.getUid(), m2.getUid());
    }

    @Test
    void headerDateBeforeShouldWork() throws Exception {
        // Date : 2015/06/01 00:00:00.000 ( Paris time zone )
        SearchQuery searchQuery = SearchQuery.builder()
            .andCriteria(SearchQuery.headerDateBefore("sentDate", new Date(1433109600000L), DateResolution.Day))
            .sorts(new Sort(SortClause.Arrival, Order.REVERSE))
            .build();

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery).toStream())
            .containsOnly(m5.getUid());
    }

    @Test
    void headerDateOnShouldWork() throws Exception {
        // Date : 2015/06/02 08:00:00.000 ( Paris time zone )
        SearchQuery searchQuery = SearchQuery.builder()
            .andCriteria(SearchQuery.headerDateOn("sentDate", new Date(1433224800000L), DateResolution.Day))
            .sorts(new Sort(SortClause.Arrival, Order.REVERSE))
            .build();

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery).toStream())
            .containsOnly(m4.getUid(), m9.getUid());
    }

    @Test
    protected void mailsContainsShouldIncludeMailHavingAttachmentsMatchingAllTermsOfTheRequest() throws Exception {
        SearchQuery searchQuery = SearchQuery.of(SearchQuery.mailContains("root mailing list"));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery).toStream())
            .containsOnly(m1.getUid());
    }

    @Test
    protected void sortOnCcShouldWork() throws Exception {
        SearchQuery searchQuery = SearchQuery.builder()
            .andCriteria(SearchQuery.uid(new SearchQuery.UidRange(m2.getUid(), m5.getUid())))
            .sorts(new Sort(SortClause.MailboxCc))
            .build();

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery).toStream())
            .containsExactly(m3.getUid(), m5.getUid(), m4.getUid(), m2.getUid());
        // 2 : No cc
        // 3 : Cc : abc@abc.org
        // 4 : zzz@bcd.org
        // 5 : monkey@any.com
    }

    // TODO fix
    @Test
    protected void sortOnFromShouldWork() throws Exception {
        SearchQuery searchQuery = SearchQuery.builder()
            .andCriteria(SearchQuery.uid(new SearchQuery.UidRange(m2.getUid(), m5.getUid())))
            .sorts(new Sort(SortClause.MailboxFrom))
            .build();

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery).toStream())
            .containsExactly(m4.getUid(), m3.getUid(), m5.getUid(), m2.getUid());
        // m4 : "abcd" <jira@apache.org>
        // m3 : "efij" <jira1@apache.org>
        // m5 : "Eric Charles (JIRA)" <mailet-api@james.apache.org>
        // m2 : "Tellier Benoit (JIRA)" <jira2@apache.org>
    }

    @Test
    protected void sortOnToShouldWork() throws Exception {
        SearchQuery searchQuery = SearchQuery.builder()
            .andCriteria(SearchQuery.uid(new SearchQuery.UidRange(m2.getUid(), m5.getUid())))
            .sorts(new Sort(SortClause.MailboxTo))
            .build();

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery).toStream())
            .containsExactly(m3.getUid(), m2.getUid(), m4.getUid(), m5.getUid());
        // 3 : "aaa" <a-server-dev@james.apache.org>
        // 2 : "abc" <b-server-dev@james.apache.org>
        // 4 : "server" <c-server-dev@james.apache.org>
        // 5 : "zzz" <mailet-api@james.apache.org>
    }

    @Test
    void sortOnSubjectShouldWork() throws Exception {
        SearchQuery searchQuery = SearchQuery.builder()
            .andCriteria(SearchQuery.uid(new SearchQuery.UidRange(m2.getUid(), m5.getUid())))
            .sorts(new Sort(SortClause.BaseSubject))
            .build();

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery).toStream())
            .containsExactly(m4.getUid(), m3.getUid(), m2.getUid(), m5.getUid());
        // 2 : [jira] [Created] (MAILBOX-234) Convert Message into JSON
        // 3 : [jira] [Closed] (MAILBOX-217) We should index attachment in elastic search
        // 4 : [jira] [Closed] (MAILBOX-11) MailboxQuery ignore namespace
        // 5 : [jira] [Resolved] (MAILET-94) James Mailet should use latest version of other James subprojects
    }

    @Test
    void sortOnSizeShouldWork() throws Exception {
        SearchQuery.UidRange[] numericRanges = {new SearchQuery.UidRange(m2.getUid(), m5.getUid())};
        SearchQuery searchQuery = SearchQuery.builder()
            .andCriteria(SearchQuery.uid(numericRanges))
            .sorts(new Sort(SortClause.Size))
            .build();

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery).toStream())
            .containsExactly(m2.getUid(), m3.getUid(), m5.getUid(), m4.getUid());
        // 2 : 3210 o
        // 3 : 3647 o
        // 4 : 4360 o
        // 5 : 3653 o
    }

    @Test
    void sortOnSentDateShouldWork() throws Exception {
        SearchQuery.UidRange[] numericRanges = {new SearchQuery.UidRange(m2.getUid(), m5.getUid())};
        SearchQuery searchQuery = SearchQuery.builder()
            .andCriteria(SearchQuery.uid(numericRanges))
            .sorts(new Sort(SortClause.SentDate))
            .build();

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery).toStream())
            .containsExactly(m5.getUid(), m4.getUid(), m2.getUid(), m3.getUid());
        // 2 : 4 Jun 2015 09:23:37
        // 3 : 4 Jun 2015 09:27:37
        // 4 : 2 Jun 2015 08:16:19
        // 5 : 15 May 2015 06:35:59
    }

    @Test
    void sortOnIdShouldWork() throws Exception {
        SearchQuery.UidRange[] numericRanges = {new SearchQuery.UidRange(m2.getUid(), m5.getUid())};
        SearchQuery searchQuery = SearchQuery.builder()
            .andCriteria(SearchQuery.uid(numericRanges))
            .sorts(new Sort(SortClause.Uid))
            .build();

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery).toStream())
            .containsExactly(m2.getUid(), m3.getUid(), m4.getUid(), m5.getUid());
    }

    @Test
    void searchWithTextAttachmentShouldReturnMailsWhenAttachmentContentMatches() throws Exception {
        assumeTrue(storeMailboxManager.getSupportedSearchCapabilities().contains(MailboxManager.SearchCapabilities.Attachment));
        ComposedMessageId messageWithBeautifulBananaAsTextAttachment = myFolderMessageManager.appendMessage(
            MessageManager.AppendCommand.builder()
                .build(ClassLoaderUtils.getSystemResourceAsSharedStream("eml/emailWithTextAttachment.eml")),
            session).getId();
        awaitMessageCount(ImmutableList.of(), SearchQuery.matchAll(), 14);

        SearchQuery searchQuery = SearchQuery.of(SearchQuery.attachmentContains("beautiful banana"));

        assertThat(messageSearchIndex.search(session, mailbox2, searchQuery).toStream())
            .containsExactly(messageWithBeautifulBananaAsTextAttachment.getUid());
    }

    @Test
    void searchWithTextAttachmentShouldNotMatchMessageBody() throws Exception {
        assumeTrue(storeMailboxManager.getSupportedSearchCapabilities().contains(MailboxManager.SearchCapabilities.Attachment));
        myFolderMessageManager.appendMessage(
            MessageManager.AppendCommand.builder()
                .build(ClassLoaderUtils.getSystemResourceAsSharedStream("eml/emailWithTextAttachment.eml")),
            session).getId();
        awaitMessageCount(ImmutableList.of(), SearchQuery.matchAll(), 14);

        SearchQuery searchQuery = SearchQuery.of(SearchQuery.attachmentContains("message"));

        assertThat(messageSearchIndex.search(session, mailbox2, searchQuery).toStream())
            .isEmpty();
    }

    @Test
    void searchWithPDFAttachmentShouldReturnMailsWhenAttachmentContentMatches() throws Exception {
        assumeTrue(storeMailboxManager.getSupportedSearchCapabilities().contains(MailboxManager.SearchCapabilities.Attachment));
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
            .appendMessage(MessageManager.AppendCommand.from(message), session).getId();
        awaitMessageCount(ImmutableList.of(), SearchQuery.matchAll(), 14);

        SearchQuery searchQuery = SearchQuery.of(SearchQuery.attachmentContains("beautiful banana"));

        assertThat(messageSearchIndex.search(session, mailbox2, searchQuery).toStream())
            .containsExactly(messageWithBeautifulBananaAsPDFAttachment.getUid());
    }

    @Test
    void sortShouldNotDiscardResultWhenSearchingFieldIsIdentical() throws Exception {
        SearchQuery searchQuery = SearchQuery.allSortedWith(new Sort(SortClause.Arrival));

        List<MessageId> actual = messageSearchIndex.search(
            session,
            ImmutableList.of(mailbox.getMailboxId(), mailbox2.getMailboxId()),
            searchQuery,
            LIMIT)
            .collectList().block();

        assertThat(actual).containsOnly(m1.getMessageId(), m2.getMessageId(), m3.getMessageId(), m4.getMessageId(), m5.getMessageId(),
            m6.getMessageId(), m7.getMessageId(), m8.getMessageId(), m9.getMessageId(), mOther.getMessageId(), mailWithAttachment.getMessageId(), mailWithInlinedAttachment.getMessageId());
    }

    @Test
    void searchShouldOrderByInternalDateWhenSortOnSentDateAndNoCorrespondingHeader() throws Exception {
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
            session).getId();
        ComposedMessageId message2 = messageManager.appendMessage(MessageManager.AppendCommand.builder()
            .withInternalDate(date2)
            .build(Message.Builder.of()
                .setSubject("test")
                .setBody("testmail", StandardCharsets.UTF_8)),
            session).getId();
        ComposedMessageId message3 = messageManager.appendMessage(MessageManager.AppendCommand.builder()
            .withInternalDate(date3)
            .build(Message.Builder.of()
                .setSubject("test")
                .setBody("testmail", StandardCharsets.UTF_8)),
            session).getId();

        awaitMessageCount(ImmutableList.of(), SearchQuery.matchAll(), 16);

        SearchQuery searchQuery = SearchQuery.allSortedWith(new Sort(SortClause.SentDate));

        assertThat(Flux.from(messageManager.search(searchQuery, session)).toStream())
            .containsExactly(message2.getUid(),
                message1.getUid(),
                message3.getUid());
    }

    @Test
    void searchShouldOrderBySentDateThenInternalDateWhenSortOnSentDateAndNonHomogeneousCorrespondingHeader() throws Exception {
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
                .setBody("testmail", StandardCharsets.UTF_8)), session).getId();
        ComposedMessageId message2 = messageManager.appendMessage(MessageManager.AppendCommand.builder()
            .withInternalDate(date2)
            .build(Message.Builder.of()
                .setSubject("test")
                .setDate(new SimpleDateFormat("yyyy/MM/dd HH:mm:ss")
                    .parse("2017/08/23 00:00:00 "), TimeZone.getTimeZone(ZoneId.of("+0200")))
                .setBody("testmail", StandardCharsets.UTF_8)),
            session).getId();
        ComposedMessageId message3 = messageManager.appendMessage(MessageManager.AppendCommand.builder()
            .withInternalDate(date3)
            .build(Message.Builder.of()
                .setSubject("test")
                .setBody("testmail", StandardCharsets.UTF_8)), session).getId();

        awaitMessageCount(ImmutableList.of(), SearchQuery.matchAll(), 16);

        SearchQuery searchQuery = SearchQuery.allSortedWith(new Sort(SortClause.SentDate));

        assertThat(Flux.from(messageManager.search(searchQuery, session)).toStream())
            .containsExactly(message2.getUid(),
                message1.getUid(),
                message3.getUid());
    }

    @Test
    void searchShouldRetrieveExactlyMatchingMimeMessageID() throws Exception {
        SearchQuery searchQuery = SearchQuery.of(SearchQuery.mimeMessageID("<JIRA.12781874.1426269127000.9353.1433410057953@Atlassian.JIRA>"));
        // Correspond to mail.eml

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery).toStream())
            .containsOnly(m3.getUid());
    }

    @Test
    void copiedMessageShouldAllBeIndexed() throws Exception {
        MailboxPath newBoxPath = MailboxPath.forUser(USERNAME, "newBox");
        MailboxId newBoxId = storeMailboxManager.createMailbox(newBoxPath, session).get();

        storeMailboxManager.copyMessages(MessageRange.all(), inboxMessageManager.getId(), newBoxId, session);

        SearchQuery searchQuery = SearchQuery.matchAll();

        StoreMessageManager newBox = (StoreMessageManager) storeMailboxManager.getMailbox(newBoxId, session);

        Awaitility.with()
            .pollInterval(ONE_HUNDRED_MILLISECONDS)
            .and().with()
            .pollDelay(Duration.ofMillis(1))
            .await()
            .atMost(30, TimeUnit.SECONDS)
            .until(
                () -> messageSearchIndex.search(session, newBox.getMailboxEntity(), searchQuery).toStream().count() == 9);
    }

    @Test
    void searchShouldRetrieveMailByAttachmentFileName() throws Exception {
        assumeTrue(messageSearchIndex.getSupportedCapabilities(storeMailboxManager.getSupportedMessageCapabilities())
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
            session).getId();

        awaitMessageCount(ImmutableList.of(), SearchQuery.matchAll(), 14);

        SearchQuery searchQuery = SearchQuery.of(SearchQuery.attachmentFileName(fileName));

        assertThat(messageSearchIndex.search(session, mailbox, searchQuery).toStream())
            .containsOnly(mWithFileName.getUid());
    }

    @Test
    void givenThreeMailsInAThreadThenGetThreadShouldReturnAListWithThreeMessageIdsInThatThread() throws MailboxException {
        MailboxMessage message1 = createMessage(quanMailbox, ThreadId.fromBaseMessageId(newBasedMessageId));
        MailboxMessage message2 = createMessage(quanMailbox, ThreadId.fromBaseMessageId(newBasedMessageId));
        MailboxMessage message3 = createMessage(quanMailbox, ThreadId.fromBaseMessageId(newBasedMessageId));

        appendMessageThenDispatchAddedEvent(quanMailbox, message1);
        appendMessageThenDispatchAddedEvent(quanMailbox, message2);
        appendMessageThenDispatchAddedEvent(quanMailbox, message3);

        awaitMessageCount(ImmutableList.of(), SearchQuery.matchAll(), 16);

        SearchQuery searchQuery = SearchQuery.of(SearchQuery.threadId(ThreadId.fromBaseMessageId(newBasedMessageId)));
        List<MessageId> actual = messageSearchIndex.search(quanSession, ImmutableList.of(quanMailbox.getMailboxId()), searchQuery, LIMIT)
            .collectList().block();

        assertThat(actual).isEqualTo(ImmutableList.of(message1.getMessageId(), message2.getMessageId(), message3.getMessageId()));
    }

    @Test
    void givenAMailInAThreadThenGetThreadShouldReturnAListWithOnlyOneMessageIdInThatThread() throws MailboxException {
        MailboxMessage message1 = createMessage(quanMailbox, ThreadId.fromBaseMessageId(newBasedMessageId));

        appendMessageThenDispatchAddedEvent(quanMailbox, message1);

        awaitMessageCount(ImmutableList.of(), SearchQuery.matchAll(), 14);

        SearchQuery searchQuery = SearchQuery.of(SearchQuery.threadId(ThreadId.fromBaseMessageId(newBasedMessageId)));
        List<MessageId> actual = messageSearchIndex.search(quanSession, ImmutableList.of(quanMailbox.getMailboxId()), searchQuery, LIMIT)
            .collectList().block();

        assertThat(actual).containsOnly(message1.getMessageId());
    }

    @Test
    void givenTwoDistinctThreadsThenGetThreadShouldNotReturnUnrelatedMails() throws MailboxException {
        // given message1 and message2 in thread1, message3 in thread2
        ThreadId threadId1 = ThreadId.fromBaseMessageId(newBasedMessageId);
        ThreadId threadId2 = ThreadId.fromBaseMessageId(otherBasedMessageId);
        MailboxMessage message1 = createMessage(quanMailbox, threadId1);
        MailboxMessage message2 = createMessage(quanMailbox, threadId1);
        MailboxMessage message3 = createMessage(quanMailbox, threadId2);

        appendMessageThenDispatchAddedEvent(quanMailbox, message1);
        appendMessageThenDispatchAddedEvent(quanMailbox, message2);
        appendMessageThenDispatchAddedEvent(quanMailbox, message3);

        awaitMessageCount(ImmutableList.of(), SearchQuery.matchAll(), 16);

        // then get thread2 should not return unrelated message1 and message2
        SearchQuery searchQuery = SearchQuery.of(SearchQuery.threadId(threadId2));
        List<MessageId> actual = messageSearchIndex.search(quanSession, ImmutableList.of(quanMailbox.getMailboxId()), searchQuery, LIMIT)
            .collectList().block();

        assertThat(actual).doesNotContain(message1.getMessageId(), message2.getMessageId());
    }

    @Test
    void givenNonThreadThenGetThreadShouldReturnEmptyListMessageId() throws MailboxException {
        // given non messages in thread1
        ThreadId threadId1 = ThreadId.fromBaseMessageId(newBasedMessageId);

        // then get thread1 should return empty list messageId
        SearchQuery searchQuery = SearchQuery.of(SearchQuery.threadId(threadId1));
        List<MessageId> actual = messageSearchIndex.search(quanSession, ImmutableList.of(quanMailbox.getMailboxId()), searchQuery, LIMIT)
            .collectList().block();

        assertThat(actual).isEmpty();
    }

    private void appendMessageThenDispatchAddedEvent(Mailbox mailbox, MailboxMessage mailboxMessage) throws MailboxException {
        MessageMetaData messageMetaData = messageMapper.add(mailbox, mailboxMessage);
        eventBus.dispatch(EventFactory.added()
                .randomEventId()
                .mailboxSession(quanSession)
                .mailbox(quanMailbox)
                .addMetaData(messageMetaData)
                .isDelivery(!IS_DELIVERY)
                .isAppended(IS_APPENDED)
                .build(),
            new MailboxIdRegistrationKey(quanMailbox.getMailboxId())).block();
    }

    private SimpleMailboxMessage createMessage(Mailbox mailbox, ThreadId threadId) {
        MessageId messageId = messageIdFactory.generate();
        String content = "Some content";
        int bodyStart = 16;
        return new SimpleMailboxMessage(messageId,
            threadId,
            new Date(),
            content.length(),
            bodyStart,
            new ByteContent(content.getBytes()),
            new Flags(),
            new PropertyBuilder().build(),
            mailbox.getMailboxId());
    }

}
