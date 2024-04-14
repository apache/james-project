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

package org.apache.james.imap.api.process;

import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.james.mailbox.Role;
import org.apache.james.mailbox.model.MailboxPath;

import com.google.common.collect.ImmutableMap;

public class DefaultMailboxTyper implements MailboxTyper {
    private static final ImmutableMap<Role, MailboxType> ROLES_TO_MAILBOX_TYPE = ImmutableMap.of(
        Role.INBOX, MailboxType.INBOX,
        Role.SENT, MailboxType.SENT,
        Role.ARCHIVE, MailboxType.ARCHIVE,
        Role.SPAM, MailboxType.SPAM,
        Role.DRAFTS, MailboxType.DRAFTS,
        Role.TRASH, MailboxType.TRASH);

    @Inject
    public DefaultMailboxTyper() {
    }

    @Override
    public MailboxType getMailboxType(ImapSession session, MailboxPath path) {
        return Role.from(path.getName())
            .flatMap(this::asMailboxType)
            .orElse(MailboxType.OTHER);
    }

    private Optional<MailboxType> asMailboxType(Role role) {
        return Optional.ofNullable(ROLES_TO_MAILBOX_TYPE.get(role));
    }
}
