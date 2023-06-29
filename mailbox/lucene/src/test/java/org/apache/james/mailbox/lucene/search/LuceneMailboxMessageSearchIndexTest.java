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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.james.mailbox.store.MessageIdManagerTestSystem.MOD_SEQ;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import javax.mail.Flags;
import javax.mail.Flags.Flag;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.model.SearchQuery.AddressType;
import org.apache.james.mailbox.model.SearchQuery.DateResolution;
import org.apache.james.mailbox.model.SearchQuery.Sort;
import org.apache.james.mailbox.model.SearchQuery.Sort.Order;
import org.apache.james.mailbox.model.SearchQuery.Sort.SortClause;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.model.TestMessageId;
import org.apache.james.mailbox.model.UidValidity;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.store.MessageBuilder;
import org.apache.james.mailbox.store.search.ListeningMessageSearchIndex;
import org.apache.james.mailbox.store.search.ListeningMessageSearchIndexContract;
import org.apache.lucene.store.RAMDirectory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

class LuceneMailboxMessageSearchIndexTest {
    static final long LIMIT = 100L;
    static final TestId TEST_ID_1 = TestId.of(0);
    static final TestId TEST_ID_2 = TestId.of(1);
    static final TestId TEST_ID_3 = TestId.of(2);

    static final Username BOB = Username.of("bob");
    Mailbox mailbox = new Mailbox(MailboxPath.forUser(BOB, "box"), UidValidity.of(18), TEST_ID_1);
    Mailbox mailbox2 = new Mailbox(MailboxPath.forUser(BOB, "box"), UidValidity.of(19), TEST_ID_2);
    Mailbox mailbox3 = new Mailbox(MailboxPath.forUser(BOB, "box"), UidValidity.of(12), TEST_ID_3);
    LuceneMessageSearchIndex index;
    MailboxSession session;

    static final String FROM_ADDRESS = "Harry <harry@example.org>";

    static final String SUBJECT_PART = "Mixed";

    static final String CUSTARD = "CUSTARD";

    static final String RHUBARD = "Rhubard";

    static final String BODY = "This is a simple email\r\n "
            + "It has " + RHUBARD + ".\r\n" + "It has " + CUSTARD + ".\r\n"
            + "It needs naught else.\r\n";

    TestMessageId.Factory factory;

    MessageUid uid1;
    MessageUid uid2;
    MessageUid uid3;
    MessageUid uid4;
    MessageUid uid5;
    MessageId id1;
    MessageId id2;
    MessageId id3;
    MessageId id4;
    MessageId id5;

    protected boolean useLenient() {
        return true;
    }
    
    @BeforeEach
    void setUp() throws Exception {
        session = MailboxSessionUtil.create(Username.of("username"));
        factory = new TestMessageId.Factory();
        id1 = factory.generate();
        id2 = factory.generate();
        id3 = factory.generate();
        id4 = factory.generate();
        id5 = factory.generate();
        index = new LuceneMessageSearchIndex(null, new TestId.Factory(), new RAMDirectory(), true, useLenient(), factory, null);
        index.setEnableSuffixMatch(true);
        Map<String, String> headersSubject = new HashMap<>();
        headersSubject.put("Subject", "test (fwd)");
        headersSubject.put("From", "test99 <test99@localhost>");
        headersSubject.put("To", "test2 <test2@localhost>, test3 <test3@localhost>");

        Map<String, String> headersTest = new HashMap<>();
        headersTest.put("Test", "test");
        headersTest.put("From", "test1 <test1@localhost>");
        headersTest.put("To", "test3 <test3@localhost>, test4 <test4@localhost>");
        headersTest.put("Cc", "test21 <test21@localhost>, test6 <test6@foobar>");

        Map<String, String> headersTestSubject = new HashMap<>();
        headersTestSubject.put("Test", "test");
        headersTestSubject.put("Subject", "test2");
        headersTestSubject.put("Date", "Thu, 14 Feb 1990 12:00:00 +0000 (GMT)");
        headersTestSubject.put("From", "test12 <test12@localhost>");
        headersTestSubject.put("Cc", "test211 <test21@localhost>, test6 <test6@foobar>");
        
        uid1 = MessageUid.of(1);
        MessageBuilder builder1 = new MessageBuilder()
            .headers(headersSubject)
            .flags(new Flags(Flag.ANSWERED))
            .mailboxId(TEST_ID_1)
            .uid(uid1)
            .internalDate(new Date())
            .body("My Body".getBytes(UTF_8))
            .size(200);
        index.add(session, mailbox, builder1.build(id1)).block();

        uid2 = MessageUid.of(1);
        MessageBuilder builder2 = new MessageBuilder()
            .headers(headersSubject)
            .flags(new Flags(Flag.ANSWERED))
            .mailboxId(TEST_ID_2)
            .uid(uid2)
            .internalDate(new Date())
            .body("My Body".getBytes(UTF_8))
            .size(20);
        index.add(session, mailbox2, builder2.build(id2)).block();
        
        uid3 = MessageUid.of(2);
        Calendar cal = Calendar.getInstance();
        cal.set(1980, 2, 10);
        MessageBuilder builder3 = new MessageBuilder()
            .headers(headersTest)
            .flags(new Flags(Flag.DELETED))
            .mailboxId(TEST_ID_1)
            .uid(uid3)
            .internalDate(cal.getTime())
            .body("My Otherbody".getBytes(UTF_8))
            .size(20);
        index.add(session, mailbox, builder3.build(id3)).block();
        
        uid4 = MessageUid.of(3);
        Calendar cal2 = Calendar.getInstance();
        cal2.set(8000, 2, 10);
        MessageBuilder builder4 = new MessageBuilder()
            .headers(headersTestSubject)
            .flags(new Flags(Flag.DELETED))
            .mailboxId(TEST_ID_1)
            .uid(uid4)
            .internalDate(cal2.getTime())
            .body("My Otherbody2".getBytes(UTF_8))
            .size(20);
        index.add(session, mailbox, builder4.build(id4)).block();
        
        uid5 = MessageUid.of(10);
        MessageBuilder builder = new MessageBuilder();
        builder.header("From", "test <user-from@domain.org>");
        builder.header("To", FROM_ADDRESS);
        builder.header("Subject", "A " + SUBJECT_PART + " Multipart Mail");
        builder.header("Date", "Thu, 14 Feb 2008 12:00:00 +0000 (GMT)");
        builder.body(StandardCharsets.US_ASCII.encode(BODY).array());
        builder.uid(uid5);
        builder.mailboxId(TEST_ID_3);
        index.add(session, mailbox3, builder.build(id5)).block();

    }

    @Test
    void bodySearchShouldMatchPhraseInBody() throws Exception {
        SearchQuery query = SearchQuery.of(SearchQuery.bodyContains(CUSTARD));
        Stream<MessageUid> result = index.search(session, mailbox3, query).toStream();
        assertThat(result).containsExactly(uid5);
    }

    @Test
    void bodySearchShouldNotMatchAbsentPhraseInBody() throws Exception {
        SearchQuery query = SearchQuery.of(SearchQuery.bodyContains(CUSTARD + CUSTARD));
        Stream<MessageUid> result = index.search(session, mailbox3, query).toStream();
        assertThat(result).isEmpty();
    }
    
    @Test
    void bodySearchShouldBeCaseInsensitive() throws Exception {
        SearchQuery query = SearchQuery.of(SearchQuery.bodyContains(RHUBARD));
        Stream<MessageUid> result = index.search(session, mailbox3, query).toStream();
        assertThat(result).containsExactly(uid5);
    }

    @Test
    void bodySearchNotMatchPhraseOnlyInFrom() throws Exception {
        SearchQuery query = SearchQuery.of(SearchQuery.bodyContains(FROM_ADDRESS));
        Stream<MessageUid> result = index.search(session, mailbox3, query).toStream();
        assertThat(result).isEmpty();
    }

    @Test
    void bodySearchShouldNotMatchPhraseOnlyInSubject() throws Exception {
        SearchQuery query = SearchQuery.of(SearchQuery.bodyContains(SUBJECT_PART));
        Stream<MessageUid> result = index.search(session, mailbox3, query).toStream();
        assertThat(result).isEmpty();
    }

    @Test
    void dateHeaderParsingShouldNotImpactProcessing() throws Exception {
        TestId mailboxId = TestId.of(2);

        MessageId id = factory.generate();
        MessageUid uid = MessageUid.of(500);
        String subject = "Mail " + id;

        MessageBuilder builder = new MessageBuilder()
                .header("Subject", subject)
                .header("Date", "31.3.2009")
                .uid(uid)
                .mailboxId(mailboxId);

        index.add(session, mailbox3, builder.build(id)).block();

        SearchQuery query = SearchQuery.of(SearchQuery.headerContains("Subject", subject));
        Stream<MessageUid> result = index.search(session, mailbox3, query).toStream();
        assertThat(result).containsExactly(uid);
    }

    @Test
    void textSearchShouldMatchPhraseInBody() throws Exception {
        SearchQuery query = SearchQuery.of(SearchQuery.mailContains(CUSTARD));
        Stream<MessageUid> result = index.search(session, mailbox3, query).toStream();
        assertThat(result).containsExactly(uid5);
    }

    @Test
    void textSearchShouldNotAbsentMatchPhraseInBody() throws Exception {
        SearchQuery query = SearchQuery.of(SearchQuery.mailContains(CUSTARD + CUSTARD));
        Stream<MessageUid> result = index.search(session, mailbox3, query).toStream();
        assertThat(result).isEmpty();
    }

    @Test
    void textSearchMatchShouldBeCaseInsensitive() throws Exception {
        SearchQuery query = SearchQuery.of(SearchQuery.mailContains(RHUBARD.toLowerCase(Locale.US)));
        Stream<MessageUid> result = index.search(session, mailbox3, query).toStream();
        assertThat(result).containsExactly(uid5);
    }

    @Test
    void addressSearchShouldMatchToFullAddress() throws Exception {
        SearchQuery query = SearchQuery.of(SearchQuery.address(AddressType.To,FROM_ADDRESS));
        Stream<MessageUid> result = index.search(session, mailbox3, query).toStream();
        assertThat(result).containsExactly(uid5);
    }

    @Test
    void addressSearchShouldMatchToDisplayName() throws Exception {
        SearchQuery query = SearchQuery.of(SearchQuery.address(AddressType.To,"Harry"));
        Stream<MessageUid> result = index.search(session, mailbox3, query).toStream();
        assertThat(result).containsExactly(uid5);
    }
    
    @Test
    void addressSearchShouldMatchToEmail() throws Exception {
        SearchQuery query = SearchQuery.of(SearchQuery.address(AddressType.To,"Harry@example.org"));
        Stream<MessageUid> result = index.search(session, mailbox3, query).toStream();
        assertThat(result).containsExactly(uid5);
    }
    
    @Test
    void addressSearchShouldMatchFrom() throws Exception {
        SearchQuery query = SearchQuery.of(SearchQuery.address(AddressType.From,"ser-from@domain.or"));
        Stream<MessageUid> result = index.search(session, mailbox3, query).toStream();
        assertThat(result).containsExactly(uid5);
    }

    @Test
    void textSearchShouldMatchPhraseOnlyInToHeader() throws Exception {
        SearchQuery query = SearchQuery.of(SearchQuery.mailContains(FROM_ADDRESS));
        Stream<MessageUid> result = index.search(session, mailbox3, query).toStream();
        assertThat(result).containsExactly(uid5);
    }
    
    @Test
    void textSearchShouldMatchPhraseOnlyInSubjectHeader() throws Exception {
        SearchQuery query = SearchQuery.of(SearchQuery.mailContains(SUBJECT_PART));
        Stream<MessageUid> result = index.search(session, mailbox3, query).toStream();
        assertThat(result).containsExactly(uid5);
    }
    
    @Test
    void searchAllShouldMatchAllMailboxEmails() throws Exception {
        SearchQuery query = SearchQuery.of(SearchQuery.all());
        Stream<MessageUid> result = index.search(session, mailbox2, query).toStream();
        assertThat(result).containsExactly(uid2);
    }

    @Test
    void searchBodyInAllMailboxesShouldMatch() throws Exception {
        SearchQuery query = SearchQuery.of(SearchQuery.bodyContains("My Body"));

        List<MessageId> result = index.search(session, ImmutableList.of(mailbox.getMailboxId(), mailbox2.getMailboxId(), mailbox3.getMailboxId()), query, LIMIT)
            .collectList().block();

        assertThat(result).containsOnly(id1, id2);
    }

    @Test
    void searchBodyInSpecificMailboxesShouldMatch() throws Exception {
        SearchQuery query = SearchQuery.of(SearchQuery.bodyContains("My Body"));

        List<MessageId> result = index.search(session,
                ImmutableList.of(mailbox.getMailboxId(), mailbox3.getMailboxId()),
                query,
                LIMIT)
            .collectList().block();

        assertThat(result).containsOnly(id1);
    }

    @Test
    void searchAllShouldMatchAllUserEmails() throws Exception {
        SearchQuery query = SearchQuery.of(SearchQuery.all());

        List<MessageId> result = index.search(session, ImmutableList.of(mailbox.getMailboxId(), mailbox2.getMailboxId(), mailbox3.getMailboxId()), query, LIMIT)
            .collectList().block();

        // The query is not limited to one mailbox and we have 5 indexed messages
        assertThat(result).hasSize(5);
    }

    @Test
    void searchAllShouldLimitTheSize() throws Exception {
        SearchQuery query = SearchQuery.of(SearchQuery.all());

        int limit = 1;
        List<MessageId> result = index.search(session, ImmutableList.of(mailbox.getMailboxId(), mailbox2.getMailboxId(), mailbox3.getMailboxId()), query, limit)
            .collectList().block();

        assertThat(result).hasSize(limit);
    }
    
    @Test
    void flagSearchShouldMatch() throws Exception {
        SearchQuery query = SearchQuery.of(SearchQuery.flagIsSet(Flag.DELETED));
        Stream<MessageUid> result = index.search(session, mailbox, query).toStream();
        assertThat(result).containsExactly(uid3, uid4);
    }
    
    @Test
    void bodySearchShouldMatchSeveralEmails() throws Exception {    
        SearchQuery query = SearchQuery.of(SearchQuery.bodyContains("body"));
        Stream<MessageUid> result = index.search(session, mailbox, query).toStream();
        assertThat(result).containsExactly(uid1, uid3, uid4);
    }
    
    @Test
    void textSearchShouldMatchSeveralEmails() throws Exception {    
        SearchQuery query = SearchQuery.of(SearchQuery.mailContains("body"));
        Stream<MessageUid> result = index.search(session, mailbox, query).toStream();
        assertThat(result).containsExactly(uid1, uid3, uid4);
    }
    
    @Test
    void headerSearchShouldMatch() throws Exception {
        SearchQuery query = SearchQuery.of(SearchQuery.headerContains("Subject", "test"));
        Stream<MessageUid> result = index.search(session, mailbox, query).toStream();
        assertThat(result).containsExactly(uid1, uid4);
    }
    
    @Test
    void headerExistsShouldMatch() throws Exception {
        SearchQuery query = SearchQuery.of(SearchQuery.headerExists("Subject"));
        Stream<MessageUid> result = index.search(session, mailbox, query).toStream();
        assertThat(result).containsExactly(uid1, uid4);
    }
    
    @Test
    void flagUnsetShouldMatch() throws Exception {
        SearchQuery query = SearchQuery.of(SearchQuery.flagIsUnSet(Flag.DRAFT));
        Stream<MessageUid> result = index.search(session, mailbox, query).toStream();
        assertThat(result).containsExactly(uid1, uid3, uid4);
    }
    
    @Test
    void internalDateBeforeShouldMatch() throws Exception {
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());
        SearchQuery query = SearchQuery.of(SearchQuery.internalDateBefore(cal.getTime(), DateResolution.Day));
        
        Stream<MessageUid> result = index.search(session, mailbox, query).toStream();
        assertThat(result).containsExactly(uid3);
    }
    
    
    @Test
    void internalDateAfterShouldMatch() throws Exception {
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());
        SearchQuery query = SearchQuery.of(SearchQuery.internalDateAfter(cal.getTime(), DateResolution.Day));
        Stream<MessageUid> result = index.search(session, mailbox, query).toStream();
        assertThat(result).containsExactly(uid4);
    }
    
    
    
    @Test
    void internalDateOnShouldMatch() throws Exception {
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());
        SearchQuery query = SearchQuery.of(SearchQuery.internalDateOn(cal.getTime(), DateResolution.Day));
        Stream<MessageUid> result = index.search(session, mailbox, query).toStream();
        assertThat(result).containsExactly(uid1);
    }
    
    @Test
    void uidSearchShouldMatch() throws Exception {
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());
        SearchQuery query = SearchQuery.of(SearchQuery.uid(new SearchQuery.UidRange(uid1)));
        Stream<MessageUid> result = index.search(session, mailbox, query).toStream();
        assertThat(result).containsExactly(uid1);
    }
    
    @Test
    void uidRangeSearchShouldMatch() throws Exception {
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());
        SearchQuery query = SearchQuery.of(SearchQuery.uid(new SearchQuery.UidRange(uid1), new SearchQuery.UidRange(uid3,uid4)));
        Stream<MessageUid> result = index.search(session, mailbox, query).toStream();
        assertThat(result).containsExactly(uid1, uid3, uid4);
    }
    
    @Test
    void sizeEqualsShouldMatch() throws Exception {
        SearchQuery query = SearchQuery.of(SearchQuery.sizeEquals(200));
        Stream<MessageUid> result = index.search(session, mailbox, query).toStream();
        assertThat(result).containsExactly(uid1);
    }
    
    @Test
    void sizeLessThanShouldMatch() throws Exception {
        SearchQuery query = SearchQuery.of(SearchQuery.sizeLessThan(200));
        Stream<MessageUid> result = index.search(session, mailbox, query).toStream();
        assertThat(result).containsExactly(uid3, uid4);
    }
    
    @Test
    void sizeGreaterThanShouldMatch() throws Exception {
        SearchQuery query = SearchQuery.of(SearchQuery.sizeGreaterThan(6));
        Stream<MessageUid> result = index.search(session, mailbox, query).toStream();
        assertThat(result).containsExactly(uid1, uid3, uid4);
    }
    
    @Test
    void uidShouldBeSorted() throws Exception {
        SearchQuery query = SearchQuery.of(SearchQuery.all());
        Stream<MessageUid> result = index.search(session, mailbox, query).toStream();
        assertThat(result).containsExactly(uid1, uid3, uid4);
    }
    
    @Test
    void uidReverseSortShouldReturnWellOrderedResults() throws Exception {
        SearchQuery query = SearchQuery.allSortedWith(new Sort(SortClause.Uid, Order.REVERSE));

        Stream<MessageUid> result = index.search(session, mailbox, query).toStream();
        assertThat(result).containsExactly(uid4, uid3, uid1);
    }
    
    @Test
    void sortOnSentDateShouldReturnWellOrderedResults() throws Exception {
        SearchQuery query = SearchQuery.allSortedWith(new Sort(SortClause.SentDate, Order.NATURAL));

        Stream<MessageUid> result = index.search(session, mailbox, query).toStream();
        assertThat(result).containsExactly(uid3, uid4, uid1);
    }
    
    @Test
    void reverseSortOnSentDateShouldReturnWellOrderedResults() throws Exception {
        SearchQuery query = SearchQuery.allSortedWith(new Sort(SortClause.SentDate, Order.REVERSE));

        Stream<MessageUid> result = index.search(session, mailbox, query).toStream();
        assertThat(result).containsExactly(uid1, uid4, uid3);
    }

    @Test
    void sortOnSubjectShouldReturnWellOrderedResults() throws Exception {
        SearchQuery query = SearchQuery.allSortedWith(new Sort(SortClause.BaseSubject, Order.NATURAL));

        Stream<MessageUid> result = index.search(session, mailbox, query).toStream();
        assertThat(result).containsExactly(uid3, uid1, uid4);
    }
    
    @Test
    void reverseSortOnSubjectShouldReturnWellOrderedResults() throws Exception {
        SearchQuery query = SearchQuery.allSortedWith(new Sort(SortClause.BaseSubject, Order.REVERSE));

        Stream<MessageUid> result = index.search(session, mailbox, query).toStream();
        assertThat(result).containsExactly(uid4, uid1, uid3);
    }
    
    @Test
    void sortOnMailboxFromShouldReturnWellOrderedResults() throws Exception {
        SearchQuery query = SearchQuery.allSortedWith(new Sort(SortClause.MailboxFrom, Order.NATURAL));

        Stream<MessageUid> result = index.search(session, mailbox, query).toStream();
        assertThat(result).containsExactly(uid3, uid4, uid1);
    }
    
    @Test
    void reverseSortOnMailboxFromShouldReturnWellOrderedResults() throws Exception {
        SearchQuery query = SearchQuery.allSortedWith(new Sort(SortClause.MailboxFrom, Order.REVERSE));

        Stream<MessageUid> result = index.search(session, mailbox, query).toStream();
        assertThat(result).containsExactly(uid1, uid4, uid3);
    }
    
    @Test
    void sortOnMailboxCCShouldReturnWellOrderedResults() throws Exception {
        SearchQuery query = SearchQuery.allSortedWith(new Sort(SortClause.MailboxCc, Order.NATURAL));

        Stream<MessageUid> result = index.search(session, mailbox, query).toStream();
        assertThat(result).containsExactly(uid1, uid3, uid4);
    }
    
    @Test
    void reverseSortOnMailboxCCShouldReturnWellOrderedResults() throws Exception {
        SearchQuery query = SearchQuery.allSortedWith(new Sort(SortClause.MailboxCc, Order.REVERSE));

        Stream<MessageUid> result = index.search(session, mailbox, query).toStream();
        assertThat(result).containsExactly(uid3, uid4, uid1);
    }
    
    @Test
    void sortOnMailboxToShouldReturnWellOrderedResults() throws Exception {
        SearchQuery query = SearchQuery.allSortedWith(new Sort(SortClause.MailboxTo, Order.NATURAL));

        Stream<MessageUid> result = index.search(session, mailbox, query).toStream();
        assertThat(result).containsExactly(uid4, uid1, uid3);
    }
    
    @Test
    void reverseSortOnMailboxToShouldReturnWellOrderedResults() throws Exception {
        SearchQuery query = SearchQuery.allSortedWith(new Sort(SortClause.MailboxTo, Order.REVERSE));

        Stream<MessageUid> result = index.search(session, mailbox, query).toStream();
        assertThat(result).containsExactly(uid3, uid1, uid4);
    }
    
    @Test
    void sortOnArrivalDateShouldReturnWellOrderedResults() throws Exception {
        SearchQuery query = SearchQuery.allSortedWith(new Sort(SortClause.Arrival, Order.NATURAL));

        Stream<MessageUid> result = index.search(session, mailbox, query).toStream();
        assertThat(result).containsExactly(uid3, uid1, uid4);
    }
    
    @Test
    void reverseSortOnArrivalDateShouldReturnWellOrderedResults() throws Exception {
        SearchQuery query = SearchQuery.allSortedWith(new Sort(SortClause.Arrival, Order.REVERSE));

        Stream<MessageUid> result = index.search(session, mailbox, query).toStream();
        assertThat(result).containsExactly(uid4, uid1, uid3);
    }
    
    @Test
    void sortOnSizeShouldReturnWellOrderedResults() throws Exception {
        SearchQuery query = SearchQuery.allSortedWith(new Sort(SortClause.Size, Order.NATURAL));

        Stream<MessageUid> result = index.search(session, mailbox, query).toStream();
        assertThat(result).containsExactly(uid3, uid4, uid1);
    }
    
    @Test
    void reverseSortOnSizeShouldReturnWellOrderedResults() throws Exception {
        SearchQuery query = SearchQuery.allSortedWith(new Sort(SortClause.Size, Order.REVERSE));

        Stream<MessageUid> result = index.search(session, mailbox, query).toStream();
        assertThat(result).containsExactly(uid1, uid3, uid4);
    }
    
    @Test
    void notOperatorShouldReverseMatching() throws Exception {
        SearchQuery query = SearchQuery.of(SearchQuery.not(SearchQuery.uid(new SearchQuery.UidRange(uid1))));

        Stream<MessageUid> result = index.search(session, mailbox, query).toStream();
        assertThat(result).containsExactly(uid3, uid4);
    }

    @Test
    void updateShouldUpdateFlags() throws Exception {
        Flags newFlags = new Flags(Flags.Flag.DRAFT);
        UpdatedFlags updatedFlags = UpdatedFlags.builder()
            .uid(uid2)
            .modSeq(MOD_SEQ)
            .oldFlags(new Flags(Flag.ANSWERED))
            .newFlags(newFlags)
            .build();

        index.update(session, mailbox.getMailboxId(), Lists.newArrayList(updatedFlags)).block();

        SearchQuery query = SearchQuery.of(SearchQuery.flagIsSet(Flags.Flag.DRAFT));
        assertThat(index.search(session, mailbox, query).toStream())
            .containsExactly(uid2);
    }

    @Test
    void updateShouldNotUpdateNorThrowOnUnknownMessageUid() throws Exception {
        Flags newFlags = new Flags(Flags.Flag.DRAFT);
        UpdatedFlags updatedFlags = UpdatedFlags.builder()
            .uid(MessageUid.of(42))
            .modSeq(MOD_SEQ)
            .oldFlags(new Flags())
            .newFlags(newFlags)
            .build();

        index.update(session, mailbox.getMailboxId(), Lists.newArrayList(updatedFlags)).block();

        SearchQuery query = SearchQuery.of(SearchQuery.flagIsSet(Flags.Flag.DRAFT));
        assertThat(index.search(session, mailbox, query).toStream())
            .isEmpty();
    }

    @Test
    void updateShouldBeIdempotent() throws Exception {
        Flags newFlags = new Flags(Flags.Flag.DRAFT);
        UpdatedFlags updatedFlags = UpdatedFlags.builder()
            .uid(uid2)
            .modSeq(MOD_SEQ)
            .oldFlags(new Flags())
            .newFlags(newFlags)
            .build();

        index.update(session, mailbox.getMailboxId(), Lists.newArrayList(updatedFlags)).block();
        index.update(session, mailbox.getMailboxId(), Lists.newArrayList(updatedFlags)).block();

        SearchQuery query = SearchQuery.of(SearchQuery.flagIsSet(Flags.Flag.DRAFT));
        assertThat(index.search(session, mailbox, query).toStream())
            .containsExactly(uid2);
    }

    @Nested
    class RetrieveIndexedFlags implements ListeningMessageSearchIndexContract {
        @Override
        public ListeningMessageSearchIndex testee() {
            return index;
        }

        @Override
        public MailboxSession session() {
            return session;
        }

        @Override
        public Mailbox mailbox() {
            return mailbox;
        }
    }
}
