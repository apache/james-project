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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.message.BodyFetchElement;
import org.apache.james.imap.api.message.FetchData;
import org.apache.james.imap.api.message.IdRange;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.decode.ImapRequestLineReader;
import org.apache.james.imap.decode.ImapRequestStreamLineReader;
import org.apache.james.protocols.imap.DecodingException;
import org.junit.Before;
import org.junit.Test;

public class FetchCommandParserPartialFetchTest  {

    FetchCommandParser parser;
    ImapCommand command;
    ImapSession session;

    @Before
    public void setUp() throws Exception {
        parser = new FetchCommandParser();
        command = ImapCommand.anyStateCommand("Command");
        session = mock(ImapSession.class);
    }

    @Test
    public void testShouldParseZeroAndLength() throws Exception {
        IdRange[] ranges = { new IdRange(1) };
        FetchData data = new FetchData();
        data.add(new BodyFetchElement("BODY[]", BodyFetchElement.CONTENT, null,
                null, new Long(0), new Long(100)), false);
        check("1 (BODY[]<0.100>)\r\n", ranges, false, data, "A01");
    }

    @Test
    public void testShouldParseNonZeroAndLength() throws Exception {
        IdRange[] ranges = { new IdRange(1) };
        FetchData data = new FetchData();
        data.add(new BodyFetchElement("BODY[]", BodyFetchElement.CONTENT, null,
                null, new Long(20), new Long(12342348)), false);
        check("1 (BODY[]<20.12342348>)\r\n", ranges, false, data, "A01");
    }

    @Test
    public void testShouldNotParseZeroLength() throws Exception {
        ImapRequestLineReader reader = new ImapRequestStreamLineReader(
                new ByteArrayInputStream("1 (BODY[]<20.0>)\r\n"
                        .getBytes("US-ASCII")), new ByteArrayOutputStream());

        assertThatThrownBy(() -> parser.decode(command, reader, "A01", false, session))
            .isInstanceOf(DecodingException.class);
    }

    private void check(String input, IdRange[] idSet,
            boolean useUids, FetchData data, String tag) throws Exception {
        ImapRequestLineReader reader = new ImapRequestStreamLineReader(
                new ByteArrayInputStream(input.getBytes("US-ASCII")),
                new ByteArrayOutputStream());

        parser.decode(command, reader, tag, useUids, session);
    }
}
