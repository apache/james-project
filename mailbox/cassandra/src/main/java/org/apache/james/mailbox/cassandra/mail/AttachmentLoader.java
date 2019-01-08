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
import java.util.stream.Stream;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.mailbox.model.Attachment;
import org.apache.james.mailbox.model.MessageAttachment;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class AttachmentLoader {

    private final CassandraAttachmentMapper attachmentMapper;

    public AttachmentLoader(CassandraAttachmentMapper attachmentMapper) {
        this.attachmentMapper = attachmentMapper;
    }

    public Mono<SimpleMailboxMessage> addAttachmentToMessage(Pair<MessageWithoutAttachment, Stream<MessageAttachmentRepresentation>> messageRepresentation, MessageMapper.FetchType fetchType) {

        if (fetchType == MessageMapper.FetchType.Body || fetchType == MessageMapper.FetchType.Full) {
            return getAttachments(messageRepresentation.getRight().collect(ImmutableList.toImmutableList()))
                        .map(attachments -> messageRepresentation.getLeft().toMailboxMessage(attachments));
        } else {
            return Mono.just(messageRepresentation.getLeft().toMailboxMessage(ImmutableList.of()));
        }
    }

    @VisibleForTesting
    Mono<List<MessageAttachment>> getAttachments(List<MessageAttachmentRepresentation> attachmentRepresentations) {
        return Flux.fromIterable(attachmentRepresentations)
                .flatMap(attachmentRepresentation ->
                        attachmentMapper.getAttachmentsAsMono(attachmentRepresentation.getAttachmentId())
                            .map(attachment -> constructMessageAttachment(attachment, attachmentRepresentation)))
                .collectList();
    }

    private MessageAttachment constructMessageAttachment(Attachment attachment, MessageAttachmentRepresentation messageAttachmentRepresentation) {
        return MessageAttachment.builder()
                .attachment(attachment)
                .name(messageAttachmentRepresentation.getName().orElse(null))
                .cid(messageAttachmentRepresentation.getCid())
                .isInline(messageAttachmentRepresentation.isInline())
                .build();
    }

}
