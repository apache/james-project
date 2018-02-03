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

package org.apache.james.mailbox.store;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;
import java.util.TimeZone;

import javax.mail.Flags;

import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.extractor.TextExtractor;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.model.SearchQuery.AddressType;
import org.apache.james.mailbox.model.SearchQuery.DateResolution;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.search.MessageSearches;
import org.junit.Before;
import org.junit.Test;

public class SearchUtilsTest {

    private static final String RHUBARD = "Rhubard";

    private static final String CUSTARD = "Custard";

    private static final Date SUN_SEP_9TH_2001 = new Date(1000000000000L);

    private static final int SIZE = 1729;

    private static final String DATE_FIELD = "Date";

    private static final String SUBJECT_FIELD = "Subject";

    private static final String RFC822_SUN_SEP_9TH_2001 = "Sun, 9 Sep 2001 09:10:48 +0000 (GMT)";

    private static final String TEXT = RHUBARD + RHUBARD + RHUBARD;

    MessageBuilder builder;

    Collection<MessageUid> recent;

    private MessageSearches messageSearches;
    
    private Calendar getGMT() {
        return Calendar.getInstance(TimeZone.getTimeZone("GMT"), Locale.UK);
    }
    
    private Date getDate(int day, int month, int year) {
        Calendar cal = getGMT();
        cal.set(year, month - 1, day);
        return cal.getTime();
    }
    
    @Before
    public void setUp() throws Exception {
        recent = new ArrayList<>();
        builder = new MessageBuilder();
        builder.uid = MessageUid.of(1009);
        
        Iterator<MailboxMessage> messages = null;
        SearchQuery query = null; 
        TextExtractor textExtractor = null;
        messageSearches = new MessageSearches(messages, query, textExtractor);
    }
    
    @Test
    public void testMatchSizeLessThan() throws Exception {
        builder.size = SIZE;
        MailboxMessage row = builder.build();
        assertFalse(messageSearches.isMatch(SearchQuery.sizeLessThan(SIZE - 1), row,
                recent));
        assertFalse(messageSearches.isMatch(SearchQuery.sizeLessThan(SIZE), row,
                recent));
        assertTrue(messageSearches.isMatch(SearchQuery.sizeLessThan(SIZE + 1), row,
                recent));
        assertTrue(messageSearches.isMatch(
                SearchQuery.sizeLessThan(Integer.MAX_VALUE), row, recent));
    }

    @Test
    public void testMatchSizeMoreThan() throws Exception {
        builder.size = SIZE;
        MailboxMessage row = builder.build();
        assertTrue(messageSearches.isMatch(SearchQuery.sizeGreaterThan(SIZE - 1), row,
                recent));
        assertFalse(messageSearches.isMatch(SearchQuery.sizeGreaterThan(SIZE), row,
                recent));
        assertFalse(messageSearches.isMatch(SearchQuery.sizeGreaterThan(SIZE + 1),
                row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery
                .sizeGreaterThan(Integer.MAX_VALUE), row, recent));
    }

    @Test
    public void testMatchSizeEquals() throws Exception {
        builder.size = SIZE;
        MailboxMessage row = builder.build();
        assertFalse(messageSearches.isMatch(SearchQuery.sizeEquals(SIZE - 1), row,
                recent));
        assertTrue(messageSearches.isMatch(SearchQuery.sizeEquals(SIZE), row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery.sizeEquals(SIZE + 1), row,
                recent));
        assertFalse(messageSearches.isMatch(SearchQuery.sizeEquals(Integer.MAX_VALUE),
                row, recent));
    }

    @Test
    public void testMatchInternalDateEquals() throws Exception {
        builder.internalDate = SUN_SEP_9TH_2001;
        MailboxMessage row = builder.build();
        assertFalse(messageSearches.isMatch(SearchQuery.internalDateOn(getDate(9, 9, 2000), DateResolution.Day),
                row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery.internalDateOn(getDate(8, 9, 2001), DateResolution.Day),
                row, recent));
        assertTrue(messageSearches.isMatch(SearchQuery.internalDateOn(getDate(9, 9, 2001), DateResolution.Day),
                row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery.internalDateOn(getDate(10, 9, 2001), DateResolution.Day),
                row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery.internalDateOn(getDate(9, 9, 2002), DateResolution.Day),
                row, recent));
    }

    
    @Test
    public void testMatchInternalDateBefore() throws Exception {
        builder.internalDate = SUN_SEP_9TH_2001;
        MailboxMessage row = builder.build();
        assertFalse(messageSearches.isMatch(
                SearchQuery.internalDateBefore(getDate(9, 9, 2000), DateResolution.Day), row, recent));
        assertFalse(messageSearches.isMatch(
                SearchQuery.internalDateBefore(getDate(8, 9, 2001), DateResolution.Day), row, recent));
        assertFalse(messageSearches.isMatch(
                SearchQuery.internalDateBefore(getDate(9, 9, 2001), DateResolution.Day), row, recent));
        assertTrue(messageSearches.isMatch(
                SearchQuery.internalDateBefore(getDate(10, 9, 2001), DateResolution.Day), row, recent));
        assertTrue(messageSearches.isMatch(SearchQuery.internalDateBefore(getDate(9, 9, 2002), DateResolution.Day),
                row, recent));
    }

    @Test
    public void testMatchInternalDateAfter() throws Exception {
        builder.internalDate = SUN_SEP_9TH_2001;
        MailboxMessage row = builder.build();
        assertTrue(messageSearches.isMatch(SearchQuery.internalDateAfter(getDate(9, 9, 2000), DateResolution.Day),
                row, recent));
        assertTrue(messageSearches.isMatch(SearchQuery.internalDateAfter(getDate(8, 9, 2001), DateResolution.Day),
                row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery.internalDateAfter(getDate(9, 9, 2001), DateResolution.Day),
                row, recent));
        assertFalse(messageSearches.isMatch(
                SearchQuery.internalDateAfter(getDate(10, 9, 2001), DateResolution.Day), row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery.internalDateAfter(getDate(9, 9, 2002), DateResolution.Day),
                row, recent));
    }

    @Test
    public void testMatchHeaderDateAfter() throws Exception {
        builder.header(DATE_FIELD, RFC822_SUN_SEP_9TH_2001);
        MailboxMessage row = builder.build();
        assertTrue(messageSearches.isMatch(SearchQuery.headerDateAfter(DATE_FIELD, getDate(9,
                9, 2000), DateResolution.Day), row, recent));
        assertTrue(messageSearches.isMatch(SearchQuery.headerDateAfter(DATE_FIELD, getDate(8,
                9, 2001), DateResolution.Day), row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery.headerDateAfter(DATE_FIELD, getDate(9,
                9, 2001), DateResolution.Day), row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery.headerDateAfter(DATE_FIELD,
                getDate(10, 9, 2001), DateResolution.Day), row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery.headerDateAfter(DATE_FIELD, getDate(9,
                9, 2002), DateResolution.Day), row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery.headerDateAfter("BOGUS", getDate(9, 9,
                2001), DateResolution.Day), row, recent));
    }

    @Test
    public void testShouldMatchCapsHeaderDateAfter() throws Exception {
        builder.header(DATE_FIELD.toUpperCase(Locale.US), RFC822_SUN_SEP_9TH_2001);
        MailboxMessage row = builder.build();
        assertTrue(messageSearches.isMatch(SearchQuery.headerDateAfter(DATE_FIELD, getDate(9,
                9, 2000), DateResolution.Day), row, recent));
        assertTrue(messageSearches.isMatch(SearchQuery.headerDateAfter(DATE_FIELD, getDate(8,
                9, 2001), DateResolution.Day), row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery.headerDateAfter(DATE_FIELD, getDate(9,
                9, 2001), DateResolution.Day), row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery.headerDateAfter(DATE_FIELD,
                getDate(10, 9, 2001), DateResolution.Day), row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery.headerDateAfter(DATE_FIELD, getDate(9,
                9, 2002), DateResolution.Day), row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery.headerDateAfter("BOGUS", getDate(9, 9,
                2001), DateResolution.Day), row, recent));
    }

    @Test
    public void testShouldMatchLowersHeaderDateAfter() throws Exception {
        builder.header(DATE_FIELD.toLowerCase(Locale.US), RFC822_SUN_SEP_9TH_2001);
        MailboxMessage row = builder.build();
        assertTrue(messageSearches.isMatch(SearchQuery.headerDateAfter(DATE_FIELD, getDate(9,
                9, 2000), DateResolution.Day), row, recent));
        assertTrue(messageSearches.isMatch(SearchQuery.headerDateAfter(DATE_FIELD, getDate(8,
                9, 2001),DateResolution.Day), row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery.headerDateAfter(DATE_FIELD, getDate(9,
                9, 2001), DateResolution.Day),row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery.headerDateAfter(DATE_FIELD,
                getDate(10, 9, 2001), DateResolution.Day),row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery.headerDateAfter(DATE_FIELD, getDate(9,
                9, 2002), DateResolution.Day),row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery.headerDateAfter("BOGUS", getDate(9, 9,
                2001), DateResolution.Day),row, recent));
    }

    @Test
    public void testMatchHeaderDateOn() throws Exception {
        builder.header(DATE_FIELD, RFC822_SUN_SEP_9TH_2001);
        MailboxMessage row = builder.build();
        assertFalse(messageSearches.isMatch(SearchQuery.headerDateOn(DATE_FIELD, getDate(9, 9,
                2000), DateResolution.Day),row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery.headerDateOn(DATE_FIELD, getDate(8, 9,
                2001), DateResolution.Day),row, recent));
        assertTrue(messageSearches.isMatch(SearchQuery.headerDateOn(DATE_FIELD, getDate(9, 9,
                2001), DateResolution.Day),row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery.headerDateOn(DATE_FIELD, getDate(10,
                9, 2001), DateResolution.Day),row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery.headerDateOn(DATE_FIELD, getDate(9, 9,
                2002), DateResolution.Day), row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery.headerDateOn("BOGUS", getDate(9, 9,
                2001), DateResolution.Day), row, recent));
    }

    @Test
    public void testShouldMatchCapsHeaderDateOn() throws Exception {
        builder.header(DATE_FIELD.toUpperCase(Locale.US), RFC822_SUN_SEP_9TH_2001);
        MailboxMessage row = builder.build();
        assertFalse(messageSearches.isMatch(SearchQuery.headerDateOn(DATE_FIELD, getDate(9, 9,
                2000), DateResolution.Day),row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery.headerDateOn(DATE_FIELD, getDate(8, 9,
                2001), DateResolution.Day),row, recent));
        assertTrue(messageSearches.isMatch(SearchQuery.headerDateOn(DATE_FIELD, getDate(9, 9,
                2001), DateResolution.Day),row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery.headerDateOn(DATE_FIELD, getDate(10,
                9, 2001), DateResolution.Day),row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery.headerDateOn(DATE_FIELD, getDate(9, 9,
                2002), DateResolution.Day),row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery.headerDateOn("BOGUS", getDate(9, 9,
                2001), DateResolution.Day),row, recent));
    }

    @Test
    public void testShouldMatchLowersHeaderDateOn() throws Exception {
        builder.header(DATE_FIELD.toLowerCase(Locale.US), RFC822_SUN_SEP_9TH_2001);
        MailboxMessage row = builder.build();
        assertFalse(messageSearches.isMatch(SearchQuery.headerDateOn(DATE_FIELD, getDate(9, 9,
                2000), DateResolution.Day),row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery.headerDateOn(DATE_FIELD, getDate(8, 9,
                2001), DateResolution.Day),row, recent));
        assertTrue(messageSearches.isMatch(SearchQuery.headerDateOn(DATE_FIELD, getDate(9, 9,
                2001), DateResolution.Day),row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery.headerDateOn(DATE_FIELD, getDate(10,
                9, 2001), DateResolution.Day),row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery.headerDateOn(DATE_FIELD, getDate(9, 9,
                2002), DateResolution.Day),row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery.headerDateOn("BOGUS", getDate(9, 9,
                2001), DateResolution.Day),row, recent));
    }

    @Test
    public void testMatchHeaderDateBefore() throws Exception {
        builder.header(DATE_FIELD.toLowerCase(Locale.US), RFC822_SUN_SEP_9TH_2001);
        MailboxMessage row = builder.build();
        assertFalse(messageSearches.isMatch(SearchQuery.headerDateBefore(DATE_FIELD,
                getDate(9, 9, 2000), DateResolution.Day),row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery.headerDateBefore(DATE_FIELD,
                getDate(8, 9, 2001), DateResolution.Day),row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery.headerDateBefore(DATE_FIELD,
                getDate(9, 9, 2001), DateResolution.Day),row, recent));
        assertTrue(messageSearches.isMatch(SearchQuery.headerDateBefore(DATE_FIELD,
                getDate(10, 9, 2001), DateResolution.Day),row, recent));
        assertTrue(messageSearches.isMatch(SearchQuery.headerDateBefore(DATE_FIELD, getDate(9,
                9, 2002), DateResolution.Day),row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery.headerDateBefore("BOGUS", getDate(9,
                9, 2001), DateResolution.Day),row, recent));
    }

    @Test
    public void testShouldMatchCapsHeaderDateBefore() throws Exception {
        builder.header(DATE_FIELD.toLowerCase(Locale.US), RFC822_SUN_SEP_9TH_2001);
        MailboxMessage row = builder.build();
        assertFalse(messageSearches.isMatch(SearchQuery.headerDateBefore(DATE_FIELD,
                getDate(9, 9, 2000), DateResolution.Day),row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery.headerDateBefore(DATE_FIELD,
                getDate(8, 9, 2001), DateResolution.Day),row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery.headerDateBefore(DATE_FIELD,
                getDate(9, 9, 2001), DateResolution.Day),row, recent));
        assertTrue(messageSearches.isMatch(SearchQuery.headerDateBefore(DATE_FIELD,
                getDate(10, 9, 2001), DateResolution.Day),row, recent));
        assertTrue(messageSearches.isMatch(SearchQuery.headerDateBefore(DATE_FIELD, getDate(9,
                9, 2002), DateResolution.Day),row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery.headerDateBefore("BOGUS", getDate(9,
                9, 2001), DateResolution.Day),row, recent));
    }

    @Test
    public void testShouldMatchLowersHeaderDateBefore() throws Exception {
        builder.header(DATE_FIELD.toLowerCase(Locale.US), RFC822_SUN_SEP_9TH_2001);
        MailboxMessage row = builder.build();
        assertFalse(messageSearches.isMatch(SearchQuery.headerDateBefore(DATE_FIELD,
                getDate(9, 9, 2000), DateResolution.Day),row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery.headerDateBefore(DATE_FIELD,
                getDate(8, 9, 2001), DateResolution.Day),row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery.headerDateBefore(DATE_FIELD,
                getDate(9, 9, 2001), DateResolution.Day),row, recent));
        assertTrue(messageSearches.isMatch(SearchQuery.headerDateBefore(DATE_FIELD,
                getDate(10, 9, 2001), DateResolution.Day),row, recent));
        assertTrue(messageSearches.isMatch(SearchQuery.headerDateBefore(DATE_FIELD, getDate(9,
                9, 2002), DateResolution.Day),row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery.headerDateBefore("BOGUS", getDate(9,
                9, 2001), DateResolution.Day),row, recent));
    }

    @Test
    public void testMatchHeaderContainsCaps() throws Exception {
        builder.header(SUBJECT_FIELD, TEXT.toUpperCase(Locale.US));
        MailboxMessage row = builder.build();
        assertFalse(messageSearches.isMatch(SearchQuery.headerContains(DATE_FIELD,
                CUSTARD), row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery.headerContains(DATE_FIELD,
                TEXT), row, recent));
        assertTrue(messageSearches.isMatch(SearchQuery.headerContains(SUBJECT_FIELD,
                TEXT), row, recent));
        assertTrue(messageSearches.isMatch(SearchQuery.headerContains(SUBJECT_FIELD,
                RHUBARD), row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery.headerContains(SUBJECT_FIELD,
                CUSTARD), row, recent));
    }

    @Test
    public void testMatchHeaderContainsLowers() throws Exception {
        builder.header(SUBJECT_FIELD, TEXT.toUpperCase(Locale.US));
        MailboxMessage row = builder.build();
        assertFalse(messageSearches.isMatch(SearchQuery.headerContains(DATE_FIELD,
                CUSTARD), row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery.headerContains(DATE_FIELD,
                TEXT), row, recent));
        assertTrue(messageSearches.isMatch(SearchQuery.headerContains(SUBJECT_FIELD,
                TEXT), row, recent));
        assertTrue(messageSearches.isMatch(SearchQuery.headerContains(SUBJECT_FIELD,
                RHUBARD), row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery.headerContains(SUBJECT_FIELD,
                CUSTARD), row, recent));
    }

    @Test
    public void testMatchHeaderContains() throws Exception {
        builder.header(SUBJECT_FIELD, TEXT.toUpperCase(Locale.US));
        MailboxMessage row = builder.build();
        assertFalse(messageSearches.isMatch(SearchQuery.headerContains(DATE_FIELD,
                CUSTARD), row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery.headerContains(DATE_FIELD,
                TEXT), row, recent));
        assertTrue(messageSearches.isMatch(SearchQuery.headerContains(SUBJECT_FIELD,
                TEXT), row, recent));
        assertTrue(messageSearches.isMatch(SearchQuery.headerContains(SUBJECT_FIELD,
                RHUBARD), row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery.headerContains(SUBJECT_FIELD,
                CUSTARD), row, recent));
    }

    @Test
    public void testShouldMatchLowerHeaderContains() throws Exception {
        builder.header(SUBJECT_FIELD.toLowerCase(Locale.US), TEXT);
        MailboxMessage row = builder.build();
        assertFalse(messageSearches.isMatch(SearchQuery.headerContains(DATE_FIELD,
                CUSTARD), row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery.headerContains(DATE_FIELD,
                TEXT), row, recent));
        assertTrue(messageSearches.isMatch(SearchQuery.headerContains(SUBJECT_FIELD,
                TEXT), row, recent));
        assertTrue(messageSearches.isMatch(SearchQuery.headerContains(SUBJECT_FIELD,
                RHUBARD), row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery.headerContains(SUBJECT_FIELD,
                CUSTARD), row, recent));
    }

    @Test
    public void testShouldMatchCapsHeaderContains() throws Exception {
        builder.header(SUBJECT_FIELD.toUpperCase(Locale.US), TEXT);
        MailboxMessage row = builder.build();
        assertFalse(messageSearches.isMatch(SearchQuery.headerContains(DATE_FIELD,
                CUSTARD), row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery.headerContains(DATE_FIELD,
                TEXT), row, recent));
        assertTrue(messageSearches.isMatch(SearchQuery.headerContains(SUBJECT_FIELD,
                TEXT), row, recent));
        assertTrue(messageSearches.isMatch(SearchQuery.headerContains(SUBJECT_FIELD,
                RHUBARD), row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery.headerContains(SUBJECT_FIELD,
                CUSTARD), row, recent));
    }

    @Test
    public void testMatchHeaderExists() throws Exception {
        builder.header(SUBJECT_FIELD, TEXT);
        MailboxMessage row = builder.build();
        assertFalse(messageSearches.isMatch(SearchQuery.headerExists(DATE_FIELD), row,
                recent));
        assertTrue(messageSearches.isMatch(SearchQuery.headerExists(SUBJECT_FIELD),
                row, recent));
    }

    @Test
    public void testShouldMatchLowersHeaderExists() throws Exception {
        builder.header(SUBJECT_FIELD.toLowerCase(Locale.US), TEXT);
        MailboxMessage row = builder.build();
        assertFalse(messageSearches.isMatch(SearchQuery.headerExists(DATE_FIELD), row,
                recent));
        assertTrue(messageSearches.isMatch(SearchQuery.headerExists(SUBJECT_FIELD),
                row, recent));
    }

    @Test
    public void testShouldMatchUppersHeaderExists() throws Exception {
        builder.header(SUBJECT_FIELD.toLowerCase(Locale.US), TEXT);
        MailboxMessage row = builder.build();
        assertFalse(messageSearches.isMatch(SearchQuery.headerExists(DATE_FIELD), row,
                recent));
        assertTrue(messageSearches.isMatch(SearchQuery.headerExists(SUBJECT_FIELD),
                row, recent));
    }

    @Test
    public void testShouldMatchUidRange() throws Exception {
        builder.setKey(1, MessageUid.of(1729));
        MailboxMessage row = builder.build();
        assertFalse(messageSearches.isMatch(SearchQuery.uid(range(MessageUid.of(1), MessageUid.of(1))), row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery.uid(range(MessageUid.of(1728), MessageUid.of(1728))), row,
                recent));
        assertTrue(messageSearches.isMatch(SearchQuery.uid(range(MessageUid.of(1729), MessageUid.of(1729))), row,
                recent));
        assertFalse(messageSearches.isMatch(SearchQuery.uid(range(MessageUid.of(1730), MessageUid.of(1730))), row,
                recent));
        assertFalse(messageSearches.isMatch(SearchQuery.uid(range(MessageUid.of(1), MessageUid.of(1728))), row,
                recent));
        assertTrue(messageSearches.isMatch(SearchQuery.uid(range(MessageUid.of(1), MessageUid.of(1729))), row,
                recent));
        assertTrue(messageSearches.isMatch(SearchQuery.uid(range(MessageUid.of(1729), MessageUid.of(1800))), row,
                recent));
        assertFalse(messageSearches.isMatch(SearchQuery
                .uid(range(MessageUid.of(1730), MessageUid.MAX_VALUE)), row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery.uid(range(MessageUid.of(1730),
                MessageUid.MAX_VALUE, MessageUid.of(1), MessageUid.of(1728))), row, recent));
        assertTrue(messageSearches.isMatch(SearchQuery.uid(range(MessageUid.of(1730), MessageUid.MAX_VALUE,
                MessageUid.of(1), MessageUid.of(1729))), row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery
                .uid(range(MessageUid.of(1), MessageUid.of(1728), MessageUid.of(1800), MessageUid.of(1810))), row, recent));
        assertTrue(messageSearches.isMatch(SearchQuery.uid(range(MessageUid.of(1), MessageUid.of(1), MessageUid.of(1729), MessageUid.of(1729))),
                row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery.uid(range(MessageUid.of(1), MessageUid.of(1), MessageUid.of(1800), MessageUid.of(1800))),
                row, recent));
    }

    @Test
    public void testShouldMatchSeenFlagSet() throws Exception {
        builder.setFlags(true, false, false, false, false, false);
        MailboxMessage row = builder.build();
        assertTrue(messageSearches.isMatch(SearchQuery.flagIsSet(Flags.Flag.SEEN),
                row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery.flagIsSet(Flags.Flag.FLAGGED),
                row, recent));
        assertFalse(messageSearches.isMatch(
                SearchQuery.flagIsSet(Flags.Flag.ANSWERED), row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery.flagIsSet(Flags.Flag.DRAFT),
                row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery.flagIsSet(Flags.Flag.DELETED),
                row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery.flagIsSet(Flags.Flag.RECENT),
                row, recent));
    }

    @Test
    public void testShouldMatchAnsweredFlagSet() throws Exception {
        builder.setFlags(false, false, true, false, false, false);
        MailboxMessage row = builder.build();
        assertFalse(messageSearches.isMatch(SearchQuery.flagIsSet(Flags.Flag.SEEN),
                row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery.flagIsSet(Flags.Flag.FLAGGED),
                row, recent));
        assertTrue(messageSearches.isMatch(SearchQuery.flagIsSet(Flags.Flag.ANSWERED),
                row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery.flagIsSet(Flags.Flag.DRAFT),
                row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery.flagIsSet(Flags.Flag.DELETED),
                row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery.flagIsSet(Flags.Flag.RECENT),
                row, recent));
    }

    @Test
    public void testShouldMatchFlaggedFlagSet() throws Exception {
        builder.setFlags(false, true, false, false, false, false);
        MailboxMessage row = builder.build();
        assertFalse(messageSearches.isMatch(SearchQuery.flagIsSet(Flags.Flag.SEEN),
                row, recent));
        assertTrue(messageSearches.isMatch(SearchQuery.flagIsSet(Flags.Flag.FLAGGED),
                row, recent));
        assertFalse(messageSearches.isMatch(
                SearchQuery.flagIsSet(Flags.Flag.ANSWERED), row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery.flagIsSet(Flags.Flag.DRAFT),
                row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery.flagIsSet(Flags.Flag.DELETED),
                row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery.flagIsSet(Flags.Flag.RECENT),
                row, recent));
    }

    @Test
    public void testShouldMatchDraftFlagSet() throws Exception {
        builder.setFlags(false, false, false, true, false, false);
        MailboxMessage row = builder.build();
        assertFalse(messageSearches.isMatch(SearchQuery.flagIsSet(Flags.Flag.SEEN),
                row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery.flagIsSet(Flags.Flag.FLAGGED),
                row, recent));
        assertFalse(messageSearches.isMatch(
                SearchQuery.flagIsSet(Flags.Flag.ANSWERED), row, recent));
        assertTrue(messageSearches.isMatch(SearchQuery.flagIsSet(Flags.Flag.DRAFT),
                row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery.flagIsSet(Flags.Flag.DELETED),
                row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery.flagIsSet(Flags.Flag.RECENT),
                row, recent));
    }

    
    @Test
    public void testShouldMatchDeletedFlagSet() throws Exception {
        builder.setFlags(false, false, false, false, true, false);
        MailboxMessage row = builder.build();
        assertFalse(messageSearches.isMatch(SearchQuery.flagIsSet(Flags.Flag.SEEN),
                row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery.flagIsSet(Flags.Flag.FLAGGED),
                row, recent));
        assertFalse(messageSearches.isMatch(
                SearchQuery.flagIsSet(Flags.Flag.ANSWERED), row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery.flagIsSet(Flags.Flag.DRAFT),
                row, recent));
        assertTrue(messageSearches.isMatch(SearchQuery.flagIsSet(Flags.Flag.DELETED),
                row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery.flagIsSet(Flags.Flag.RECENT),
                row, recent));
    }

    @Test
    public void testShouldMatchSeenRecentSet() throws Exception {
        builder.setFlags(false, false, false, false, false, false);
        MailboxMessage row = builder.build();
        recent.add(row.getUid());
        assertFalse(messageSearches.isMatch(SearchQuery.flagIsSet(Flags.Flag.SEEN),
                row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery.flagIsSet(Flags.Flag.FLAGGED),
                row, recent));
        assertFalse(messageSearches.isMatch(
                SearchQuery.flagIsSet(Flags.Flag.ANSWERED), row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery.flagIsSet(Flags.Flag.DRAFT),
                row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery.flagIsSet(Flags.Flag.DELETED),
                row, recent));
        assertTrue(messageSearches.isMatch(SearchQuery.flagIsSet(Flags.Flag.RECENT),
                row, recent));
    }

    @Test
    public void testShouldMatchSeenFlagUnSet() throws Exception {
        builder.setFlags(false, true, true, true, true, true);
        MailboxMessage row = builder.build();
        recent.add(row.getUid());
        assertTrue(messageSearches.isMatch(SearchQuery.flagIsUnSet(Flags.Flag.SEEN),
                row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery
                .flagIsUnSet(Flags.Flag.FLAGGED), row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery
                .flagIsUnSet(Flags.Flag.ANSWERED), row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery.flagIsUnSet(Flags.Flag.DRAFT),
                row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery
                .flagIsUnSet(Flags.Flag.DELETED), row, recent));
        assertFalse(messageSearches.isMatch(
                SearchQuery.flagIsUnSet(Flags.Flag.RECENT), row, recent));
    }

    @Test
    public void testShouldMatchAnsweredFlagUnSet() throws Exception {
        builder.setFlags(true, true, false, true, true, true);
        MailboxMessage row = builder.build();
        recent.add(row.getUid());
        assertFalse(messageSearches.isMatch(SearchQuery.flagIsUnSet(Flags.Flag.SEEN),
                row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery
                .flagIsUnSet(Flags.Flag.FLAGGED), row, recent));
        assertTrue(messageSearches.isMatch(SearchQuery
                .flagIsUnSet(Flags.Flag.ANSWERED), row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery.flagIsUnSet(Flags.Flag.DRAFT),
                row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery
                .flagIsUnSet(Flags.Flag.DELETED), row, recent));
        assertFalse(messageSearches.isMatch(
                SearchQuery.flagIsUnSet(Flags.Flag.RECENT), row, recent));
    }

    @Test
    public void testShouldMatchFlaggedFlagUnSet() throws Exception {
        builder.setFlags(true, false, true, true, true, true);
        MailboxMessage row = builder.build();
        recent.add(row.getUid());
        assertFalse(messageSearches.isMatch(SearchQuery.flagIsUnSet(Flags.Flag.SEEN),
                row, recent));
        assertTrue(messageSearches.isMatch(
                SearchQuery.flagIsUnSet(Flags.Flag.FLAGGED), row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery
                .flagIsUnSet(Flags.Flag.ANSWERED), row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery.flagIsUnSet(Flags.Flag.DRAFT),
                row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery
                .flagIsUnSet(Flags.Flag.DELETED), row, recent));
        assertFalse(messageSearches.isMatch(
                SearchQuery.flagIsUnSet(Flags.Flag.RECENT), row, recent));
    }

    @Test
    public void testShouldMatchDraftFlagUnSet() throws Exception {
        builder.setFlags(true, true, true, false, true, true);
        MailboxMessage row = builder.build();
        recent.add(row.getUid());
        assertFalse(messageSearches.isMatch(SearchQuery.flagIsUnSet(Flags.Flag.SEEN),
                row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery
                .flagIsUnSet(Flags.Flag.FLAGGED), row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery
                .flagIsUnSet(Flags.Flag.ANSWERED), row, recent));
        assertTrue(messageSearches.isMatch(SearchQuery.flagIsUnSet(Flags.Flag.DRAFT),
                row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery
                .flagIsUnSet(Flags.Flag.DELETED), row, recent));
        assertFalse(messageSearches.isMatch(
                SearchQuery.flagIsUnSet(Flags.Flag.RECENT), row, recent));
    }

    @Test
    public void testShouldMatchDeletedFlagUnSet() throws Exception {
        builder.setFlags(true, true, true, true, false, true);
        MailboxMessage row = builder.build();
        recent.add(row.getUid());
        assertFalse(messageSearches.isMatch(SearchQuery.flagIsUnSet(Flags.Flag.SEEN),
                row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery
                .flagIsUnSet(Flags.Flag.FLAGGED), row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery
                .flagIsUnSet(Flags.Flag.ANSWERED), row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery.flagIsUnSet(Flags.Flag.DRAFT),
                row, recent));
        assertTrue(messageSearches.isMatch(
                SearchQuery.flagIsUnSet(Flags.Flag.DELETED), row, recent));
        assertFalse(messageSearches.isMatch(
                SearchQuery.flagIsUnSet(Flags.Flag.RECENT), row, recent));
    }

    @Test
    public void testShouldMatchSeenRecentUnSet() throws Exception {
        builder.setFlags(true, true, true, true, true, true);
        MailboxMessage row = builder.build();
        recent.add(row.getUid().next());
        assertFalse(messageSearches.isMatch(SearchQuery.flagIsUnSet(Flags.Flag.SEEN),
                row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery
                .flagIsUnSet(Flags.Flag.FLAGGED), row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery
                .flagIsUnSet(Flags.Flag.ANSWERED), row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery.flagIsUnSet(Flags.Flag.DRAFT),
                row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery
                .flagIsUnSet(Flags.Flag.DELETED), row, recent));
        assertTrue(messageSearches.isMatch(SearchQuery.flagIsUnSet(Flags.Flag.RECENT),
                row, recent));
    }

    @Test
    public void testShouldMatchAll() throws Exception {
        MailboxMessage row = builder.build();
        assertTrue(messageSearches.isMatch(SearchQuery.all(), row, recent));
    }

    @Test
    public void testShouldMatchNot() throws Exception {
        MailboxMessage row = builder.build();
        assertFalse(messageSearches.isMatch(SearchQuery.not(SearchQuery.all()), row,
                recent));
        assertTrue(messageSearches.isMatch(SearchQuery.not(SearchQuery
                .headerExists(DATE_FIELD)), row, recent));
    }

    @Test
    public void testShouldMatchOr() throws Exception {
        MailboxMessage row = builder.build();
        assertTrue(messageSearches.isMatch(SearchQuery.or(SearchQuery.all(),
                SearchQuery.headerExists(DATE_FIELD)), row, recent));
        assertTrue(messageSearches.isMatch(SearchQuery.or(SearchQuery
                .headerExists(DATE_FIELD), SearchQuery.all()), row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery
                .or(SearchQuery.headerExists(DATE_FIELD), SearchQuery
                        .headerExists(DATE_FIELD)), row, recent));
        assertTrue(messageSearches.isMatch(SearchQuery.or(SearchQuery.all(),
                SearchQuery.all()), row, recent));
    }

    @Test
    public void testShouldMatchAnd() throws Exception {
        MailboxMessage row = builder.build();
        assertFalse(messageSearches.isMatch(SearchQuery.and(SearchQuery.all(),
                SearchQuery.headerExists(DATE_FIELD)), row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery.and(SearchQuery
                .headerExists(DATE_FIELD), SearchQuery.all()), row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery
                .and(SearchQuery.headerExists(DATE_FIELD), SearchQuery
                        .headerExists(DATE_FIELD)), row, recent));
        assertTrue(messageSearches.isMatch(SearchQuery.and(SearchQuery.all(),
                SearchQuery.all()), row, recent));
    }
    
    private SearchQuery.UidRange[] range(MessageUid low, MessageUid high) {
        return new SearchQuery.UidRange[]{ new SearchQuery.UidRange(low, high) };
    }

    private SearchQuery.UidRange[] range(MessageUid lowOne, MessageUid highOne,
            MessageUid lowTwo, MessageUid highTwo) {
        return new SearchQuery.UidRange[]{
                new SearchQuery.UidRange(lowOne, highOne),
                new SearchQuery.UidRange(lowTwo, highTwo) };
    }
    
    
    @Test
    public void testMatchHeaderDateOnWithOffset() throws Exception {
        builder.header(DATE_FIELD, "Mon, 26 Mar 2007 00:00:00 +0300");
        MailboxMessage row = builder.build();
        assertTrue(messageSearches.isMatch(SearchQuery.headerDateOn(DATE_FIELD, getDate(26, 3,
                2007), DateResolution.Day),row, recent));
        
        assertFalse(messageSearches.isMatch(SearchQuery.headerDateOn(DATE_FIELD, getDate(25, 3,
                2007), DateResolution.Day),row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery.headerDateOn(DATE_FIELD, getDate(27, 3,
                2007), DateResolution.Day),row, recent));
    }
    

    @Test
    public void testShouldMatchHeaderDateBeforeWithOffset() throws Exception {
        builder.header(DATE_FIELD, "Mon, 26 Mar 2007 00:00:00 +0300");
        MailboxMessage row = builder.build();
        assertFalse(messageSearches.isMatch(SearchQuery.headerDateBefore(DATE_FIELD, getDate(26, 3,
                2007), DateResolution.Day),row, recent));
        
        assertTrue(messageSearches.isMatch(SearchQuery.headerDateBefore(DATE_FIELD, getDate(27, 3,
                2007), DateResolution.Day),row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery.headerDateBefore(DATE_FIELD, getDate(25, 3,
                2007), DateResolution.Day),row, recent));
    }

    @Test
    public void testShouldMatchHeaderDateAfterWithOffset() throws Exception {
        builder.header(DATE_FIELD, "Mon, 26 Mar 2007 00:00:00 +0300");
        MailboxMessage row = builder.build();
        assertFalse(messageSearches.isMatch(SearchQuery.headerDateAfter(DATE_FIELD, getDate(26, 3,
                2007), DateResolution.Day),row, recent));
        
        assertFalse(messageSearches.isMatch(SearchQuery.headerDateAfter(DATE_FIELD, getDate(27, 3,
                2007), DateResolution.Day),row, recent));
        assertTrue(messageSearches.isMatch(SearchQuery.headerDateAfter(DATE_FIELD, getDate(25, 3,
                2007), DateResolution.Day),row, recent));
    }
    
    @Test
    public void testShouldMatchAddressHeaderWithComments() throws Exception {
        builder.header("To", "<user-from (comment)@ (comment) domain.org>");
        MailboxMessage row = builder.build();
        assertTrue(messageSearches.isMatch(SearchQuery.address(AddressType.To, "user-from@domain.org"), row, recent));
        assertFalse(messageSearches.isMatch(SearchQuery.address(AddressType.From, "user-from@domain.org"), row, recent));
    }

}
