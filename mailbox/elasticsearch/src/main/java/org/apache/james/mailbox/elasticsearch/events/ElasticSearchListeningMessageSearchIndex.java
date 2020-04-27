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
package org.apache.james.mailbox.elasticsearch.events;

import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.james.backends.es.DocumentId;
import org.apache.james.backends.es.ElasticSearchIndexer;
import org.apache.james.backends.es.RoutingKey;
import org.apache.james.backends.es.UpdatedRepresentation;
import org.apache.james.mailbox.MailboxManager.MessageCapabilities;
import org.apache.james.mailbox.MailboxManager.SearchCapabilities;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.SessionProvider;
import org.apache.james.mailbox.elasticsearch.MailboxElasticSearchConstants;
import org.apache.james.mailbox.elasticsearch.json.JsonMessageConstants;
import org.apache.james.mailbox.elasticsearch.json.MessageToElasticSearchJson;
import org.apache.james.mailbox.elasticsearch.search.ElasticSearchSearcher;
import org.apache.james.mailbox.events.Group;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.search.ListeningMessageSearchIndex;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.fge.lambdas.Throwing;
import com.github.steveash.guavate.Guavate;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class ElasticSearchListeningMessageSearchIndex extends ListeningMessageSearchIndex {
    public static class ElasticSearchListeningMessageSearchIndexGroup extends Group {

    }

    private static final Logger LOGGER = LoggerFactory.getLogger(ElasticSearchListeningMessageSearchIndex.class);
    private static final String ID_SEPARATOR = ":";
    private static final Group GROUP = new ElasticSearchListeningMessageSearchIndexGroup();

    private final ElasticSearchIndexer elasticSearchIndexer;
    private final ElasticSearchSearcher searcher;
    private final MessageToElasticSearchJson messageToElasticSearchJson;
    private final RoutingKey.Factory<MailboxId> routingKeyFactory;

    @Inject
    public ElasticSearchListeningMessageSearchIndex(MailboxSessionMapperFactory factory,
                                                    @Named(MailboxElasticSearchConstants.InjectionNames.MAILBOX) ElasticSearchIndexer indexer,
                                                    ElasticSearchSearcher searcher, MessageToElasticSearchJson messageToElasticSearchJson,
                                                    SessionProvider sessionProvider, RoutingKey.Factory<MailboxId> routingKeyFactory) {
        super(factory, sessionProvider);
        this.elasticSearchIndexer = indexer;
        this.messageToElasticSearchJson = messageToElasticSearchJson;
        this.searcher = searcher;
        this.routingKeyFactory = routingKeyFactory;
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
    public Stream<MessageUid> search(MailboxSession session, Mailbox mailbox, SearchQuery searchQuery) {
        Preconditions.checkArgument(session != null, "'session' is mandatory");
        Optional<Integer> noLimit = Optional.empty();

        return searcher
            .search(ImmutableList.of(mailbox.getMailboxId()), searchQuery, noLimit)
            .map(SearchResult::getMessageUid)
            .toStream();
    }
    
    @Override
    public Flux<MessageId> search(MailboxSession session, Collection<MailboxId> mailboxIds, SearchQuery searchQuery, long limit) {
        Preconditions.checkArgument(session != null, "'session' is mandatory");

        if (mailboxIds.isEmpty()) {
            return Flux.empty();
        }

        return searcher.search(mailboxIds, searchQuery, Optional.empty())
            .doOnNext(this::logIfNoMessageId)
            .map(SearchResult::getMessageId)
            .flatMap(Mono::justOrEmpty)
            .distinct()
            .take(limit);
    }

    @Override
    public Mono<Void> add(MailboxSession session, Mailbox mailbox, MailboxMessage message) {
        LOGGER.info("Indexing mailbox {}-{} of user {} on message {}",
            mailbox.getName(),
            mailbox.getMailboxId(),
            session.getUser().asString(),
            message.getUid());

        RoutingKey from = routingKeyFactory.from(mailbox.getMailboxId());
        DocumentId id = indexIdFor(mailbox, message.getUid());

        return Mono.fromCallable(() -> generateIndexedJson(mailbox, message, session))
            .flatMap(jsonContent -> elasticSearchIndexer.index(id, jsonContent, from))
            .then();
    }

    private String generateIndexedJson(Mailbox mailbox, MailboxMessage message, MailboxSession session) throws JsonProcessingException {
        try {
            return messageToElasticSearchJson.convertToJson(message, ImmutableList.of(session.getUser()));
        } catch (Exception e) {
            LOGGER.warn("Indexing mailbox {}-{} of user {} on message {} without attachments ",
                mailbox.getName(),
                mailbox.getMailboxId().serialize(),
                session.getUser().asString(),
                message.getUid(),
                e);
            return messageToElasticSearchJson.convertToJsonWithoutAttachment(message, ImmutableList.of(session.getUser()));
        }
    }

    @Override
    public Mono<Void> delete(MailboxSession session, Mailbox mailbox, Collection<MessageUid> expungedUids) {
        return elasticSearchIndexer
            .delete(expungedUids.stream()
                .map(uid ->  indexIdFor(mailbox, uid))
                .collect(Guavate.toImmutableList()),
                routingKeyFactory.from(mailbox.getMailboxId()))
            .then();
    }

    @Override
    public Mono<Void> deleteAll(MailboxSession session, MailboxId mailboxId) {
        TermQueryBuilder queryBuilder = termQuery(
            JsonMessageConstants.MAILBOX_ID,
            mailboxId.serialize());

        return elasticSearchIndexer
                .deleteAllMatchingQuery(queryBuilder, routingKeyFactory.from(mailboxId));
    }

    @Override
    public Mono<Void> update(MailboxSession session, Mailbox mailbox, List<UpdatedFlags> updatedFlagsList) {
        RoutingKey routingKey = routingKeyFactory.from(mailbox.getMailboxId());

        return Flux.fromIterable(updatedFlagsList)
            .map(Throwing.<UpdatedFlags, UpdatedRepresentation>function(
                updatedFlags -> createUpdatedDocumentPartFromUpdatedFlags(mailbox, updatedFlags))
                .sneakyThrow())
            .collect(Guavate.toImmutableList())
            .flatMap(updates -> elasticSearchIndexer.update(updates, routingKey))
            .then();
    }

    private UpdatedRepresentation createUpdatedDocumentPartFromUpdatedFlags(Mailbox mailbox, UpdatedFlags updatedFlags) throws JsonProcessingException {
        return new UpdatedRepresentation(
            indexIdFor(mailbox, updatedFlags.getUid()),
            messageToElasticSearchJson
                .getUpdatedJsonMessagePart(updatedFlags.getNewFlags(), updatedFlags.getModSeq()));
    }

    private DocumentId indexIdFor(Mailbox mailbox, MessageUid uid) {
        return DocumentId.fromString(String.join(ID_SEPARATOR, mailbox.getMailboxId().serialize(), String.valueOf(uid.asLong())));
    }

    private void logIfNoMessageId(SearchResult searchResult) {
        if (!searchResult.getMessageId().isPresent()) {
            LOGGER.error("No messageUid for {} in mailbox {}", searchResult.getMessageUid(), searchResult.getMailboxId());
        }
    }
}
