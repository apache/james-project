/****************************************************************
O * Licensed to the Apache Software Foundation (ASF) under one   *
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

package org.apache.james.jmap.utils;

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
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.github.fge.lambdas.functions.ThrowingFunction;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;

public class MailboxUtils {

    private static final boolean DONT_RESET_RECENT = false;
    private static final Logger LOGGER = LoggerFactory.getLogger(MailboxUtils.class);

    private final MailboxManager mailboxManager;

    @Inject
    @VisibleForTesting
    public MailboxUtils(MailboxManager mailboxManager) {
        this.mailboxManager = mailboxManager;
    }

    public Optional<Mailbox> mailboxFromMailboxPath(MailboxPath mailboxPath, MailboxSession mailboxSession) {
        try {
            MessageManager mailbox = mailboxManager.getMailbox(mailboxPath, mailboxSession);
            return mailboxFromMessageManager(mailbox, mailboxSession);
        } catch (MailboxException e) {
            LOGGER.warn("Cannot find mailbox for: " + mailboxPath.getName(), e);
            return Optional.empty();
        }
    }

    private Optional<Mailbox> mailboxFromMessageManager(MessageManager messageManager, MailboxSession mailboxSession) throws MailboxException {
        MailboxPath mailboxPath = messageManager.getMailboxPath();
        Optional<Role> role = Role.from(mailboxPath.getName());
        MessageManager.MetaData mailboxMetaData = getMailboxMetaData(messageManager, mailboxSession);
        return Optional.ofNullable(Mailbox.builder()
                .id(messageManager.getId())
                .name(getName(mailboxPath, mailboxSession))
                .parentId(getParentIdFromMailboxPath(mailboxPath, mailboxSession).orElse(null))
                .role(role)
                .unreadMessages(mailboxMetaData.getUnseenCount())
                .totalMessages(mailboxMetaData.getMessageCount())
                .sortOrder(SortOrder.getSortOrder(role))
                .build());
    }

    private MessageManager.MetaData getMailboxMetaData(MessageManager messageManager, MailboxSession mailboxSession) throws MailboxException {
        return messageManager.getMetaData(DONT_RESET_RECENT, mailboxSession, MessageManager.MetaData.FetchGroup.UNSEEN_COUNT);
    }

    private MailboxId getMailboxId(MailboxPath mailboxPath, MailboxSession mailboxSession) throws MailboxException {
        return mailboxManager.getMailbox(mailboxPath, mailboxSession)
                .getId();
    }

    @VisibleForTesting String getName(MailboxPath mailboxPath, MailboxSession mailboxSession) {
        String name = mailboxPath.getName();
        if (name.contains(String.valueOf(mailboxSession.getPathDelimiter()))) {
            List<String> levels = Splitter.on(mailboxSession.getPathDelimiter()).splitToList(name);
            return levels.get(levels.size() - 1);
        }
        return name;
    }

    private Optional<MessageManager> getMailboxFromId(MailboxId mailboxId, MailboxSession mailboxSession) throws MailboxException {
        try {
            return Optional.of(mailboxManager.getMailbox(mailboxId, mailboxSession));
        } catch (MailboxNotFoundException e) {
            return Optional.empty();
        }
    }

    @VisibleForTesting Optional<MailboxId> getParentIdFromMailboxPath(MailboxPath mailboxPath, MailboxSession mailboxSession) throws MailboxException {
        List<MailboxPath> levels = mailboxPath.getHierarchyLevels(mailboxSession.getPathDelimiter());
        if (levels.size() <= 1) {
            return Optional.empty();
        }
        MailboxPath parent = levels.get(levels.size() - 2);
        return Optional.of(getMailboxId(parent, mailboxSession));
    }

    public Optional<Mailbox> mailboxFromMailboxId(MailboxId mailboxId, MailboxSession mailboxSession) {
        ThrowingFunction<MessageManager, Optional<Mailbox>> toMailbox = path -> mailboxFromMessageManager(path, mailboxSession);
        try {
            return getMailboxFromId(mailboxId, mailboxSession)
                .flatMap(Throwing.function(toMailbox).sneakyThrow());
        } catch (MailboxException e) {
            return Optional.empty();
        }
    }

    public boolean hasChildren(MailboxId mailboxId, MailboxSession mailboxSession) throws MailboxException {
        return getMailboxFromId(mailboxId, mailboxSession)
                .map(Throwing.function(MessageManager::getMailboxPath).sneakyThrow())
                .map(Throwing.function(path -> mailboxManager.hasChildren(path, mailboxSession)))
                .orElse(false);
    }
}
