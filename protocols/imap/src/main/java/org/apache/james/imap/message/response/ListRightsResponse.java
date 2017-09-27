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

import java.util.Arrays;

import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.message.response.ImapResponseMessage;
import org.apache.james.mailbox.model.MailboxACL;

/**
 * LISTRIGHTS Response.
 * 
 * @author Peter Palaga
 */
public final class ListRightsResponse implements ImapResponseMessage {

    private final String identifier;
    private final String mailboxName;
    private final MailboxACL.Rfc4314Rights[] rights;

    public ListRightsResponse(String mailboxName, String identifier, MailboxACL.Rfc4314Rights[] rights) {
        super();
        this.mailboxName = mailboxName;
        this.identifier = identifier;
        this.rights = rights;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof ListRightsResponse) {
            ListRightsResponse other = (ListRightsResponse) o;
            return (this.mailboxName == other.mailboxName || (this.mailboxName != null && this.mailboxName.equals(other.mailboxName))) && (this.identifier == other.identifier || (this.identifier != null && this.identifier.equals(other.identifier))) && Arrays.equals(this.rights, other.rights);
        }
        return false;
    }

    public String getIdentifier() {
        return identifier;
    }

    public String getMailboxName() {
        return mailboxName;
    }

    public MailboxACL.Rfc4314Rights[] getRights() {
        return rights;
    }

    @Override
    public int hashCode() {
        final int PRIME = 31;
        int hashCode = (mailboxName == null ? 0 : mailboxName.hashCode());
        hashCode = PRIME * hashCode + (identifier == null ? 0 : identifier.hashCode());
        hashCode = PRIME * hashCode + (rights == null ? 0 : Arrays.hashCode(rights));

        return hashCode;
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder().append(ImapConstants.LISTRIGHTS_RESPONSE_NAME).append(' ').append(mailboxName).append(' ').append(identifier);

        for (MailboxACL.Rfc4314Rights optionalRightsGroup : rights) {
            result.append(' ').append(optionalRightsGroup.toString());
        }

        return result.toString();
    }

}
