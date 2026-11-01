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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.james.imap.api.message.request.DayMonthYear;
import org.apache.james.imap.api.message.request.SearchKey;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.decode.DecodingException;
import org.apache.james.imap.decode.ImapRequestStreamLineReader;
import org.apache.james.imap.decode.parser.SearchCommandParser.Context;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SearchCommandParserQuotedDateTest {
    private static final DayMonthYear DATE = new DayMonthYear(18, 1, 1930);

    private SearchCommandParser parser;

    @BeforeEach
    void setUp() {
        parser = new SearchCommandParser(mock(StatusResponseFactory.class));
    }

    private ImapRequestStreamLineReader reader(String input) {
        return new ImapRequestStreamLineReader(
            new ByteArrayInputStream(input.getBytes(StandardCharsets.US_ASCII)),
            new ByteArrayOutputStream());
    }

    private SearchKey parse(String input) throws Exception {
        return parser.searchKey(null, reader(input), new Context(), false);
    }

    @Test
    void quotedSentOnShouldBeAccepted() throws Exception {
        assertThat(parse("SENTON \"18-Jan-1930\"\r\n"))
            .isEqualTo(SearchKey.buildSentOn(DATE));
    }

    @Test
    void quotedSentBeforeShouldBeAccepted() throws Exception {
        assertThat(parse("SENTBEFORE \"18-Jan-1930\"\r\n"))
            .isEqualTo(SearchKey.buildSentBefore(DATE));
    }

    @Test
    void quotedSentSinceShouldBeAccepted() throws Exception {
        assertThat(parse("SENTSINCE \"18-Jan-1930\"\r\n"))
            .isEqualTo(SearchKey.buildSentSince(DATE));
    }

    @Test
    void quotedOnShouldBeAccepted() throws Exception {
        assertThat(parse("ON \"18-Jan-1930\"\r\n"))
            .isEqualTo(SearchKey.buildOn(DATE));
    }

    @Test
    void quotedBeforeShouldBeAccepted() throws Exception {
        assertThat(parse("BEFORE \"18-Jan-1930\"\r\n"))
            .isEqualTo(SearchKey.buildBefore(DATE));
    }

    @Test
    void quotedSinceShouldBeAccepted() throws Exception {
        assertThat(parse("SINCE \"1-Jan-2026\"\r\n"))
            .isEqualTo(SearchKey.buildSince(new DayMonthYear(1, 1, 2026)));
    }

    @Test
    void quotedDateShouldBeAcceptedBeforeAClosingParenthesis() throws Exception {
        assertThat(parse("(SENTON \"18-Jan-1930\")\r\n"))
            .isEqualTo(SearchKey.buildAnd(List.of(SearchKey.buildSentOn(DATE))));
    }

    @Test
    void unquotedDateShouldStillBeAccepted() throws Exception {
        assertThat(parse("SENTON 18-Jan-1930\r\n"))
            .isEqualTo(SearchKey.buildSentOn(DATE));
    }

    @Test
    void unterminatedQuotedDateShouldBeRejected() {
        assertThatThrownBy(() -> parse("SENTON \"18-Jan-1930\r\n"))
            .isInstanceOf(DecodingException.class);
    }
}
