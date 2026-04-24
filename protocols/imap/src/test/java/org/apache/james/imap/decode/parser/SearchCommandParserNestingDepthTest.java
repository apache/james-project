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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.decode.DecodingException;
import org.apache.james.imap.decode.ImapRequestStreamLineReader;
import org.apache.james.imap.decode.parser.SearchCommandParser.Context;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SearchCommandParserNestingDepthTest {

    private static final int MAX_NESTING_DEPTH = 32;

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

    /** Builds "(((...(ALL)...)))\r\n" with exactly {@code depth} levels of parentheses. */
    private String nested(int depth) {
        return nestedNoEol(depth) + "\r\n";
    }

    /** Same without trailing CRLF, for embedding inside compound commands (OR, NOT). */
    private String nestedNoEol(int depth) {
        return "(".repeat(depth) + "ALL" + ")".repeat(depth);
    }

    @Test
    void nestingAtMaxDepthShouldBeAccepted() {
        assertThatCode(() ->
            parser.searchKey(null, reader(nested(MAX_NESTING_DEPTH)), new Context(), false))
            .doesNotThrowAnyException();
    }

    @Test
    void nestingBelowMaxDepthShouldBeAccepted() {
        assertThatCode(() ->
            parser.searchKey(null, reader(nested(MAX_NESTING_DEPTH - 1)), new Context(), false))
            .doesNotThrowAnyException();
    }

    @Test
    void nestingOneAboveMaxDepthShouldBeRejected() {
        assertThatThrownBy(() ->
            parser.searchKey(null, reader(nested(MAX_NESTING_DEPTH + 1)), new Context(), false))
            .isInstanceOf(DecodingException.class)
            .hasMessageContaining("nesting depth");
    }

    @Test
    void deeplyNestedPayloadShouldBeRejectedWithoutStackOverflow() {
        // 3998 levels fits in 8000 chars (the Netty line limit).
        // Before the fix this caused StackOverflowError; after the fix it must
        // throw a clean DecodingException well before the stack is exhausted.
        String payload = nested(3998);
        assertThatThrownBy(() ->
            parser.searchKey(null, reader(payload), new Context(), false))
            .isInstanceOf(DecodingException.class)
            .isNotInstanceOf(StackOverflowError.class)
            .hasMessageContaining("nesting depth");
    }

    @Test
    void notWrappingNestingAtMaxDepthShouldBeAccepted() {
        String payload = "NOT " + nestedNoEol(MAX_NESTING_DEPTH) + "\r\n";
        assertThatCode(() ->
            parser.searchKey(null, reader(payload), new Context(), false))
            .doesNotThrowAnyException();
    }

    @Test
    void notWrappingNestingAboveMaxDepthShouldBeRejected() {
        String payload = "NOT " + nestedNoEol(MAX_NESTING_DEPTH + 1) + "\r\n";
        assertThatThrownBy(() ->
            parser.searchKey(null, reader(payload), new Context(), false))
            .isInstanceOf(DecodingException.class)
            .hasMessageContaining("nesting depth");
    }

    @Test
    void orWithBothArgsAtMaxDepthShouldBeAccepted() {
        String payload = "OR " + nestedNoEol(MAX_NESTING_DEPTH) + " " + nestedNoEol(MAX_NESTING_DEPTH) + "\r\n";
        assertThatCode(() ->
            parser.searchKey(null, reader(payload), new Context(), false))
            .doesNotThrowAnyException();
    }

    @Test
    void orWithOneArgAboveMaxDepthShouldBeRejected() {
        String payload = "OR " + nestedNoEol(MAX_NESTING_DEPTH + 1) + " ALL\r\n";
        assertThatThrownBy(() ->
            parser.searchKey(null, reader(payload), new Context(), false))
            .isInstanceOf(DecodingException.class)
            .hasMessageContaining("nesting depth");
    }
}
