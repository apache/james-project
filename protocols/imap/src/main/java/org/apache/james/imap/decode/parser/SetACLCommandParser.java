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

import jakarta.inject.Inject;

import org.apache.commons.lang3.StringUtils;
import org.apache.james.core.Username;
import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.ImapMessage;
import org.apache.james.imap.api.Tag;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.decode.DecodingException;
import org.apache.james.imap.decode.ImapRequestLineReader;
import org.apache.james.imap.decode.base.AbstractImapCommandParser;
import org.apache.james.imap.message.request.SetACLRequest;
import org.apache.james.mailbox.exception.UnsupportedRightException;
import org.apache.james.mailbox.model.MailboxACL;

/**
 * SETACL Parser
 */
public class SetACLCommandParser extends AbstractImapCommandParser {

    @Inject
    public SetACLCommandParser(StatusResponseFactory statusResponseFactory) {
        super(ImapConstants.SETACL_COMMAND, statusResponseFactory);
    }

    @Override
    protected ImapMessage decode(ImapRequestLineReader request, Tag tag, ImapSession session) throws DecodingException {
        try {
            final SetACLRequest.MailboxName mailboxName = new SetACLRequest.MailboxName(request.mailbox());
            final Username identifier = Username.of(request.astring());
            final String editModeAndRights = request.astring();
            request.eol();

            MailboxACL.EditMode editMode = MailboxACL.EditMode.REPLACE;
            if (StringUtils.isNotEmpty(editModeAndRights)) {
                switch (editModeAndRights.charAt(0)) {
                    case MailboxACL.ADD_RIGHTS_MARKER:
                        editMode = MailboxACL.EditMode.ADD;
                        break;
                    case MailboxACL.REMOVE_RIGHTS_MARKER:
                        editMode = MailboxACL.EditMode.REMOVE;
                        break;
                }
            }

            MailboxACL.Rfc4314Rights rights = MailboxACL.Rfc4314Rights.deserialize(editModeAndRights.substring(1));

            return new SetACLRequest(tag, mailboxName, identifier, editMode, rights);
        } catch (UnsupportedRightException e) {
            throw new DecodingException(HumanReadableText.UNSUPPORTED, e.getMessage(), e.getCause());
        }
    }
}
