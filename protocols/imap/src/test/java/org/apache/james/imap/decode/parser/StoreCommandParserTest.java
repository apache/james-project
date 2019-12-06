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

import static org.apache.james.imap.ImapFixture.TAG;
import static org.mockito.Mockito.mock;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import javax.mail.Flags;

import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.ImapMessage;
import org.apache.james.imap.api.Tag;
import org.apache.james.imap.api.message.IdRange;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.decode.ImapRequestLineReader;
import org.apache.james.imap.decode.ImapRequestStreamLineReader;
import org.junit.Before;
import org.junit.Test;

public class StoreCommandParserTest {

    StoreCommandParser parser;
    ImapCommand command;
    ImapMessage message;

    private ImapSession session;

    @Before
    public void setUp() throws Exception {
        parser = new StoreCommandParser(mock(StatusResponseFactory.class));
        command = ImapCommand.anyStateCommand("Command");
        session = mock(ImapSession.class);
    }

    @Test
    public void testShouldParseSilentDraftFlagged() throws Exception {
        IdRange[] ranges = { new IdRange(1) };
        Flags flags = new Flags();
        flags.add(Flags.Flag.DRAFT);
        flags.add(Flags.Flag.FLAGGED);
        check("1 FLAGS.SILENT (\\Draft \\Flagged)\r\n", ranges, true, null,
                flags, false, TAG);
    }


    @Test
    public void testShouldParseUnchangedSince() throws Exception {
        IdRange[] ranges = { new IdRange(1) };
        Flags flags = new Flags();
        flags.add(Flags.Flag.DRAFT);
        flags.add(Flags.Flag.FLAGGED);
        check("1 (UNCHANGEDSINCE 100) FLAGS.SILENT (\\Draft \\Flagged)\r\n", ranges, true, null,
                flags, false, TAG);
    }
    
    private void check(String input, IdRange[] idSet, boolean silent,
            final Boolean sign, Flags flags, boolean useUids, Tag tag)
            throws Exception {
        ImapRequestLineReader reader = new ImapRequestStreamLineReader(
                new ByteArrayInputStream(input.getBytes(StandardCharsets.US_ASCII)),
                new ByteArrayOutputStream());

        parser.decode(command, reader, tag, useUids, session);
    }
}
