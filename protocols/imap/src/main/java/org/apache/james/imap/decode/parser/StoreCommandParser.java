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

import java.util.Locale;

import jakarta.inject.Inject;
import jakarta.mail.Flags;

import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.ImapMessage;
import org.apache.james.imap.api.Tag;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.IdRange;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.decode.DecodingException;
import org.apache.james.imap.decode.ImapRequestLineReader;
import org.apache.james.imap.decode.ImapRequestLineReader.StringMatcherCharacterValidator;
import org.apache.james.imap.message.request.StoreRequest;
import org.apache.james.mailbox.MessageManager;

/**
 * Parse STORE commands
 */
public class StoreCommandParser extends AbstractUidCommandParser {

    private static final String UNCHANGEDSINCE = "UNCHANGEDSINCE";

    @Inject
    public StoreCommandParser(StatusResponseFactory statusResponseFactory) {
        super(ImapConstants.STORE_COMMAND, statusResponseFactory);
    }

    @Override
    protected ImapMessage decode(ImapRequestLineReader request, Tag tag, boolean useUids, ImapSession session) throws DecodingException {
        IdRange[] idSet = request.parseIdRange(session);
        long unchangedSince = -1;
        char next = request.nextWordChar();
        if (next == '(') {
            // Seems like we have a CONDSTORE parameter
            request.consume();

            request.consumeWord(StringMatcherCharacterValidator.ignoreCase(UNCHANGEDSINCE));
            request.consumeChar(' ');
            unchangedSince = request.number(true);
            request.consumeChar(')');
            next = request.nextWordChar();
        }

        MessageManager.FlagsUpdateMode flagsUpdateMode = parseFlagsUpdateMode(request, next);
        String directive = request.consumeWord(ImapRequestLineReader.NoopCharValidator.INSTANCE);
        boolean silent = parseSilent(directive);
        Flags flags = parseFlags(request);

        request.eol();
        return new StoreRequest(idSet, silent, flags, useUids, tag, flagsUpdateMode, unchangedSince);
    }

    private Flags parseFlags(ImapRequestLineReader request) throws DecodingException {
        // Handle all kind of "store-att-flags"
        // See IMAP-281
        Flags flags = new Flags();
        if (request.nextWordChar() == '(') {
            flags.add(request.flagList());
        } else {
            boolean moreFlags = true;
            while (moreFlags) {
                flags.add(request.flag());
                try {
                    request.consumeChar(' ');
                } catch (DecodingException e) {
                    // seems like no more flags were found
                    moreFlags = false;
                }
            }
        }
        return flags;
    }

    private boolean parseSilent(String directive) throws DecodingException {
        switch (directive.toUpperCase(Locale.US)) {
            case "FLAGS":
                return false;
            case "FLAGS.SILENT":
                return true;
            default:
                throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Invalid Store Directive: '" + directive + "'");
        }
    }

    private MessageManager.FlagsUpdateMode parseFlagsUpdateMode(ImapRequestLineReader request, char next) throws DecodingException {
        switch (next) {
            case '+':
                request.consume();
                return MessageManager.FlagsUpdateMode.ADD;
            case '-':
                request.consume();
                return MessageManager.FlagsUpdateMode.REMOVE;
            default:
                return MessageManager.FlagsUpdateMode.REPLACE;
        }
    }
}
