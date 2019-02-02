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
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.ImapSessionUtils;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.decode.ImapRequestStreamLineReader;
import org.apache.james.imap.message.request.CreateRequest;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.protocols.imap.DecodingException;
import org.junit.Before;
import org.junit.Test;

public class CreateCommandParserTest {
    private static final OutputStream outputStream = null;
    private static final ImapCommand command = ImapCommand.anyStateCommand("Command");
    private static final String TAG = "A1";

    private ImapSession mockImapSession;
    private MailboxSession mailboxSession;
    private CreateCommandParser parser;

    @Before
    public void setUp() throws Exception {
        mockImapSession = mock(ImapSession.class);
        mailboxSession = MailboxSessionUtil.create("userName");

        when(mockImapSession.getAttribute(ImapSessionUtils.MAILBOX_SESSION_ATTRIBUTE_SESSION_KEY)).thenReturn(mailboxSession);

        parser = new CreateCommandParser();
    }

    @Test
    public void decodeShouldThrowWhenCommandHasEmptyMailbox() {
        InputStream inputStream = new ByteArrayInputStream(" \n".getBytes(StandardCharsets.US_ASCII));
        ImapRequestStreamLineReader lineReader = new ImapRequestStreamLineReader(inputStream, outputStream);

        assertThatThrownBy(() -> parser.decode(command, lineReader, TAG, mockImapSession))
            .isInstanceOf(DecodingException.class);
    }

    @Test
    public void decodeShouldThrowWhenCommandHasOnlySeparatorMailbox() {
        InputStream inputStream = new ByteArrayInputStream("..\n".getBytes(StandardCharsets.US_ASCII));
        ImapRequestStreamLineReader lineReader = new ImapRequestStreamLineReader(inputStream, outputStream);

        assertThatThrownBy(() -> parser.decode(command, lineReader, TAG, mockImapSession))
            .isInstanceOf(DecodingException.class);
    }

    @Test
    public void decodeShouldReturnCreateRequestWhenValidMailboxName() throws Exception {
        InputStream inputStream = new ByteArrayInputStream(".AnyMailbox.\n".getBytes(StandardCharsets.US_ASCII));
        ImapRequestStreamLineReader lineReader = new ImapRequestStreamLineReader(inputStream, outputStream);

        CreateRequest imapMessage = (CreateRequest)parser.decode(command, lineReader, TAG, mockImapSession);
        assertThat(imapMessage.getMailboxName()).isEqualTo(".AnyMailbox");
    }

}