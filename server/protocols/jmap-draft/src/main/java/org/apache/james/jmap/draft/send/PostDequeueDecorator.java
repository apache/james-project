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
package org.apache.james.jmap.draft.send;

import java.util.List;
import java.util.Optional;

import javax.mail.Flags;
import javax.mail.Flags.Flag;

import org.apache.james.core.Username;
import org.apache.james.jmap.draft.send.exception.MailShouldBeInOutboxException;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.Role;
import org.apache.james.mailbox.SystemMailboxesProvider;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxRoleNotFoundException;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageId.Factory;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.queue.api.MailQueue.MailQueueException;
import org.apache.james.queue.api.MailQueue.MailQueueItem;
import org.apache.james.queue.api.MailQueueItemDecoratorFactory.MailQueueItemDecorator;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeUtils;
import org.apache.mailet.Mail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

public class PostDequeueDecorator extends MailQueueItemDecorator {
    private static final Logger LOG = LoggerFactory.getLogger(PostDequeueDecorator.class);
    private static final Attribute IS_DELIVERED = Attribute.convertToAttribute("DELIVERED", "DELIVERED");

    private final MailboxManager mailboxManager;
    private final Factory messageIdFactory;
    private final MessageIdManager messageIdManager;
    private final SystemMailboxesProvider systemMailboxesProvider;

    public PostDequeueDecorator(MailQueueItem mailQueueItem,
                                MailboxManager mailboxManager,
                                Factory messageIdFactory,
                                MessageIdManager messageIdManager,
                                SystemMailboxesProvider systemMailboxesProvider) {
        super(mailQueueItem);
        this.mailboxManager = mailboxManager;
        this.messageIdFactory = messageIdFactory;
        this.messageIdManager = messageIdManager;
        this.systemMailboxesProvider = systemMailboxesProvider;
    }

    @Override
    public Mail getMail() {
        return mailQueueItem.getMail();
    }

    @Override
    public void done(boolean success) throws MailQueueException {
        mailQueueItem.done(success);
        if (success && mandatoryJmapMetaDataIsPresent()) {
            Optional<?> optionalRawMessageId = retrieveMessageId();
            MessageId messageId = messageIdFactory.fromString((String) optionalRawMessageId.get());
            Optional<String> username = retrieveUsername();
            if (!getMail().getAttribute(IS_DELIVERED.getName()).isPresent()) {
                try {
                    MailboxSession mailboxSession = mailboxManager.createSystemSession(Username.of(username.get()));
                    moveFromOutboxToSentWithSeenFlag(messageId, mailboxSession);
                    getMail().setAttribute(IS_DELIVERED);
                } catch (MailShouldBeInOutboxException e) {
                    LOG.info("Message does not exist on Outbox anymore, it could have already been sent", e);
                } catch (MailboxException e) {
                    throw new MailQueueException(e.getMessage(), e);
                }
            }
        }
    }

    private Optional<?> retrieveMessageId() {
        return AttributeUtils.getAttributeValueFromMail(getMail(), MailMetadata.MAIL_METADATA_MESSAGE_ID_ATTRIBUTE);
    }

    private Optional<String> retrieveUsername() {
        return AttributeUtils.getValueAndCastFromMail(getMail(), MailMetadata.MAIL_METADATA_USERNAME_ATTRIBUTE, String.class);
    }

    private boolean mandatoryJmapMetaDataIsPresent() {
        return checkMessageIdAttribute()
            && checkUsernameAttribute();
    }

    private boolean checkMessageIdAttribute() {
        return retrieveMessageId()
            .map(this::validateMessageId)
            .orElse(false);
    }

    private boolean validateMessageId(Object messageId) {
        if (messageId instanceof String) {
            try {
                messageIdFactory.fromString((String) messageId);
                return true;
            } catch (Exception e) {
                LOG.error("Invalid messageId: {}", messageId, e);
            }
        }

        LOG.error("Non-String messageId {} has type {}", messageId, messageId.getClass());
        return false;
    }

    private boolean checkUsernameAttribute() {
        return retrieveUsername().isPresent();
    }

    private void moveFromOutboxToSentWithSeenFlag(MessageId messageId, MailboxSession mailboxSession) throws MailQueueException, MailboxException {
        assertMessageBelongsToOutbox(messageId, mailboxSession);
        MailboxId sentMailboxId = getSentMailboxId(mailboxSession);
        messageIdManager.setInMailboxes(messageId, ImmutableList.of(sentMailboxId), mailboxSession);
        messageIdManager.setFlags(new Flags(Flag.SEEN), MessageManager.FlagsUpdateMode.ADD, messageId, ImmutableList.of(sentMailboxId), mailboxSession);
    }

    private void assertMessageBelongsToOutbox(MessageId messageId, MailboxSession mailboxSession) throws MailboxException, MailShouldBeInOutboxException {
        MailboxId outboxMailboxId = getOutboxMailboxId(mailboxSession);
        List<MessageResult> messages = messageIdManager.getMessage(messageId, FetchGroup.MINIMAL, mailboxSession);
        for (MessageResult message: messages) {
            if (message.getMailboxId().equals(outboxMailboxId)) {
                return;
            }
        }
        throw new MailShouldBeInOutboxException(messageId);
    }

    private MailboxId getSentMailboxId(MailboxSession session) throws MailboxRoleNotFoundException, MailboxException {
        return systemMailboxesProvider.findMailbox(Role.SENT, session.getUser())
            .getId();
    }
    
    private MailboxId getOutboxMailboxId(MailboxSession session) throws MailboxRoleNotFoundException, MailboxException {
        return systemMailboxesProvider.findMailbox(Role.OUTBOX, session.getUser())
            .getId();
    }
    
}
