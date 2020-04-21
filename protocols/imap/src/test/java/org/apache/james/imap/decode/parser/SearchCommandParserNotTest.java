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
import java.util.List;

import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.message.IdRange;
import org.apache.james.imap.api.message.UidRange;
import org.apache.james.imap.api.message.request.DayMonthYear;
import org.apache.james.imap.api.message.request.SearchKey;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.decode.ImapRequestLineReader;
import org.apache.james.imap.decode.ImapRequestStreamLineReader;
import org.apache.james.mailbox.MessageUid;
import org.junit.Before;
import org.junit.Test;

public class SearchCommandParserNotTest {

    SearchCommandParser parser;
    ImapCommand command;
    
    @Before
    public void setUp() throws Exception {
        parser = new SearchCommandParser(mock(StatusResponseFactory.class));
        command = ImapCommand.anyStateCommand("Command");
    }

    @Test
    public void testShouldParseNotSequence() throws Exception {
        IdRange[] range = { new IdRange(100, Long.MAX_VALUE), new IdRange(110),
                new IdRange(200, 201), new IdRange(400, Long.MAX_VALUE) };
        SearchKey notdKey = SearchKey.buildSequenceSet(IdRange.mergeRanges(Arrays.asList(range)).toArray(IdRange[]::new));
        SearchKey key = SearchKey.buildNot(notdKey);
        checkValid("NOT *:100,110,200:201,400:*\r\n", key);
    }

    @Test
    public void testShouldParseNotUid() throws Exception {
        UidRange[] range = { 
                new UidRange(MessageUid.of(100), MessageUid.MAX_VALUE), 
                new UidRange(MessageUid.of(110)),
                new UidRange(MessageUid.of(200), MessageUid.of(201)), 
                new UidRange(MessageUid.of(400), MessageUid.MAX_VALUE) 
                };
        SearchKey notdKey = SearchKey.buildUidSet(UidRange.mergeRanges(Arrays.asList(range)).toArray(UidRange[]::new));
        SearchKey key = SearchKey.buildNot(notdKey);
        checkValid("NOT UID *:100,110,200:201,400:*\r\n", key);
    }

    @Test
    public void testShouldParseNotHeaderKey() throws Exception {
        SearchKey notdKey = SearchKey.buildHeader("FROM", "Smith");
        SearchKey key = SearchKey.buildNot(notdKey);
        checkValid("NOT HEADER FROM Smith\r\n", key);
        checkValid("NOT header FROM Smith\r\n", key);
    }

    @Test
    public void testShouldParseNotDateParameterKey() throws Exception {
        SearchKey notdKey = SearchKey.buildSince(new DayMonthYear(11, 1, 2001));
        SearchKey key = SearchKey.buildNot(notdKey);
        checkValid("NOT since 11-Jan-2001\r\n", key);
        checkValid("NOT SINCE 11-Jan-2001\r\n", key);
    }

    @Test
    public void testShouldParseNotStringParameterKey() throws Exception {
        SearchKey notdKey = SearchKey.buildFrom("Smith");
        SearchKey key = SearchKey.buildNot(notdKey);
        checkValid("NOT FROM Smith\r\n", key);
        checkValid("NOT FROM \"Smith\"\r\n", key);
    }

    @Test
    public void testShouldParseNotStringQuotedParameterKey() throws Exception {
        SearchKey notdKey = SearchKey.buildFrom("Smith And Jones");
        SearchKey key = SearchKey.buildNot(notdKey);
        checkValid("NOT FROM \"Smith And Jones\"\r\n", key);
    }

    @Test
    public void testShouldParseNotNoParameterKey() throws Exception {
        SearchKey notdKey = SearchKey.buildNew();
        SearchKey key = SearchKey.buildNot(notdKey);
        checkValid("NOT NEW\r\n", key);
        checkValid("Not NEW\r\n", key);
        checkValid("not new\r\n", key);
    }
    
    @Test 
    public void testUserFlagsParsing() throws Exception { 
        ImapRequestLineReader reader = new ImapRequestStreamLineReader(
                new ByteArrayInputStream("NOT (KEYWORD bar KEYWORD foo)".getBytes(StandardCharsets.US_ASCII)),
                new ByteArrayOutputStream()); 
        SearchKey key = parser.searchKey(null, reader, null, false); 
        List<SearchKey> keys = key.getKeys().get(0).getKeys(); 
        assertThat(keys.size()).isEqualTo(2);
        assertThat(keys.get(0).getValue()).isEqualTo("bar");
        assertThat(keys.get(1).getValue()).isEqualTo("foo");
    } 

    private void checkValid(String input, SearchKey key) throws Exception {
        ImapRequestLineReader reader = new ImapRequestStreamLineReader(
                new ByteArrayInputStream(input.getBytes(StandardCharsets.US_ASCII)),
                new ByteArrayOutputStream());

        assertThat(parser.searchKey(null, reader, null, false)).isEqualTo(key);
    }
}
