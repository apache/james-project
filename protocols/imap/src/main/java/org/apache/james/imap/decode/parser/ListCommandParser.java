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
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.decode.ImapRequestLineReader;
import org.apache.james.imap.decode.ImapRequestLineReader.ATOM_CHARValidator;
import org.apache.james.imap.message.request.ListRequest;
import org.apache.james.protocols.imap.DecodingException;

/**
 * Parse LIST commands
 */
public class ListCommandParser extends AbstractUidCommandParser {

    public ListCommandParser() {
        super(ImapCommand.authenticatedStateCommand(ImapConstants.LIST_COMMAND_NAME));
    }

    protected ListCommandParser(final ImapCommand command) {
        super(command);
    }

    /**
     * Reads an argument of type "list_mailbox" from the request, which is the
     * second argument for a LIST or LSUB command. Valid values are a "string"
     * argument, an "atom" with wildcard characters.
     * 
     * @return An argument of type "list_mailbox"
     */
    public String listMailbox(ImapRequestLineReader request) throws DecodingException {
        char next = request.nextWordChar();
        switch (next) {
        case '"':
            return request.consumeQuoted();
        case '{':
            return request.consumeLiteral(null);
        default:
            return request.consumeWord(new ListCharValidator());
        }
    }

    private class ListCharValidator extends ATOM_CHARValidator {
        public boolean isValid(char chr) {
            if (ImapRequestLineReader.isListWildcard(chr)) {
                return true;
            }
            return super.isValid(chr);
        }
    }

    /**
     * @see
     * org.apache.james.imap.decode.parser.AbstractUidCommandParser#decode(org.apache.james.imap.api.ImapCommand,
     * org.apache.james.imap.decode.ImapRequestLineReader, java.lang.String,
     * boolean, org.apache.james.imap.api.process.ImapSession)
     */
    protected ImapMessage decode(ImapCommand command, ImapRequestLineReader request, String tag, boolean useUids, ImapSession session) throws DecodingException {
        String referenceName = request.mailbox();
        String mailboxPattern = listMailbox(request);
        request.eol();
        final ImapMessage result = createMessage(command, referenceName, mailboxPattern, tag);
        return result;
    }

    protected ImapMessage createMessage(ImapCommand command, final String referenceName, final String mailboxPattern, final String tag) {
        final ImapMessage result = new ListRequest(command, referenceName, mailboxPattern, tag);
        return result;
    }
}
