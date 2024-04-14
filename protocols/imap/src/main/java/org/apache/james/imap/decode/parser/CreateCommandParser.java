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

import java.util.function.Predicate;

import jakarta.inject.Inject;

import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.ImapMessage;
import org.apache.james.imap.api.Tag;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.decode.DecodingException;
import org.apache.james.imap.decode.ImapRequestLineReader;
import org.apache.james.imap.decode.base.AbstractImapCommandParser;
import org.apache.james.imap.message.request.CreateRequest;
import org.apache.james.mailbox.MailboxSession;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;

/**
 * Parse CREATE commands
 */
public class CreateCommandParser extends AbstractImapCommandParser {

    @Inject
    public CreateCommandParser(StatusResponseFactory statusResponseFactory) {
        super(ImapConstants.CREATE_COMMAND, statusResponseFactory);
    }

    @Override
    protected ImapMessage decode(ImapRequestLineReader request, Tag tag, ImapSession session) throws DecodingException {
        String mailboxName = request.mailbox();

        MailboxSession mailboxSession = session.getMailboxSession();

        // Check if we have an mailboxsession. This is a workaround for
        // IMAP-240:
        // https://issues.apache.org/jira/browse/IMAP-240
        if (mailboxSession != null) {
            // RFC3501@6.3.3p2
            // When mailbox name is suffixed with hierarchy separator
            // name created must remove tailing delimiter
            if (mailboxName.endsWith(Character.toString(mailboxSession.getPathDelimiter()))) { 
                mailboxName = mailboxName.substring(0, mailboxName.length() - 1);
            }
        }
        request.eol();
        assertMailboxNameJustContainDelimiter(mailboxName, mailboxSession.getPathDelimiter());
        return new CreateRequest(mailboxName, tag);
    }

    private void assertMailboxNameJustContainDelimiter(String mailboxName, char delimiter) throws DecodingException {
        Splitter.on(delimiter)
            .splitToStream(mailboxName)
            .filter(Predicate.not(Strings::isNullOrEmpty))
            .findAny()
            .orElseThrow(() -> new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Invalid mailbox name"));
    }
}
