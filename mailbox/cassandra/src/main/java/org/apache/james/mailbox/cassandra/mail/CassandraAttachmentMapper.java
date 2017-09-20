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

package org.apache.james.mailbox.cassandra.mail;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.james.mailbox.cassandra.mail.CassandraAttachmentDAOV2.DAOAttachment;
import org.apache.james.mailbox.exception.AttachmentNotFoundException;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Attachment;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.mail.AttachmentMapper;
import org.apache.james.util.FluentFutureStream;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.steveash.guavate.Guavate;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

public class CassandraAttachmentMapper implements AttachmentMapper {
    private static final Logger LOGGER = LoggerFactory.getLogger(CassandraAttachmentMapper.class);

    private final CassandraAttachmentDAO attachmentDAO;
    private final CassandraAttachmentDAOV2 attachmentDAOV2;
    private final CassandraBlobsDAO blobsDAO;
    private final CassandraAttachmentMessageIdDAO attachmentMessageIdDAO;

    @Inject
    public CassandraAttachmentMapper(CassandraAttachmentDAO attachmentDAO, CassandraAttachmentDAOV2 attachmentDAOV2, CassandraBlobsDAO blobsDAO, CassandraAttachmentMessageIdDAO attachmentMessageIdDAO) {
        this.attachmentDAO = attachmentDAO;
        this.attachmentDAOV2 = attachmentDAOV2;
        this.blobsDAO = blobsDAO;
        this.attachmentMessageIdDAO = attachmentMessageIdDAO;
    }

    @Override
    public void endRequest() {
    }

    @Override
    public <T> T execute(Transaction<T> transaction) throws MailboxException {
        return transaction.run();
    }

    @Override
    public Attachment getAttachment(AttachmentId attachmentId) throws AttachmentNotFoundException {
        Preconditions.checkArgument(attachmentId != null);
        return getAttachmentInternal(attachmentId)
            .join()
            .orElseThrow(() -> new AttachmentNotFoundException(attachmentId.getId()));
    }

    @Nullable
    private CompletionStage<Optional<Attachment>> retrievePayload(Optional<DAOAttachment> daoAttachmentOptional) {
        if (!daoAttachmentOptional.isPresent()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        DAOAttachment daoAttachment = daoAttachmentOptional.get();
        return blobsDAO.read(daoAttachment.getBlobId())
            .thenApply(bytes -> Optional.of(daoAttachment.toAttachment(bytes)));
    }

    @Override
    public List<Attachment> getAttachments(Collection<AttachmentId> attachmentIds) {
        return getAttachmentsAsFuture(attachmentIds).join();
    }

    public CompletableFuture<ImmutableList<Attachment>> getAttachmentsAsFuture(Collection<AttachmentId> attachmentIds) {
        Preconditions.checkArgument(attachmentIds != null);

        Stream<CompletableFuture<Optional<Attachment>>> attachments = attachmentIds
                .stream()
                .distinct()
                .map(id -> getAttachmentInternal(id)
                    .thenApply(finalValue -> logNotFound(id, finalValue)));

        return FluentFutureStream
            .ofOptionals(attachments)
            .collect(Guavate.toImmutableList());
    }

    private CompletableFuture<Optional<Attachment>> getAttachmentInternal(AttachmentId id) {
        return attachmentDAOV2.getAttachment(id)
            .thenCompose(this::retrievePayload)
            .thenCompose(v2Value -> fallbackToV1(id, v2Value));
    }

    private CompletionStage<Optional<Attachment>> fallbackToV1(AttachmentId attachmentId, Optional<Attachment> v2Value) {
        if (v2Value.isPresent()) {
            return CompletableFuture.completedFuture(v2Value);
        }
        return attachmentDAO.getAttachment(attachmentId);
    }

    @Override
    public void storeAttachment(Attachment attachment) throws MailboxException {
        blobsDAO.save(attachment.getBytes())
            .thenApply(blobId -> CassandraAttachmentDAOV2.from(attachment, blobId))
            .thenCompose(attachmentDAOV2::storeAttachment)
            .join();
    }

    @Override
    public void storeAttachmentsForMessage(Collection<Attachment> attachments, MessageId ownerMessageId) throws MailboxException {
        FluentFutureStream.of(
            attachments.stream()
                .map(attachment -> storeAttachmentAsync(attachment, ownerMessageId)))
            .join();
    }

    @Override
    public Collection<MessageId> getRelatedMessageIds(AttachmentId attachmentId) throws MailboxException {
        return attachmentMessageIdDAO.getOwnerMessageIds(attachmentId)
            .join();
    }

    public CompletableFuture<Void> storeAttachmentAsync(Attachment attachment, MessageId ownerMessageId) {
        return blobsDAO.save(attachment.getBytes())
            .thenApply(blobId -> CassandraAttachmentDAOV2.from(attachment, blobId))
            .thenCompose(daoAttachment -> storeAttachmentWithIndex(daoAttachment, ownerMessageId));
    }

    private CompletableFuture<Void> storeAttachmentWithIndex(DAOAttachment daoAttachment, MessageId ownerMessageId) {
        return attachmentDAOV2.storeAttachment(daoAttachment)
                .thenCompose(any -> attachmentMessageIdDAO.storeAttachmentForMessageId(daoAttachment.getAttachmentId(), ownerMessageId));
    }

    private Optional<Attachment> logNotFound(AttachmentId attachmentId, Optional<Attachment> optionalAttachment) {
        if (!optionalAttachment.isPresent()) {
            LOGGER.warn("Failed retrieving attachment {}", attachmentId);
        }
        return optionalAttachment;
    }
}
