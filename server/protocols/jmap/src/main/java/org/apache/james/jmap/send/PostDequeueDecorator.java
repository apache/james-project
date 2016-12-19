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
package org.apache.james.jmap.send;

import java.io.Serializable;
import java.util.List;

import org.apache.james.jmap.exceptions.MailboxRoleNotFoundException;
import org.apache.james.jmap.model.mailbox.Role;
import org.apache.james.jmap.send.exception.MailShouldBeInOutboxException;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.FetchGroupImpl;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxMetaData;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MailboxQuery;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageId.Factory;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.queue.api.MailQueue.MailQueueException;
import org.apache.james.queue.api.MailQueue.MailQueueItem;
import org.apache.james.queue.api.MailQueueItemDecoratorFactory.MailQueueItemDecorator;
import org.apache.mailet.Mail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

public class PostDequeueDecorator extends MailQueueItemDecorator {
    private static final Logger LOG = LoggerFactory.getLogger(PostDequeueDecorator.class);

    private final MailboxManager mailboxManager;
    private final Factory messageIdFactory;
    private final MessageIdManager messageIdManager;

    public PostDequeueDecorator(MailQueueItem mailQueueItem,
            MailboxManager mailboxManager,
            MessageId.Factory messageIdFactory,
            MessageIdManager messageIdManager) {
        super(mailQueueItem);
        this.mailboxManager = mailboxManager;
        this.messageIdFactory = messageIdFactory;
        this.messageIdManager = messageIdManager;
    }

    @Override
    public Mail getMail() {
        return mailQueueItem.getMail();
    }

    @Override
    public void done(boolean success) throws MailQueueException {
        mailQueueItem.done(success);
        if (success && mandatoryJmapMetaDataIsPresent()) {
            MessageId messageId = messageIdFactory.fromString((String) getMail().getAttribute(MailMetadata.MAIL_METADATA_MESSAGE_ID_ATTRIBUTE));
            String username = (String) getMail().getAttribute(MailMetadata.MAIL_METADATA_USERNAME_ATTRIBUTE);
            try {
                MailboxSession mailboxSession = mailboxManager.createSystemSession(username, LOG);
                moveFromOutboxToSent(messageId, mailboxSession);
            } catch (MailboxException e) {
                throw new MailQueueException(e.getMessage(), e);
            }
        }
    }

    private boolean mandatoryJmapMetaDataIsPresent() {
        return checkMessageIdAttribute()
            && checkUsernameAttribute();
    }

    private boolean checkMessageIdAttribute() {
        Serializable messageId = getMail().getAttribute(MailMetadata.MAIL_METADATA_MESSAGE_ID_ATTRIBUTE);
        if (messageId == null || ! (messageId instanceof String)) {
            return false;
        }
        try {
            messageIdFactory.fromString((String) messageId);
        } catch (Exception e) {
            LOG.error("Invalid messageId: " + (String) messageId);
            return false;
        }
        return true;
    }

    private boolean checkUsernameAttribute() {
        Serializable username = getMail().getAttribute(MailMetadata.MAIL_METADATA_USERNAME_ATTRIBUTE);
        return (username != null && username instanceof String);
    }

    private void moveFromOutboxToSent(MessageId messageId, MailboxSession mailboxSession) throws MailQueueException, MailboxException {
        assertMessageBelongsToOutbox(messageId, mailboxSession);
        messageIdManager.setInMailboxes(messageId, ImmutableList.of(getSentMailboxId(mailboxSession)), mailboxSession);
    }

    private void assertMessageBelongsToOutbox(MessageId messageId, MailboxSession mailboxSession) throws MailboxException, MailShouldBeInOutboxException {
        MailboxId outboxMailboxId = getOutboxMailboxId(mailboxSession);
        List<MessageResult> messages = messageIdManager.getMessages(ImmutableList.of(messageId), FetchGroupImpl.MINIMAL, mailboxSession);
        for (MessageResult message: messages) {
            if (message.getMailboxId().equals(outboxMailboxId)) {
                return;
            }
        }
        throw new MailShouldBeInOutboxException(messageId);
    }

    private MailboxId getSentMailboxId(MailboxSession session) throws MailboxRoleNotFoundException, MailboxException {
        return getMailboxIdForRole(Role.SENT, session);
    }
    
    private MailboxId getOutboxMailboxId(MailboxSession session) throws MailboxRoleNotFoundException, MailboxException {
        return getMailboxIdForRole(Role.OUTBOX, session);
    }
    
    private MailboxId getMailboxIdForRole(Role role, MailboxSession session) throws MailboxRoleNotFoundException, MailboxException {
        MailboxQuery allUserMailboxesQuery = MailboxQuery.builder(session)
            .privateUserMailboxes()
            .build();
        return mailboxManager.search(allUserMailboxesQuery, session)
                .stream()
                .filter(meta -> hasRole(meta.getPath(), role))
                .map(MailboxMetaData::getId)
                .findFirst()
                .orElseThrow(() -> new MailboxRoleNotFoundException(role));
    }
    
    private boolean hasRole(MailboxPath mailBoxPath, Role role) {
        return Role.from(mailBoxPath.getName())
                .map(role::equals)
                .orElse(false);
    }
}
