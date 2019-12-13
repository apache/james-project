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
import java.util.stream.Stream;

import javax.mail.Flags;
import javax.mail.Flags.Flag;

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
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class SearchProcessorTest {
    private static final int DAY = 6;

    private static final int MONTH = 6;

    private static final int YEAR = 1944;

    private static final DayMonthYear DAY_MONTH_YEAR = new DayMonthYear(DAY,
            MONTH, YEAR);

    private static final long SIZE = 1729;

    private static final String KEYWORD = "BD3";

    private static final long[] EMPTY = {};

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
    ImapProcessor next;
    ImapProcessor.Responder responder;
    FakeImapSession session;
    StatusResponseFactory serverResponseFactory;
    StatusResponse statusResponse;
    MessageManager mailbox;
    MailboxManager mailboxManager;
    MailboxSession mailboxSession;
    SelectedMailbox selectedMailbox;

    @Before
    public void setUp() throws Exception {
        serverResponseFactory = mock(StatusResponseFactory.class);
        session = new FakeImapSession();
        next = mock(ImapProcessor.class);
        responder = mock(ImapProcessor.Responder.class);
        statusResponse = mock(StatusResponse.class);
        mailbox = mock(MessageManager.class);
        mailboxManager = mock(MailboxManager.class);
        mailboxSession = MailboxSessionUtil.create(USER);
        selectedMailbox = mock(SelectedMailbox.class);
        when(selectedMailbox.getMailboxId()).thenReturn(mailboxId);
        
        processor = new SearchProcessor(next,  mailboxManager, serverResponseFactory, new RecordingMetricFactory());
        expectOk();
    }

    @After
    public void afterEach() {
        verifyCalls();
    }

    private void allowUnsolicitedResponses() {
        session.setMailboxSession(mailboxSession);
    }

    @Test
    public void testSequenceSetUpperUnlimited() throws Exception {
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
    public void testSequenceSetMsnRange() throws Exception {
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
    public void testSequenceSetSingleMsn() throws Exception {
        expectsGetSelectedMailbox();
        final IdRange[] ids = { new IdRange(1) };
        final SearchQuery.UidRange[] ranges = { new SearchQuery.UidRange(MessageUid.of(42)) };

        when(selectedMailbox.existsCount()).thenReturn(1L);
        when(selectedMailbox.uid(1)).thenReturn(Optional.of(MessageUid.of(42L)));

        allowUnsolicitedResponses();
        check(SearchKey.buildSequenceSet(ids), SearchQuery.uid(ranges));
    }

    @Test
    public void testALL() throws Exception {
        expectsGetSelectedMailbox();
        check(SearchKey.buildAll(), SearchQuery.all());
    }

    private void expectsGetSelectedMailbox() throws Exception {
        when(mailboxManager.getMailbox(mailboxId, mailboxSession)).thenReturn(mailbox, mailbox);
        session.selected(selectedMailbox);
        when(selectedMailbox.isRecentUidRemoved()).thenReturn(false);
        when(selectedMailbox.isSizeChanged()).thenReturn(false);
        when(selectedMailbox.getPath()).thenReturn(mailboxPath);
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
    public void testANSWERED() throws Exception {
        expectsGetSelectedMailbox();
        check(SearchKey.buildAnswered(), SearchQuery.flagIsSet(Flag.ANSWERED));
    }

    @Test
    public void testBCC() throws Exception {
        expectsGetSelectedMailbox();
        check(SearchKey.buildBcc(ADDRESS), SearchQuery.address(
                AddressType.Bcc, ADDRESS));
    }

    @Test
    public void testBEFORE() throws Exception {
        expectsGetSelectedMailbox();
        check(SearchKey.buildBefore(DAY_MONTH_YEAR), SearchQuery
                .internalDateBefore(getDate(DAY, MONTH, YEAR), DateResolution.Day));
    }

    @Test
    public void testBODY() throws Exception {
        expectsGetSelectedMailbox();
        check(SearchKey.buildBody(SUBJECT), SearchQuery.bodyContains(SUBJECT));
    }

    @Test
    public void testCC() throws Exception {
        expectsGetSelectedMailbox();
        check(SearchKey.buildCc(ADDRESS), SearchQuery.address(
                AddressType.Cc, ADDRESS));
    }

    @Test
    public void testDELETED() throws Exception {
        expectsGetSelectedMailbox();
        check(SearchKey.buildDeleted(), SearchQuery.flagIsSet(Flag.DELETED));
    }

    @Test
    public void testDRAFT() throws Exception {
        expectsGetSelectedMailbox();
        check(SearchKey.buildDraft(), SearchQuery.flagIsSet(Flag.DRAFT));
    }

    @Test
    public void testFLAGGED() throws Exception {
        expectsGetSelectedMailbox();
        check(SearchKey.buildFlagged(), SearchQuery.flagIsSet(Flag.FLAGGED));
    }

    @Test
    public void testFROM() throws Exception {
        expectsGetSelectedMailbox();
        check(SearchKey.buildFrom(ADDRESS), SearchQuery.address(
                AddressType.From, ADDRESS));
    }

    @Test
    public void testHEADER() throws Exception {
        expectsGetSelectedMailbox();
        check(SearchKey.buildHeader(ImapConstants.RFC822_IN_REPLY_TO, ADDRESS),
                SearchQuery.headerContains(ImapConstants.RFC822_IN_REPLY_TO,
                        ADDRESS));
    }

    @Test
    public void testKEYWORD() throws Exception {
        expectsGetSelectedMailbox();
        check(SearchKey.buildKeyword(KEYWORD), SearchQuery.flagIsSet(KEYWORD));
    }

    @Test
    public void testLARGER() throws Exception {
        expectsGetSelectedMailbox();
        check(SearchKey.buildLarger(SIZE), SearchQuery.sizeGreaterThan(SIZE));
    }

    @Test
    public void testNEW() throws Exception {
        expectsGetSelectedMailbox();
        check(SearchKey.buildNew(), SearchQuery.and(SearchQuery
                .flagIsSet(Flag.RECENT), SearchQuery.flagIsUnSet(Flag.SEEN)));
    }

    @Test
    public void testNOT() throws Exception {
        expectsGetSelectedMailbox();
        check(SearchKey.buildNot(SearchKey.buildOn(DAY_MONTH_YEAR)),
                SearchQuery.not(SearchQuery.internalDateOn(getDate(DAY, MONTH, YEAR), DateResolution.Day)));
    }


    @Test
    public void testOLD() throws Exception {
        expectsGetSelectedMailbox();
        check(SearchKey.buildOld(), SearchQuery.flagIsUnSet(Flag.RECENT));
    }

    @Test
    public void testON() throws Exception {
        expectsGetSelectedMailbox();
        check(SearchKey.buildOn(DAY_MONTH_YEAR), SearchQuery.internalDateOn(getDate(
                DAY, MONTH, YEAR), DateResolution.Day));
    }

    @Test
    public void testAND() throws Exception {
        expectsGetSelectedMailbox();
        List<SearchKey> keys = new ArrayList<>();
        keys.add(SearchKey.buildOn(DAY_MONTH_YEAR));
        keys.add(SearchKey.buildOld());
        keys.add(SearchKey.buildLarger(SIZE));
        List<Criterion> criteria = new ArrayList<>();
        criteria.add(SearchQuery.internalDateOn(getDate(DAY, MONTH, YEAR), DateResolution.Day));
        criteria.add(SearchQuery.flagIsUnSet(Flag.RECENT));
        criteria.add(SearchQuery.sizeGreaterThan(SIZE));
        check(SearchKey.buildAnd(keys), SearchQuery.and(criteria));
    }

    @Test
    public void testOR() throws Exception {
        expectsGetSelectedMailbox();
        check(SearchKey.buildOr(SearchKey.buildOn(DAY_MONTH_YEAR), SearchKey
                .buildOld()), SearchQuery.or(SearchQuery.internalDateOn(getDate(DAY,
                MONTH, YEAR), DateResolution.Day), SearchQuery.flagIsUnSet(Flag.RECENT)));
    }

    @Test
    public void testRECENT() throws Exception {
        expectsGetSelectedMailbox();
        check(SearchKey.buildRecent(), SearchQuery.flagIsSet(Flag.RECENT));
    }
    
    @Test
    public void testSEEN() throws Exception {
        expectsGetSelectedMailbox();
        check(SearchKey.buildSeen(), SearchQuery.flagIsSet(Flag.SEEN));
    }

    @Test
    public void testSENTBEFORE() throws Exception {
        expectsGetSelectedMailbox();
        check(SearchKey.buildSentBefore(DAY_MONTH_YEAR), SearchQuery.headerDateBefore(ImapConstants.RFC822_DATE, getDate(DAY, MONTH, YEAR), DateResolution.Day));
    }

    @Test
    public void testSENTON() throws Exception {
        expectsGetSelectedMailbox();
        check(SearchKey.buildSentOn(DAY_MONTH_YEAR), SearchQuery.headerDateOn(
                ImapConstants.RFC822_DATE, getDate(DAY, MONTH, YEAR), DateResolution.Day));
    }
    
    @Test
    public void testSENTSINCE() throws Exception {
        expectsGetSelectedMailbox();
        check(SearchKey.buildSentSince(DAY_MONTH_YEAR), SearchQuery.or(SearchQuery.headerDateOn(ImapConstants.RFC822_DATE, getDate(DAY, MONTH, YEAR), DateResolution.Day), SearchQuery
                .headerDateAfter(ImapConstants.RFC822_DATE, getDate(DAY, MONTH, YEAR), DateResolution.Day)));
    }

    @Test
    public void testSINCE() throws Exception {
        expectsGetSelectedMailbox();
        check(SearchKey.buildSince(DAY_MONTH_YEAR), SearchQuery.or(SearchQuery
                .internalDateOn(getDate(DAY, MONTH, YEAR), DateResolution.Day), SearchQuery
                .internalDateAfter(getDate(DAY, MONTH, YEAR), DateResolution.Day)));
    }

    @Test
    public void testSMALLER() throws Exception {
        expectsGetSelectedMailbox();
        check(SearchKey.buildSmaller(SIZE), SearchQuery.sizeLessThan(SIZE));
    }

    @Test
    public void testSUBJECT() throws Exception {
        expectsGetSelectedMailbox();
        check(SearchKey.buildSubject(SUBJECT), SearchQuery.headerContains(
                ImapConstants.RFC822_SUBJECT, SUBJECT));
    }

    @Test
    public void testTEXT() throws Exception {
        expectsGetSelectedMailbox();
        check(SearchKey.buildText(SUBJECT), SearchQuery.mailContains(SUBJECT));
    }

    @Test
    public void testTO() throws Exception {
        expectsGetSelectedMailbox();
        check(SearchKey.buildTo(ADDRESS), SearchQuery.address(
                AddressType.To, ADDRESS));
    }

    @Test
    public void testUID() throws Exception {
        when(selectedMailbox.getFirstUid()).thenReturn(Optional.of(MessageUid.of(1)));
        when(selectedMailbox.getLastUid()).thenReturn(Optional.of(MessageUid.of(1048)));
        when(selectedMailbox.existsCount()).thenReturn(1L);

        expectsGetSelectedMailbox();

        check(SearchKey.buildUidSet(IDS), SearchQuery.uid(RANGES));
    }

    @Test
    public void testUNANSWERED() throws Exception {
        expectsGetSelectedMailbox();
        check(SearchKey.buildUnanswered(), SearchQuery
                .flagIsUnSet(Flag.ANSWERED));
    }

    @Test
    public void testUNDELETED() throws Exception {
        expectsGetSelectedMailbox();
        check(SearchKey.buildUndeleted(), SearchQuery.flagIsUnSet(Flag.DELETED));
    }

    @Test
    public void testUNDRAFT() throws Exception {
        expectsGetSelectedMailbox();
        check(SearchKey.buildUndraft(), SearchQuery.flagIsUnSet(Flag.DRAFT));
    }

    @Test
    public void testUNFLAGGED() throws Exception {
        expectsGetSelectedMailbox();
        check(SearchKey.buildUnflagged(), SearchQuery.flagIsUnSet(Flag.FLAGGED));
    }

    @Test
    public void testUNKEYWORD() throws Exception {
        expectsGetSelectedMailbox();
        check(SearchKey.buildUnkeyword(KEYWORD), SearchQuery
                .flagIsUnSet(KEYWORD));
    }

    @Test
    public void testUNSEEN() throws Exception {
        expectsGetSelectedMailbox();
        check(SearchKey.buildUnseen(), SearchQuery.flagIsUnSet(Flag.SEEN));
    }

   
    private void check(SearchKey key, SearchQuery.Criterion criterion)
            throws Exception {
        SearchQuery query = new SearchQuery();
        query.andCriteria(criterion);
        check(key, query);
    }

    private void check(SearchKey key, final SearchQuery query) throws Exception {
        session.setMailboxSession(mailboxSession);
        when(mailbox.search(query, mailboxSession)).thenReturn(Stream.empty());
        when(selectedMailbox.getApplicableFlags()).thenReturn(new Flags());
        when(selectedMailbox.hasNewApplicableFlags()).thenReturn(false);

        SearchRequest message = new SearchRequest(new SearchOperation(key, new ArrayList<>()), false, TAG);
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
