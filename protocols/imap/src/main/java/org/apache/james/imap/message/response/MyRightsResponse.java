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

import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.message.response.ImapResponseMessage;
import org.apache.james.mailbox.model.MailboxACL;

/**
 * MYRIGHTS Response.
 * 
 * @author Peter Palaga
 */
public final class MyRightsResponse implements ImapResponseMessage {
    private final String mailboxName;
    private final MailboxACL.Rfc4314Rights myRights;

    public MyRightsResponse(String mailboxName, MailboxACL.Rfc4314Rights myRights) {
        super();
        this.mailboxName = mailboxName;
        this.myRights = myRights;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof MyRightsResponse) {
            MyRightsResponse other = (MyRightsResponse) o;
            return (this.myRights == other.myRights || (this.myRights != null && this.myRights.equals(other.myRights)))
                    && (this.mailboxName == other.mailboxName || (this.mailboxName != null && this.mailboxName.equals(other.mailboxName)))
                    ;
        }
        return false;
    }

    public String getMailboxName() {
        return mailboxName;
    }

    public MailboxACL.Rfc4314Rights getMyRights() {
        return myRights;
    }

    @Override
    public int hashCode() {
        final int PRIME = 31;
        return PRIME * myRights.hashCode() + mailboxName.hashCode();
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder()
        .append(ImapConstants.ACL_RESPONSE_NAME)
        .append(' ')
        .append(mailboxName)
        .append(' ')
        .append(myRights.toString());
        
        return result.toString();
    }

}
