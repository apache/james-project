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
import org.apache.james.imap.api.ImapSessionUtils;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.decode.ImapRequestLineReader;
import org.apache.james.imap.decode.base.AbstractImapCommandParser;
import org.apache.james.imap.message.request.CreateRequest;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.protocols.imap.DecodingException;

/**
 * Parse CREATE commands
 */
public class CreateCommandParser extends AbstractImapCommandParser {

    public CreateCommandParser() {
        super(ImapCommand.authenticatedStateCommand(ImapConstants.CREATE_COMMAND_NAME));
    }

    /**
     * @see
     * org.apache.james.imap.decode.base.AbstractImapCommandParser#decode(org.apache.james.imap.api.ImapCommand,
     * org.apache.james.imap.decode.ImapRequestLineReader, java.lang.String,
     * org.apache.james.imap.api.process.ImapSession)
     */
    protected ImapMessage decode(ImapCommand command, ImapRequestLineReader request, String tag, ImapSession session) throws DecodingException {
        String mailboxName = request.mailbox();

        MailboxSession mailboxSession = ImapSessionUtils.getMailboxSession(session);

        // Check if we have an mailboxsession. This is a workaround for
        // IMAP-240:
        // https://issues.apache.org/jira/browse/IMAP-240
        if (mailboxSession != null) {
            // RFC3501@6.3.3p2
            // When mailbox name is suffixed with hierarchy separator
            // name created must remove tailing delimiter
            if (mailboxName.endsWith(Character.toString(mailboxSession.getPathDelimiter()))) { // NOPMD
                                                                                               // keep
                                                                                               // comment
                mailboxName = mailboxName.substring(0, mailboxName.length() - 1);
            }
        }
        request.eol();
        final ImapMessage result = new CreateRequest(command, mailboxName, tag);
        return result;
    }

}
