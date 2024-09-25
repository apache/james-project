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

package org.apache.james.imap.message.request;

import org.apache.james.core.Username;
import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.Tag;
import org.apache.james.mailbox.model.MailboxACL;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

/**
 * SETACL Request.
 */
public class SetACLRequest extends AbstractImapRequest {
    private final Username identifier;
    private final MailboxACL.EditMode editMode;
    private final MailboxACL.Rfc4314Rights rights;

    public static class MailboxName {
        private final String mailboxName;

        public MailboxName(String mailboxName) {
            Preconditions.checkArgument(!Strings.isNullOrEmpty(mailboxName), "MailboxName must not be null or empty");
            this.mailboxName = mailboxName;
        }

        public String asString() {
            return mailboxName;
        }
    }

    private final MailboxName mailboxName;

    public SetACLRequest(Tag tag, MailboxName mailboxName, Username identifier, MailboxACL.EditMode editMode, MailboxACL.Rfc4314Rights rights) {
        super(tag, ImapConstants.SETACL_COMMAND);
        this.mailboxName = mailboxName;
        this.identifier = identifier;
        this.editMode = editMode;
        this.rights = rights;
    }

    public Username getIdentifier() {
        return identifier;
    }

    public MailboxName getMailboxName() {
        return mailboxName;
    }

    public MailboxACL.EditMode getEditMode() {
        return editMode;
    }

    public MailboxACL.Rfc4314Rights getRights() {
        return rights;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("identifier", identifier)
            .add("mailboxName", mailboxName)
            .add("edit mode", editMode)
            .add("rights", rights)
            .toString();
    }
}
