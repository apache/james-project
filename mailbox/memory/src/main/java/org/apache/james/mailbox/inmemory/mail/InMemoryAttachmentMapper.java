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
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.io.IOUtils;
import org.apache.james.core.Username;
import org.apache.james.mailbox.exception.AttachmentNotFoundException;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.model.AttachmentMetadata;
import org.apache.james.mailbox.model.ContentType;
import org.apache.james.mailbox.model.MessageAttachmentMetadata;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.ParsedAttachment;
import org.apache.james.mailbox.store.mail.AttachmentMapper;

import com.github.fge.lambdas.Throwing;
import com.github.steveash.guavate.Guavate;
import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;

import reactor.core.publisher.Mono;

public class InMemoryAttachmentMapper implements AttachmentMapper {
    
    private static final int INITIAL_SIZE = 128;
    private final Map<AttachmentId, AttachmentMetadata> attachmentsById;
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
    public AttachmentMetadata getAttachment(AttachmentId attachmentId) throws AttachmentNotFoundException {
        Preconditions.checkArgument(attachmentId != null);
        if (!attachmentsById.containsKey(attachmentId)) {
            throw new AttachmentNotFoundException(attachmentId.getId());
        }
        return attachmentsById.get(attachmentId);
    }

    @Override
    public List<AttachmentMetadata> getAttachments(Collection<AttachmentId> attachmentIds) {
        Preconditions.checkArgument(attachmentIds != null);
        Builder<AttachmentMetadata> builder = ImmutableList.builder();
        for (AttachmentId attachmentId : attachmentIds) {
            if (attachmentsById.containsKey(attachmentId)) {
                builder.add(attachmentsById.get(attachmentId));
            }
        }
        return builder.build();
    }

    @Override
    public Mono<AttachmentMetadata> storeAttachmentForOwner(ContentType contentType, InputStream inputStream, Username owner) {
        return Mono.fromCallable(() -> {
            byte[] bytes = toByteArray(inputStream);
            AttachmentMetadata attachment = AttachmentMetadata.builder()
                .type(contentType)
                .attachmentId(AttachmentId.random())
                .size(bytes.length)
                .build();
            attachmentsById.put(attachment.getAttachmentId(), attachment);
            attachmentsRawContentById.put(attachment.getAttachmentId(), bytes);
            ownersByAttachmentId.put(attachment.getAttachmentId(), owner);
            return attachment;
        });
    }

    private byte[] toByteArray(InputStream inputStream) {
        try {
            return IOUtils.toByteArray(inputStream);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public List<MessageAttachmentMetadata> storeAttachmentsForMessage(Collection<ParsedAttachment> parsedAttachments, MessageId ownerMessageId) throws MailboxException {
        return parsedAttachments.stream()
            .map(Throwing.<ParsedAttachment, MessageAttachmentMetadata>function(
                typedContent -> storeAttachmentForMessage(ownerMessageId, typedContent))
                .sneakyThrow())
            .collect(Guavate.toImmutableList());
    }

    private MessageAttachmentMetadata storeAttachmentForMessage(MessageId ownerMessageId, ParsedAttachment parsedAttachment) throws MailboxException {
        AttachmentId attachmentId = AttachmentId.random();
        try {
            byte[] bytes = IOUtils.toByteArray(parsedAttachment.getContent().openStream());
            attachmentsById.put(attachmentId, AttachmentMetadata.builder()
                .attachmentId(attachmentId)
                .type(parsedAttachment.getContentType())
                .size(bytes.length)
                .build());
            attachmentsRawContentById.put(attachmentId, bytes);
            messageIdsByAttachmentId.put(attachmentId, ownerMessageId);
            return parsedAttachment.asMessageAttachment(attachmentId, bytes.length);
        } catch (IOException e) {
            throw new RuntimeException(e);
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