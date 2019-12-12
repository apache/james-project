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
import org.apache.james.imap.api.Tag;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.decode.DecodingException;
import org.apache.james.imap.decode.ImapRequestLineReader;
import org.apache.james.imap.decode.ImapRequestLineReader.AtomCharValidator;
import org.apache.james.imap.message.request.ListRequest;

/**
 * Parse LIST commands
 */
public class ListCommandParser extends AbstractUidCommandParser {

    public ListCommandParser(StatusResponseFactory statusResponseFactory) {
        super(ImapConstants.LIST_COMMAND, statusResponseFactory);
    }

    protected ListCommandParser(ImapCommand command, StatusResponseFactory statusResponseFactory) {
        super(command, statusResponseFactory);
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

    private static class ListCharValidator extends AtomCharValidator {
        @Override
        public boolean isValid(char chr) {
            if (ImapRequestLineReader.isListWildcard(chr)) {
                return true;
            }
            return super.isValid(chr);
        }
    }

    @Override
    protected ImapMessage decode(ImapCommand command, ImapRequestLineReader request, Tag tag, boolean useUids, ImapSession session) throws DecodingException {
        String referenceName = request.mailbox();
        String mailboxPattern = listMailbox(request);
        request.eol();
        return createMessage(command, referenceName, mailboxPattern, tag);
    }

    protected ImapMessage createMessage(ImapCommand command, String referenceName, String mailboxPattern, Tag tag) {
        return new ListRequest(command, referenceName, mailboxPattern, tag);
    }
}
