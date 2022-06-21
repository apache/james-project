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
import org.apache.james.backends.opensearch.ElasticSearchIndexer;
import org.apache.james.backends.opensearch.RoutingKey;
import org.apache.james.backends.opensearch.UpdatedRepresentation;
import org.apache.james.events.Group;
import org.apache.james.mailbox.FlagsBuilder;
import org.apache.james.mailbox.MailboxManager.MessageCapabilities;
import org.apache.james.mailbox.MailboxManager.SearchCapabilities;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.SessionProvider;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.opensearch.MailboxElasticSearchConstants;
import org.apache.james.mailbox.opensearch.json.MessageToElasticSearchJson;
import org.apache.james.mailbox.opensearch.search.ElasticSearchSearcher;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.search.ListeningMessageSearchIndex;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.json.JsonpDeserializer;
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

public class ElasticSearchListeningMessageSearchIndex extends ListeningMessageSearchIndex {
    private static final int FLAGS_UPDATE_PROCESSING_WINDOW_SIZE = 32;

    public static class ElasticSearchListeningMessageSearchIndexGroup extends Group {

    }

    private static final Logger LOGGER = LoggerFactory.getLogger(ElasticSearchListeningMessageSearchIndex.class);
    private static final String ID_SEPARATOR = ":";
    private static final Group GROUP = new ElasticSearchListeningMessageSearchIndexGroup();

    private static final ImmutableList<String> MESSAGE_ID_FIELD = ImmutableList.of(MESSAGE_ID);
    private static final ImmutableList<String> UID_FIELD = ImmutableList.of(UID);

    private final ElasticSearchIndexer elasticSearchIndexer;
    private final ElasticSearchSearcher searcher;
    private final MessageToElasticSearchJson messageToElasticSearchJson;
    private final RoutingKey.Factory<MailboxId> routingKeyFactory;
    private final MessageId.Factory messageIdFactory;

    @Inject
    public ElasticSearchListeningMessageSearchIndex(MailboxSessionMapperFactory factory,
                                                    Set<SearchOverride> searchOverrides,
                                                    @Named(MailboxElasticSearchConstants.InjectionNames.MAILBOX) ElasticSearchIndexer indexer,
                                                    ElasticSearchSearcher searcher, MessageToElasticSearchJson messageToElasticSearchJson,
                                                    SessionProvider sessionProvider, RoutingKey.Factory<MailboxId> routingKeyFactory, MessageId.Factory messageIdFactory) {
        super(factory, searchOverrides, sessionProvider);
        this.elasticSearchIndexer = indexer;
        this.messageToElasticSearchJson = messageToElasticSearchJson;
        this.searcher = searcher;
        this.routingKeyFactory = routingKeyFactory;
        this.messageIdFactory = messageIdFactory;
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

        RoutingKey from = routingKeyFactory.from(mailbox.getMailboxId());
        DocumentId id = indexIdFor(mailbox.getMailboxId(), message.getUid());

        return generateIndexedJson(mailbox, message, session)
            .flatMap(jsonContent -> elasticSearchIndexer.index(id, jsonContent, from))
            .then();
    }

    private Mono<String> generateIndexedJson(Mailbox mailbox, MailboxMessage message, MailboxSession session) {
        return messageToElasticSearchJson.convertToJson(message)
            .onErrorResume(e -> {
                LOGGER.warn("Indexing mailbox {}-{} of user {} on message {} without attachments ",
                    mailbox.getName(),
                    mailbox.getMailboxId().serialize(),
                    session.getUser().asString(),
                    message.getUid(),
                    e);
                return messageToElasticSearchJson.convertToJsonWithoutAttachment(message);
            });
    }

    @Override
    public Mono<Void> delete(MailboxSession session, MailboxId mailboxId, Collection<MessageUid> expungedUids) {
        return elasticSearchIndexer
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

        return elasticSearchIndexer
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
                .flatMap(updates -> elasticSearchIndexer.update(updates, routingKey)))
            .then();
    }

    private UpdatedRepresentation createUpdatedDocumentPartFromUpdatedFlags(MailboxId mailboxId, UpdatedFlags updatedFlags) throws JsonProcessingException {
        return new UpdatedRepresentation(
            indexIdFor(mailboxId, updatedFlags.getUid()),
            messageToElasticSearchJson
                .getUpdatedJsonMessagePart(updatedFlags.getNewFlags(), updatedFlags.getModSeq()));
    }

    private DocumentId indexIdFor(MailboxId mailboxId, MessageUid uid) {
        return DocumentId.fromString(mailboxId.serialize() + ID_SEPARATOR + uid.asLong());
    }

    private void logIfNoMessageId(SearchResult searchResult) {
        if (!searchResult.getMessageId().isPresent()) {
            LOGGER.error("No messageUid for {} in mailbox {}", searchResult.getMessageUid(), searchResult.getMailboxId());
        }
    }

    @Override
    public Mono<Flags> retrieveIndexedFlags(Mailbox mailbox, MessageUid uid) {
        RoutingKey routingKey = routingKeyFactory.from(mailbox.getMailboxId());

        return elasticSearchIndexer.get(indexIdFor(mailbox.getMailboxId(), uid), routingKey)
            .filter(GetResponse::found)
            .map(GetResponse::source)
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
            List<String> extractMessageId = messageId.deserialize(JsonpDeserializer.arrayDeserializer(JsonpDeserializer.stringDeserializer()));
            sink.next(messageIdFactory.fromString(extractMessageId.get(0)));
        } else {
            LOGGER.warn("Can not extract UID, MessageID and/or MailboxId for search result {}", hit.id());
        }
    }

    private void extractUidFromHit(Hit<ObjectNode> hit, SynchronousSink<MessageUid> sink) {
        JsonData uid = hit.fields().get(UID);
        if (uid != null) {
            List<Number> uidAsNumber = uid.deserialize(JsonpDeserializer.arrayDeserializer(JsonpDeserializer.numberDeserializer()));
            sink.next(MessageUid.of(uidAsNumber.get(0).longValue()));
        } else {
            LOGGER.warn("Can not extract UID, MessageID and/or MailboxId for search result {}", hit.id());
        }
    }
}
