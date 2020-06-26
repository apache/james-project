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

import org.apache.james.imap.api.Tag;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.decode.ImapRequestStreamLineReader;
import org.apache.james.imap.message.request.AbstractMailboxSelectionRequest.ClientSpecifiedUidValidity;
import org.apache.james.imap.message.request.SelectRequest;
import org.apache.james.mailbox.model.UidValidity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SelectCommandParserTest {
    private SelectCommandParser testee;

    @BeforeEach
    void setUp() {
        testee = new SelectCommandParser(mock(StatusResponseFactory.class));
    }

    @Test
    void decodeShouldSanitizeUidValidity() throws Exception {
        ImapRequestStreamLineReader request = toRequest("mailbox (QRESYNC (0 42))\n");

        SelectRequest selectRequest = (SelectRequest) testee.decode(request, new Tag("0001"), mock(ImapSession.class));

        assertThat(selectRequest.getLastKnownUidValidity())
            .isEqualTo(ClientSpecifiedUidValidity.invalid(0));
    }

    @Test
    void decodeShouldParseValidUidValidity() throws Exception {
        ImapRequestStreamLineReader request = toRequest("mailbox (QRESYNC (18 42))\n");

        SelectRequest selectRequest = (SelectRequest) testee.decode(request, new Tag("0001"), mock(ImapSession.class));

        assertThat(selectRequest.getLastKnownUidValidity())
            .isEqualTo(ClientSpecifiedUidValidity.valid(UidValidity.of(18)));
    }

    private ImapRequestStreamLineReader toRequest(String input) {
        return new ImapRequestStreamLineReader(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)), new ByteArrayOutputStream());
    }
}