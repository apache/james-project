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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Stream;

import org.apache.james.mailbox.model.Attachment;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.model.MessageAttachment;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.apache.james.util.FluentFutureStream;
import org.apache.commons.lang3.tuple.Pair;

import com.github.steveash.guavate.Guavate;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class AttachmentLoader {

    private final CassandraAttachmentMapper attachmentMapper;

    public AttachmentLoader(CassandraAttachmentMapper attachmentMapper) {
        this.attachmentMapper = attachmentMapper;
    }

    public CompletableFuture<Stream<SimpleMailboxMessage>> addAttachmentToMessages(Stream<Pair<MessageWithoutAttachment,
            Stream<MessageAttachmentRepresentation>>> messageRepresentations, MessageMapper.FetchType fetchType) {

        if (fetchType == MessageMapper.FetchType.Body || fetchType == MessageMapper.FetchType.Full) {
            return FluentFutureStream.<SimpleMailboxMessage>of(
                messageRepresentations
                    .map(pair -> getAttachments(pair.getRight().collect(Guavate.toImmutableList()))
                        .thenApply(attachments -> pair.getLeft().toMailboxMessage(attachments))))
                .completableFuture();
        } else {
            return CompletableFuture.completedFuture(messageRepresentations
                .map(pair -> pair.getLeft()
                    .toMailboxMessage(ImmutableList.of())));
        }
    }

    @VisibleForTesting
    CompletableFuture<List<MessageAttachment>> getAttachments(List<MessageAttachmentRepresentation> attachmentRepresentations) {
        CompletableFuture<Map<AttachmentId, Attachment>> attachmentsByIdFuture =
            attachmentsById(attachmentRepresentations.stream()
                .map(MessageAttachmentRepresentation::getAttachmentId)
                .collect(Guavate.toImmutableSet()));

        return attachmentsByIdFuture.thenApply(attachmentsById ->
            attachmentRepresentations.stream()
                .map(representation -> constructMessageAttachment(attachmentsById.get(representation.getAttachmentId()), representation))
                .collect(Guavate.toImmutableList()));
    }

    private MessageAttachment constructMessageAttachment(Attachment attachment, MessageAttachmentRepresentation messageAttachmentRepresentation) {
        return MessageAttachment.builder()
                .attachment(attachment)
                .name(messageAttachmentRepresentation.getName().orElse(null))
                .cid(messageAttachmentRepresentation.getCid())
                .isInline(messageAttachmentRepresentation.isInline())
                .build();
    }

    @VisibleForTesting
    CompletableFuture<Map<AttachmentId, Attachment>> attachmentsById(Set<AttachmentId> attachmentIds) {
        if (attachmentIds.isEmpty()) {
            return CompletableFuture.completedFuture(ImmutableMap.of());
        }
        return attachmentMapper.getAttachmentsAsFuture(attachmentIds)
            .thenApply(attachments -> attachments
                .stream()
                .collect(Guavate.toImmutableMap(Attachment::getAttachmentId, Function.identity())));
    }

}
