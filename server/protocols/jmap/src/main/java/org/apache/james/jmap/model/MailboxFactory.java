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
package org.apache.james.jmap.model;

import java.util.List;
import java.util.Optional;

import javax.inject.Inject;

import org.apache.james.jmap.model.mailbox.Mailbox;
import org.apache.james.jmap.model.mailbox.Role;
import org.apache.james.jmap.model.mailbox.SortOrder;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxCounters;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxMetaData;
import org.apache.james.mailbox.model.MailboxPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;

public class MailboxFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(MailboxFactory.class);

    private final MailboxManager mailboxManager;

    @Inject
    public MailboxFactory(MailboxManager mailboxManager) {
        this.mailboxManager = mailboxManager;
    }
    
    public Optional<Mailbox> fromMailboxPath(MailboxPath mailboxPath, MailboxSession mailboxSession) {
        try {
            MessageManager mailbox = mailboxManager.getMailbox(mailboxPath, mailboxSession);
            return fromMessageManager(mailbox, mailboxSession);
        } catch (MailboxException e) {
            LOGGER.warn("Cannot find mailbox for: " + mailboxPath.getName(), e);
            return Optional.empty();
        }
    }

    public Optional<Mailbox> fromMailboxId(MailboxId mailboxId, MailboxSession mailboxSession) {
        try {
            MessageManager mailbox = mailboxManager.getMailbox(mailboxId, mailboxSession);
            return fromMessageManager(mailbox, mailboxSession);
        } catch (MailboxException e) {
            return Optional.empty();
        }
    }

    private Optional<Mailbox> fromMessageManager(MessageManager messageManager, MailboxSession mailboxSession) throws MailboxException {
        MailboxPath mailboxPath = messageManager.getMailboxPath();
        Optional<Role> role = Role.from(mailboxPath.getName());
        MailboxCounters mailboxCounters = messageManager.getMailboxCounters(mailboxSession);
        return Optional.ofNullable(Mailbox.builder()
                .id(messageManager.getId())
                .name(getName(mailboxPath, mailboxSession))
                .parentId(getParentIdFromMailboxPath(mailboxPath, mailboxSession).orElse(null))
                .role(role)
                .unreadMessages(mailboxCounters.getUnseen())
                .totalMessages(mailboxCounters.getCount())
                .sortOrder(SortOrder.getSortOrder(role))
                .build());
    }

    @VisibleForTesting String getName(MailboxPath mailboxPath, MailboxSession mailboxSession) {
        String name = mailboxPath.getName();
        if (name.contains(String.valueOf(mailboxSession.getPathDelimiter()))) {
            List<String> levels = Splitter.on(mailboxSession.getPathDelimiter()).splitToList(name);
            return levels.get(levels.size() - 1);
        }
        return name;
    }

    @VisibleForTesting Optional<MailboxId> getParentIdFromMailboxPath(MailboxPath mailboxPath, MailboxSession mailboxSession) throws MailboxException {
        List<MailboxPath> levels = mailboxPath.getHierarchyLevels(mailboxSession.getPathDelimiter());
        if (levels.size() <= 1) {
            return Optional.empty();
        }
        MailboxPath parent = levels.get(levels.size() - 2);
        return Optional.of(getMailboxId(parent, mailboxSession));
    }

    private MailboxId getMailboxId(MailboxPath mailboxPath, MailboxSession mailboxSession) throws MailboxException {
        return mailboxManager.getMailbox(mailboxPath, mailboxSession)
                .getId();
    }
}