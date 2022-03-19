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

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;
import java.util.TimeZone;

import jakarta.mail.Flags;

import org.apache.james.mailbox.AttachmentContentLoader;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.extractor.TextExtractor;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.model.SearchQuery.AddressType;
import org.apache.james.mailbox.model.SearchQuery.DateResolution;
import org.apache.james.mailbox.store.MessageBuilder;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SearchUtilsTest {
    static final String RHUBARD = "Rhubard";

    static final String CUSTARD = "Custard";

    static final Date SUN_SEP_9TH_2001 = new Date(1000000000000L);

    static final int SIZE = 1729;

    static final String DATE_FIELD = "Date";

    static final String SUBJECT_FIELD = "Subject";

    static final String RFC822_SUN_SEP_9TH_2001 = "Sun, 9 Sep 2001 09:10:48 +0000 (GMT)";

    static final String TEXT = RHUBARD + RHUBARD + RHUBARD;

    MessageBuilder builder;

    Collection<MessageUid> recent;

    MessageSearches messageSearches;
    
    Calendar getGMT() {
        return Calendar.getInstance(TimeZone.getTimeZone("GMT"), Locale.UK);
    }
    
    Date getDate(int day, int month, int year) {
        Calendar cal = getGMT();
        cal.set(year, month - 1, day);
        return cal.getTime();
    }
    
    @BeforeEach
    void setUp() throws Exception {
        recent = new ArrayList<>();
        builder = new MessageBuilder()
            .uid(MessageUid.of(1009));
        
        Iterator<MailboxMessage> messages = null;
        SearchQuery query = null; 
        TextExtractor textExtractor = null;
        MailboxSession session = null;

        AttachmentContentLoader attachmentContentLoader = null;
        messageSearches = new MessageSearches(messages, query, textExtractor, attachmentContentLoader, session);
    }
    
    @Test
    void testMatchSizeLessThan() throws Exception {
        builder.size(SIZE);
        MailboxMessage row = builder.build();
        assertThat(messageSearches.isMatch(SearchQuery.sizeLessThan(SIZE - 1), row,
                recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.sizeLessThan(SIZE), row,
                recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.sizeLessThan(SIZE + 1), row,
                recent)).isTrue();
        assertThat(messageSearches.isMatch(
                SearchQuery.sizeLessThan(Integer.MAX_VALUE), row, recent)).isTrue();
    }

    @Test
    void testMatchSizeMoreThan() throws Exception {
        builder.size(SIZE);
        MailboxMessage row = builder.build();
        assertThat(messageSearches.isMatch(SearchQuery.sizeGreaterThan(SIZE - 1), row,
                recent)).isTrue();
        assertThat(messageSearches.isMatch(SearchQuery.sizeGreaterThan(SIZE), row,
                recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.sizeGreaterThan(SIZE + 1),
                row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery
                .sizeGreaterThan(Integer.MAX_VALUE), row, recent)).isFalse();
    }

    @Test
    void testMatchSizeEquals() throws Exception {
        builder.size(SIZE);
        MailboxMessage row = builder.build();
        assertThat(messageSearches.isMatch(SearchQuery.sizeEquals(SIZE - 1), row,
                recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.sizeEquals(SIZE), row, recent)).isTrue();
        assertThat(messageSearches.isMatch(SearchQuery.sizeEquals(SIZE + 1), row,
                recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.sizeEquals(Integer.MAX_VALUE),
                row, recent)).isFalse();
    }

    @Test
    void testMatchInternalDateEquals() throws Exception {
        builder.internalDate(SUN_SEP_9TH_2001);
        MailboxMessage row = builder.build();
        assertThat(messageSearches.isMatch(SearchQuery.internalDateOn(getDate(9, 9, 2000), DateResolution.Day),
                row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.internalDateOn(getDate(8, 9, 2001), DateResolution.Day),
                row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.internalDateOn(getDate(9, 9, 2001), DateResolution.Day),
                row, recent)).isTrue();
        assertThat(messageSearches.isMatch(SearchQuery.internalDateOn(getDate(10, 9, 2001), DateResolution.Day),
                row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.internalDateOn(getDate(9, 9, 2002), DateResolution.Day),
                row, recent)).isFalse();
    }

    
    @Test
    void testMatchInternalDateBefore() throws Exception {
        builder.internalDate(SUN_SEP_9TH_2001);
        MailboxMessage row = builder.build();
        assertThat(messageSearches.isMatch(
                SearchQuery.internalDateBefore(getDate(9, 9, 2000), DateResolution.Day), row, recent)).isFalse();
        assertThat(messageSearches.isMatch(
                SearchQuery.internalDateBefore(getDate(8, 9, 2001), DateResolution.Day), row, recent)).isFalse();
        assertThat(messageSearches.isMatch(
                SearchQuery.internalDateBefore(getDate(9, 9, 2001), DateResolution.Day), row, recent)).isFalse();
        assertThat(messageSearches.isMatch(
                SearchQuery.internalDateBefore(getDate(10, 9, 2001), DateResolution.Day), row, recent)).isTrue();
        assertThat(messageSearches.isMatch(SearchQuery.internalDateBefore(getDate(9, 9, 2002), DateResolution.Day),
                row, recent)).isTrue();
    }

    @Test
    void testMatchInternalDateAfter() throws Exception {
        builder.internalDate(SUN_SEP_9TH_2001);
        MailboxMessage row = builder.build();
        assertThat(messageSearches.isMatch(SearchQuery.internalDateAfter(getDate(9, 9, 2000), DateResolution.Day),
                row, recent)).isTrue();
        assertThat(messageSearches.isMatch(SearchQuery.internalDateAfter(getDate(8, 9, 2001), DateResolution.Day),
                row, recent)).isTrue();
        assertThat(messageSearches.isMatch(SearchQuery.internalDateAfter(getDate(9, 9, 2001), DateResolution.Day),
                row, recent)).isFalse();
        assertThat(messageSearches.isMatch(
                SearchQuery.internalDateAfter(getDate(10, 9, 2001), DateResolution.Day), row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.internalDateAfter(getDate(9, 9, 2002), DateResolution.Day),
                row, recent)).isFalse();
    }

    @Test
    void testMatchHeaderDateAfter() throws Exception {
        builder.header(DATE_FIELD, RFC822_SUN_SEP_9TH_2001);
        MailboxMessage row = builder.build();
        assertThat(messageSearches.isMatch(SearchQuery.headerDateAfter(DATE_FIELD, getDate(9,
                9, 2000), DateResolution.Day), row, recent)).isTrue();
        assertThat(messageSearches.isMatch(SearchQuery.headerDateAfter(DATE_FIELD, getDate(8,
                9, 2001), DateResolution.Day), row, recent)).isTrue();
        assertThat(messageSearches.isMatch(SearchQuery.headerDateAfter(DATE_FIELD, getDate(9,
                9, 2001), DateResolution.Day), row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.headerDateAfter(DATE_FIELD,
                getDate(10, 9, 2001), DateResolution.Day), row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.headerDateAfter(DATE_FIELD, getDate(9,
                9, 2002), DateResolution.Day), row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.headerDateAfter("BOGUS", getDate(9, 9,
                2001), DateResolution.Day), row, recent)).isFalse();
    }

    @Test
    void testShouldMatchCapsHeaderDateAfter() throws Exception {
        builder.header(DATE_FIELD.toUpperCase(Locale.US), RFC822_SUN_SEP_9TH_2001);
        MailboxMessage row = builder.build();
        assertThat(messageSearches.isMatch(SearchQuery.headerDateAfter(DATE_FIELD, getDate(9,
                9, 2000), DateResolution.Day), row, recent)).isTrue();
        assertThat(messageSearches.isMatch(SearchQuery.headerDateAfter(DATE_FIELD, getDate(8,
                9, 2001), DateResolution.Day), row, recent)).isTrue();
        assertThat(messageSearches.isMatch(SearchQuery.headerDateAfter(DATE_FIELD, getDate(9,
                9, 2001), DateResolution.Day), row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.headerDateAfter(DATE_FIELD,
                getDate(10, 9, 2001), DateResolution.Day), row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.headerDateAfter(DATE_FIELD, getDate(9,
                9, 2002), DateResolution.Day), row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.headerDateAfter("BOGUS", getDate(9, 9,
                2001), DateResolution.Day), row, recent)).isFalse();
    }

    @Test
    void testShouldMatchLowersHeaderDateAfter() throws Exception {
        builder.header(DATE_FIELD.toLowerCase(Locale.US), RFC822_SUN_SEP_9TH_2001);
        MailboxMessage row = builder.build();
        assertThat(messageSearches.isMatch(SearchQuery.headerDateAfter(DATE_FIELD, getDate(9,
                9, 2000), DateResolution.Day), row, recent)).isTrue();
        assertThat(messageSearches.isMatch(SearchQuery.headerDateAfter(DATE_FIELD, getDate(8,
                9, 2001),DateResolution.Day), row, recent)).isTrue();
        assertThat(messageSearches.isMatch(SearchQuery.headerDateAfter(DATE_FIELD, getDate(9,
                9, 2001), DateResolution.Day),row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.headerDateAfter(DATE_FIELD,
                getDate(10, 9, 2001), DateResolution.Day),row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.headerDateAfter(DATE_FIELD, getDate(9,
                9, 2002), DateResolution.Day),row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.headerDateAfter("BOGUS", getDate(9, 9,
                2001), DateResolution.Day),row, recent)).isFalse();
    }

    @Test
    void testMatchHeaderDateOn() throws Exception {
        builder.header(DATE_FIELD, RFC822_SUN_SEP_9TH_2001);
        MailboxMessage row = builder.build();
        assertThat(messageSearches.isMatch(SearchQuery.headerDateOn(DATE_FIELD, getDate(9, 9,
                2000), DateResolution.Day),row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.headerDateOn(DATE_FIELD, getDate(8, 9,
                2001), DateResolution.Day),row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.headerDateOn(DATE_FIELD, getDate(9, 9,
                2001), DateResolution.Day),row, recent)).isTrue();
        assertThat(messageSearches.isMatch(SearchQuery.headerDateOn(DATE_FIELD, getDate(10,
                9, 2001), DateResolution.Day),row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.headerDateOn(DATE_FIELD, getDate(9, 9,
                2002), DateResolution.Day), row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.headerDateOn("BOGUS", getDate(9, 9,
                2001), DateResolution.Day), row, recent)).isFalse();
    }

    @Test
    void testShouldMatchCapsHeaderDateOn() throws Exception {
        builder.header(DATE_FIELD.toUpperCase(Locale.US), RFC822_SUN_SEP_9TH_2001);
        MailboxMessage row = builder.build();
        assertThat(messageSearches.isMatch(SearchQuery.headerDateOn(DATE_FIELD, getDate(9, 9,
                2000), DateResolution.Day),row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.headerDateOn(DATE_FIELD, getDate(8, 9,
                2001), DateResolution.Day),row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.headerDateOn(DATE_FIELD, getDate(9, 9,
                2001), DateResolution.Day),row, recent)).isTrue();
        assertThat(messageSearches.isMatch(SearchQuery.headerDateOn(DATE_FIELD, getDate(10,
                9, 2001), DateResolution.Day),row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.headerDateOn(DATE_FIELD, getDate(9, 9,
                2002), DateResolution.Day),row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.headerDateOn("BOGUS", getDate(9, 9,
                2001), DateResolution.Day),row, recent)).isFalse();
    }

    @Test
    void testShouldMatchLowersHeaderDateOn() throws Exception {
        builder.header(DATE_FIELD.toLowerCase(Locale.US), RFC822_SUN_SEP_9TH_2001);
        MailboxMessage row = builder.build();
        assertThat(messageSearches.isMatch(SearchQuery.headerDateOn(DATE_FIELD, getDate(9, 9,
                2000), DateResolution.Day),row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.headerDateOn(DATE_FIELD, getDate(8, 9,
                2001), DateResolution.Day),row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.headerDateOn(DATE_FIELD, getDate(9, 9,
                2001), DateResolution.Day),row, recent)).isTrue();
        assertThat(messageSearches.isMatch(SearchQuery.headerDateOn(DATE_FIELD, getDate(10,
                9, 2001), DateResolution.Day),row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.headerDateOn(DATE_FIELD, getDate(9, 9,
                2002), DateResolution.Day),row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.headerDateOn("BOGUS", getDate(9, 9,
                2001), DateResolution.Day),row, recent)).isFalse();
    }

    @Test
    void testMatchHeaderDateBefore() throws Exception {
        builder.header(DATE_FIELD.toLowerCase(Locale.US), RFC822_SUN_SEP_9TH_2001);
        MailboxMessage row = builder.build();
        assertThat(messageSearches.isMatch(SearchQuery.headerDateBefore(DATE_FIELD,
                getDate(9, 9, 2000), DateResolution.Day),row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.headerDateBefore(DATE_FIELD,
                getDate(8, 9, 2001), DateResolution.Day),row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.headerDateBefore(DATE_FIELD,
                getDate(9, 9, 2001), DateResolution.Day),row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.headerDateBefore(DATE_FIELD,
                getDate(10, 9, 2001), DateResolution.Day),row, recent)).isTrue();
        assertThat(messageSearches.isMatch(SearchQuery.headerDateBefore(DATE_FIELD, getDate(9,
                9, 2002), DateResolution.Day),row, recent)).isTrue();
        assertThat(messageSearches.isMatch(SearchQuery.headerDateBefore("BOGUS", getDate(9,
                9, 2001), DateResolution.Day),row, recent)).isFalse();
    }

    @Test
    void testShouldMatchCapsHeaderDateBefore() throws Exception {
        builder.header(DATE_FIELD.toLowerCase(Locale.US), RFC822_SUN_SEP_9TH_2001);
        MailboxMessage row = builder.build();
        assertThat(messageSearches.isMatch(SearchQuery.headerDateBefore(DATE_FIELD,
                getDate(9, 9, 2000), DateResolution.Day),row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.headerDateBefore(DATE_FIELD,
                getDate(8, 9, 2001), DateResolution.Day),row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.headerDateBefore(DATE_FIELD,
                getDate(9, 9, 2001), DateResolution.Day),row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.headerDateBefore(DATE_FIELD,
                getDate(10, 9, 2001), DateResolution.Day),row, recent)).isTrue();
        assertThat(messageSearches.isMatch(SearchQuery.headerDateBefore(DATE_FIELD, getDate(9,
                9, 2002), DateResolution.Day),row, recent)).isTrue();
        assertThat(messageSearches.isMatch(SearchQuery.headerDateBefore("BOGUS", getDate(9,
                9, 2001), DateResolution.Day),row, recent)).isFalse();
    }

    @Test
    void testShouldMatchLowersHeaderDateBefore() throws Exception {
        builder.header(DATE_FIELD.toLowerCase(Locale.US), RFC822_SUN_SEP_9TH_2001);
        MailboxMessage row = builder.build();
        assertThat(messageSearches.isMatch(SearchQuery.headerDateBefore(DATE_FIELD,
                getDate(9, 9, 2000), DateResolution.Day),row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.headerDateBefore(DATE_FIELD,
                getDate(8, 9, 2001), DateResolution.Day),row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.headerDateBefore(DATE_FIELD,
                getDate(9, 9, 2001), DateResolution.Day),row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.headerDateBefore(DATE_FIELD,
                getDate(10, 9, 2001), DateResolution.Day),row, recent)).isTrue();
        assertThat(messageSearches.isMatch(SearchQuery.headerDateBefore(DATE_FIELD, getDate(9,
                9, 2002), DateResolution.Day),row, recent)).isTrue();
        assertThat(messageSearches.isMatch(SearchQuery.headerDateBefore("BOGUS", getDate(9,
                9, 2001), DateResolution.Day),row, recent)).isFalse();
    }

    @Test
    void testMatchHeaderContainsCaps() throws Exception {
        builder.header(SUBJECT_FIELD, TEXT.toUpperCase(Locale.US));
        MailboxMessage row = builder.build();
        assertThat(messageSearches.isMatch(SearchQuery.headerContains(DATE_FIELD,
                CUSTARD), row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.headerContains(DATE_FIELD,
                TEXT), row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.headerContains(SUBJECT_FIELD,
                TEXT), row, recent)).isTrue();
        assertThat(messageSearches.isMatch(SearchQuery.headerContains(SUBJECT_FIELD,
                RHUBARD), row, recent)).isTrue();
        assertThat(messageSearches.isMatch(SearchQuery.headerContains(SUBJECT_FIELD,
                CUSTARD), row, recent)).isFalse();
    }

    @Test
    void testMatchHeaderContainsLowers() throws Exception {
        builder.header(SUBJECT_FIELD, TEXT.toUpperCase(Locale.US));
        MailboxMessage row = builder.build();
        assertThat(messageSearches.isMatch(SearchQuery.headerContains(DATE_FIELD,
                CUSTARD), row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.headerContains(DATE_FIELD,
                TEXT), row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.headerContains(SUBJECT_FIELD,
                TEXT), row, recent)).isTrue();
        assertThat(messageSearches.isMatch(SearchQuery.headerContains(SUBJECT_FIELD,
                RHUBARD), row, recent)).isTrue();
        assertThat(messageSearches.isMatch(SearchQuery.headerContains(SUBJECT_FIELD,
                CUSTARD), row, recent)).isFalse();
    }

    @Test
    void testMatchHeaderContains() throws Exception {
        builder.header(SUBJECT_FIELD, TEXT.toUpperCase(Locale.US));
        MailboxMessage row = builder.build();
        assertThat(messageSearches.isMatch(SearchQuery.headerContains(DATE_FIELD,
                CUSTARD), row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.headerContains(DATE_FIELD,
                TEXT), row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.headerContains(SUBJECT_FIELD,
                TEXT), row, recent)).isTrue();
        assertThat(messageSearches.isMatch(SearchQuery.headerContains(SUBJECT_FIELD,
                RHUBARD), row, recent)).isTrue();
        assertThat(messageSearches.isMatch(SearchQuery.headerContains(SUBJECT_FIELD,
                CUSTARD), row, recent)).isFalse();
    }

    @Test
    void testShouldMatchLowerHeaderContains() throws Exception {
        builder.header(SUBJECT_FIELD.toLowerCase(Locale.US), TEXT);
        MailboxMessage row = builder.build();
        assertThat(messageSearches.isMatch(SearchQuery.headerContains(DATE_FIELD,
                CUSTARD), row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.headerContains(DATE_FIELD,
                TEXT), row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.headerContains(SUBJECT_FIELD,
                TEXT), row, recent)).isTrue();
        assertThat(messageSearches.isMatch(SearchQuery.headerContains(SUBJECT_FIELD,
                RHUBARD), row, recent)).isTrue();
        assertThat(messageSearches.isMatch(SearchQuery.headerContains(SUBJECT_FIELD,
                CUSTARD), row, recent)).isFalse();
    }

    @Test
    void testShouldMatchCapsHeaderContains() throws Exception {
        builder.header(SUBJECT_FIELD.toUpperCase(Locale.US), TEXT);
        MailboxMessage row = builder.build();
        assertThat(messageSearches.isMatch(SearchQuery.headerContains(DATE_FIELD,
                CUSTARD), row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.headerContains(DATE_FIELD,
                TEXT), row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.headerContains(SUBJECT_FIELD,
                TEXT), row, recent)).isTrue();
        assertThat(messageSearches.isMatch(SearchQuery.headerContains(SUBJECT_FIELD,
                RHUBARD), row, recent)).isTrue();
        assertThat(messageSearches.isMatch(SearchQuery.headerContains(SUBJECT_FIELD,
                CUSTARD), row, recent)).isFalse();
    }

    @Test
    void testMatchHeaderExists() throws Exception {
        builder.header(SUBJECT_FIELD, TEXT);
        MailboxMessage row = builder.build();
        assertThat(messageSearches.isMatch(SearchQuery.headerExists(DATE_FIELD), row,
                recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.headerExists(SUBJECT_FIELD),
                row, recent)).isTrue();
    }

    @Test
    void testShouldMatchLowersHeaderExists() throws Exception {
        builder.header(SUBJECT_FIELD.toLowerCase(Locale.US), TEXT);
        MailboxMessage row = builder.build();
        assertThat(messageSearches.isMatch(SearchQuery.headerExists(DATE_FIELD), row,
                recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.headerExists(SUBJECT_FIELD),
                row, recent)).isTrue();
    }

    @Test
    void testShouldMatchUppersHeaderExists() throws Exception {
        builder.header(SUBJECT_FIELD.toLowerCase(Locale.US), TEXT);
        MailboxMessage row = builder.build();
        assertThat(messageSearches.isMatch(SearchQuery.headerExists(DATE_FIELD), row,
                recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.headerExists(SUBJECT_FIELD),
                row, recent)).isTrue();
    }

    @Test
    void testShouldMatchUidRange() throws Exception {
        builder.setKey(1, MessageUid.of(1729));
        MailboxMessage row = builder.build();
        assertThat(messageSearches.isMatch(SearchQuery.uid(new SearchQuery.UidRange(MessageUid.of(1), MessageUid.of(1))), row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.uid(new SearchQuery.UidRange(MessageUid.of(1728), MessageUid.of(1728))), row,
                recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.uid(new SearchQuery.UidRange(MessageUid.of(1729), MessageUid.of(1729))), row,
                recent)).isTrue();
        assertThat(messageSearches.isMatch(SearchQuery.uid(new SearchQuery.UidRange(MessageUid.of(1730), MessageUid.of(1730))), row,
                recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.uid(new SearchQuery.UidRange(MessageUid.of(1), MessageUid.of(1728))), row,
                recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.uid(new SearchQuery.UidRange(MessageUid.of(1), MessageUid.of(1729))), row,
                recent)).isTrue();
        assertThat(messageSearches.isMatch(SearchQuery.uid(new SearchQuery.UidRange(MessageUid.of(1729), MessageUid.of(1800))), row,
                recent)).isTrue();
        assertThat(messageSearches.isMatch(SearchQuery
                .uid(new SearchQuery.UidRange(MessageUid.of(1730), MessageUid.MAX_VALUE)), row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.uid(range(MessageUid.of(1730),
                MessageUid.MAX_VALUE, MessageUid.of(1), MessageUid.of(1728))), row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.uid(range(MessageUid.of(1730), MessageUid.MAX_VALUE,
                MessageUid.of(1), MessageUid.of(1729))), row, recent)).isTrue();
        assertThat(messageSearches.isMatch(SearchQuery
                .uid(range(MessageUid.of(1), MessageUid.of(1728), MessageUid.of(1800), MessageUid.of(1810))), row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.uid(range(MessageUid.of(1), MessageUid.of(1), MessageUid.of(1729), MessageUid.of(1729))),
                row, recent)).isTrue();
        assertThat(messageSearches.isMatch(SearchQuery.uid(range(MessageUid.of(1), MessageUid.of(1), MessageUid.of(1800), MessageUid.of(1800))),
                row, recent)).isFalse();
    }

    @Test
    void testShouldMatchSeenFlagSet() throws Exception {
        builder.setFlags(true, false, false, false, false, false);
        MailboxMessage row = builder.build();
        assertThat(messageSearches.isMatch(SearchQuery.flagIsSet(Flags.Flag.SEEN),
                row, recent)).isTrue();
        assertThat(messageSearches.isMatch(SearchQuery.flagIsSet(Flags.Flag.FLAGGED),
                row, recent)).isFalse();
        assertThat(messageSearches.isMatch(
                SearchQuery.flagIsSet(Flags.Flag.ANSWERED), row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.flagIsSet(Flags.Flag.DRAFT),
                row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.flagIsSet(Flags.Flag.DELETED),
                row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.flagIsSet(Flags.Flag.RECENT),
                row, recent)).isFalse();
    }

    @Test
    void testShouldMatchAnsweredFlagSet() throws Exception {
        builder.setFlags(false, false, true, false, false, false);
        MailboxMessage row = builder.build();
        assertThat(messageSearches.isMatch(SearchQuery.flagIsSet(Flags.Flag.SEEN),
                row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.flagIsSet(Flags.Flag.FLAGGED),
                row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.flagIsSet(Flags.Flag.ANSWERED),
                row, recent)).isTrue();
        assertThat(messageSearches.isMatch(SearchQuery.flagIsSet(Flags.Flag.DRAFT),
                row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.flagIsSet(Flags.Flag.DELETED),
                row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.flagIsSet(Flags.Flag.RECENT),
                row, recent)).isFalse();
    }

    @Test
    void testShouldMatchFlaggedFlagSet() throws Exception {
        builder.setFlags(false, true, false, false, false, false);
        MailboxMessage row = builder.build();
        assertThat(messageSearches.isMatch(SearchQuery.flagIsSet(Flags.Flag.SEEN),
                row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.flagIsSet(Flags.Flag.FLAGGED),
                row, recent)).isTrue();
        assertThat(messageSearches.isMatch(
                SearchQuery.flagIsSet(Flags.Flag.ANSWERED), row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.flagIsSet(Flags.Flag.DRAFT),
                row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.flagIsSet(Flags.Flag.DELETED),
                row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.flagIsSet(Flags.Flag.RECENT),
                row, recent)).isFalse();
    }

    @Test
    void testShouldMatchDraftFlagSet() throws Exception {
        builder.setFlags(false, false, false, true, false, false);
        MailboxMessage row = builder.build();
        assertThat(messageSearches.isMatch(SearchQuery.flagIsSet(Flags.Flag.SEEN),
                row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.flagIsSet(Flags.Flag.FLAGGED),
                row, recent)).isFalse();
        assertThat(messageSearches.isMatch(
                SearchQuery.flagIsSet(Flags.Flag.ANSWERED), row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.flagIsSet(Flags.Flag.DRAFT),
                row, recent)).isTrue();
        assertThat(messageSearches.isMatch(SearchQuery.flagIsSet(Flags.Flag.DELETED),
                row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.flagIsSet(Flags.Flag.RECENT),
                row, recent)).isFalse();
    }

    
    @Test
    void testShouldMatchDeletedFlagSet() throws Exception {
        builder.setFlags(false, false, false, false, true, false);
        MailboxMessage row = builder.build();
        assertThat(messageSearches.isMatch(SearchQuery.flagIsSet(Flags.Flag.SEEN),
                row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.flagIsSet(Flags.Flag.FLAGGED),
                row, recent)).isFalse();
        assertThat(messageSearches.isMatch(
                SearchQuery.flagIsSet(Flags.Flag.ANSWERED), row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.flagIsSet(Flags.Flag.DRAFT),
                row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.flagIsSet(Flags.Flag.DELETED),
                row, recent)).isTrue();
        assertThat(messageSearches.isMatch(SearchQuery.flagIsSet(Flags.Flag.RECENT),
                row, recent)).isFalse();
    }

    @Test
    void testShouldMatchSeenRecentSet() throws Exception {
        builder.setFlags(false, false, false, false, false, false);
        MailboxMessage row = builder.build();
        recent.add(row.getUid());
        assertThat(messageSearches.isMatch(SearchQuery.flagIsSet(Flags.Flag.SEEN),
                row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.flagIsSet(Flags.Flag.FLAGGED),
                row, recent)).isFalse();
        assertThat(messageSearches.isMatch(
                SearchQuery.flagIsSet(Flags.Flag.ANSWERED), row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.flagIsSet(Flags.Flag.DRAFT),
                row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.flagIsSet(Flags.Flag.DELETED),
                row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.flagIsSet(Flags.Flag.RECENT),
                row, recent)).isTrue();
    }

    @Test
    void testShouldMatchSeenFlagUnSet() throws Exception {
        builder.setFlags(false, true, true, true, true, true);
        MailboxMessage row = builder.build();
        recent.add(row.getUid());
        assertThat(messageSearches.isMatch(SearchQuery.flagIsUnSet(Flags.Flag.SEEN),
                row, recent)).isTrue();
        assertThat(messageSearches.isMatch(SearchQuery
                .flagIsUnSet(Flags.Flag.FLAGGED), row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery
                .flagIsUnSet(Flags.Flag.ANSWERED), row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.flagIsUnSet(Flags.Flag.DRAFT),
                row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery
                .flagIsUnSet(Flags.Flag.DELETED), row, recent)).isFalse();
        assertThat(messageSearches.isMatch(
                SearchQuery.flagIsUnSet(Flags.Flag.RECENT), row, recent)).isFalse();
    }

    @Test
    void testShouldMatchAnsweredFlagUnSet() throws Exception {
        builder.setFlags(true, true, false, true, true, true);
        MailboxMessage row = builder.build();
        recent.add(row.getUid());
        assertThat(messageSearches.isMatch(SearchQuery.flagIsUnSet(Flags.Flag.SEEN),
                row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery
                .flagIsUnSet(Flags.Flag.FLAGGED), row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery
                .flagIsUnSet(Flags.Flag.ANSWERED), row, recent)).isTrue();
        assertThat(messageSearches.isMatch(SearchQuery.flagIsUnSet(Flags.Flag.DRAFT),
                row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery
                .flagIsUnSet(Flags.Flag.DELETED), row, recent)).isFalse();
        assertThat(messageSearches.isMatch(
                SearchQuery.flagIsUnSet(Flags.Flag.RECENT), row, recent)).isFalse();
    }

    @Test
    void testShouldMatchFlaggedFlagUnSet() throws Exception {
        builder.setFlags(true, false, true, true, true, true);
        MailboxMessage row = builder.build();
        recent.add(row.getUid());
        assertThat(messageSearches.isMatch(SearchQuery.flagIsUnSet(Flags.Flag.SEEN),
                row, recent)).isFalse();
        assertThat(messageSearches.isMatch(
                SearchQuery.flagIsUnSet(Flags.Flag.FLAGGED), row, recent)).isTrue();
        assertThat(messageSearches.isMatch(SearchQuery
                .flagIsUnSet(Flags.Flag.ANSWERED), row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.flagIsUnSet(Flags.Flag.DRAFT),
                row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery
                .flagIsUnSet(Flags.Flag.DELETED), row, recent)).isFalse();
        assertThat(messageSearches.isMatch(
                SearchQuery.flagIsUnSet(Flags.Flag.RECENT), row, recent)).isFalse();
    }

    @Test
    void testShouldMatchDraftFlagUnSet() throws Exception {
        builder.setFlags(true, true, true, false, true, true);
        MailboxMessage row = builder.build();
        recent.add(row.getUid());
        assertThat(messageSearches.isMatch(SearchQuery.flagIsUnSet(Flags.Flag.SEEN),
                row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery
                .flagIsUnSet(Flags.Flag.FLAGGED), row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery
                .flagIsUnSet(Flags.Flag.ANSWERED), row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.flagIsUnSet(Flags.Flag.DRAFT),
                row, recent)).isTrue();
        assertThat(messageSearches.isMatch(SearchQuery
                .flagIsUnSet(Flags.Flag.DELETED), row, recent)).isFalse();
        assertThat(messageSearches.isMatch(
                SearchQuery.flagIsUnSet(Flags.Flag.RECENT), row, recent)).isFalse();
    }

    @Test
    void testShouldMatchDeletedFlagUnSet() throws Exception {
        builder.setFlags(true, true, true, true, false, true);
        MailboxMessage row = builder.build();
        recent.add(row.getUid());
        assertThat(messageSearches.isMatch(SearchQuery.flagIsUnSet(Flags.Flag.SEEN),
                row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery
                .flagIsUnSet(Flags.Flag.FLAGGED), row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery
                .flagIsUnSet(Flags.Flag.ANSWERED), row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.flagIsUnSet(Flags.Flag.DRAFT),
                row, recent)).isFalse();
        assertThat(messageSearches.isMatch(
                SearchQuery.flagIsUnSet(Flags.Flag.DELETED), row, recent)).isTrue();
        assertThat(messageSearches.isMatch(
                SearchQuery.flagIsUnSet(Flags.Flag.RECENT), row, recent)).isFalse();
    }

    @Test
    void testShouldMatchSeenRecentUnSet() throws Exception {
        builder.setFlags(true, true, true, true, true, true);
        MailboxMessage row = builder.build();
        recent.add(row.getUid().next());
        assertThat(messageSearches.isMatch(SearchQuery.flagIsUnSet(Flags.Flag.SEEN),
                row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery
                .flagIsUnSet(Flags.Flag.FLAGGED), row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery
                .flagIsUnSet(Flags.Flag.ANSWERED), row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.flagIsUnSet(Flags.Flag.DRAFT),
                row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery
                .flagIsUnSet(Flags.Flag.DELETED), row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.flagIsUnSet(Flags.Flag.RECENT),
                row, recent)).isTrue();
    }

    @Test
    void testShouldMatchAll() throws Exception {
        MailboxMessage row = builder.build();
        assertThat(messageSearches.isMatch(SearchQuery.all(), row, recent)).isTrue();
    }

    @Test
    void testShouldMatchNot() throws Exception {
        MailboxMessage row = builder.build();
        assertThat(messageSearches.isMatch(SearchQuery.not(SearchQuery.all()), row,
                recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.not(SearchQuery
                .headerExists(DATE_FIELD)), row, recent)).isTrue();
    }

    @Test
    void testShouldMatchOr() throws Exception {
        MailboxMessage row = builder.build();
        assertThat(messageSearches.isMatch(SearchQuery.or(SearchQuery.all(),
                SearchQuery.headerExists(DATE_FIELD)), row, recent)).isTrue();
        assertThat(messageSearches.isMatch(SearchQuery.or(SearchQuery
                .headerExists(DATE_FIELD), SearchQuery.all()), row, recent)).isTrue();
        assertThat(messageSearches.isMatch(SearchQuery
                .or(SearchQuery.headerExists(DATE_FIELD), SearchQuery
                        .headerExists(DATE_FIELD)), row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.or(SearchQuery.all(),
                SearchQuery.all()), row, recent)).isTrue();
    }

    @Test
    void testShouldMatchAnd() throws Exception {
        MailboxMessage row = builder.build();
        assertThat(messageSearches.isMatch(SearchQuery.and(SearchQuery.all(),
                SearchQuery.headerExists(DATE_FIELD)), row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.and(SearchQuery
                .headerExists(DATE_FIELD), SearchQuery.all()), row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery
                .and(SearchQuery.headerExists(DATE_FIELD), SearchQuery
                        .headerExists(DATE_FIELD)), row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.and(SearchQuery.all(),
                SearchQuery.all()), row, recent)).isTrue();
    }

    SearchQuery.UidRange[] range(MessageUid lowOne, MessageUid highOne,
            MessageUid lowTwo, MessageUid highTwo) {
        return new SearchQuery.UidRange[]{
                new SearchQuery.UidRange(lowOne, highOne),
                new SearchQuery.UidRange(lowTwo, highTwo) };
    }
    
    
    @Test
    void testMatchHeaderDateOnWithOffset() throws Exception {
        builder.header(DATE_FIELD, "Mon, 26 Mar 2007 03:00:00 +0300");
        MailboxMessage row = builder.build();
        assertThat(messageSearches.isMatch(SearchQuery.headerDateOn(DATE_FIELD, getDate(26, 3,
                2007), DateResolution.Day),row, recent)).isTrue();
        
        assertThat(messageSearches.isMatch(SearchQuery.headerDateOn(DATE_FIELD, getDate(25, 3,
                2007), DateResolution.Day),row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.headerDateOn(DATE_FIELD, getDate(27, 3,
                2007), DateResolution.Day),row, recent)).isFalse();
    }
    

    @Test
    void testShouldMatchHeaderDateBeforeWithOffset() throws Exception {
        builder.header(DATE_FIELD, "Mon, 26 Mar 2007 03:00:00 +0300");
        MailboxMessage row = builder.build();
        assertThat(messageSearches.isMatch(SearchQuery.headerDateBefore(DATE_FIELD, getDate(26, 3,
                2007), DateResolution.Day),row, recent)).isFalse();
        
        assertThat(messageSearches.isMatch(SearchQuery.headerDateBefore(DATE_FIELD, getDate(27, 3,
                2007), DateResolution.Day),row, recent)).isTrue();
        assertThat(messageSearches.isMatch(SearchQuery.headerDateBefore(DATE_FIELD, getDate(25, 3,
                2007), DateResolution.Day),row, recent)).isFalse();
    }

    @Test
    void testShouldMatchHeaderDateAfterWithOffset() throws Exception {
        builder.header(DATE_FIELD, "Mon, 26 Mar 2007 03:00:00 +0300");
        MailboxMessage row = builder.build();
        assertThat(messageSearches.isMatch(SearchQuery.headerDateAfter(DATE_FIELD, getDate(26, 3,
                2007), DateResolution.Day),row, recent)).isFalse();
        
        assertThat(messageSearches.isMatch(SearchQuery.headerDateAfter(DATE_FIELD, getDate(27, 3,
                2007), DateResolution.Day),row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.headerDateAfter(DATE_FIELD, getDate(25, 3,
                2007), DateResolution.Day),row, recent)).isTrue();
    }
    
    @Test
    void testShouldMatchAddressHeaderWithComments() throws Exception {
        builder.header("To", "<user-from (comment)@ (comment) domain.org>");
        MailboxMessage row = builder.build();
        assertThat(messageSearches.isMatch(SearchQuery.address(AddressType.To, "user-from@domain.org"), row, recent)).isTrue();
        assertThat(messageSearches.isMatch(SearchQuery.address(AddressType.From, "user-from@domain.org"), row, recent)).isFalse();
    }
}
