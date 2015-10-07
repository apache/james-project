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

import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.message.IdRange;
import org.apache.james.imap.api.message.request.ImapRequest;

/**
 * {@link ImapRequest} which request the copy of messages
 */
public class CopyRequest extends AbstractImapRequest {

    private final IdRange[] idSet;

    private final String mailboxName;

    private final boolean useUids;

    public CopyRequest(final ImapCommand command, final IdRange[] idSet, final String mailboxName, final boolean useUids, final String tag) {
        super(tag, command);
        this.idSet = idSet;
        this.mailboxName = mailboxName;
        this.useUids = useUids;
    }

    /**
     * Return an Array of {@link IdRange} to copy
     * 
     * @return range
     */
    public final IdRange[] getIdSet() {
        return idSet;
    }

    /**
     * Return the name of the mailbox
     * 
     * @return mailbox
     */
    public final String getMailboxName() {
        return mailboxName;
    }

    public final boolean isUseUids() {
        return useUids;
    }
}
