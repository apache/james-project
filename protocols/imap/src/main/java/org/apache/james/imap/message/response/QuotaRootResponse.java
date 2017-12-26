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
/**
 * Quota Root Response
 */
public class QuotaRootResponse implements ImapResponseMessage {
    private final String quotaRoot;

    private final String mailboxName;

    public QuotaRootResponse(String mailboxName, String quotaRoot) {
        super();
        this.mailboxName = mailboxName;
        this.quotaRoot = quotaRoot;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof QuotaRootResponse) {
            QuotaRootResponse other = (QuotaRootResponse) o;
            return (this.quotaRoot == other.quotaRoot || (this.quotaRoot != null && this.quotaRoot.equals(other.quotaRoot)))
                    && (this.mailboxName == other.mailboxName || (this.mailboxName != null && this.mailboxName.equals(other.mailboxName)))
                    ;
        }
        return false;
    }

    public String getQuotaRoot() {
        return quotaRoot;
    }

    public String getMailboxName() {
        return mailboxName;
    }

    @Override
    public int hashCode() {
        final int PRIME = 31;
        return PRIME * quotaRoot.hashCode() + mailboxName.hashCode();
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder()
                .append(ImapConstants.QUOTAROOT_RESPONSE_NAME)
                .append(' ')
                .append(mailboxName)
                .append(' ')
                .append(quotaRoot);
        return result.toString();
    }

}
