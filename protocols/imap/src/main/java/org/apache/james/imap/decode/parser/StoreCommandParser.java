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

import javax.mail.Flags;

import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.ImapMessage;
import org.apache.james.imap.api.Tag;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.IdRange;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.decode.DecodingException;
import org.apache.james.imap.decode.ImapRequestLineReader;
import org.apache.james.imap.decode.ImapRequestLineReader.CharacterValidator;
import org.apache.james.imap.message.request.StoreRequest;
import org.apache.james.mailbox.MessageManager;

/**
 * Parse STORE commands
 */
public class StoreCommandParser extends AbstractUidCommandParser {

    private static final byte[] UNCHANGEDSINCE = "UNCHANGEDSINCE".getBytes();
    
    public StoreCommandParser() {
        super(ImapCommand.selectedStateCommand(ImapConstants.STORE_COMMAND_NAME));
    }

    @Override
    protected ImapMessage decode(ImapCommand command, ImapRequestLineReader request, Tag tag, boolean useUids, ImapSession session) throws DecodingException {
        final IdRange[] idSet = request.parseIdRange(session);
        boolean silent = false;
        long unchangedSince = -1;
        char next = request.nextWordChar();
        if (next == '(') {
            // Seems like we have a CONDSTORE parameter
            request.consume();
            
            request.consumeWord(new CharacterValidator() {
                private int pos = 0;
                @Override
                public boolean isValid(char chr) {
                    if (pos >= UNCHANGEDSINCE.length) {
                        return false;
                    } else {
                        return ImapRequestLineReader.cap(chr) == UNCHANGEDSINCE[pos++];
                    }
                }
            });
            request.consumeChar(' ');
            unchangedSince = request.number(true);
            request.consumeChar(')');
            next = request.nextWordChar();
        }

        final MessageManager.FlagsUpdateMode flagsUpdateMode;
        if (next == '+') {
            flagsUpdateMode = MessageManager.FlagsUpdateMode.ADD;
            request.consume();
        } else if (next == '-') {
            flagsUpdateMode = MessageManager.FlagsUpdateMode.REMOVE;
            request.consume();
        } else {
            flagsUpdateMode = MessageManager.FlagsUpdateMode.REPLACE;
        }

        String directive = request.consumeWord(new ImapRequestLineReader.NoopCharValidator());
        if ("FLAGS".equalsIgnoreCase(directive)) {
            silent = false;
        } else if ("FLAGS.SILENT".equalsIgnoreCase(directive)) {
            silent = true;
        } else {
            throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Invalid Store Directive: '" + directive + "'");
        }

        // Handle all kind of "store-att-flags"
        // See IMAP-281
        final Flags flags = new Flags();
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

        request.eol();
        return new StoreRequest(command, idSet, silent, flags, useUids, tag, flagsUpdateMode, unchangedSince);
    }
}
