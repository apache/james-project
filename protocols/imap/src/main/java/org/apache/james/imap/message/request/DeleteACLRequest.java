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

import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.Tag;
import org.apache.james.imap.message.MailboxName;
import org.apache.james.mailbox.model.MailboxACL;

import com.google.common.base.MoreObjects;

/**
 * DELETEACL Request.
 */
public class DeleteACLRequest extends AbstractImapRequest {
    private final MailboxACL.EntryKey entryKey;
    private final MailboxName mailboxName;

    public DeleteACLRequest(Tag tag, MailboxName mailboxName, MailboxACL.EntryKey entryKey) {
        super(tag, ImapConstants.DELETEACL_COMMAND);
        this.mailboxName = mailboxName;
        this.entryKey = entryKey;
    }

    public MailboxACL.EntryKey getEntryKey() {
        return entryKey;
    }

    public MailboxName getMailboxName() {
        return mailboxName;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("mailboxName", mailboxName)
            .add("identifier", entryKey)
            .toString();
    }
}
