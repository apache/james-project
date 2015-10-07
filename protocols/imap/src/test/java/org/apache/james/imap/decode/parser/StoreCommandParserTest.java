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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import javax.mail.Flags;

import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.ImapMessage;
import org.apache.james.imap.api.message.IdRange;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.decode.ImapRequestLineReader;
import org.apache.james.imap.decode.ImapRequestStreamLineReader;
import org.jmock.Mockery;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JMock.class)
public class StoreCommandParserTest {

    StoreCommandParser parser;


    ImapCommand command;

    ImapMessage message;

    private Mockery mockery = new JUnit4Mockery();


	private ImapSession session;

    @Before
    public void setUp() throws Exception {
        parser = new StoreCommandParser();
        command = ImapCommand.anyStateCommand("Command");
        message = mockery.mock(ImapMessage.class);
        session = mockery.mock(ImapSession.class);

    }

    @Test
    public void testShouldParseSilentDraftFlagged() throws Exception {
        IdRange[] ranges = { new IdRange(1) };
        Flags flags = new Flags();
        flags.add(Flags.Flag.DRAFT);
        flags.add(Flags.Flag.FLAGGED);
        check("1 FLAGS.SILENT (\\Draft \\Flagged)\r\n", ranges, true, null,
                flags, false, "A01");
    }


    @Test
    public void testShouldParseUnchangedSince() throws Exception {
        IdRange[] ranges = { new IdRange(1) };
        Flags flags = new Flags();
        flags.add(Flags.Flag.DRAFT);
        flags.add(Flags.Flag.FLAGGED);
        check("1 (UNCHANGEDSINCE 100) FLAGS.SILENT (\\Draft \\Flagged)\r\n", ranges, true, null,
                flags, false, "A01");
    }
    
    private void check(String input, final IdRange[] idSet,final boolean silent,
            final Boolean sign, final Flags flags, final boolean useUids, final String tag)
            throws Exception {
        ImapRequestLineReader reader = new ImapRequestStreamLineReader(
                new ByteArrayInputStream(input.getBytes("US-ASCII")),
                new ByteArrayOutputStream());

        parser.decode(command, reader, tag, useUids, session);
    }
}
