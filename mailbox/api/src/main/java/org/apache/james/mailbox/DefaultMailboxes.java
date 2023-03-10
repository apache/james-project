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
package org.apache.james.mailbox;

import java.util.List;

import org.apache.james.core.Username;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxPath;

import com.google.common.collect.ImmutableList;

public interface DefaultMailboxes {

    String INBOX = MailboxConstants.INBOX;
    String OUTBOX = "Outbox";
    String SENT = "Sent";
    String TRASH = "Trash";
    String DRAFTS = "Drafts";
    String ARCHIVE = "Archive";
    String SPAM = "Spam";
    String TEMPLATES = "Templates";
    String RESTORED_MESSAGES = "Restored-Messages";

    List<String> DEFAULT_MAILBOXES = ImmutableList.of(INBOX, OUTBOX, SENT, TRASH, DRAFTS, ARCHIVE, SPAM);

    static List<MailboxPath> defaultMailboxesAsPath(Username username) {
        return DEFAULT_MAILBOXES.stream()
            .map(s -> MailboxPath.forUser(username, s))
            .collect(ImmutableList.toImmutableList());
    }
}
