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

import java.util.Map.Entry;
import java.util.Objects;

import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.message.response.ImapResponseMessage;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxACL.EntryKey;
import org.apache.james.mailbox.model.MailboxACL.Rfc4314Rights;

/**
 * ACL Response.
 * 
 */
public final class ACLResponse implements ImapResponseMessage {
    private final MailboxACL acl;
    private final String mailboxName;

    public ACLResponse(String mailboxName, MailboxACL acl) {
        this.mailboxName = mailboxName;
        this.acl = acl;
    }

    public MailboxACL getAcl() {
        return acl;
    }

    public String getMailboxName() {
        return mailboxName;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof ACLResponse) {
            ACLResponse other = (ACLResponse) o;

            return Objects.equals(this.acl, other.acl)
                && Objects.equals(this.mailboxName, other.mailboxName);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(acl, mailboxName);
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder()
        .append(ImapConstants.ACL_RESPONSE_NAME)
        .append(' ')
        .append(mailboxName);
        
        for (Entry<EntryKey, Rfc4314Rights> en : acl.getEntries().entrySet()) {
            result.append(' ')
                .append(en.getKey().toString())
                .append(' ')
                .append(en.getValue().toString());
        }
        
        return result.toString();
    }

}
