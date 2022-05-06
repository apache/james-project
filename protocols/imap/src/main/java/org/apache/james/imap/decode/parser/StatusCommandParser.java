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

import java.util.EnumSet;

import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.ImapMessage;
import org.apache.james.imap.api.Tag;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.StatusDataItems;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.decode.DecodingException;
import org.apache.james.imap.decode.ImapRequestLineReader;
import org.apache.james.imap.decode.base.AbstractImapCommandParser;
import org.apache.james.imap.message.request.StatusRequest;

/**
 * Parse STATUS commands
 */
public class StatusCommandParser extends AbstractImapCommandParser {

    public StatusCommandParser(StatusResponseFactory statusResponseFactory) {
        super(ImapConstants.STATUS_COMMAND, statusResponseFactory);
    }

    @Override
    protected ImapMessage decode(ImapRequestLineReader request, Tag tag, ImapSession session) throws DecodingException {
        String mailboxName = request.mailbox();
        StatusDataItems statusDataItems = statusDataItems(request);
        request.eol();
        return new StatusRequest(mailboxName, statusDataItems, tag);
    }

    private StatusDataItems statusDataItems(ImapRequestLineReader request) throws DecodingException {
        return new StatusDataItems(splitWords(request));
    }

    private EnumSet<StatusDataItems.StatusItem> splitWords(ImapRequestLineReader request) throws DecodingException {
        EnumSet<StatusDataItems.StatusItem> words = EnumSet.noneOf(StatusDataItems.StatusItem.class);

        request.nextWordChar();
        request.consumeChar('(');
        request.nextWordChar();

        while (request.nextChar() != ')') {
            words.add(parseStatus(request));
            request.nextWordChar();
        }
        request.consumeChar(')');
        return words;
    }

    private StatusDataItems.StatusItem parseStatus(ImapRequestLineReader request) throws DecodingException {
        // All the matching must be done in a case-insensitive fashion.
        // See rfc3501 9. Formal Syntax and IMAP-282
        char c = request.nextWordChar();
        if (c == 'm' || c == 'M') {
            return readMessages(request);
        }
        if (c == 'r' || c == 'R') {
            return readRecent(request);
        }
        if (c == 'h' || c == 'H') {
            return readHighestModseq(request);
        }
        if (c == 'u' || c == 'U') {
            return readU(request);
        }
        throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Unknown status item: '" + request.consumeWord(ImapRequestLineReader.NoopCharValidator.INSTANCE) + "'");
    }

    private StatusDataItems.StatusItem readU(ImapRequestLineReader request) throws DecodingException {
        char c;
        assertChar(request, 'u', 'U');
        c = request.nextWordChar();
        if (c == 'n' || c == 'N') {
            return readUnseen(request);
        }
        assertChar(request, 'i', 'I');
        assertChar(request, 'd', 'D');
        c = request.nextWordChar();
        if (c == 'n' || c == 'N') {
            return readUidNext(request);
        }
        readValidity(request);
        return StatusDataItems.StatusItem.UID_VALIDITY;
    }

    private void readValidity(ImapRequestLineReader request) throws DecodingException {
        assertChar(request, 'v', 'V');
        assertChar(request, 'a', 'A');
        assertChar(request, 'l', 'L');
        assertChar(request, 'i', 'I');
        assertChar(request, 'd', 'D');
        assertChar(request, 'i', 'I');
        assertChar(request, 't', 'T');
        assertChar(request, 'y', 'Y');
    }

    private StatusDataItems.StatusItem readUidNext(ImapRequestLineReader request) throws DecodingException {
        assertChar(request, 'n', 'N');
        assertChar(request, 'e', 'E');
        assertChar(request, 'x', 'X');
        assertChar(request, 't', 'T');
        return StatusDataItems.StatusItem.UID_NEXT;
    }

    private StatusDataItems.StatusItem readUnseen(ImapRequestLineReader request) throws DecodingException {
        assertChar(request, 'n', 'N');
        assertChar(request, 's', 'S');
        assertChar(request, 'e', 'E');
        assertChar(request, 'e', 'E');
        assertChar(request, 'n', 'N');
        return StatusDataItems.StatusItem.UNSEEN;
    }

    private StatusDataItems.StatusItem readHighestModseq(ImapRequestLineReader request) throws DecodingException {
        assertChar(request, 'h', 'H');
        assertChar(request, 'i', 'I');
        assertChar(request, 'g', 'G');
        assertChar(request, 'h', 'H');
        assertChar(request, 'e', 'E');
        assertChar(request, 's', 'S');
        assertChar(request, 't', 'T');
        assertChar(request, 'm', 'M');
        assertChar(request, 'o', 'O');
        assertChar(request, 'd', 'D');
        assertChar(request, 's', 'S');
        assertChar(request, 'e', 'E');
        assertChar(request, 'q', 'Q');
        return StatusDataItems.StatusItem.HIGHEST_MODSEQ;
    }

    private StatusDataItems.StatusItem readRecent(ImapRequestLineReader request) throws DecodingException {
        assertChar(request, 'r', 'R');
        assertChar(request, 'e', 'E');
        assertChar(request, 'c', 'C');
        assertChar(request, 'e', 'E');
        assertChar(request, 'n', 'N');
        assertChar(request, 't', 'T');
        return StatusDataItems.StatusItem.RECENT;
    }

    private StatusDataItems.StatusItem readMessages(ImapRequestLineReader request) throws DecodingException {
        assertChar(request, 'm', 'M');
        assertChar(request, 'e', 'E');
        assertChar(request, 's', 'S');
        assertChar(request, 's', 'S');
        assertChar(request, 'a', 'A');
        assertChar(request, 'g', 'G');
        assertChar(request, 'e', 'E');
        assertChar(request, 's', 'S');
        return StatusDataItems.StatusItem.MESSAGES;
    }

    private void assertChar(ImapRequestLineReader reader, char low, char up) throws DecodingException {
        char c = reader.consume();
        if (c != low && c != up) {
            throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Unexpected token in Status item. Expecting " + up + " got " + c);
        }
    }
}
