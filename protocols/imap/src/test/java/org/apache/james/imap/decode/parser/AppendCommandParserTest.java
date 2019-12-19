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
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.decode.ImapRequestStreamLineReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AppendCommandParserTest {
    private static final Instant DATE = Instant.parse("2007-07-03T10:15:30.00Z");
    private static final Clock CLOCK = Clock.fixed(DATE, ZoneOffset.UTC);

    private AppendCommandParser testee;

    @BeforeEach
    void setUp() {
        testee = new AppendCommandParser(mock(StatusResponseFactory.class), CLOCK);
    }

    @Test
    void parseDateTimeShouldReturnNowWhenNotADate() throws Exception {
        ImapRequestStreamLineReader request = toRequest("any\n");

        assertThat(testee.parseDateTime(request).toInstant(ZoneOffset.UTC)).isEqualTo(DATE);
    }

    @Test
    void parseDateTimeShouldNotConsumeNonDateLiteral() throws Exception {
        ImapRequestStreamLineReader request = toRequest("any\n");

        testee.parseDateTime(request);

        assertThat(request.atom()).isEqualTo("any");
    }

    @Test
    void parseDateTimeShouldConsumeDateLiteral() throws Exception {
        ImapRequestStreamLineReader request = toRequest("\"09-Apr-2008 15:17:51 +0200\" any\n");

        testee.parseDateTime(request);

        assertThat(request.atom()).isEqualTo("any");
    }

    @Test
    void parseDateTimeShouldReturnSuppliedValue() throws Exception {
        ImapRequestStreamLineReader request = toRequest("\"09-Apr-2008 15:17:51 +0000\"");

        assertThat(
            testee.parseDateTime(request)
                .atZone(ZoneOffset.systemDefault())
                .withZoneSameInstant(ZoneOffset.UTC))
            .isEqualTo("2008-04-09T15:17:51Z");
    }

    private ImapRequestStreamLineReader toRequest(String input) {
        return new ImapRequestStreamLineReader(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)), new ByteArrayOutputStream());
    }
}