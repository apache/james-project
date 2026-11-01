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

import org.apache.james.imap.api.message.request.SearchKey;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.decode.DecodingException;
import org.apache.james.imap.decode.ImapRequestStreamLineReader;
import org.apache.james.imap.decode.parser.SearchCommandParser.Context;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SearchCommandParserNumberBeforeParenthesisTest {
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
    void largerShouldBeAcceptedBeforeAClosingParenthesis() throws Exception {
        assertThat(parse("(LARGER 30)\r\n"))
            .isEqualTo(SearchKey.buildAnd(List.of(SearchKey.buildLarger(30))));
    }

    @Test
    void smallerShouldBeAcceptedBeforeAClosingParenthesis() throws Exception {
        assertThat(parse("(SMALLER 10)\r\n"))
            .isEqualTo(SearchKey.buildAnd(List.of(SearchKey.buildSmaller(10))));
    }

    @Test
    void youngerShouldBeAcceptedBeforeAClosingParenthesis() throws Exception {
        assertThat(parse("(YOUNGER 5)\r\n"))
            .isEqualTo(SearchKey.buildAnd(List.of(SearchKey.buildYounger(5))));
    }

    @Test
    void olderShouldNotFallBackToOldBeforeAClosingParenthesis() throws Exception {
        assertThat(parse("(OLDER 30)\r\n"))
            .isEqualTo(SearchKey.buildAnd(List.of(SearchKey.buildOlder(30))));
    }

    @Test
    void modSeqShouldBeAcceptedBeforeAClosingParenthesis() throws Exception {
        assertThat(parse("(MODSEQ 30)\r\n"))
            .isEqualTo(SearchKey.buildAnd(List.of(SearchKey.buildModSeq(30))));
    }

    @Test
    void numericKeysShouldBeAcceptedInOrOperandsBetweenParentheses() throws Exception {
        assertThat(parse("OR (DELETED) (SMALLER 10)\r\n"))
            .isEqualTo(SearchKey.buildOr(
                SearchKey.buildAnd(List.of(SearchKey.buildDeleted())),
                SearchKey.buildAnd(List.of(SearchKey.buildSmaller(10)))));
    }

    @Test
    void oldShouldBeAcceptedBeforeAClosingParenthesis() throws Exception {
        assertThat(parse("(OLD)\r\n"))
            .isEqualTo(SearchKey.buildAnd(List.of(SearchKey.buildOld())));
    }

    @Test
    void oldShouldStillBeAccepted() throws Exception {
        assertThat(parse("OLD\r\n"))
            .isEqualTo(SearchKey.buildOld());
    }

    @Test
    void olderShouldStillBeAccepted() throws Exception {
        assertThat(parse("OLDER 30\r\n"))
            .isEqualTo(SearchKey.buildOlder(30));
    }

    @Test
    void malformedOlderShouldBeRejectedInsteadOfBeingReadAsOld() {
        assertThatThrownBy(() -> parse("OLDER abc\r\n"))
            .isInstanceOf(DecodingException.class);
    }

    @Test
    void closingParenthesisWithoutNumberShouldBeRejected() {
        assertThatThrownBy(() -> parse("(LARGER )\r\n"))
            .isInstanceOf(DecodingException.class);
    }
}
