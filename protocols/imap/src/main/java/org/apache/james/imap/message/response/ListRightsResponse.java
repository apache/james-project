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

package org.apache.james.imap.message.response;

import java.util.List;
import java.util.Objects;

import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.message.response.ImapResponseMessage;
import org.apache.james.imap.message.MailboxName;
import org.apache.james.mailbox.model.MailboxACL;

/**
 * LISTRIGHTS Response.
 */
public final class ListRightsResponse implements ImapResponseMessage {
    private final MailboxACL.EntryKey entryKey;
    private final MailboxName mailboxName;
    private final List<MailboxACL.Rfc4314Rights> rights;

    public ListRightsResponse(MailboxName mailboxName, MailboxACL.EntryKey entryKey, List<MailboxACL.Rfc4314Rights> rights) {
        super();
        this.mailboxName = mailboxName;
        this.entryKey = entryKey;
        this.rights = rights;
    }

    public MailboxACL.EntryKey getEntryKey() {
        return entryKey;
    }

    public MailboxName getMailboxName() {
        return mailboxName;
    }

    public List<MailboxACL.Rfc4314Rights> getRights() {
        return rights;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof ListRightsResponse) {
            ListRightsResponse other = (ListRightsResponse) o;

            return Objects.equals(this.mailboxName, other.mailboxName) &&
                Objects.equals(this.entryKey, other.entryKey) &&
                Objects.equals(this.rights, other.rights);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(mailboxName, entryKey, rights);
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder().append(ImapConstants.LISTRIGHTS_COMMAND.getName()).append(' ').append(mailboxName).append(' ').append(entryKey);

        for (MailboxACL.Rfc4314Rights optionalRightsGroup : rights) {
            result.append(' ').append(optionalRightsGroup.toString());
        }

        return result.toString();
    }

}
