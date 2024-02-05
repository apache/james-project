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

package org.apache.james.mailbox.postgres.mail;

import static org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageTable.ATTACHMENT_METADATA;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.mailbox.model.AttachmentMetadata;
import org.apache.james.mailbox.model.MessageAttachmentMetadata;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.apache.james.util.ReactorUtils;
import org.jooq.Record;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class AttachmentLoader {

    private final PostgresAttachmentMapper attachmentMapper;

    public AttachmentLoader(PostgresAttachmentMapper attachmentMapper) {
        this.attachmentMapper = attachmentMapper;
    }


    public Flux<Pair<SimpleMailboxMessage.Builder, Record>> addAttachmentToMessage(Flux<Pair<SimpleMailboxMessage.Builder, Record>> findMessagePublisher, MessageMapper.FetchType fetchType) {
        return findMessagePublisher.flatMapSequential(pair -> {
            if (fetchType == MessageMapper.FetchType.FULL || fetchType == MessageMapper.FetchType.ATTACHMENTS_METADATA) {
                return Mono.fromCallable(() -> pair.getRight().get(ATTACHMENT_METADATA))
                    .flatMapMany(Flux::fromIterable)
                    .flatMapSequential(attachmentRepresentation -> attachmentMapper.getAttachmentReactive(attachmentRepresentation.getAttachmentId())
                        .map(attachment -> constructMessageAttachment(attachment, attachmentRepresentation)))
                    .collectList()
                    .map(messageAttachmentMetadata -> {
                        pair.getLeft().addAttachments(messageAttachmentMetadata);
                        return pair;
                    }).switchIfEmpty(Mono.just(pair));
            } else {
                return Mono.just(pair);
            }
        }, ReactorUtils.DEFAULT_CONCURRENCY);
    }

    private MessageAttachmentMetadata constructMessageAttachment(AttachmentMetadata attachment, MessageRepresentation.AttachmentRepresentation messageAttachmentRepresentation) {
        return MessageAttachmentMetadata.builder()
            .attachment(attachment)
            .name(messageAttachmentRepresentation.getName().orElse(null))
            .cid(messageAttachmentRepresentation.getCid())
            .isInline(messageAttachmentRepresentation.isInline())
            .build();
    }
}
