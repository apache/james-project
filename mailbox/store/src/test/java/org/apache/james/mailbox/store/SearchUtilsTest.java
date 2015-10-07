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

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import javax.mail.Flags;

import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.model.SearchQuery.AddressType;
import org.apache.james.mailbox.model.SearchQuery.DateResolution;
import org.apache.james.mailbox.store.mail.model.Message;
import org.apache.james.mailbox.store.search.MessageSearches;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    Collection<Long> recent;
    private Logger log = LoggerFactory.getLogger(getClass());
    
    private Calendar getGMT() {
        return Calendar.getInstance(TimeZone.getTimeZone("GMT"), Locale.UK);
    }
    
    private Date getDate(int day, int month, int year) {
        Calendar cal = getGMT();
        cal.set(year, month -1, day);
        return cal.getTime();
    }
    @Before
    public void setUp() throws Exception {
        recent = new ArrayList<Long>();
        builder = new MessageBuilder();
        builder.uid = 1009;
    }
    
    @Test
    public void testMatchSizeLessThan() throws Exception {
        builder.size = SIZE;
        Message<TestId> row = builder.build();
        assertFalse(new MessageSearches().isMatch(SearchQuery.sizeLessThan(SIZE - 1), row,
                recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.sizeLessThan(SIZE), row,
                recent, log));
        assertTrue(new MessageSearches().isMatch(SearchQuery.sizeLessThan(SIZE + 1), row,
                recent, log));
        assertTrue(new MessageSearches().isMatch(
                SearchQuery.sizeLessThan(Integer.MAX_VALUE), row, recent, log));
    }

    @Test
    public void testMatchSizeMoreThan() throws Exception {
        builder.size = SIZE;
        Message<TestId> row = builder.build();
        assertTrue(new MessageSearches().isMatch(SearchQuery.sizeGreaterThan(SIZE - 1), row,
                recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.sizeGreaterThan(SIZE), row,
                recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.sizeGreaterThan(SIZE + 1),
                row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery
                .sizeGreaterThan(Integer.MAX_VALUE), row, recent, log));
    }

    @Test
    public void testMatchSizeEquals() throws Exception {
        builder.size = SIZE;
        Message<TestId> row = builder.build();
        assertFalse(new MessageSearches().isMatch(SearchQuery.sizeEquals(SIZE - 1), row,
                recent, log));
        assertTrue(new MessageSearches().isMatch(SearchQuery.sizeEquals(SIZE), row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.sizeEquals(SIZE + 1), row,
                recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.sizeEquals(Integer.MAX_VALUE),
                row, recent, log));
    }

    @Test
    public void testMatchInternalDateEquals() throws Exception {
        builder.internalDate = SUN_SEP_9TH_2001;
        Message<TestId> row = builder.build();
        assertFalse(new MessageSearches().isMatch(SearchQuery.internalDateOn(getDate(9, 9, 2000), DateResolution.Day),
                row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.internalDateOn(getDate(8, 9, 2001), DateResolution.Day),
                row, recent, log));
        assertTrue(new MessageSearches().isMatch(SearchQuery.internalDateOn(getDate(9, 9, 2001), DateResolution.Day),
                row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.internalDateOn(getDate(10, 9, 2001), DateResolution.Day),
                row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.internalDateOn(getDate(9, 9, 2002), DateResolution.Day),
                row, recent, log));
    }

    
    @Test
    public void testMatchInternalDateBefore() throws Exception {
        builder.internalDate = SUN_SEP_9TH_2001;
        Message<TestId> row = builder.build();
        assertFalse(new MessageSearches().isMatch(
                SearchQuery.internalDateBefore(getDate(9, 9, 2000), DateResolution.Day), row, recent, log));
        assertFalse(new MessageSearches().isMatch(
                SearchQuery.internalDateBefore(getDate(8, 9, 2001), DateResolution.Day), row, recent, log));
        assertFalse(new MessageSearches().isMatch(
                SearchQuery.internalDateBefore(getDate(9, 9, 2001), DateResolution.Day), row, recent, log));
        assertTrue(new MessageSearches().isMatch(
                SearchQuery.internalDateBefore(getDate(10, 9, 2001), DateResolution.Day), row, recent, log));
        assertTrue(new MessageSearches().isMatch(SearchQuery.internalDateBefore(getDate(9, 9, 2002), DateResolution.Day),
                row, recent, log));
    }

    @Test
    public void testMatchInternalDateAfter() throws Exception {
        builder.internalDate = SUN_SEP_9TH_2001;
        Message<TestId> row = builder.build();
        assertTrue(new MessageSearches().isMatch(SearchQuery.internalDateAfter(getDate(9, 9, 2000), DateResolution.Day),
                row, recent, log));
        assertTrue(new MessageSearches().isMatch(SearchQuery.internalDateAfter(getDate(8, 9, 2001), DateResolution.Day),
                row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.internalDateAfter(getDate(9, 9, 2001), DateResolution.Day),
                row, recent, log));
        assertFalse(new MessageSearches().isMatch(
                SearchQuery.internalDateAfter(getDate(10, 9, 2001), DateResolution.Day), row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.internalDateAfter(getDate(9, 9, 2002), DateResolution.Day),
                row, recent, log));
    }

    @Test
    public void testMatchHeaderDateAfter() throws Exception {
        builder.header(DATE_FIELD, RFC822_SUN_SEP_9TH_2001);
        Message<TestId> row = builder.build();
        assertTrue(new MessageSearches().isMatch(SearchQuery.headerDateAfter(DATE_FIELD, getDate(9,
                9, 2000), DateResolution.Day), row, recent, log));
        assertTrue(new MessageSearches().isMatch(SearchQuery.headerDateAfter(DATE_FIELD, getDate(8,
                9, 2001), DateResolution.Day), row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.headerDateAfter(DATE_FIELD, getDate(9,
                9, 2001), DateResolution.Day), row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.headerDateAfter(DATE_FIELD,
                getDate(10, 9, 2001), DateResolution.Day), row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.headerDateAfter(DATE_FIELD, getDate(9,
                9, 2002), DateResolution.Day), row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.headerDateAfter("BOGUS", getDate(9, 9,
                2001), DateResolution.Day), row, recent, log));
    }

    @Test
    public void testShouldMatchCapsHeaderDateAfter() throws Exception {
        builder.header(DATE_FIELD.toUpperCase(), RFC822_SUN_SEP_9TH_2001);
        Message<TestId> row = builder.build();
        assertTrue(new MessageSearches().isMatch(SearchQuery.headerDateAfter(DATE_FIELD, getDate(9,
                9, 2000), DateResolution.Day), row, recent, log));
        assertTrue(new MessageSearches().isMatch(SearchQuery.headerDateAfter(DATE_FIELD, getDate(8,
                9, 2001), DateResolution.Day), row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.headerDateAfter(DATE_FIELD, getDate(9,
                9, 2001), DateResolution.Day), row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.headerDateAfter(DATE_FIELD,
                getDate(10, 9, 2001), DateResolution.Day), row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.headerDateAfter(DATE_FIELD, getDate(9,
                9, 2002), DateResolution.Day), row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.headerDateAfter("BOGUS", getDate(9, 9,
                2001), DateResolution.Day), row, recent, log));
    }

    @Test
    public void testShouldMatchLowersHeaderDateAfter() throws Exception {
        builder.header(DATE_FIELD.toLowerCase(), RFC822_SUN_SEP_9TH_2001);
        Message<TestId> row = builder.build();
        assertTrue(new MessageSearches().isMatch(SearchQuery.headerDateAfter(DATE_FIELD, getDate(9,
                9, 2000), DateResolution.Day), row, recent, log));
        assertTrue(new MessageSearches().isMatch(SearchQuery.headerDateAfter(DATE_FIELD, getDate(8,
                9, 2001),DateResolution.Day), row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.headerDateAfter(DATE_FIELD, getDate(9,
                9, 2001), DateResolution.Day),row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.headerDateAfter(DATE_FIELD,
                getDate(10, 9, 2001), DateResolution.Day),row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.headerDateAfter(DATE_FIELD, getDate(9,
                9, 2002), DateResolution.Day),row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.headerDateAfter("BOGUS", getDate(9, 9,
                2001), DateResolution.Day),row, recent, log));
    }

    @Test
    public void testMatchHeaderDateOn() throws Exception {
        builder.header(DATE_FIELD, RFC822_SUN_SEP_9TH_2001);
        Message<TestId> row = builder.build();
        assertFalse(new MessageSearches().isMatch(SearchQuery.headerDateOn(DATE_FIELD, getDate(9, 9,
                2000), DateResolution.Day),row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.headerDateOn(DATE_FIELD, getDate(8, 9,
                2001), DateResolution.Day),row, recent, log));
        assertTrue(new MessageSearches().isMatch(SearchQuery.headerDateOn(DATE_FIELD, getDate(9, 9,
                2001), DateResolution.Day),row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.headerDateOn(DATE_FIELD, getDate(10,
                9, 2001), DateResolution.Day),row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.headerDateOn(DATE_FIELD, getDate(9, 9,
                2002), DateResolution.Day), row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.headerDateOn("BOGUS", getDate(9, 9,
                2001), DateResolution.Day), row, recent, log));
    }

    @Test
    public void testShouldMatchCapsHeaderDateOn() throws Exception {
        builder.header(DATE_FIELD.toUpperCase(), RFC822_SUN_SEP_9TH_2001);
        Message<TestId> row = builder.build();
        assertFalse(new MessageSearches().isMatch(SearchQuery.headerDateOn(DATE_FIELD, getDate(9, 9,
                2000), DateResolution.Day),row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.headerDateOn(DATE_FIELD, getDate(8, 9,
                2001), DateResolution.Day),row, recent, log));
        assertTrue(new MessageSearches().isMatch(SearchQuery.headerDateOn(DATE_FIELD, getDate(9, 9,
                2001), DateResolution.Day),row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.headerDateOn(DATE_FIELD, getDate(10,
                9, 2001), DateResolution.Day),row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.headerDateOn(DATE_FIELD, getDate(9, 9,
                2002), DateResolution.Day),row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.headerDateOn("BOGUS", getDate(9, 9,
                2001), DateResolution.Day),row, recent, log));
    }

    @Test
    public void testShouldMatchLowersHeaderDateOn() throws Exception {
        builder.header(DATE_FIELD.toLowerCase(), RFC822_SUN_SEP_9TH_2001);
        Message<TestId> row = builder.build();
        assertFalse(new MessageSearches().isMatch(SearchQuery.headerDateOn(DATE_FIELD, getDate(9, 9,
                2000), DateResolution.Day),row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.headerDateOn(DATE_FIELD, getDate(8, 9,
                2001), DateResolution.Day),row, recent, log));
        assertTrue(new MessageSearches().isMatch(SearchQuery.headerDateOn(DATE_FIELD, getDate(9, 9,
                2001), DateResolution.Day),row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.headerDateOn(DATE_FIELD, getDate(10,
                9, 2001), DateResolution.Day),row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.headerDateOn(DATE_FIELD, getDate(9, 9,
                2002), DateResolution.Day),row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.headerDateOn("BOGUS", getDate(9, 9,
                2001), DateResolution.Day),row, recent, log));
    }

    @Test
    public void testMatchHeaderDateBefore() throws Exception {
        builder.header(DATE_FIELD.toLowerCase(), RFC822_SUN_SEP_9TH_2001);
        Message<TestId> row = builder.build();
        assertFalse(new MessageSearches().isMatch(SearchQuery.headerDateBefore(DATE_FIELD,
                getDate(9, 9, 2000), DateResolution.Day),row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.headerDateBefore(DATE_FIELD,
                getDate(8, 9, 2001), DateResolution.Day),row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.headerDateBefore(DATE_FIELD,
                getDate(9, 9, 2001), DateResolution.Day),row, recent, log));
        assertTrue(new MessageSearches().isMatch(SearchQuery.headerDateBefore(DATE_FIELD,
                getDate(10, 9, 2001), DateResolution.Day),row, recent, log));
        assertTrue(new MessageSearches().isMatch(SearchQuery.headerDateBefore(DATE_FIELD, getDate(9,
                9, 2002), DateResolution.Day),row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.headerDateBefore("BOGUS", getDate(9,
                9, 2001), DateResolution.Day),row, recent, log));
    }

    @Test
    public void testShouldMatchCapsHeaderDateBefore() throws Exception {
        builder.header(DATE_FIELD.toLowerCase(), RFC822_SUN_SEP_9TH_2001);
        Message<TestId> row = builder.build();
        assertFalse(new MessageSearches().isMatch(SearchQuery.headerDateBefore(DATE_FIELD,
                getDate(9, 9, 2000), DateResolution.Day),row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.headerDateBefore(DATE_FIELD,
                getDate(8, 9, 2001), DateResolution.Day),row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.headerDateBefore(DATE_FIELD,
                getDate(9, 9, 2001), DateResolution.Day),row, recent, log));
        assertTrue(new MessageSearches().isMatch(SearchQuery.headerDateBefore(DATE_FIELD,
                getDate(10, 9, 2001), DateResolution.Day),row, recent, log));
        assertTrue(new MessageSearches().isMatch(SearchQuery.headerDateBefore(DATE_FIELD, getDate(9,
                9, 2002), DateResolution.Day),row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.headerDateBefore("BOGUS", getDate(9,
                9, 2001), DateResolution.Day),row, recent, log));
    }

    @Test
    public void testShouldMatchLowersHeaderDateBefore() throws Exception {
        builder.header(DATE_FIELD.toLowerCase(), RFC822_SUN_SEP_9TH_2001);
        Message<TestId> row = builder.build();
        assertFalse(new MessageSearches().isMatch(SearchQuery.headerDateBefore(DATE_FIELD,
                getDate(9, 9, 2000), DateResolution.Day),row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.headerDateBefore(DATE_FIELD,
                getDate(8, 9, 2001), DateResolution.Day),row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.headerDateBefore(DATE_FIELD,
                getDate(9, 9, 2001), DateResolution.Day),row, recent, log));
        assertTrue(new MessageSearches().isMatch(SearchQuery.headerDateBefore(DATE_FIELD,
                getDate(10, 9, 2001), DateResolution.Day),row, recent, log));
        assertTrue(new MessageSearches().isMatch(SearchQuery.headerDateBefore(DATE_FIELD, getDate(9,
                9, 2002), DateResolution.Day),row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.headerDateBefore("BOGUS", getDate(9,
                9, 2001), DateResolution.Day),row, recent, log));
    }

    @Test
    public void testMatchHeaderContainsCaps() throws Exception {
        builder.header(SUBJECT_FIELD, TEXT.toUpperCase());
        Message<TestId> row = builder.build();
        assertFalse(new MessageSearches().isMatch(SearchQuery.headerContains(DATE_FIELD,
                CUSTARD), row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.headerContains(DATE_FIELD,
                TEXT), row, recent, log));
        assertTrue(new MessageSearches().isMatch(SearchQuery.headerContains(SUBJECT_FIELD,
                TEXT), row, recent, log));
        assertTrue(new MessageSearches().isMatch(SearchQuery.headerContains(SUBJECT_FIELD,
                RHUBARD), row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.headerContains(SUBJECT_FIELD,
                CUSTARD), row, recent, log));
    }

    @Test
    public void testMatchHeaderContainsLowers() throws Exception {
        builder.header(SUBJECT_FIELD, TEXT.toUpperCase());
        Message<TestId> row = builder.build();
        assertFalse(new MessageSearches().isMatch(SearchQuery.headerContains(DATE_FIELD,
                CUSTARD), row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.headerContains(DATE_FIELD,
                TEXT), row, recent, log));
        assertTrue(new MessageSearches().isMatch(SearchQuery.headerContains(SUBJECT_FIELD,
                TEXT), row, recent, log));
        assertTrue(new MessageSearches().isMatch(SearchQuery.headerContains(SUBJECT_FIELD,
                RHUBARD), row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.headerContains(SUBJECT_FIELD,
                CUSTARD), row, recent, log));
    }

    @Test
    public void testMatchHeaderContains() throws Exception {
        builder.header(SUBJECT_FIELD, TEXT.toUpperCase());
        Message<TestId> row = builder.build();
        assertFalse(new MessageSearches().isMatch(SearchQuery.headerContains(DATE_FIELD,
                CUSTARD), row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.headerContains(DATE_FIELD,
                TEXT), row, recent, log));
        assertTrue(new MessageSearches().isMatch(SearchQuery.headerContains(SUBJECT_FIELD,
                TEXT), row, recent, log));
        assertTrue(new MessageSearches().isMatch(SearchQuery.headerContains(SUBJECT_FIELD,
                RHUBARD), row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.headerContains(SUBJECT_FIELD,
                CUSTARD), row, recent, log));
    }

    @Test
    public void testShouldMatchLowerHeaderContains() throws Exception {
        builder.header(SUBJECT_FIELD.toLowerCase(), TEXT);
        Message<TestId> row = builder.build();
        assertFalse(new MessageSearches().isMatch(SearchQuery.headerContains(DATE_FIELD,
                CUSTARD), row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.headerContains(DATE_FIELD,
                TEXT), row, recent, log));
        assertTrue(new MessageSearches().isMatch(SearchQuery.headerContains(SUBJECT_FIELD,
                TEXT), row, recent, log));
        assertTrue(new MessageSearches().isMatch(SearchQuery.headerContains(SUBJECT_FIELD,
                RHUBARD), row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.headerContains(SUBJECT_FIELD,
                CUSTARD), row, recent, log));
    }

    @Test
    public void testShouldMatchCapsHeaderContains() throws Exception {
        builder.header(SUBJECT_FIELD.toUpperCase(), TEXT);
        Message<TestId> row = builder.build();
        assertFalse(new MessageSearches().isMatch(SearchQuery.headerContains(DATE_FIELD,
                CUSTARD), row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.headerContains(DATE_FIELD,
                TEXT), row, recent, log));
        assertTrue(new MessageSearches().isMatch(SearchQuery.headerContains(SUBJECT_FIELD,
                TEXT), row, recent, log));
        assertTrue(new MessageSearches().isMatch(SearchQuery.headerContains(SUBJECT_FIELD,
                RHUBARD), row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.headerContains(SUBJECT_FIELD,
                CUSTARD), row, recent, log));
    }

    @Test
    public void testMatchHeaderExists() throws Exception {
        builder.header(SUBJECT_FIELD, TEXT);
        Message<TestId> row = builder.build();
        assertFalse(new MessageSearches().isMatch(SearchQuery.headerExists(DATE_FIELD), row,
                recent, log));
        assertTrue(new MessageSearches().isMatch(SearchQuery.headerExists(SUBJECT_FIELD),
                row, recent, log));
    }

    @Test
    public void testShouldMatchLowersHeaderExists() throws Exception {
        builder.header(SUBJECT_FIELD.toLowerCase(), TEXT);
        Message<TestId> row = builder.build();
        assertFalse(new MessageSearches().isMatch(SearchQuery.headerExists(DATE_FIELD), row,
                recent, log));
        assertTrue(new MessageSearches().isMatch(SearchQuery.headerExists(SUBJECT_FIELD),
                row, recent, log));
    }

    @Test
    public void testShouldMatchUppersHeaderExists() throws Exception {
        builder.header(SUBJECT_FIELD.toLowerCase(), TEXT);
        Message<TestId> row = builder.build();
        assertFalse(new MessageSearches().isMatch(SearchQuery.headerExists(DATE_FIELD), row,
                recent, log));
        assertTrue(new MessageSearches().isMatch(SearchQuery.headerExists(SUBJECT_FIELD),
                row, recent, log));
    }

    @Test
    public void testShouldMatchUidRange() throws Exception {
        builder.setKey(1, 1729);
        Message<TestId> row = builder.build();
        assertFalse(new MessageSearches().isMatch(SearchQuery.uid(range(1, 1)), row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.uid(range(1728, 1728)), row,
                recent, log));
        assertTrue(new MessageSearches().isMatch(SearchQuery.uid(range(1729, 1729)), row,
                recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.uid(range(1730, 1730)), row,
                recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.uid(range(1, 1728)), row,
                recent, log));
        assertTrue(new MessageSearches().isMatch(SearchQuery.uid(range(1, 1729)), row,
                recent, log));
        assertTrue(new MessageSearches().isMatch(SearchQuery.uid(range(1729, 1800)), row,
                recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery
                .uid(range(1730, Long.MAX_VALUE)), row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.uid(range(1730,
                Long.MAX_VALUE, 1, 1728)), row, recent, log));
        assertTrue(new MessageSearches().isMatch(SearchQuery.uid(range(1730, Long.MAX_VALUE,
                1, 1729)), row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery
                .uid(range(1, 1728, 1800, 1810)), row, recent, log));
        assertTrue(new MessageSearches().isMatch(SearchQuery.uid(range(1, 1, 1729, 1729)),
                row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.uid(range(1, 1, 1800, 1800)),
                row, recent, log));
    }

    @Test
    public void testShouldMatchSeenFlagSet() throws Exception {
        builder.setFlags(true, false, false, false, false, false);
        Message<TestId> row = builder.build();
        assertTrue(new MessageSearches().isMatch(SearchQuery.flagIsSet(Flags.Flag.SEEN),
                row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.flagIsSet(Flags.Flag.FLAGGED),
                row, recent, log));
        assertFalse(new MessageSearches().isMatch(
                SearchQuery.flagIsSet(Flags.Flag.ANSWERED), row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.flagIsSet(Flags.Flag.DRAFT),
                row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.flagIsSet(Flags.Flag.DELETED),
                row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.flagIsSet(Flags.Flag.RECENT),
                row, recent, log));
    }

    @Test
    public void testShouldMatchAnsweredFlagSet() throws Exception {
        builder.setFlags(false, false, true, false, false, false);
        Message<TestId> row = builder.build();
        assertFalse(new MessageSearches().isMatch(SearchQuery.flagIsSet(Flags.Flag.SEEN),
                row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.flagIsSet(Flags.Flag.FLAGGED),
                row, recent, log));
        assertTrue(new MessageSearches().isMatch(SearchQuery.flagIsSet(Flags.Flag.ANSWERED),
                row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.flagIsSet(Flags.Flag.DRAFT),
                row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.flagIsSet(Flags.Flag.DELETED),
                row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.flagIsSet(Flags.Flag.RECENT),
                row, recent, log));
    }

    @Test
    public void testShouldMatchFlaggedFlagSet() throws Exception {
        builder.setFlags(false, true, false, false, false, false);
        Message<TestId> row = builder.build();
        assertFalse(new MessageSearches().isMatch(SearchQuery.flagIsSet(Flags.Flag.SEEN),
                row, recent, log));
        assertTrue(new MessageSearches().isMatch(SearchQuery.flagIsSet(Flags.Flag.FLAGGED),
                row, recent, log));
        assertFalse(new MessageSearches().isMatch(
                SearchQuery.flagIsSet(Flags.Flag.ANSWERED), row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.flagIsSet(Flags.Flag.DRAFT),
                row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.flagIsSet(Flags.Flag.DELETED),
                row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.flagIsSet(Flags.Flag.RECENT),
                row, recent, log));
    }

    @Test
    public void testShouldMatchDraftFlagSet() throws Exception {
        builder.setFlags(false, false, false, true, false, false);
        Message<TestId> row = builder.build();
        assertFalse(new MessageSearches().isMatch(SearchQuery.flagIsSet(Flags.Flag.SEEN),
                row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.flagIsSet(Flags.Flag.FLAGGED),
                row, recent, log));
        assertFalse(new MessageSearches().isMatch(
                SearchQuery.flagIsSet(Flags.Flag.ANSWERED), row, recent, log));
        assertTrue(new MessageSearches().isMatch(SearchQuery.flagIsSet(Flags.Flag.DRAFT),
                row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.flagIsSet(Flags.Flag.DELETED),
                row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.flagIsSet(Flags.Flag.RECENT),
                row, recent, log));
    }

    
    @Test
    public void testShouldMatchDeletedFlagSet() throws Exception {
        builder.setFlags(false, false, false, false, true, false);
        Message<TestId> row = builder.build();
        assertFalse(new MessageSearches().isMatch(SearchQuery.flagIsSet(Flags.Flag.SEEN),
                row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.flagIsSet(Flags.Flag.FLAGGED),
                row, recent, log));
        assertFalse(new MessageSearches().isMatch(
                SearchQuery.flagIsSet(Flags.Flag.ANSWERED), row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.flagIsSet(Flags.Flag.DRAFT),
                row, recent, log));
        assertTrue(new MessageSearches().isMatch(SearchQuery.flagIsSet(Flags.Flag.DELETED),
                row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.flagIsSet(Flags.Flag.RECENT),
                row, recent, log));
    }

    @Test
    public void testShouldMatchSeenRecentSet() throws Exception {
        builder.setFlags(false, false, false, false, false, false);
        Message<TestId> row = builder.build();
        recent.add(new Long(row.getUid()));
        assertFalse(new MessageSearches().isMatch(SearchQuery.flagIsSet(Flags.Flag.SEEN),
                row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.flagIsSet(Flags.Flag.FLAGGED),
                row, recent, log));
        assertFalse(new MessageSearches().isMatch(
                SearchQuery.flagIsSet(Flags.Flag.ANSWERED), row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.flagIsSet(Flags.Flag.DRAFT),
                row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.flagIsSet(Flags.Flag.DELETED),
                row, recent, log));
        assertTrue(new MessageSearches().isMatch(SearchQuery.flagIsSet(Flags.Flag.RECENT),
                row, recent, log));
    }

    @Test
    public void testShouldMatchSeenFlagUnSet() throws Exception {
        builder.setFlags(false, true, true, true, true, true);
        Message<TestId> row = builder.build();
        recent.add(new Long(row.getUid()));
        assertTrue(new MessageSearches().isMatch(SearchQuery.flagIsUnSet(Flags.Flag.SEEN),
                row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery
                .flagIsUnSet(Flags.Flag.FLAGGED), row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery
                .flagIsUnSet(Flags.Flag.ANSWERED), row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.flagIsUnSet(Flags.Flag.DRAFT),
                row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery
                .flagIsUnSet(Flags.Flag.DELETED), row, recent, log));
        assertFalse(new MessageSearches().isMatch(
                SearchQuery.flagIsUnSet(Flags.Flag.RECENT), row, recent, log));
    }

    @Test
    public void testShouldMatchAnsweredFlagUnSet() throws Exception {
        builder.setFlags(true, true, false, true, true, true);
        Message<TestId> row = builder.build();
        recent.add(new Long(row.getUid()));
        assertFalse(new MessageSearches().isMatch(SearchQuery.flagIsUnSet(Flags.Flag.SEEN),
                row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery
                .flagIsUnSet(Flags.Flag.FLAGGED), row, recent, log));
        assertTrue(new MessageSearches().isMatch(SearchQuery
                .flagIsUnSet(Flags.Flag.ANSWERED), row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.flagIsUnSet(Flags.Flag.DRAFT),
                row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery
                .flagIsUnSet(Flags.Flag.DELETED), row, recent, log));
        assertFalse(new MessageSearches().isMatch(
                SearchQuery.flagIsUnSet(Flags.Flag.RECENT), row, recent, log));
    }

    @Test
    public void testShouldMatchFlaggedFlagUnSet() throws Exception {
        builder.setFlags(true, false, true, true, true, true);
        Message<TestId> row = builder.build();
        recent.add(new Long(row.getUid()));
        assertFalse(new MessageSearches().isMatch(SearchQuery.flagIsUnSet(Flags.Flag.SEEN),
                row, recent, log));
        assertTrue(new MessageSearches().isMatch(
                SearchQuery.flagIsUnSet(Flags.Flag.FLAGGED), row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery
                .flagIsUnSet(Flags.Flag.ANSWERED), row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.flagIsUnSet(Flags.Flag.DRAFT),
                row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery
                .flagIsUnSet(Flags.Flag.DELETED), row, recent, log));
        assertFalse(new MessageSearches().isMatch(
                SearchQuery.flagIsUnSet(Flags.Flag.RECENT), row, recent, log));
    }

    @Test
    public void testShouldMatchDraftFlagUnSet() throws Exception {
        builder.setFlags(true, true, true, false, true, true);
        Message<TestId> row = builder.build();
        recent.add(new Long(row.getUid()));
        assertFalse(new MessageSearches().isMatch(SearchQuery.flagIsUnSet(Flags.Flag.SEEN),
                row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery
                .flagIsUnSet(Flags.Flag.FLAGGED), row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery
                .flagIsUnSet(Flags.Flag.ANSWERED), row, recent, log));
        assertTrue(new MessageSearches().isMatch(SearchQuery.flagIsUnSet(Flags.Flag.DRAFT),
                row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery
                .flagIsUnSet(Flags.Flag.DELETED), row, recent, log));
        assertFalse(new MessageSearches().isMatch(
                SearchQuery.flagIsUnSet(Flags.Flag.RECENT), row, recent, log));
    }

    @Test
    public void testShouldMatchDeletedFlagUnSet() throws Exception {
        builder.setFlags(true, true, true, true, false, true);
        Message<TestId> row = builder.build();
        recent.add(new Long(row.getUid()));
        assertFalse(new MessageSearches().isMatch(SearchQuery.flagIsUnSet(Flags.Flag.SEEN),
                row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery
                .flagIsUnSet(Flags.Flag.FLAGGED), row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery
                .flagIsUnSet(Flags.Flag.ANSWERED), row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.flagIsUnSet(Flags.Flag.DRAFT),
                row, recent, log));
        assertTrue(new MessageSearches().isMatch(
                SearchQuery.flagIsUnSet(Flags.Flag.DELETED), row, recent, log));
        assertFalse(new MessageSearches().isMatch(
                SearchQuery.flagIsUnSet(Flags.Flag.RECENT), row, recent, log));
    }

    @Test
    public void testShouldMatchSeenRecentUnSet() throws Exception {
        builder.setFlags(true, true, true, true, true, true);
        Message<TestId> row = builder.build();
        recent.add(new Long(row.getUid() + 1));
        assertFalse(new MessageSearches().isMatch(SearchQuery.flagIsUnSet(Flags.Flag.SEEN),
                row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery
                .flagIsUnSet(Flags.Flag.FLAGGED), row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery
                .flagIsUnSet(Flags.Flag.ANSWERED), row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.flagIsUnSet(Flags.Flag.DRAFT),
                row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery
                .flagIsUnSet(Flags.Flag.DELETED), row, recent, log));
        assertTrue(new MessageSearches().isMatch(SearchQuery.flagIsUnSet(Flags.Flag.RECENT),
                row, recent, log));
    }

    @Test
    public void testShouldMatchAll() throws Exception {
        Message<TestId> row = builder.build();
        assertTrue(new MessageSearches().isMatch(SearchQuery.all(), row, recent, log));
    }

    @Test
    public void testShouldMatchNot() throws Exception {
        Message<TestId> row = builder.build();
        assertFalse(new MessageSearches().isMatch(SearchQuery.not(SearchQuery.all()), row,
                recent, log));
        assertTrue(new MessageSearches().isMatch(SearchQuery.not(SearchQuery
                .headerExists(DATE_FIELD)), row, recent, log));
    }

    @Test
    public void testShouldMatchOr() throws Exception {
        Message<TestId> row = builder.build();
        assertTrue(new MessageSearches().isMatch(SearchQuery.or(SearchQuery.all(),
                SearchQuery.headerExists(DATE_FIELD)), row, recent, log));
        assertTrue(new MessageSearches().isMatch(SearchQuery.or(SearchQuery
                .headerExists(DATE_FIELD), SearchQuery.all()), row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery
                .or(SearchQuery.headerExists(DATE_FIELD), SearchQuery
                        .headerExists(DATE_FIELD)), row, recent, log));
        assertTrue(new MessageSearches().isMatch(SearchQuery.or(SearchQuery.all(),
                SearchQuery.all()), row, recent, log));
    }

    @Test
    public void testShouldMatchAnd() throws Exception {
        Message<TestId> row = builder.build();
        assertFalse(new MessageSearches().isMatch(SearchQuery.and(SearchQuery.all(),
                SearchQuery.headerExists(DATE_FIELD)), row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.and(SearchQuery
                .headerExists(DATE_FIELD), SearchQuery.all()), row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery
                .and(SearchQuery.headerExists(DATE_FIELD), SearchQuery
                        .headerExists(DATE_FIELD)), row, recent, log));
        assertTrue(new MessageSearches().isMatch(SearchQuery.and(SearchQuery.all(),
                SearchQuery.all()), row, recent, log));
    }
    
    private SearchQuery.NumericRange[] range(long low, long high) {
        SearchQuery.NumericRange[] results = { new SearchQuery.NumericRange(
                low, high) };
        return results;
    }

    private SearchQuery.NumericRange[] range(long lowOne, long highOne,
            long lowTwo, long highTwo) {
        SearchQuery.NumericRange[] results = {
                new SearchQuery.NumericRange(lowOne, highOne),
                new SearchQuery.NumericRange(lowTwo, highTwo) };
        return results;
    }
    
    
    @Test
    public void testMatchHeaderDateOnWithOffset() throws Exception {
        builder.header(DATE_FIELD, "Mon, 26 Mar 2007 00:00:00 +0300");
        Message<TestId> row = builder.build();
        assertTrue(new MessageSearches().isMatch(SearchQuery.headerDateOn(DATE_FIELD, getDate(26, 3,
                2007), DateResolution.Day),row, recent, log));
        
        assertFalse(new MessageSearches().isMatch(SearchQuery.headerDateOn(DATE_FIELD, getDate(25, 3,
                2007), DateResolution.Day),row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.headerDateOn(DATE_FIELD, getDate(27, 3,
                2007), DateResolution.Day),row, recent, log));
    }
    

    @Test
    public void testShouldMatchHeaderDateBeforeWithOffset() throws Exception {
        builder.header(DATE_FIELD, "Mon, 26 Mar 2007 00:00:00 +0300");
        Message<TestId> row = builder.build();
        assertFalse(new MessageSearches().isMatch(SearchQuery.headerDateBefore(DATE_FIELD, getDate(26, 3,
                2007), DateResolution.Day),row, recent, log));
        
        assertTrue(new MessageSearches().isMatch(SearchQuery.headerDateBefore(DATE_FIELD, getDate(27, 3,
                2007), DateResolution.Day),row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.headerDateBefore(DATE_FIELD, getDate(25, 3,
                2007), DateResolution.Day),row, recent, log));
    }

    @Test
    public void testShouldMatchHeaderDateAfterWithOffset() throws Exception {
        builder.header(DATE_FIELD, "Mon, 26 Mar 2007 00:00:00 +0300");
        Message<TestId> row = builder.build();
        assertFalse(new MessageSearches().isMatch(SearchQuery.headerDateAfter(DATE_FIELD, getDate(26, 3,
                2007), DateResolution.Day),row, recent, log));
        
        assertFalse(new MessageSearches().isMatch(SearchQuery.headerDateAfter(DATE_FIELD, getDate(27, 3,
                2007), DateResolution.Day),row, recent, log));
        assertTrue(new MessageSearches().isMatch(SearchQuery.headerDateAfter(DATE_FIELD, getDate(25, 3,
                2007), DateResolution.Day),row, recent, log));
    }
    
    @Test
    public void testShouldMatchAddressHeaderWithComments() throws Exception {
        builder.header("To", "<user-from (comment)@ (comment) domain.org>");
        Message<TestId> row = builder.build();
        assertTrue(new MessageSearches().isMatch(SearchQuery.address(AddressType.To, "user-from@domain.org"), row, recent, log));
        assertFalse(new MessageSearches().isMatch(SearchQuery.address(AddressType.From, "user-from@domain.org"), row, recent, log));
    }

}
