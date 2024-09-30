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
import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.ImapMessage;
import org.apache.james.imap.api.Tag;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.decode.DecodingException;
import org.apache.james.imap.decode.ImapRequestLineReader;
import org.apache.james.imap.decode.base.AbstractImapCommandParser;
import org.apache.james.imap.message.MailboxName;
import org.apache.james.imap.message.request.SetACLRequest;
import org.apache.james.mailbox.exception.UnsupportedRightException;
import org.apache.james.mailbox.model.MailboxACL;

import com.google.common.base.CharMatcher;

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
        MailboxName mailboxName = new MailboxName(request.mailbox());
        MailboxACL.EntryKey entryKey = MailboxACL.EntryKey.deserialize(request.astring());
        String editModeAndRights = request.astring();
        request.eol();

        MailboxACL.ACLCommand aclCommand = MailboxACL.command()
                .key(entryKey)
                .mode(parseEditMode(editModeAndRights))
                .rights(parseRights(editModeAndRights))
                .build();

        return new SetACLRequest(tag, mailboxName, aclCommand);
    }

    private MailboxACL.EditMode parseEditMode(String editModeAndRights) {
        if (StringUtils.isEmpty(editModeAndRights)) {
            return MailboxACL.EditMode.REPLACE;
        }

        return switch (editModeAndRights.charAt(0)) {
            case MailboxACL.ADD_RIGHTS_MARKER -> MailboxACL.EditMode.ADD;
            case MailboxACL.REMOVE_RIGHTS_MARKER -> MailboxACL.EditMode.REMOVE;
            default -> MailboxACL.EditMode.REPLACE;
        };
    }

    private MailboxACL.Rfc4314Rights parseRights(String editModeAndRights) throws DecodingException {
        if (StringUtils.isEmpty(editModeAndRights)) {
            throw new DecodingException(HumanReadableText.INVALID_COMMAND, "SETACL command must include rights. If you want to remove rights for that user, please use a DELETEACL command.");
        }
        try {
            if (CharMatcher.anyOf("" + MailboxACL.ADD_RIGHTS_MARKER + MailboxACL.REMOVE_RIGHTS_MARKER).matches(editModeAndRights.charAt(0))) {
                // first character is the edit mode, to be excluded here
                return MailboxACL.Rfc4314Rights.deserialize(editModeAndRights.substring(1));
            } else {
                // no edit mode
                return MailboxACL.Rfc4314Rights.deserialize(editModeAndRights);
            }
        } catch (UnsupportedRightException e) {
            throw new DecodingException(HumanReadableText.UNSUPPORTED, e.getMessage(), e.getCause());
        }
    }
}
