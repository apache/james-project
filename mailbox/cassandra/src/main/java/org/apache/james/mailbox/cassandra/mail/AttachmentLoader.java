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
import org.apache.james.mailbox.model.AttachmentMetadata;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.mailbox.model.MessageAttachmentMetadata;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;

import com.github.steveash.guavate.Guavate;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class AttachmentLoader {

    private final CassandraAttachmentMapper attachmentMapper;

    public AttachmentLoader(CassandraAttachmentMapper attachmentMapper) {
        this.attachmentMapper = attachmentMapper;
    }

    public Mono<MailboxMessage> addAttachmentToMessage(Pair<ComposedMessageIdWithMetaData, MessageRepresentation> messageRepresentation,
                                                       MessageMapper.FetchType fetchType) {
        return loadAttachments(messageRepresentation.getRight().getAttachments().stream(), fetchType)
            .map(attachments -> messageRepresentation.getRight().toMailboxMessage(messageRepresentation.getLeft(), attachments));
    }

    private Mono<List<MessageAttachmentMetadata>> loadAttachments(Stream<MessageAttachmentRepresentation> messageAttachmentRepresentations, MessageMapper.FetchType fetchType) {
        if (fetchType == MessageMapper.FetchType.Body || fetchType == MessageMapper.FetchType.Full) {
            return getAttachments(messageAttachmentRepresentations.collect(Guavate.toImmutableList()));
        } else {
            return Mono.just(ImmutableList.of());
        }
    }

    @VisibleForTesting
    Mono<List<MessageAttachmentMetadata>> getAttachments(List<MessageAttachmentRepresentation> attachmentRepresentations) {
        return Flux.fromIterable(attachmentRepresentations)
                .flatMapSequential(attachmentRepresentation ->
                        attachmentMapper.getAttachmentsAsMono(attachmentRepresentation.getAttachmentId())
                            .map(attachment -> constructMessageAttachment(attachment, attachmentRepresentation)))
                .collect(Guavate.toImmutableList());
    }

    private MessageAttachmentMetadata constructMessageAttachment(AttachmentMetadata attachment, MessageAttachmentRepresentation messageAttachmentRepresentation) {
        return MessageAttachmentMetadata.builder()
                .attachment(attachment)
                .name(messageAttachmentRepresentation.getName().orElse(null))
                .cid(messageAttachmentRepresentation.getCid())
                .isInline(messageAttachmentRepresentation.isInline())
                .build();
    }

}
