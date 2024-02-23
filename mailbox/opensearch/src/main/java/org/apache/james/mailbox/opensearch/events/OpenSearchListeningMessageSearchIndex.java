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
package org.apache.james.mailbox.opensearch.events;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static org.apache.james.mailbox.opensearch.json.JsonMessageConstants.IS_ANSWERED;
import static org.apache.james.mailbox.opensearch.json.JsonMessageConstants.IS_DELETED;
import static org.apache.james.mailbox.opensearch.json.JsonMessageConstants.IS_DRAFT;
import static org.apache.james.mailbox.opensearch.json.JsonMessageConstants.IS_FLAGGED;
import static org.apache.james.mailbox.opensearch.json.JsonMessageConstants.IS_RECENT;
import static org.apache.james.mailbox.opensearch.json.JsonMessageConstants.IS_UNREAD;
import static org.apache.james.mailbox.opensearch.json.JsonMessageConstants.MAILBOX_ID;
import static org.apache.james.mailbox.opensearch.json.JsonMessageConstants.MESSAGE_ID;
import static org.apache.james.mailbox.opensearch.json.JsonMessageConstants.UID;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;
import javax.mail.Flags;

import org.apache.james.backends.opensearch.DocumentId;
import org.apache.james.backends.opensearch.OpenSearchIndexer;
import org.apache.james.backends.opensearch.RoutingKey;
import org.apache.james.backends.opensearch.UpdatedRepresentation;
import org.apache.james.events.Event;
import org.apache.james.events.Group;
import org.apache.james.mailbox.FlagsBuilder;
import org.apache.james.mailbox.MailboxManager.MessageCapabilities;
import org.apache.james.mailbox.MailboxManager.SearchCapabilities;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.SessionProvider;
import org.apache.james.mailbox.events.MailboxEvents;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.opensearch.IndexBody;
import org.apache.james.mailbox.opensearch.MailboxOpenSearchConstants;
import org.apache.james.mailbox.opensearch.OpenSearchMailboxConfiguration;
import org.apache.james.mailbox.opensearch.json.MessageToOpenSearchJson;
import org.apache.james.mailbox.opensearch.search.OpenSearchSearcher;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.mail.MessageMapper.FetchType;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.search.ListeningMessageSearchIndex;
import org.apache.james.metrics.api.Metric;
import org.apache.james.metrics.api.MetricFactory;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.TermQuery;
import org.opensearch.client.opensearch.core.GetResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.fge.lambdas.Throwing;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SynchronousSink;
import reactor.core.scheduler.Schedulers;

public class OpenSearchListeningMessageSearchIndex extends ListeningMessageSearchIndex {

    static class OpenSearchNotFoundException extends RuntimeException {
        public OpenSearchNotFoundException(String message) {
            super(message);
        }
    }

    interface IndexingStrategy {
        Mono<Void> handleExpungedEvent(MailboxEvents.Expunged expunged, MailboxSession session, MailboxId mailboxId);

        Mono<Void> handleAddedEvent(MailboxSession session, MailboxEvents.Added addedEvent, MailboxId mailboxId);
    }

    class NaiveIndexingStrategy implements IndexingStrategy {

        @Override
        public Mono<Void> handleExpungedEvent(MailboxEvents.Expunged expunged, MailboxSession session, MailboxId mailboxId) {
            return delete(session, expunged.getMailboxId(), expunged.getUids());
        }

        @Override
        public Mono<Void> handleAddedEvent(MailboxSession session, MailboxEvents.Added addedEvent, MailboxId mailboxId) {
            return processAddedEvent(session, addedEvent, mailboxId);
        }
    }


    class OptimizedIndexingStrategy implements IndexingStrategy {

        @Override
        public Mono<Void> handleExpungedEvent(MailboxEvents.Expunged expunged, MailboxSession session, MailboxId mailboxId) {
            return Mono.just(expunged)
                .filter(MailboxEvents.Expunged::isMoved)
                .flatMap(expungedEvent -> processExpunged(session, mailboxId, expungedEvent).thenReturn(expunged))
                .switchIfEmpty(Mono.defer(() -> delete(session, mailboxId, expunged.getUids())).thenReturn(expunged))
                .then();
        }

        @Override
        public Mono<Void> handleAddedEvent(MailboxSession session, MailboxEvents.Added addedEvent, MailboxId mailboxId) {
            return Mono.just(addedEvent)
                .filter(event -> !event.isMoved())
                .flatMap(added -> processAddedEvent(session, added, mailboxId));
        }

        private Mono<Void> processExpunged(MailboxSession session, MailboxId mailboxId, MailboxEvents.Expunged expungedEvent) {
            return Mono.justOrEmpty(expungedEvent.movedToMailboxId())
                .flatMap(movedToMailboxId -> Flux.fromIterable(expungedEvent.getExpunged().values())
                    .concatMap(oldMessageData -> handleExpungedMessage(oldMessageData, mailboxId, movedToMailboxId, session))
                    .then(Mono.defer(() -> delete(session, mailboxId, expungedEvent.getUids()))));
        }

        private Mono<Void> handleExpungedMessage(MessageMetaData expungedMessageMetaData,
                                                 MailboxId movedFromMailboxId,
                                                 MailboxId movedToMailboxId,
                                                 MailboxSession session) {
            return newIndexByIndexedDocument(expungedMessageMetaData.getUid(), movedFromMailboxId, movedToMailboxId, session, expungedMessageMetaData.getMessageId())
                .onErrorResume(OpenSearchNotFoundException.class,
                    notFoundException -> {
                        LOGGER.warn("Can not find message {} in mailbox {} for reindexing",
                            expungedMessageMetaData.getUid(),
                            movedFromMailboxId.serialize());
                        return notFoundHandleFallBack(expungedMessageMetaData, movedToMailboxId, session);
                    });
        }

        private Mono<Void> newIndexByIndexedDocument(MessageUid oldMessageUid,
                                                     MailboxId movedFromMailboxId,
                                                     MailboxId movedToMailboxId,
                                                     MailboxSession session,
                                                     MessageId messageId) {
            RoutingKey movedFromRoutingKey = routingKeyFactory.from(movedFromMailboxId);
            return openSearchIndexer.get(indexIdFor(movedFromMailboxId, oldMessageUid), movedFromRoutingKey)
                .filter(GetResponse::found)
                .switchIfEmpty(Mono.error(() -> new OpenSearchNotFoundException("Can not find message " + oldMessageUid + " in mailbox " + movedFromMailboxId.serialize())))
                .mapNotNull(GetResponse::source)
                .flatMap(document -> updateDocumentThenIndex(messageId, movedToMailboxId, document, session));
        }

        private Mono<Void> notFoundHandleFallBack(MessageMetaData expungedMessageMetaData, MailboxId movedToMailboxId, MailboxSession session) {
            return retrieveMailboxMessage(session, expungedMessageMetaData.getMessageId(), movedToMailboxId, FetchType.FULL)
                .publishOn(Schedulers.parallel())
                .flatMap(mailboxMessage -> factory.getMailboxMapper(session)
                    .findMailboxById(movedToMailboxId)
                    .flatMap(mailbox -> add(session, mailbox, mailboxMessage))
                    .doOnSuccess(any -> reIndexNotFoundMetric.increment()));
        }

        private Mono<Void> updateDocumentThenIndex(MessageId messageId,
                                                   MailboxId movedToMailboxId,
                                                   ObjectNode origin,
                                                   MailboxSession session) {
            return retrieveMailboxMessage(session, messageId, movedToMailboxId, FetchType.METADATA)
                .flatMap(mailboxMessage -> Mono.fromCallable(() -> {
                        messageToOpenSearchJson.updateMessageUid(origin, mailboxMessage.getUid());
                        messageToOpenSearchJson.updateMailboxId(origin, movedToMailboxId);
                        mailboxMessage.getSaveDate().ifPresent(newSaveDate -> messageToOpenSearchJson.updateSaveDate(origin, newSaveDate));
                        return origin;
                    }).map(messageToOpenSearchJson::toString)
                    .flatMap(jsonContent -> add(movedToMailboxId, mailboxMessage.getUid(), jsonContent)));
        }

        private Mono<MailboxMessage> retrieveMailboxMessage(MailboxSession session,
                                                            MessageId messageId,
                                                            MailboxId movedToMailboxId,
                                                            FetchType fetchType) {
            return factory.getMessageIdMapper(session)
                .findReactive(List.of(messageId), fetchType)
                .filter(mailboxMessage -> mailboxMessage.getMailboxId().equals(movedToMailboxId))
                .next();
        }
    }

    private static final int FLAGS_UPDATE_PROCESSING_WINDOW_SIZE = 32;

    public static class OpenSearchListeningMessageSearchIndexGroup extends Group {

    }

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenSearchListeningMessageSearchIndex.class);
    private static final String ID_SEPARATOR = ":";
    private static final Group GROUP = new OpenSearchListeningMessageSearchIndexGroup();

    private static final ImmutableList<String> MESSAGE_ID_FIELD = ImmutableList.of(MESSAGE_ID);
    private static final ImmutableList<String> UID_FIELD = ImmutableList.of(UID);

    private final OpenSearchIndexer openSearchIndexer;
    private final OpenSearchSearcher searcher;
    private final MessageToOpenSearchJson messageToOpenSearchJson;
    private final RoutingKey.Factory<MailboxId> routingKeyFactory;
    private final MessageId.Factory messageIdFactory;
    private final SessionProvider sessionProvider;
    private final MailboxSessionMapperFactory factory;
    
    private final Metric reIndexNotFoundMetric;
    private final IndexingStrategy indexingStrategy;
    private final IndexBody indexBody;

    @Inject
    public OpenSearchListeningMessageSearchIndex(MailboxSessionMapperFactory factory,
                                                 Set<SearchOverride> searchOverrides,
                                                 @Named(MailboxOpenSearchConstants.InjectionNames.MAILBOX) OpenSearchIndexer indexer,
                                                 OpenSearchSearcher searcher, MessageToOpenSearchJson messageToOpenSearchJson,
                                                 SessionProvider sessionProvider, RoutingKey.Factory<MailboxId> routingKeyFactory, MessageId.Factory messageIdFactory,
                                                 OpenSearchMailboxConfiguration configuration, MetricFactory metricFactory) {
        super(factory, searchOverrides, sessionProvider);
        this.sessionProvider = sessionProvider;
        this.factory = factory;
        this.openSearchIndexer = indexer;
        this.messageToOpenSearchJson = messageToOpenSearchJson;
        this.searcher = searcher;
        this.routingKeyFactory = routingKeyFactory;
        this.messageIdFactory = messageIdFactory;
        if (configuration.isOptimiseMoves()) {
            this.indexingStrategy = new OptimizedIndexingStrategy();
        } else {
            this.indexingStrategy = new NaiveIndexingStrategy();
        }
        this.indexBody = configuration.getIndexBody();
        this.reIndexNotFoundMetric = metricFactory.generate("opensearch_reindex_not_found");

        LOGGER.info("OpenSearchMessageSearchIndex activated with index strategy: {}", indexingStrategy.getClass().getSimpleName());
    }

    @Override
    public Group getDefaultGroup() {
        return GROUP;
    }

    @Override
    public EnumSet<SearchCapabilities> getSupportedCapabilities(EnumSet<MessageCapabilities> messageCapabilities) {
        return EnumSet.of(
            SearchCapabilities.MultimailboxSearch,
            SearchCapabilities.Text,
            SearchCapabilities.FullText,
            SearchCapabilities.Attachment,
            SearchCapabilities.AttachmentFileName,
            SearchCapabilities.PartialEmailMatch);
    }

    @Override
    public Mono<Void> reactiveEvent(Event event) {
        MailboxSession systemSession = sessionProvider.createSystemSession(event.getUsername());
        return handleMailboxEvent(event, systemSession, (MailboxEvents.MailboxEvent) event)
            .then(Mono.fromRunnable(() -> factory.endProcessingRequest(systemSession)));
    }

    private Mono<Void> handleMailboxEvent(Event event, MailboxSession session, MailboxEvents.MailboxEvent mailboxEvent) {
        MailboxId mailboxId = mailboxEvent.getMailboxId();

        if (event instanceof MailboxEvents.Added) {
            return indexingStrategy.handleAddedEvent(session, (MailboxEvents.Added) event, mailboxId);
        } else if (event instanceof MailboxEvents.Expunged) {
            return indexingStrategy.handleExpungedEvent((MailboxEvents.Expunged) event, session, mailboxId);
        } else if (event instanceof MailboxEvents.FlagsUpdated) {
            MailboxEvents.FlagsUpdated flagsUpdated = (MailboxEvents.FlagsUpdated) event;
            return update(session, mailboxId, flagsUpdated.getUpdatedFlags());
        } else if (event instanceof MailboxEvents.MailboxDeletion) {
            return deleteAll(session, mailboxId);
        } else {
            return Mono.empty();
        }
    }

    private Mono<Void> processAddedEvent(MailboxSession session, MailboxEvents.Added addedEvent, MailboxId mailboxId) {
        return factory.getMailboxMapper(session)
            .findMailboxById(mailboxId)
            .flatMap(mailbox -> handleAdded(session, mailbox, addedEvent, chooseFetchType()));
    }

    private FetchType chooseFetchType() {
        if (indexBody == IndexBody.YES) {
            return FetchType.FULL;
        }
        return FetchType.HEADERS;
    }

    @Override
    protected Flux<MessageUid> doSearch(MailboxSession session, Mailbox mailbox, SearchQuery searchQuery) {
        Preconditions.checkArgument(session != null, "'session' is mandatory");
        Optional<Integer> noLimit = Optional.empty();

        return searcher.search(ImmutableList.of(mailbox.getMailboxId()), searchQuery, noLimit, UID_FIELD)
            .handle(this::extractUidFromHit);
    }
    
    @Override
    public Flux<MessageId> search(MailboxSession session, Collection<MailboxId> mailboxIds, SearchQuery searchQuery, long limit) {
        Preconditions.checkArgument(session != null, "'session' is mandatory");

        if (mailboxIds.isEmpty()) {
            return Flux.empty();
        }

        return searcher.search(mailboxIds, searchQuery, Optional.empty(), MESSAGE_ID_FIELD)
            .handle(this::extractMessageIdFromHit)
            .distinct()
            .take(limit);
    }

    @Override
    public Mono<Void> add(MailboxSession session, Mailbox mailbox, MailboxMessage message) {
        LOGGER.info("Indexing mailbox {}-{} of user {} on message {}",
            mailbox.getName(),
            mailbox.getMailboxId().serialize(),
            session.getUser().asString(),
            message.getUid().asLong());

        return generateIndexedJson(mailbox, message, session)
            .flatMap(jsonContent -> add(mailbox.getMailboxId(), message.getUid(), jsonContent));
    }

    private Mono<Void> add(MailboxId mailboxId, MessageUid messageUid, String jsonContent) {
        RoutingKey from = routingKeyFactory.from(mailboxId);
        DocumentId id = indexIdFor(mailboxId, messageUid);
        return openSearchIndexer.index(id, jsonContent, from)
            .then();
    }

    private Mono<String> generateIndexedJson(Mailbox mailbox, MailboxMessage message, MailboxSession session) {
        return messageToOpenSearchJson.convertToJson(message)
            .onErrorResume(e -> {
                LOGGER.warn("Indexing mailbox {}-{} of user {} on message {} without attachments ",
                    mailbox.getName(),
                    mailbox.getMailboxId().serialize(),
                    session.getUser().asString(),
                    message.getUid(),
                    e);
                return messageToOpenSearchJson.convertToJsonWithoutAttachment(message);
            });
    }

    @Override
    public Mono<Void> delete(MailboxSession session, MailboxId mailboxId, Collection<MessageUid> expungedUids) {
        return openSearchIndexer
            .delete(expungedUids.stream()
                .map(uid ->  indexIdFor(mailboxId, uid))
                .collect(toImmutableList()),
                routingKeyFactory.from(mailboxId))
            .then();
    }

    @Override
    public Mono<Void> deleteAll(MailboxSession session, MailboxId mailboxId) {
        Query query = TermQuery.of(t -> t
            .field(MAILBOX_ID)
            .value(new FieldValue.Builder().stringValue(mailboxId.serialize()).build()))._toQuery();

        return openSearchIndexer
                .deleteAllMatchingQuery(query, routingKeyFactory.from(mailboxId));
    }

    @Override
    public Mono<Void> update(MailboxSession session, MailboxId mailboxId, List<UpdatedFlags> updatedFlagsList) {
        RoutingKey routingKey = routingKeyFactory.from(mailboxId);

        return Flux.fromIterable(updatedFlagsList)
            .map(Throwing.<UpdatedFlags, UpdatedRepresentation>function(
                updatedFlags -> createUpdatedDocumentPartFromUpdatedFlags(mailboxId, updatedFlags))
                .sneakyThrow())
            .window(FLAGS_UPDATE_PROCESSING_WINDOW_SIZE)
            .concatMap(flux -> flux.collect(toImmutableList())
                .flatMap(updates -> openSearchIndexer.update(updates, routingKey)))
            .then();
    }

    private UpdatedRepresentation createUpdatedDocumentPartFromUpdatedFlags(MailboxId mailboxId, UpdatedFlags updatedFlags) throws JsonProcessingException {
        return new UpdatedRepresentation(
            indexIdFor(mailboxId, updatedFlags.getUid()),
            messageToOpenSearchJson
                .getUpdatedJsonMessagePart(updatedFlags.getNewFlags(), updatedFlags.getModSeq()));
    }

    private DocumentId indexIdFor(MailboxId mailboxId, MessageUid uid) {
        return DocumentId.fromString(mailboxId.serialize() + ID_SEPARATOR + uid.asLong());
    }

    @Override
    public Mono<Flags> retrieveIndexedFlags(Mailbox mailbox, MessageUid uid) {
        RoutingKey routingKey = routingKeyFactory.from(mailbox.getMailboxId());

        return openSearchIndexer.get(indexIdFor(mailbox.getMailboxId(), uid), routingKey)
            .filter(GetResponse::found)
            .mapNotNull(GetResponse::source)
            .map(this::extractFlags);
    }

    private Flags extractFlags(ObjectNode source) {
        FlagsBuilder flagsBuilder = FlagsBuilder.builder()
            .isAnswered(extractFlag(source, IS_ANSWERED))
            .isDeleted(extractFlag(source, IS_DELETED))
            .isDraft(extractFlag(source, IS_DRAFT))
            .isFlagged(extractFlag(source, IS_FLAGGED))
            .isRecent(extractFlag(source, IS_RECENT))
            .isSeen(!extractFlag(source, IS_UNREAD));

        for (JsonNode userFlag : extractUserFlags(source)) {
            flagsBuilder.add(userFlag.textValue());
        }

        return flagsBuilder.build();
    }

    private boolean extractFlag(ObjectNode source, String flag) {
        return source.get(flag).asBoolean();
    }

    private ArrayNode extractUserFlags(ObjectNode source) {
        return source.withArray("userFlags");
    }

    private void extractMessageIdFromHit(Hit<ObjectNode> hit, SynchronousSink<MessageId> sink) {
        JsonData messageId = hit.fields().get(MESSAGE_ID);
        if (messageId != null) {
            String messageIdAsString = messageId.toJson()
                .asJsonArray()
                .getString(0);

            sink.next(messageIdFactory.fromString(messageIdAsString));
        } else {
            LOGGER.warn("Can not extract UID, MessageID and/or MailboxId for search result {}", hit.id());
        }
    }

    private void extractUidFromHit(Hit<ObjectNode> hit, SynchronousSink<MessageUid> sink) {
        JsonData uid = hit.fields().get(UID);
        if (uid != null) {
            int uidAsInt = uid.toJson()
                .asJsonArray()
                .getInt(0);

            sink.next(MessageUid.of(uidAsInt));
        } else {
            LOGGER.warn("Can not extract UID, MessageID and/or MailboxId for search result {}", hit.id());
        }
    }
}
