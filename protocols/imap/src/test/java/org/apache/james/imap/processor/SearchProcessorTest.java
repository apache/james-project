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

package org.apache.james.imap.processor;

import static org.apache.james.imap.ImapFixture.TAG;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.TimeZone;

import jakarta.mail.Flags;
import jakarta.mail.Flags.Flag;

import org.apache.james.core.Username;
import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.IdRange;
import org.apache.james.imap.api.message.UidRange;
import org.apache.james.imap.api.message.request.DayMonthYear;
import org.apache.james.imap.api.message.request.SearchKey;
import org.apache.james.imap.api.message.request.SearchOperation;
import org.apache.james.imap.api.message.response.StatusResponse;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.api.process.SelectedMailbox;
import org.apache.james.imap.encode.FakeImapSession;
import org.apache.james.imap.message.request.SearchRequest;
import org.apache.james.imap.message.response.SearchResponse;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.model.SearchQuery.AddressType;
import org.apache.james.mailbox.model.SearchQuery.Criterion;
import org.apache.james.mailbox.model.SearchQuery.DateResolution;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class SearchProcessorTest {
    private static final int DAY = 6;

    private static final int MONTH = 6;

    private static final int YEAR = 1944;

    private static final DayMonthYear DAY_MONTH_YEAR = new DayMonthYear(DAY,
            MONTH, YEAR);

    private static final long SIZE = 1729;

    private static final String KEYWORD = "BD3";

    private static final LongList EMPTY = new LongArrayList();

    private static final String ADDRESS = "John Smith <john@example.org>";

    private static final String SUBJECT = "Myriad Harbour";

    private static final UidRange[] IDS = { 
            new UidRange(MessageUid.of(1)),
            new UidRange(MessageUid.of(42), MessageUid.of(1048)) 
            };

    private static final SearchQuery.UidRange[] RANGES = {
            new SearchQuery.UidRange(MessageUid.of(1)),
            new SearchQuery.UidRange(MessageUid.of(42), MessageUid.of(1048)) };

    private static final Username USER = Username.of("user");
    private static final MailboxPath mailboxPath = new MailboxPath("namespace", USER, "name");
    private static final MailboxId mailboxId = TestId.of(18);

    SearchProcessor processor;
    ImapProcessor.Responder responder;
    FakeImapSession session;
    StatusResponseFactory serverResponseFactory;
    StatusResponse statusResponse;
    MessageManager mailbox;
    MailboxManager mailboxManager;
    MailboxSession mailboxSession;
    SelectedMailbox selectedMailbox;

    @BeforeEach
    void setUp() {
        serverResponseFactory = mock(StatusResponseFactory.class);
        session = new FakeImapSession();
        responder = mock(ImapProcessor.Responder.class);
        statusResponse = mock(StatusResponse.class);
        mailbox = mock(MessageManager.class);
        mailboxManager = mock(MailboxManager.class);
        when(mailboxManager.manageProcessing(any(), any())).thenAnswer((Answer<Mono>) invocation -> {
            Object[] args = invocation.getArguments();
            return (Mono) args[0];
        });
        mailboxSession = MailboxSessionUtil.create(USER);
        selectedMailbox = mock(SelectedMailbox.class);
        when(selectedMailbox.getMailboxId()).thenReturn(mailboxId);
        
        processor = new SearchProcessor(mailboxManager, serverResponseFactory, new RecordingMetricFactory());
        expectOk();
    }

    @AfterEach
    public void afterEach() {
        verifyCalls();
    }

    private void allowUnsolicitedResponses() {
        session.setMailboxSession(mailboxSession);
    }

    @Test
    void testSequenceSetUpperUnlimited() throws Exception {
        expectsGetSelectedMailbox();
        final IdRange[] ids = { new IdRange(1, Long.MAX_VALUE) };
        final SearchQuery.UidRange[] ranges = { new SearchQuery.UidRange(MessageUid.of(42), MessageUid.of(100)) };

        when(selectedMailbox.existsCount()).thenReturn(100L);
        when(selectedMailbox.uid(1)).thenReturn(Optional.of(MessageUid.of(42L)));
        when(selectedMailbox.getFirstUid()).thenReturn(Optional.of(MessageUid.of(1L)));
        when(selectedMailbox.getLastUid()).thenReturn(Optional.of(MessageUid.of(100L)));

        allowUnsolicitedResponses();
        check(SearchKey.buildSequenceSet(ids), SearchQuery.uid(ranges));
    }

    @Test
    void testSequenceSetMsnRange() throws Exception {
        expectsGetSelectedMailbox();
        final IdRange[] ids = { new IdRange(1, 5) };
        final SearchQuery.UidRange[] ranges = { new SearchQuery.UidRange(MessageUid.of(42), MessageUid.of(1729)) };

        when(selectedMailbox.existsCount()).thenReturn(100L);
        when(selectedMailbox.uid(1)).thenReturn(Optional.of(MessageUid.of(42L)));
        when(selectedMailbox.uid(5)).thenReturn(Optional.of(MessageUid.of(1729L)));
        when(selectedMailbox.getFirstUid()).thenReturn(Optional.of(MessageUid.of(1L)));
        when(selectedMailbox.getLastUid()).thenReturn(Optional.of(MessageUid.MAX_VALUE));

        allowUnsolicitedResponses();
        check(SearchKey.buildSequenceSet(ids), SearchQuery.uid(ranges));
    }

    @Test
    void testSequenceSetSingleMsn() throws Exception {
        expectsGetSelectedMailbox();
        final IdRange[] ids = { new IdRange(1) };
        final SearchQuery.UidRange[] ranges = { new SearchQuery.UidRange(MessageUid.of(42)) };

        when(selectedMailbox.existsCount()).thenReturn(1L);
        when(selectedMailbox.uid(1)).thenReturn(Optional.of(MessageUid.of(42L)));

        allowUnsolicitedResponses();
        check(SearchKey.buildSequenceSet(ids), SearchQuery.uid(ranges));
    }

    @Test
    void testALL() throws Exception {
        expectsGetSelectedMailbox();
        check(SearchKey.buildAll(), SearchQuery.all());
    }

    private void expectsGetSelectedMailbox() throws Exception {
        when(mailboxManager.getMailboxReactive(mailboxId, mailboxSession)).thenReturn(Mono.just(mailbox));
        session.selected(selectedMailbox).block();
        when(selectedMailbox.isRecentUidRemoved()).thenReturn(false);
        when(selectedMailbox.isSizeChanged()).thenReturn(false);
        when(selectedMailbox.flagUpdateUids()).thenReturn(Collections.<MessageUid>emptyList());
        when(selectedMailbox.getRecent()).thenReturn(new ArrayList<>());
    }

    private Calendar getGMT() {
        return Calendar.getInstance(TimeZone.getTimeZone("GMT"), Locale.UK);
    }
    
    private Date getDate(int day, int month, int year) {
        Calendar cal = getGMT();
        cal.set(year, month - 1, day);
        return cal.getTime();
    }
    
    @Test
    void testANSWERED() throws Exception {
        expectsGetSelectedMailbox();
        check(SearchKey.buildAnswered(), SearchQuery.flagIsSet(Flag.ANSWERED));
    }

    @Test
    void testBCC() throws Exception {
        expectsGetSelectedMailbox();
        check(SearchKey.buildBcc(ADDRESS), SearchQuery.address(
                AddressType.Bcc, ADDRESS));
    }

    @Test
    void testBEFORE() throws Exception {
        expectsGetSelectedMailbox();
        check(SearchKey.buildBefore(DAY_MONTH_YEAR), SearchQuery
                .internalDateBefore(getDate(DAY, MONTH, YEAR), DateResolution.Day));
    }

    @Test
    void testSAVEDBEFORE() throws Exception {
        expectsGetSelectedMailbox();
        check(SearchKey.buildSavedBefore(DAY_MONTH_YEAR), SearchQuery.saveDateBefore(getDate(DAY, MONTH, YEAR), DateResolution.Day));
    }

    @Test
    void testBODY() throws Exception {
        expectsGetSelectedMailbox();
        check(SearchKey.buildBody(SUBJECT), SearchQuery.bodyContains(SUBJECT));
    }

    @Test
    void testCC() throws Exception {
        expectsGetSelectedMailbox();
        check(SearchKey.buildCc(ADDRESS), SearchQuery.address(
                AddressType.Cc, ADDRESS));
    }

    @Test
    void testDELETED() throws Exception {
        expectsGetSelectedMailbox();
        check(SearchKey.buildDeleted(), SearchQuery.flagIsSet(Flag.DELETED));
    }

    @Test
    void testDRAFT() throws Exception {
        expectsGetSelectedMailbox();
        check(SearchKey.buildDraft(), SearchQuery.flagIsSet(Flag.DRAFT));
    }

    @Test
    void testFLAGGED() throws Exception {
        expectsGetSelectedMailbox();
        check(SearchKey.buildFlagged(), SearchQuery.flagIsSet(Flag.FLAGGED));
    }

    @Test
    void testFROM() throws Exception {
        expectsGetSelectedMailbox();
        check(SearchKey.buildFrom(ADDRESS), SearchQuery.address(
                AddressType.From, ADDRESS));
    }

    @Test
    void testHEADER() throws Exception {
        expectsGetSelectedMailbox();
        check(SearchKey.buildHeader(ImapConstants.RFC822_IN_REPLY_TO, ADDRESS),
                SearchQuery.headerContains(ImapConstants.RFC822_IN_REPLY_TO,
                        ADDRESS));
    }

    @Test
    void testKEYWORD() throws Exception {
        expectsGetSelectedMailbox();
        check(SearchKey.buildKeyword(KEYWORD), SearchQuery.flagIsSet(KEYWORD));
    }

    @Test
    void testLARGER() throws Exception {
        expectsGetSelectedMailbox();
        check(SearchKey.buildLarger(SIZE), SearchQuery.sizeGreaterThan(SIZE));
    }

    @Test
    void testNEW() throws Exception {
        expectsGetSelectedMailbox();
        check(SearchKey.buildNew(), SearchQuery.builder()
            .andCriteria(SearchQuery.flagIsSet(Flag.RECENT),
                SearchQuery.flagIsUnSet(Flag.SEEN))
            .build());
    }

    @Test
    void testNOT() throws Exception {
        expectsGetSelectedMailbox();
        check(SearchKey.buildNot(SearchKey.buildOn(DAY_MONTH_YEAR)),
                SearchQuery.not(SearchQuery.internalDateOn(getDate(DAY, MONTH, YEAR), DateResolution.Day)));
    }


    @Test
    void testOLD() throws Exception {
        expectsGetSelectedMailbox();
        check(SearchKey.buildOld(), SearchQuery.flagIsUnSet(Flag.RECENT));
    }

    @Test
    void testON() throws Exception {
        expectsGetSelectedMailbox();
        check(SearchKey.buildOn(DAY_MONTH_YEAR), SearchQuery.internalDateOn(getDate(
                DAY, MONTH, YEAR), DateResolution.Day));
    }

    @Test
    void testSAVEDON() throws Exception {
        expectsGetSelectedMailbox();
        check(SearchKey.buildSavedOn(DAY_MONTH_YEAR), SearchQuery.saveDateOn(getDate(DAY, MONTH, YEAR), DateResolution.Day));
    }

    @Test
    void testAND() throws Exception {
        expectsGetSelectedMailbox();
        List<SearchKey> keys = new ArrayList<>();
        keys.add(SearchKey.buildOn(DAY_MONTH_YEAR));
        keys.add(SearchKey.buildOld());
        keys.add(SearchKey.buildLarger(SIZE));
        List<Criterion> criteria = new ArrayList<>();
        criteria.add(SearchQuery.internalDateOn(getDate(DAY, MONTH, YEAR), DateResolution.Day));
        criteria.add(SearchQuery.flagIsUnSet(Flag.RECENT));
        criteria.add(SearchQuery.sizeGreaterThan(SIZE));
        check(SearchKey.buildAnd(keys), SearchQuery.builder()
            .andCriteria(criteria)
            .build());
    }

    @Test
    void testOR() throws Exception {
        expectsGetSelectedMailbox();
        check(SearchKey.buildOr(SearchKey.buildOn(DAY_MONTH_YEAR), SearchKey
                .buildOld()), SearchQuery.or(SearchQuery.internalDateOn(getDate(DAY,
                MONTH, YEAR), DateResolution.Day), SearchQuery.flagIsUnSet(Flag.RECENT)));
    }

    @Test
    void testRECENT() throws Exception {
        expectsGetSelectedMailbox();
        check(SearchKey.buildRecent(), SearchQuery.flagIsSet(Flag.RECENT));
    }
    
    @Test
    void testSEEN() throws Exception {
        expectsGetSelectedMailbox();
        check(SearchKey.buildSeen(), SearchQuery.flagIsSet(Flag.SEEN));
    }

    @Test
    void testSENTBEFORE() throws Exception {
        expectsGetSelectedMailbox();
        check(SearchKey.buildSentBefore(DAY_MONTH_YEAR), SearchQuery.headerDateBefore(ImapConstants.RFC822_DATE, getDate(DAY, MONTH, YEAR), DateResolution.Day));
    }

    @Test
    void testSENTON() throws Exception {
        expectsGetSelectedMailbox();
        check(SearchKey.buildSentOn(DAY_MONTH_YEAR), SearchQuery.headerDateOn(
                ImapConstants.RFC822_DATE, getDate(DAY, MONTH, YEAR), DateResolution.Day));
    }
    
    @Test
    void testSENTSINCE() throws Exception {
        expectsGetSelectedMailbox();
        check(SearchKey.buildSentSince(DAY_MONTH_YEAR), SearchQuery.or(SearchQuery.headerDateOn(ImapConstants.RFC822_DATE, getDate(DAY, MONTH, YEAR), DateResolution.Day), SearchQuery
                .headerDateAfter(ImapConstants.RFC822_DATE, getDate(DAY, MONTH, YEAR), DateResolution.Day)));
    }

    @Test
    void testSINCE() throws Exception {
        expectsGetSelectedMailbox();
        check(SearchKey.buildSince(DAY_MONTH_YEAR), SearchQuery.or(SearchQuery
                .internalDateOn(getDate(DAY, MONTH, YEAR), DateResolution.Day), SearchQuery
                .internalDateAfter(getDate(DAY, MONTH, YEAR), DateResolution.Day)));
    }

    @Test
    void testSAVEDSINCE() throws Exception {
        expectsGetSelectedMailbox();
        check(SearchKey.buildSavedSince(DAY_MONTH_YEAR), SearchQuery.or(SearchQuery.saveDateOn(getDate(DAY, MONTH, YEAR), DateResolution.Day),
            SearchQuery.saveDateAfter(getDate(DAY, MONTH, YEAR), DateResolution.Day)));
    }

    @Test
    void testSAVEDATESUPPORTED() throws Exception {
        expectsGetSelectedMailbox();
        check(SearchKey.buildSaveDateSupported(), SearchQuery.saveDateSupported());
    }

    @Test
    void testSMALLER() throws Exception {
        expectsGetSelectedMailbox();
        check(SearchKey.buildSmaller(SIZE), SearchQuery.sizeLessThan(SIZE));
    }

    @Test
    void testSUBJECT() throws Exception {
        expectsGetSelectedMailbox();
        check(SearchKey.buildSubject(SUBJECT), SearchQuery.subject(SUBJECT));
    }

    @Test
    void testTEXT() throws Exception {
        expectsGetSelectedMailbox();
        check(SearchKey.buildText(SUBJECT), SearchQuery.mailContains(SUBJECT));
    }

    @Test
    void testTO() throws Exception {
        expectsGetSelectedMailbox();
        check(SearchKey.buildTo(ADDRESS), SearchQuery.address(
                AddressType.To, ADDRESS));
    }

    @Test
    void testUID() throws Exception {
        when(selectedMailbox.getFirstUid()).thenReturn(Optional.of(MessageUid.of(1)));
        when(selectedMailbox.getLastUid()).thenReturn(Optional.of(MessageUid.of(1048)));
        when(selectedMailbox.existsCount()).thenReturn(1L);

        expectsGetSelectedMailbox();

        check(SearchKey.buildUidSet(IDS), SearchQuery.uid(RANGES));
    }

    @Test
    void testUNANSWERED() throws Exception {
        expectsGetSelectedMailbox();
        check(SearchKey.buildUnanswered(), SearchQuery
                .flagIsUnSet(Flag.ANSWERED));
    }

    @Test
    void testUNDELETED() throws Exception {
        expectsGetSelectedMailbox();
        check(SearchKey.buildUndeleted(), SearchQuery.flagIsUnSet(Flag.DELETED));
    }

    @Test
    void testUNDRAFT() throws Exception {
        expectsGetSelectedMailbox();
        check(SearchKey.buildUndraft(), SearchQuery.flagIsUnSet(Flag.DRAFT));
    }

    @Test
    void testUNFLAGGED() throws Exception {
        expectsGetSelectedMailbox();
        check(SearchKey.buildUnflagged(), SearchQuery.flagIsUnSet(Flag.FLAGGED));
    }

    @Test
    void testUNKEYWORD() throws Exception {
        expectsGetSelectedMailbox();
        check(SearchKey.buildUnkeyword(KEYWORD), SearchQuery
                .flagIsUnSet(KEYWORD));
    }

    @Test
    void testUNSEEN() throws Exception {
        expectsGetSelectedMailbox();
        check(SearchKey.buildUnseen(), SearchQuery.flagIsUnSet(Flag.SEEN));
    }

   
    private void check(SearchKey key, SearchQuery.Criterion criterion)
            throws Exception {
        SearchQuery query = SearchQuery.of(criterion);
        check(key, query);
    }

    private void check(SearchKey key, final SearchQuery query) throws Exception {
        session.setMailboxSession(mailboxSession);
        when(mailbox.search(query, mailboxSession)).thenReturn(Flux.empty());
        when(selectedMailbox.getApplicableFlags()).thenReturn(new Flags());
        when(selectedMailbox.hasNewApplicableFlags()).thenReturn(false);

        SearchRequest message = new SearchRequest(new SearchOperation(key, new ArrayList<>(), Optional.empty()), false, TAG);
        processor.processRequest(message, session, responder);
    }

    private void expectOk() {
        when(serverResponseFactory
                .taggedOk(eq(TAG), same(ImapConstants.SEARCH_COMMAND), eq(HumanReadableText.COMPLETED)))
            .thenReturn(statusResponse);
    }

    private void verifyCalls() {
        verify(selectedMailbox).resetEvents();

        verify(responder).respond(new SearchResponse(EMPTY, null));

        verify(responder).respond(same(statusResponse));
    }
}
