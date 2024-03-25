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

import static org.apache.james.blob.api.BlobStore.StoragePolicy.SIZE_BASED;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageTable.ATTACHMENT_METADATA;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageTable.BODY_BLOB_ID;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageTable.HEADER_CONTENT;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.model.AttachmentMetadata;
import org.apache.james.mailbox.model.Content;
import org.apache.james.mailbox.model.HeaderAndBodyByteContent;
import org.apache.james.mailbox.model.MessageAttachmentMetadata;
import org.apache.james.mailbox.postgres.mail.dto.AttachmentsDTO;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.apache.james.util.ReactorUtils;
import org.jooq.Record;

import com.google.common.collect.ImmutableMap;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class PostgresMessageRetriever {

    interface PartRetriever {

        boolean isApplicable(MessageMapper.FetchType fetchType);

        Flux<Pair<SimpleMailboxMessage.Builder, Record>> doRetrieve(Flux<Pair<SimpleMailboxMessage.Builder, Record>> chain);
    }

    class AttachmentPartRetriever implements PartRetriever {

        @Override
        public boolean isApplicable(MessageMapper.FetchType fetchType) {
            return fetchType == MessageMapper.FetchType.FULL || fetchType == MessageMapper.FetchType.ATTACHMENTS_METADATA;
        }

        @Override
        public Flux<Pair<SimpleMailboxMessage.Builder, Record>> doRetrieve(Flux<Pair<SimpleMailboxMessage.Builder, Record>> chain) {
            return chain
                .flatMapSequential(pair -> Mono.fromCallable(() -> toMap(pair.getRight().get(ATTACHMENT_METADATA)))
                    .flatMap(this::getAttachments)
                    .map(messageAttachmentMetadata -> {
                        pair.getLeft().addAttachments(messageAttachmentMetadata);
                        return pair;
                    }).switchIfEmpty(Mono.just(pair)), ReactorUtils.DEFAULT_CONCURRENCY);
        }

        private Map<AttachmentId, MessageRepresentation.AttachmentRepresentation> toMap(AttachmentsDTO attachmentRepresentations) {
            return attachmentRepresentations.stream().collect(ImmutableMap.toImmutableMap(MessageRepresentation.AttachmentRepresentation::getAttachmentId, obj -> obj));
        }

        private Mono<List<MessageAttachmentMetadata>> getAttachments(Map<AttachmentId, MessageRepresentation.AttachmentRepresentation> mapAttachmentIdToAttachmentRepresentation) {
            return Mono.fromCallable(mapAttachmentIdToAttachmentRepresentation::keySet)
                .flatMapMany(attachmentMapper::getAttachmentsReactive)
                .map(attachmentMetadata -> constructMessageAttachment(attachmentMetadata, mapAttachmentIdToAttachmentRepresentation.get(attachmentMetadata.getAttachmentId())))
                .collectList();
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

    class BlobContentPartRetriever implements PartRetriever {

        @Override
        public boolean isApplicable(MessageMapper.FetchType fetchType) {
            return fetchType == MessageMapper.FetchType.FULL;
        }

        @Override
        public Flux<Pair<SimpleMailboxMessage.Builder, Record>> doRetrieve(Flux<Pair<SimpleMailboxMessage.Builder, Record>> chain) {
            return chain
                .flatMapSequential(pair -> retrieveFullContent(pair.getRight())
                    .map(headerAndBodyContent -> Pair.of(pair.getLeft().content(headerAndBodyContent), pair.getRight())),
                    ReactorUtils.DEFAULT_CONCURRENCY);
        }

        private Mono<Content> retrieveFullContent(Record messageRecord) {
            return Mono.from(blobStore.readBytes(blobStore.getDefaultBucketName(),
                    blobIdFactory.from(messageRecord.get(BODY_BLOB_ID)),
                    SIZE_BASED))
                .map(bodyBytes -> new HeaderAndBodyByteContent(messageRecord.get(HEADER_CONTENT), bodyBytes));
        }
    }

    private final BlobStore blobStore;
    private final BlobId.Factory blobIdFactory;
    private final PostgresAttachmentMapper attachmentMapper;
    private final List<PartRetriever> partRetrievers = List.of(new AttachmentPartRetriever(), new BlobContentPartRetriever());

    public PostgresMessageRetriever(BlobStore blobStore,
                                    BlobId.Factory blobIdFactory,
                                    PostgresAttachmentMapper attachmentMapper) {
        this.blobStore = blobStore;
        this.blobIdFactory = blobIdFactory;
        this.attachmentMapper = attachmentMapper;
    }

    public Flux<MailboxMessage> get(MessageMapper.FetchType fetchType, Flux<Pair<SimpleMailboxMessage.Builder, Record>> initialFlux) {
        return Flux.fromIterable(partRetrievers)
            .filter(partRetriever -> partRetriever.isApplicable(fetchType))
            .reduce(initialFlux, (flux, partRetriever) -> partRetriever.doRetrieve(flux))
            .flatMapMany(flux -> flux)
            .map(pair -> pair.getLeft().build());
    }
}
