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

package org.apache.james.mailbox.store;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;

import jakarta.inject.Inject;

import org.apache.james.mailbox.AttachmentManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.exception.AttachmentNotFoundException;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.model.AttachmentMetadata;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.mail.AttachmentMapperFactory;

import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Mono;

public class StoreAttachmentManager implements AttachmentManager {
    private final AttachmentMapperFactory attachmentMapperFactory;
    private final MessageIdManager messageIdManager;

    @Inject
    public StoreAttachmentManager(AttachmentMapperFactory attachmentMapperFactory, MessageIdManager messageIdManager) {
        this.attachmentMapperFactory = attachmentMapperFactory;
        this.messageIdManager = messageIdManager;
    }

    @Override
    public boolean exists(AttachmentId attachmentId, MailboxSession session) throws MailboxException {
        AttachmentMetadata attachment = attachmentMapperFactory.getAttachmentMapper(session)
            .getAttachment(attachmentId);
        return exists(attachment, session);
    }

    public Mono<Boolean> existsReactive(AttachmentId attachmentId, MailboxSession session) {
        return attachmentMapperFactory.getAttachmentMapper(session)
            .getAttachmentReactive(attachmentId)
            .flatMap(attachment -> existsReactive(attachment, session));
    }

    public boolean exists(AttachmentMetadata attachment, MailboxSession session) throws MailboxException {
        return !messageIdManager.accessibleMessages(ImmutableList.of(attachment.getMessageId()), session).isEmpty();
    }

    public Mono<Boolean> existsReactive(AttachmentMetadata attachment, MailboxSession session) {
        return Mono.from(messageIdManager.accessibleMessagesReactive(ImmutableList.of(attachment.getMessageId()), session))
            .map(accessibleMessages -> !accessibleMessages.isEmpty());
    }

    @Override
    public AttachmentMetadata getAttachment(AttachmentId attachmentId, MailboxSession mailboxSession) throws MailboxException, AttachmentNotFoundException {
        AttachmentMetadata attachment = attachmentMapperFactory.getAttachmentMapper(mailboxSession).getAttachment(attachmentId);
        if (!exists(attachment, mailboxSession)) {
            throw new AttachmentNotFoundException(attachmentId.getId());
        }
        return attachment;
    }

    @Override
    public List<AttachmentMetadata> getAttachments(List<AttachmentId> attachmentIds, MailboxSession mailboxSession) throws MailboxException {
        List<AttachmentMetadata> attachments = attachmentMapperFactory.getAttachmentMapper(mailboxSession)
            .getAttachments(attachmentIds);

        Set<MessageId> accessibleMessageIds = messageIdManager.accessibleMessages(attachments.stream()
            .map(AttachmentMetadata::getMessageId)
            .collect(ImmutableList.toImmutableList()),
            mailboxSession);

        return attachments.stream()
            .filter(entry -> accessibleMessageIds.contains(entry.getMessageId()))
            .collect(ImmutableList.toImmutableList());
    }

    @Override
    public InputStream loadAttachmentContent(AttachmentId attachmentId, MailboxSession mailboxSession) throws AttachmentNotFoundException, IOException {
        try {
            if (!exists(attachmentId, mailboxSession)) {
                throw new AttachmentNotFoundException(attachmentId.getId());
            }
        } catch (MailboxException e) {
            throw new RuntimeException(e);
        }
        return attachmentMapperFactory.getAttachmentMapper(mailboxSession).loadAttachmentContent(attachmentId);
    }

    @Override
    public Mono<InputStream> loadAttachmentContentReactive(AttachmentId attachmentId, MailboxSession mailboxSession) {
        return existsReactive(attachmentId, mailboxSession)
            .flatMap(exist -> {
                    if (!exist) {
                        return Mono.error(new AttachmentNotFoundException(attachmentId.getId()));
                    }
                    return attachmentMapperFactory.getAttachmentMapper(mailboxSession).loadAttachmentContentReactive(attachmentId);
                });
    }
}
