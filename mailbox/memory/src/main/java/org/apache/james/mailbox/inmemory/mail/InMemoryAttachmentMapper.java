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
package org.apache.james.mailbox.inmemory.mail;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.james.core.Username;
import org.apache.james.mailbox.exception.AttachmentNotFoundException;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Attachment;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.mail.AttachmentMapper;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;

import reactor.core.publisher.Mono;

public class InMemoryAttachmentMapper implements AttachmentMapper {
    
    private static final int INITIAL_SIZE = 128;
    private final Map<AttachmentId, Attachment> attachmentsById;
    private final Map<AttachmentId, byte[]> attachmentsRawContentById;
    private final Multimap<AttachmentId, MessageId> messageIdsByAttachmentId;
    private final Multimap<AttachmentId, Username> ownersByAttachmentId;

    public InMemoryAttachmentMapper() {
        attachmentsById = new ConcurrentHashMap<>(INITIAL_SIZE);
        attachmentsRawContentById = new ConcurrentHashMap<>(INITIAL_SIZE);
        messageIdsByAttachmentId = Multimaps.synchronizedSetMultimap(HashMultimap.create());
        ownersByAttachmentId = Multimaps.synchronizedSetMultimap(HashMultimap.create());
    }

    @Override
    public Attachment getAttachment(AttachmentId attachmentId) throws AttachmentNotFoundException {
        Preconditions.checkArgument(attachmentId != null);
        if (!attachmentsById.containsKey(attachmentId)) {
            throw new AttachmentNotFoundException(attachmentId.getId());
        }
        return attachmentsById.get(attachmentId);
    }

    @Override
    public List<Attachment> getAttachments(Collection<AttachmentId> attachmentIds) {
        Preconditions.checkArgument(attachmentIds != null);
        Builder<Attachment> builder = ImmutableList.builder();
        for (AttachmentId attachmentId : attachmentIds) {
            if (attachmentsById.containsKey(attachmentId)) {
                builder.add(attachmentsById.get(attachmentId));
            }
        }
        return builder.build();
    }

    @Override
    public Mono<Void> storeAttachmentForOwner(Attachment attachment, Username owner) {
        return Mono.fromRunnable(() -> {
            attachmentsById.put(attachment.getAttachmentId(), attachment);
            attachmentsRawContentById.put(attachment.getAttachmentId(), attachment.getBytes());
            ownersByAttachmentId.put(attachment.getAttachmentId(), owner);
        });
    }

    @Override
    public void endRequest() {
        // Do nothing
    }

    @Override
    public <T> T execute(Transaction<T> transaction) throws MailboxException {
        return transaction.run();
    }

    @Override
    public void storeAttachmentsForMessage(Collection<Attachment> attachments, MessageId ownerMessageId) throws MailboxException {
        for (Attachment attachment: attachments) {
            attachmentsById.put(attachment.getAttachmentId(), attachment);
            attachmentsRawContentById.put(attachment.getAttachmentId(), attachment.getBytes());
            messageIdsByAttachmentId.put(attachment.getAttachmentId(), ownerMessageId);
        }
    }

    @Override
    public Collection<MessageId> getRelatedMessageIds(AttachmentId attachmentId) throws MailboxException {
        return messageIdsByAttachmentId.get(attachmentId);
    }

    @Override
    public Collection<Username> getOwners(final AttachmentId attachmentId) throws MailboxException {
        return ownersByAttachmentId.get(attachmentId);
    }

    @Override
    public InputStream loadAttachmentContent(AttachmentId attachmentId) throws AttachmentNotFoundException, IOException {
        byte[] buf = attachmentsRawContentById.get(attachmentId);
        if (buf == null) {
            throw new AttachmentNotFoundException(attachmentId.toString());
        }
        return new ByteArrayInputStream(buf);
    }
}