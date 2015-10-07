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

import java.util.Date;

import javax.mail.Flags;

import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.ImapMessage;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.decode.ImapRequestLineReader;
import org.apache.james.imap.decode.base.AbstractImapCommandParser;
import org.apache.james.imap.message.request.AppendRequest;
import org.apache.james.protocols.imap.DecodingException;

/**
 * Parses APPEND command
 */
public class AppendCommandParser extends AbstractImapCommandParser {

    public AppendCommandParser() {
        super(ImapCommand.authenticatedStateCommand(ImapConstants.APPEND_COMMAND_NAME));
    }

    /**
     * If the next character in the request is a '(', tries to read a
     * "flag_list" argument from the request. If not, returns a MessageFlags
     * with no flags set.
     */
    public Flags optionalAppendFlags(ImapRequestLineReader request) throws DecodingException {
        char next = request.nextWordChar();
        if (next == '(') {
            return request.flagList();
        } else {
            return null;
        }
    }

    /**
     * If the next character in the request is a '"', tries to read a DateTime
     * argument. If not, returns null.
     */
    public Date optionalDateTime(ImapRequestLineReader request) throws DecodingException {
        char next = request.nextWordChar();
        if (next == '"') {
            return request.dateTime();
        } else {
            return null;
        }
    }

    /**
     * @see
     * org.apache.james.imap.decode.base.AbstractImapCommandParser#decode(org.apache.james.imap.api.ImapCommand,
     * org.apache.james.imap.decode.ImapRequestLineReader, java.lang.String,
     * org.apache.james.imap.api.process.ImapSession)
     */
    protected ImapMessage decode(ImapCommand command, ImapRequestLineReader request, String tag, ImapSession session) throws DecodingException {
        String mailboxName = request.mailbox();
        Flags flags = optionalAppendFlags(request);
        if (flags == null) {
            flags = new Flags();
        }
        Date datetime = optionalDateTime(request);
        if (datetime == null) {
            datetime = new Date();
        }
        request.nextWordChar();

        final ImapMessage result = new AppendRequest(command, mailboxName, flags, datetime, request.consumeLiteral(true), tag);
        return result;
    }
}
