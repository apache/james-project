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

package org.apache.james.imap.decode.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.message.IdRange;
import org.apache.james.imap.api.message.UidRange;
import org.apache.james.imap.api.message.request.DayMonthYear;
import org.apache.james.imap.api.message.request.SearchKey;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.decode.ImapRequestLineReader;
import org.apache.james.imap.decode.ImapRequestStreamLineReader;
import org.apache.james.mailbox.MessageUid;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SearchCommandParserOrTest {

    SearchCommandParser parser;
    ImapCommand command;

    @BeforeEach
    public void setUp() throws Exception {
        parser = new SearchCommandParser(mock(StatusResponseFactory.class));
        command = ImapCommand.anyStateCommand("Command");
    }
    
    public Input sequence() {
        IdRange[] range = { new IdRange(100, Long.MAX_VALUE), new IdRange(110),
                new IdRange(200, 201), new IdRange(400, Long.MAX_VALUE) };
        SearchKey key = SearchKey.buildSequenceSet(IdRange.mergeRanges(Arrays.asList(range)).toArray(IdRange[]::new));
        return new Input("*:100,110,200:201,400:*", key);
    }

    public Input uid() {
        UidRange[] range = { 
                new UidRange(MessageUid.of(100), MessageUid.MAX_VALUE), 
                new UidRange(MessageUid.of(110)),
                new UidRange(MessageUid.of(200), MessageUid.of(201)), 
                new UidRange(MessageUid.of(400), MessageUid.MAX_VALUE) 
                };
        SearchKey key = SearchKey.buildUidSet(UidRange.mergeRanges(Arrays.asList(range)).toArray(UidRange[]::new));
        return new Input("UID *:100,110,200:201,400:*", key);
    }

    public Input header() {
        SearchKey key = SearchKey.buildHeader("FROM", "Smith");
        return new Input("HEADER FROM Smith", key);
    }

    public Input date() {
        SearchKey key = SearchKey.buildSince(new DayMonthYear(11, 1, 2001));
        return new Input("since 11-Jan-2001", key);
    }

    public Input stringUnquoted() {
        SearchKey key = SearchKey.buildFrom("Smith");
        return new Input("FROM Smith", key);
    }

    public Input stringQuoted() {
        SearchKey key = SearchKey.buildFrom("Smith And Jones");
        return new Input("FROM \"Smith And Jones\"", key);
    }

    public Input draft() {
        SearchKey key = SearchKey.buildDraft();
        return new Input("DRAFT", key);
    }

    @Test
    void testDraftPermutations() throws Exception {
        checkValid(draft(), draft());
        checkValid(draft(), stringQuoted());
        checkValid(draft(), stringUnquoted());
        checkValid(draft(), sequence());
        checkValid(draft(), header());
        checkValid(draft(), date());
        checkValid(draft(), uid());
    }

    @Test
    void testDatePermutations() throws Exception {
        checkValid(date(), draft());
        checkValid(date(), stringQuoted());
        checkValid(date(), stringUnquoted());
        checkValid(date(), sequence());
        checkValid(date(), header());
        checkValid(date(), date());
        checkValid(date(), uid());
    }

    @Test
    void testHeaderPermutations() throws Exception {
        checkValid(header(), draft());
        checkValid(header(), stringQuoted());
        checkValid(header(), stringUnquoted());
        checkValid(header(), sequence());
        checkValid(header(), header());
        checkValid(header(), date());
        checkValid(header(), uid());
    }

    @Test
    void testSequencePermutations() throws Exception {
        checkValid(sequence(), draft());
        checkValid(sequence(), stringQuoted());
        checkValid(sequence(), stringUnquoted());
        checkValid(sequence(), sequence());
        checkValid(sequence(), header());
        checkValid(sequence(), date());
        checkValid(sequence(), uid());
    }

    @Test
    void testStringQuotedPermutations() throws Exception {
        checkValid(stringQuoted(), draft());
        checkValid(stringQuoted(), stringQuoted());
        checkValid(stringQuoted(), stringUnquoted());
        checkValid(stringQuoted(), sequence());
        checkValid(stringQuoted(), header());
        checkValid(stringQuoted(), date());
        checkValid(stringQuoted(), uid());
    }

    @Test
    void testStringUnquotedPermutations() throws Exception {
        checkValid(stringUnquoted(), draft());
        checkValid(stringUnquoted(), stringQuoted());
        checkValid(stringUnquoted(), stringUnquoted());
        checkValid(stringUnquoted(), sequence());
        checkValid(stringUnquoted(), header());
        checkValid(stringUnquoted(), date());
        checkValid(stringUnquoted(), uid());
    }

    @Test
    void testUidPermutations() throws Exception {
        checkValid(uid(), draft());
        checkValid(uid(), stringQuoted());
        checkValid(uid(), stringUnquoted());
        checkValid(uid(), sequence());
        checkValid(uid(), header());
        checkValid(uid(), date());
        checkValid(uid(), uid());
    }

    
    private void checkValid(Input one, Input two) throws Exception {
        String input = "OR " + one.input + " " + two.input + "\r\n";
        SearchKey key = SearchKey.buildOr(one.key, two.key);
        ImapRequestLineReader reader = new ImapRequestStreamLineReader(
                new ByteArrayInputStream(input.getBytes(StandardCharsets.US_ASCII)),
                new ByteArrayOutputStream());

        assertThat(parser.searchKey(null, reader, null, false)).isEqualTo(key);
    }

    public class Input {
        public String input;

        public SearchKey key;

        public Input(String input, SearchKey key) {
            super();
            this.input = input;
            this.key = key;
        }
    }
}
