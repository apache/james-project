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

import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.ImapMessage;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.StatusDataItems;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.decode.ImapRequestLineReader;
import org.apache.james.imap.decode.ImapRequestLineReader.CharacterValidator;
import org.apache.james.imap.decode.base.AbstractImapCommandParser;
import org.apache.james.imap.message.request.StatusRequest;
import org.apache.james.protocols.imap.DecodingException;

/**
 * Parse STATUS commands
 */
public class StatusCommandParser extends AbstractImapCommandParser {
    public StatusCommandParser() {
        super(ImapCommand.authenticatedStateCommand(ImapConstants.STATUS_COMMAND_NAME));
    }

    StatusDataItems statusDataItems(ImapRequestLineReader request) throws DecodingException {
        StatusDataItems items = new StatusDataItems();

        request.nextWordChar();
        request.consumeChar('(');
        CharacterValidator validator = new ImapRequestLineReader.NoopCharValidator();
        String nextWord = request.consumeWord(validator);

        while (!nextWord.endsWith(")")) {
            addItem(nextWord, items);
            nextWord = request.consumeWord(validator);
        }
        // Got the closing ")", may be attached to a word.
        if (nextWord.length() > 1) {
            addItem(nextWord.substring(0, nextWord.length() - 1), items);
        }

        return items;
    }

    private void addItem(String nextWord, StatusDataItems items) throws DecodingException {
        // All the matching must be done in a case-insensitive fashion.
        // See rfc3501 9. Formal Syntax and IMAP-282
        if (nextWord.equalsIgnoreCase(ImapConstants.STATUS_MESSAGES)) {
            items.setMessages(true);
        } else if (nextWord.equalsIgnoreCase(ImapConstants.STATUS_RECENT)) {
            items.setRecent(true);
        } else if (nextWord.equalsIgnoreCase(ImapConstants.STATUS_UIDNEXT)) {
            items.setUidNext(true);
        } else if (nextWord.equalsIgnoreCase(ImapConstants.STATUS_UIDVALIDITY)) {
            items.setUidValidity(true);
        } else if (nextWord.equalsIgnoreCase(ImapConstants.STATUS_UNSEEN)) {
            items.setUnseen(true);
        } else if (nextWord.equalsIgnoreCase(ImapConstants.STATUS_HIGHESTMODSEQ)) {
            // HIGHESTMODSEQ status item as defined in RFC4551 3.6 HIGHESTMODSEQ Status Data Items
            items.setHighestModSeq(true);
        } else {
            throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Unknown status item: '" + nextWord + "'");
        }
    }

    /**
     * @see
     * org.apache.james.imap.decode.base.AbstractImapCommandParser#decode(org.apache.james.imap.api.ImapCommand,
     * org.apache.james.imap.decode.ImapRequestLineReader, java.lang.String,
     * org.apache.james.imap.api.process.ImapSession)
     */
    protected ImapMessage decode(ImapCommand command, ImapRequestLineReader request, String tag, ImapSession session) throws DecodingException {
        final String mailboxName = request.mailbox();
        final StatusDataItems statusDataItems = statusDataItems(request);
        request.eol();
        return new StatusRequest(command, mailboxName, statusDataItems, tag);
    }
}
