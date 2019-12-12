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

import org.apache.james.imap.api.ImapCommand;
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

import com.github.fge.lambdas.Throwing;
import com.github.steveash.guavate.Guavate;
import com.google.common.collect.ImmutableList;

/**
 * Parse STATUS commands
 */
public class StatusCommandParser extends AbstractImapCommandParser {
    private static final ImapRequestLineReader.NoopCharValidator NOOP_CHAR_VALIDATOR = new ImapRequestLineReader.NoopCharValidator();

    public StatusCommandParser(StatusResponseFactory statusResponseFactory) {
        super(ImapConstants.STATUS_COMMAND, statusResponseFactory);
    }

    @Override
    protected ImapMessage decode(ImapCommand command, ImapRequestLineReader request, Tag tag, ImapSession session) throws DecodingException {
        String mailboxName = request.mailbox();
        StatusDataItems statusDataItems = statusDataItems(request);
        request.eol();
        return new StatusRequest(command, mailboxName, statusDataItems, tag);
    }

    private StatusDataItems statusDataItems(ImapRequestLineReader request) throws DecodingException {
        ImmutableList<String> words = splitWords(request);

        EnumSet<StatusDataItems.StatusItem> items = EnumSet.copyOf(words.stream()
            .map(Throwing.function(this::parseStatus).sneakyThrow())
            .collect(Guavate.toImmutableList()));

        return new StatusDataItems(items);
    }

    private ImmutableList<String> splitWords(ImapRequestLineReader request) throws DecodingException {
        ImmutableList.Builder<String> words = ImmutableList.builder();

        request.nextWordChar();
        request.consumeChar('(');
        String nextWord = request.consumeWord(NOOP_CHAR_VALIDATOR);

        while (!nextWord.endsWith(")")) {
            words.add(nextWord);
            nextWord = request.consumeWord(NOOP_CHAR_VALIDATOR);
        }
        // Got the closing ")", may be attached to a word.
        if (nextWord.length() > 1) {
            words.add(nextWord.substring(0, nextWord.length() - 1));
        }
        return words.build();
    }

    private StatusDataItems.StatusItem parseStatus(String nextWord) throws DecodingException {
        // All the matching must be done in a case-insensitive fashion.
        // See rfc3501 9. Formal Syntax and IMAP-282
        if (nextWord.equalsIgnoreCase(ImapConstants.STATUS_MESSAGES)) {
            return StatusDataItems.StatusItem.MESSAGES;
        } else if (nextWord.equalsIgnoreCase(ImapConstants.STATUS_RECENT)) {
            return StatusDataItems.StatusItem.RECENT;
        } else if (nextWord.equalsIgnoreCase(ImapConstants.STATUS_UIDNEXT)) {
            return StatusDataItems.StatusItem.UID_NEXT;
        } else if (nextWord.equalsIgnoreCase(ImapConstants.STATUS_UIDVALIDITY)) {
            return StatusDataItems.StatusItem.UID_VALIDITY;
        } else if (nextWord.equalsIgnoreCase(ImapConstants.STATUS_UNSEEN)) {
            return StatusDataItems.StatusItem.UNSEEN;
        } else if (nextWord.equalsIgnoreCase(ImapConstants.STATUS_HIGHESTMODSEQ)) {
            // HIGHESTMODSEQ status item as defined in RFC4551 3.6 HIGHESTMODSEQ Status Data Items
            return StatusDataItems.StatusItem.HIGHEST_MODSEQ;
        } else {
            throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Unknown status item: '" + nextWord + "'");
        }
    }
}
