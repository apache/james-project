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

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Locale;

import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.extractor.TextExtractor;
import org.apache.james.mailbox.model.Attachment;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.search.MessageSearches;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SearchUtilsMultipartMixedTest {
/*
    static final String SAMPLE_INNER_MAIL_BODY_ONE = "far a modern quill doth come too";

    static final String SAMPLE_PART_ONE = "The better angel is a man right fair,\r\n";

    static final String SAMPLE_PART_TWO = "My bonds in thee are all determinate.";

    static final String SAMPLE_PART_TWO_FIELD = "948523475273457234952345";

    static final String SAMPLE_INNER_MAIL_FIELD = "Inner mail sample";

    static final String SAMPLE_INNER_MAIL_MIME_FIELD = "8347673450223534587232312221";

    static final String PREMABLE = "This is the premable.";

    static final String BODY = PREMABLE + "\r\n--1729\r\n"
            + "Content-Type: text/plain; charset=US-ASCII\r\n\r\n"
            + "Two loves I have of comfort and despair,\r\n"
            + "Which like two spirits do suggest me still:\r\n"
            + SAMPLE_PART_ONE + "The worser spirit a woman colour'd ill.\r\n"
            + "To win me soon to hell, my female evil,\r\n"
            + "Tempteth my better angel from my side,\r\n"
            + "And would corrupt my saint to be a devil,\r\n"
            + "Wooing his purity with her foul pride.\r\n"
            + "And whether that my angel be turn'd fiend,\r\n"
            + "Suspect I may, yet not directly tell;\r\n"
            + "But being both from me, both to each friend,\r\n"
            + "I guess one angel in another's hell:\r\n"
            + "  Yet this shall I ne'er know, but live in doubt,\r\n"
            + "  Till my bad angel fire my good one out.\r\n" + "r\n"
            + "By William Shakespere\r\n" + "\r\n--1729\r\n"
            + "Content-Type: text/plain; charset=US-ASCII\r\n"
            + "Content-Transfer-Encoding: 7bit\r\n" + "Content-ID: 45"
            + SAMPLE_PART_TWO_FIELD + "\r\n\r\n"
            + "Farewell! thou art too dear for my possessing,\r\n"
            + "And like enough thou know'st thy estimate,\r\n"
            + "The charter of thy worth gives thee releasing;\r\n"
            + SAMPLE_PART_TWO + "\r\n"
            + "For how do I hold thee but by thy granting?\r\n"
            + "And for that riches where is my deserving?\r\n"
            + "The cause of this fair gift in me is wanting,\r\n"
            + "And so my patent back again is swerving.\r\n"
            + "Thy self thou gav'st, thy own worth then not knowing,\r\n"
            + "Or me to whom thou gav'st it, else mistaking;\r\n"
            + "So thy great gift, upon misprision growing,\r\n"
            + "Comes home again, on better judgement making.\r\n"
            + "  Thus have I had thee, as a dream doth flatter,\r\n"
            + "  In sleep a king, but waking no such matter.\r\n" + "r\n"
            + "By William Shakespere\r\n" + "\r\n--1729\r\n"
            + "Content-Type: message/rfc822\r\n\r\n"
            + "From: Timothy Tayler <timothy@example.org>\r\n"
            + "To: John Smith <john@example.org>\r\n"
            + "Date: Sat, 16 Feb 2008 12:00:00 +0000 (GMT)\r\n"
            + "Subject: Custard " + SAMPLE_INNER_MAIL_FIELD + " \r\n"
            + "Content-Type: multipart/mixed;boundary=2.50290787509\r\n\r\n"
            + "--2.50290787509\r\n" + "Content-Type: text/plain\r\n"
            + "Content-ID: 4657" + SAMPLE_INNER_MAIL_MIME_FIELD + "\r\n\r\n"
            + "I never saw that you did painting need,\r\n"
            + "And therefore to your fair no painting set;\r\n"
            + "I found, or thought I found, you did exceed\r\n"
            + "That barren tender of a poet's debt:\r\n"
            + "And therefore have I slept in your report,\r\n"
            + "That you yourself, being extant, well might show\r\n" + "How "
            + SAMPLE_INNER_MAIL_BODY_ONE + " short,\r\n"
            + "Speaking of worth, what worth in you doth grow.\r\n"
            + "This silence for my sin you did impute,\r\n"
            + "Which shall be most my glory being dumb;\r\n"
            + "For I impair not beauty being mute,\r\n"
            + "When others would give life, and bring a tomb.\r\n"
            + "  There lives more life in one of your fair eyes\r\n"
            + "  Than both your poets can in praise devise.\r\n"
            + "\r\n--2.50290787509--\r\n" + "\r\n--1729--\r\n";

    MailboxMessage row;
    Collection<MessageUid> recent;
    MessageSearches messageSearches;

    @BeforeEach
    void setUp() throws Exception {
        final MessageBuilder builder = new MessageBuilder();
        
        builder.header("From", "Alex <alex@example.org");
        builder.header("To", "Harry <harry@example.org");
        builder.header("Subject", "A Mixed Multipart Mail");
        builder.header("Date", "Thu, 14 Feb 2008 12:00:00 +0000 (GMT)");
        builder.header("Content-Type", "multipart/mixed;boundary=1729");
        builder.body(Charset.forName("us-ascii").encode(BODY).array());
        row = builder.build();
        recent = new ArrayList<>();
        
        Iterator<MailboxMessage> messages = null;
        SearchQuery query = null; 
        TextExtractor textExtractor = null;
        MailboxSession session = null;
        messageSearches = new MessageSearches(messages, query, textExtractor, (attachment, ignore) -> attachment.getStream(), session);
    }
    

    @Test
    void testShouldNotFindWhatIsNotThere() throws Exception {
        assertThat(messageSearches.isMatch(SearchQuery.bodyContains("BOGUS"), row,
                recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery.mailContains("BOGUS"), row,
                recent)).isFalse();
    }

    @Test
    void testBodyShouldFindTextInBody() throws Exception {
        assertThat(messageSearches.isMatch(SearchQuery
                .bodyContains(SAMPLE_INNER_MAIL_BODY_ONE), row, recent)).isTrue();
        assertThat(messageSearches.isMatch(SearchQuery.bodyContains(SAMPLE_PART_ONE),
                row, recent)).isTrue();
        assertThat(messageSearches.isMatch(SearchQuery.bodyContains(SAMPLE_PART_TWO),
                row, recent)).isTrue();
    }

    @Test
    void testBodyShouldFindTextInBodyCaseInsensitive() throws Exception {
        assertThat(messageSearches.isMatch(SearchQuery
                .bodyContains(SAMPLE_INNER_MAIL_BODY_ONE), row, recent)).isTrue();
        assertThat(messageSearches.isMatch(SearchQuery.bodyContains(SAMPLE_PART_ONE),
                row, recent)).isTrue();
        assertThat(messageSearches.isMatch(SearchQuery.bodyContains(SAMPLE_PART_TWO),
                row, recent)).isTrue();
        assertThat(messageSearches.isMatch(SearchQuery
                .bodyContains(SAMPLE_INNER_MAIL_BODY_ONE.toLowerCase(Locale.US)), row,
                recent)).isTrue();
        assertThat(messageSearches.isMatch(SearchQuery.bodyContains(SAMPLE_PART_ONE
                .toLowerCase(Locale.US)), row, recent)).isTrue();
        assertThat(messageSearches.isMatch(SearchQuery.bodyContains(SAMPLE_PART_TWO
                .toLowerCase(Locale.US)), row, recent)).isTrue();
        assertThat(messageSearches.isMatch(SearchQuery
                .bodyContains(SAMPLE_INNER_MAIL_BODY_ONE.toUpperCase(Locale.US)), row,
                recent)).isTrue();
        assertThat(messageSearches.isMatch(SearchQuery.bodyContains(SAMPLE_PART_ONE
                .toUpperCase(Locale.US)), row, recent)).isTrue();
        assertThat(messageSearches.isMatch(SearchQuery.bodyContains(SAMPLE_PART_TWO
                .toUpperCase(Locale.US)), row, recent)).isTrue();
    }

    @Test
    void testBodyShouldNotFindTextInHeaders() throws Exception {
        assertThat(messageSearches.isMatch(SearchQuery
                .bodyContains(SAMPLE_INNER_MAIL_FIELD), row, recent)).isFalse();
        assertThat(messageSearches.isMatch(SearchQuery
                .bodyContains(SAMPLE_PART_TWO_FIELD), row, recent)).isFalse();
    }

    @Test
    void testTextShouldFindTextInBody() throws Exception {
        assertThat(messageSearches.isMatch(SearchQuery
                .mailContains(SAMPLE_INNER_MAIL_BODY_ONE), row, recent)).isTrue();
        assertThat(messageSearches.isMatch(SearchQuery.mailContains(SAMPLE_PART_ONE),
                row, recent)).isTrue();
        assertThat(messageSearches.isMatch(SearchQuery.mailContains(SAMPLE_PART_TWO),
                row, recent)).isTrue();
    }

    @Test
    void testTextShouldFindTextInBodyCaseInsensitive() throws Exception {
        assertThat(messageSearches.isMatch(SearchQuery
                .mailContains(SAMPLE_INNER_MAIL_BODY_ONE), row, recent)).isTrue();
        assertThat(messageSearches.isMatch(SearchQuery.mailContains(SAMPLE_PART_ONE),
                row, recent)).isTrue();
        assertThat(messageSearches.isMatch(SearchQuery.mailContains(SAMPLE_PART_TWO),
                row, recent)).isTrue();
        assertThat(messageSearches.isMatch(SearchQuery
                .mailContains(SAMPLE_INNER_MAIL_BODY_ONE.toLowerCase(Locale.US)), row,
                recent)).isTrue();
        assertThat(messageSearches.isMatch(SearchQuery.mailContains(SAMPLE_PART_ONE
                .toLowerCase(Locale.US)), row, recent)).isTrue();
        assertThat(messageSearches.isMatch(SearchQuery.mailContains(SAMPLE_PART_TWO
                .toLowerCase(Locale.US)), row, recent)).isTrue();
        assertThat(messageSearches.isMatch(SearchQuery
                .mailContains(SAMPLE_INNER_MAIL_BODY_ONE.toUpperCase(Locale.US)), row,
                recent)).isTrue();
        assertThat(messageSearches.isMatch(SearchQuery.mailContains(SAMPLE_PART_ONE
                .toUpperCase(Locale.US)), row, recent)).isTrue();
        assertThat(messageSearches.isMatch(SearchQuery.mailContains(SAMPLE_PART_TWO
                .toUpperCase(Locale.US)), row, recent)).isTrue();
    }

    @Test
    void testTextShouldFindTextInHeaders() throws Exception {
        assertThat(messageSearches.isMatch(SearchQuery
                .mailContains(SAMPLE_INNER_MAIL_FIELD), row, recent)).isTrue();
        assertThat(messageSearches.isMatch(SearchQuery
                .mailContains(SAMPLE_INNER_MAIL_BODY_ONE), row, recent)).isTrue();
        assertThat(messageSearches.isMatch(SearchQuery
                .mailContains(SAMPLE_PART_TWO_FIELD), row, recent)).isTrue();
    }

 */
}
