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

import static org.apache.james.imap.api.display.HumanReadableText.SOCKET_IO_FAILURE;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

import jakarta.mail.Flags;

import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.ImapMessage;
import org.apache.james.imap.api.Tag;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.decode.DecodingException;
import org.apache.james.imap.decode.ImapRequestLineReader;
import org.apache.james.imap.message.Literal;
import org.apache.james.imap.message.request.ReplaceRequest;

import com.google.common.annotations.VisibleForTesting;

/**
 * RFC-8508: IMAP Replace extension
 *
 * https://www.rfc-editor.org/rfc/rfc8508.html
 */
public class ReplaceCommandParser extends AbstractUidCommandParser {
    private final Clock clock;

    public ReplaceCommandParser(StatusResponseFactory statusResponseFactory, Clock clock) {
        super(ImapConstants.REPLACE_COMMAND, statusResponseFactory);
            this.clock = clock;
    }

    /**
     * If the next character in the request is a '(', tries to read a
     * "flag_list" argument from the request. If not, returns a Flags
     * with no flags set.
     */
    private Flags parseFlags(ImapRequestLineReader request) throws DecodingException {
        char next = request.nextWordChar();
        if (next == '(') {
            return request.flagList();
        }
        return new Flags();
    }

    /**
     * If the next character in the request is a '"', tries to read a DateTime
     * argument. If not, returns now.
     */
    @VisibleForTesting
    LocalDateTime parseDateTime(ImapRequestLineReader request) throws DecodingException {
        char next = request.nextWordChar();
        if (next == '"') {
            return request.dateTime();
        }
        return LocalDateTime.now(clock);
    }

    @Override
    protected ImapMessage decode(ImapRequestLineReader request, Tag tag, boolean useUids, ImapSession session) throws DecodingException {
        long id = request.number();
        String mailboxName = request.mailbox();
        Flags flags = parseFlags(request);
        LocalDateTime datetime = parseDateTime(request);
        request.nextWordChar();

        try {
            Literal literal = request.consumeLiteral(true).right;
            Date from = Date.from(datetime.atZone(ZoneId.systemDefault()).toInstant());

            return new ReplaceRequest(id, mailboxName, flags, from, literal, tag, useUids);
        } catch (IOException e) {
            throw new DecodingException(SOCKET_IO_FAILURE, "Error copying content", e);
        }
    }
}
