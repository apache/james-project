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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import org.apache.james.imap.api.ImapMessage;
import org.apache.james.imap.api.Tag;
import org.apache.james.imap.decode.DecodingException;
import org.apache.james.imap.decode.ImapRequestStreamLineReader;
import org.apache.james.imap.encode.FakeImapSession;
import org.apache.james.imap.message.request.ListRequest;
import org.apache.james.imap.message.request.ListRequest.ListReturnOption;
import org.apache.james.imap.message.request.ListRequest.ListSelectOption;
import org.apache.james.imap.message.response.UnpooledStatusResponseFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ListCommandParserTest {
    private ListRequest decode(String input) throws DecodingException {
        ImapMessage message = new ListCommandParser(new UnpooledStatusResponseFactory())
            .decode(new ImapRequestStreamLineReader(new ByteArrayInputStream(input.getBytes(StandardCharsets.US_ASCII)),
                new ByteArrayOutputStream()), new Tag("A1"), new FakeImapSession());
        assertThat(message).isInstanceOf(ListRequest.class);
        return (ListRequest) message;
    }

    @Test
    void decodeShouldAcceptASinglePattern() throws Exception {
        ListRequest request = decode("\"\" \"Sent/%\"\r\n");

        assertThat(request.getBaseReferenceName()).isEmpty();
        assertThat(request.getMailboxPatterns()).containsExactly("Sent/%");
        assertThat(request.getMailboxPattern()).isEqualTo("Sent/%");
    }

    @Test
    void decodeShouldAcceptAPatternList() throws Exception {
        ListRequest request = decode("\"\" (\"INBOX\" Drafts Sent/% {4+}\r\nTr*s)\r\n");

        assertThat(request.getMailboxPatterns()).containsExactly("INBOX", "Drafts", "Sent/%", "Tr*s");
    }

    @Test
    void decodeShouldAcceptASinglePatternList() throws Exception {
        assertThat(decode("\"\" (INBOX)\r\n").getMailboxPatterns()).containsExactly("INBOX");
    }

    @Test
    void decodeShouldIgnoreEmptyPatternsOfAPatternList() throws Exception {
        ListRequest request = decode("\"\" (\"\" INBOX)\r\n");

        assertThat(request.getMailboxPatterns()).containsExactly("INBOX");
        assertThat(request.isHierarchyDelimiterRequest()).isFalse();
    }

    @Test
    void decodeShouldNotTreatAListOfAnEmptyPatternAsAHierarchyDelimiterRequest() throws Exception {
        assertThat(decode("\"\" (\"\")\r\n").isHierarchyDelimiterRequest()).isFalse();
    }

    @Test
    void decodeShouldKeepTheHierarchyDelimiterRequest() throws Exception {
        assertThat(decode("\"\" \"\"\r\n").isHierarchyDelimiterRequest()).isTrue();
    }

    @Test
    void decodeShouldAcceptAPatternListWithSelectionAndReturnOptions() throws Exception {
        ListRequest request = decode("(SUBSCRIBED RECURSIVEMATCH) \"\" (INBOX \"Sent\") RETURN (CHILDREN STATUS (MESSAGES))\r\n");

        assertThat(request.getMailboxPatterns()).containsExactly("INBOX", "Sent");
        assertThat(request.getSelectOptions()).containsExactlyInAnyOrder(ListSelectOption.SUBSCRIBED, ListSelectOption.RECURSIVEMATCH);
        assertThat(request.getReturnOptions()).containsExactlyInAnyOrder(ListReturnOption.CHILDREN, ListReturnOption.STATUS);
        assertThat(request.getStatusDataItems()).isPresent();
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"\" ()\r\n", "\"\" (INBOX\r\n", "\"\" (INBOX (Sent))\r\n", "\"\" (INBOX) extra\r\n"})
    void decodeShouldRejectInvalidPatternLists(String input) {
        assertThatThrownBy(() -> decode(input))
            .isInstanceOf(DecodingException.class);
    }

    @ParameterizedTest
    @Timeout(value = 5, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    @ValueSource(strings = {"\"\" (\t)\r\n", "\"\" (INBOX\tSent)\r\n", "\"\" (x\t)\r\n", "\"\" (\"INBOX\"\t)\r\n"})
    void decodeShouldRejectATabInAPatternList(String input) {
        assertThatThrownBy(() -> decode(input))
            .isInstanceOf(DecodingException.class);
    }

    @Test
    void decodeShouldRejectATooLongQuotedPattern() {
        assertThatThrownBy(() -> decode("\"\" \"" + "a".repeat(1025) + "\"\r\n"))
            .isInstanceOf(DecodingException.class);
    }

    @Test
    void decodeShouldRejectATooLongLiteralPattern() {
        assertThatThrownBy(() -> decode("\"\" {1025+}\r\n" + "a".repeat(1025) + "\r\n"))
            .isInstanceOf(DecodingException.class);
    }

    @Test
    void decodeShouldAcceptAPatternOfTheMaximumLength() throws Exception {
        assertThat(decode("\"\" \"" + "a".repeat(1024) + "\"\r\n").getMailboxPatterns())
            .containsExactly("a".repeat(1024));
    }

    @Test
    void decodeShouldRejectTooManyPatterns() {
        assertThatThrownBy(() -> decode("\"\" (" + "INBOX ".repeat(33).trim() + ")\r\n"))
            .isInstanceOf(DecodingException.class);
    }
}
