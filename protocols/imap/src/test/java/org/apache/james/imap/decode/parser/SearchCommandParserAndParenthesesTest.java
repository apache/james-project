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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.message.IdRange;
import org.apache.james.imap.api.message.UidRange;
import org.apache.james.imap.api.message.request.DayMonthYear;
import org.apache.james.imap.api.message.request.SearchKey;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.decode.DecodingException;
import org.apache.james.imap.decode.ImapRequestLineReader;
import org.apache.james.imap.decode.ImapRequestStreamLineReader;
import org.apache.james.mailbox.MessageUid;
import org.junit.Before;
import org.junit.Test;

public class SearchCommandParserAndParenthesesTest {

    Input[] variety = { sequence(), uid(), fromHeader(), since(),
            stringQuoted(), stringUnquoted(), draft(), mailingListHeader(),
            on(), unanswered() };

    public static Input and(Input[] parts, boolean parens) {
        List<SearchKey> keys = new ArrayList<>();
        StringBuilder builder = new StringBuilder();
        if (parens) {
            builder.append("(");
        }
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                builder.append(' ');
            }
            builder.append(parts[i].input);
            keys.add(parts[i].key);
        }
        if (parens) {
            builder.append(")");
        }
        return new Input(builder.toString(), SearchKey.buildAnd(keys));
    }

    public static Input sequence() {
        IdRange[] range = { new IdRange(100, Long.MAX_VALUE), new IdRange(110),
                new IdRange(200, 201), new IdRange(400, Long.MAX_VALUE) };
        SearchKey key = SearchKey.buildSequenceSet(IdRange.mergeRanges(Arrays.asList(range)).toArray(new IdRange[0]));
        return new Input("*:100,110,200:201,400:*", key);
    }

    public static Input uid() {
        UidRange[] range = { 
                new UidRange(MessageUid.of(100), MessageUid.MAX_VALUE), 
                new UidRange(MessageUid.of(110)),
                new UidRange(MessageUid.of(200), MessageUid.of(201)), 
                new UidRange(MessageUid.of(400), MessageUid.MAX_VALUE) 
                };
        SearchKey key = SearchKey.buildUidSet(UidRange.mergeRanges(Arrays.asList(range)).toArray(new UidRange[0]));
        return new Input("UID *:100,110,200:201,400:*", key);
    }

    public static Input fromHeader() {
        SearchKey key = SearchKey.buildHeader("FROM", "Smith");
        return new Input("HEADER FROM Smith", key);
    }

    public static Input to() {
        SearchKey key = SearchKey
                .buildTo("JAMES Server Development <server-dev@james.apache.org>");
        return new Input(
                "To \"JAMES Server Development <server-dev@james.apache.org>\"",
                key);
    }

    public static Input mailingListHeader() {
        SearchKey key = SearchKey.buildHeader("Mailing-List",
                "contact server-dev-help@james.apache.org; run by ezmlm");
        return new Input(
                "HEADER Mailing-List \"contact server-dev-help@james.apache.org; run by ezmlm\"",
                key);
    }

    public static Input since() {
        SearchKey key = SearchKey.buildSince(new DayMonthYear(11, 1, 2001));
        return new Input("since 11-Jan-2001", key);
    }

    public static Input on() {
        SearchKey key = SearchKey.buildOn(new DayMonthYear(1, 2, 2001));
        return new Input("on 1-Feb-2001", key);
    }

    public static Input stringUnquoted() {
        SearchKey key = SearchKey.buildFrom("Smith");
        return new Input("FROM Smith", key);
    }

    public static Input stringQuoted() {
        SearchKey key = SearchKey.buildFrom("Smith And Jones");
        return new Input("FROM \"Smith And Jones\"", key);
    }

    public static Input draft() {
        SearchKey key = SearchKey.buildDraft();
        return new Input("DRAFT", key);
    }

    public static Input unanswered() {
        SearchKey key = SearchKey.buildUnanswered();
        return new Input("unanswered", key);
    }

    public static final class Input {
        public String input;

        public SearchKey key;

        public Input(String input, SearchKey key) {
            super();
            this.input = input;
            this.key = key;
        }
    }

    SearchCommandParser parser;
    ImapCommand command;

    @Before
    public void setUp() throws Exception {
        parser = new SearchCommandParser(mock(StatusResponseFactory.class));
        command = ImapCommand.anyStateCommand("Command");
    }

    @Test
    public void testShouldParseTopLevelParentheses() throws Exception {
        check(and(variety, true));
    }

    @Test
    public void testShouldParseDeepParentheses() throws Exception {
        Input[] deep = { and(variety, true), and(variety, true), sequence(),
                and(variety, true), draft(), mailingListHeader() };
        Input[] mid = { and(deep, true), since(), and(variety, true),
                unanswered() };
        Input[] top = { uid(), and(deep, true), and(mid, true), stringQuoted(),
                and(mid, true) };
        check(and(top, true));
    }

    @Test
    public void testShouldParseParenthesesOnTopLevel() throws Exception {
        Input[] deep = { and(variety, true), and(variety, true), sequence(),
                and(variety, true), draft(), mailingListHeader() };
        Input[] mid = { and(deep, true), since(), and(variety, true),
                unanswered() };
        Input[] top = { uid(), and(deep, true), and(mid, true), stringQuoted(),
                and(mid, true) };
        check(and(top, false));
    }

    private void check(Input in) throws
        DecodingException {
        String input = in.input + "\r\n";
        ImapRequestLineReader reader = new ImapRequestStreamLineReader(
                new ByteArrayInputStream(input.getBytes(StandardCharsets.US_ASCII)),
                new ByteArrayOutputStream());

        final SearchKey result = parser.decode(null, reader);
        assertThat(result).isEqualTo(in.key);
    }

}
