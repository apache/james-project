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

package org.apache.james.mailbox.elasticsearch.v7.search;

import static org.apache.james.util.ReactorUtils.publishIfPresent;

import java.util.Collection;
import java.util.Optional;

import org.apache.james.backends.es.v7.AliasName;
import org.apache.james.backends.es.v7.ReactorElasticSearchClient;
import org.apache.james.backends.es.v7.ReadAliasName;
import org.apache.james.backends.es.v7.RoutingKey;
import org.apache.james.backends.es.v7.search.ScrolledSearch;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.elasticsearch.v7.json.JsonMessageConstants;
import org.apache.james.mailbox.elasticsearch.v7.query.QueryConverter;
import org.apache.james.mailbox.elasticsearch.v7.query.SortConverter;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.store.search.MessageSearchIndex;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.common.document.DocumentField;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;

public class ElasticSearchSearcher {
    public static final int DEFAULT_SEARCH_SIZE = 100;
    private static final Logger LOGGER = LoggerFactory.getLogger(ElasticSearchSearcher.class);
    private static final TimeValue TIMEOUT = TimeValue.timeValueMinutes(1);
    private static final ImmutableList<String> STORED_FIELDS = ImmutableList.of(JsonMessageConstants.MAILBOX_ID,
        JsonMessageConstants.UID, JsonMessageConstants.MESSAGE_ID);
    private static final int MAX_ROUTING_KEY = 5;

    private final ReactorElasticSearchClient client;
    private final QueryConverter queryConverter;
    private final int size;
    private final MailboxId.Factory mailboxIdFactory;
    private final MessageId.Factory messageIdFactory;
    private final AliasName aliasName;
    private final RoutingKey.Factory<MailboxId> routingKeyFactory;

    public ElasticSearchSearcher(ReactorElasticSearchClient client, QueryConverter queryConverter, int size,
                                 MailboxId.Factory mailboxIdFactory, MessageId.Factory messageIdFactory,
                                 ReadAliasName aliasName, RoutingKey.Factory<MailboxId> routingKeyFactory) {
        this.client = client;
        this.queryConverter = queryConverter;
        this.size = size;
        this.mailboxIdFactory = mailboxIdFactory;
        this.messageIdFactory = messageIdFactory;
        this.aliasName = aliasName;
        this.routingKeyFactory = routingKeyFactory;
    }

    public Flux<MessageSearchIndex.SearchResult> search(Collection<MailboxId> mailboxIds, SearchQuery query,
                                                        Optional<Integer> limit) {
        SearchRequest searchRequest = prepareSearch(mailboxIds, query, limit);
        Flux<MessageSearchIndex.SearchResult> pairStream = new ScrolledSearch(client, searchRequest)
            .searchHits()
            .map(this::extractContentFromHit)
            .handle(publishIfPresent());

        return limit.map(pairStream::take)
            .orElse(pairStream);
    }

    private SearchRequest prepareSearch(Collection<MailboxId> mailboxIds, SearchQuery query, Optional<Integer> limit) {
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
            .query(queryConverter.from(mailboxIds, query))
            .size(computeRequiredSize(limit))
            .fetchField(JsonMessageConstants.MAILBOX_ID)
            .fetchField(JsonMessageConstants.UID)
            .fetchField(JsonMessageConstants.MESSAGE_ID)
            .storedFields(STORED_FIELDS);

        query.getSorts()
            .stream()
            .map(SortConverter::convertSort)
            .forEach(searchSourceBuilder::sort);

        SearchRequest request = new SearchRequest(aliasName.getValue())
            .scroll(TIMEOUT)
            .source(searchSourceBuilder);

        return toRoutingKey(mailboxIds)
            .map(request::routing)
            .orElse(request);
    }

    private Optional<String[]> toRoutingKey(Collection<MailboxId> mailboxIds) {
        if (mailboxIds.size() < MAX_ROUTING_KEY) {
            return Optional.of(mailboxIds.stream()
                .map(routingKeyFactory::from)
                .map(RoutingKey::asString)
                .toArray(String[]::new));
        }
        return Optional.empty();
    }

    private int computeRequiredSize(Optional<Integer> limit) {
        return limit.map(value -> Math.min(value.intValue(), size))
            .orElse(size);
    }

    private Optional<MessageSearchIndex.SearchResult> extractContentFromHit(SearchHit hit) {
        DocumentField mailboxId = hit.field(JsonMessageConstants.MAILBOX_ID);
        DocumentField uid = hit.field(JsonMessageConstants.UID);
        Optional<DocumentField> id = retrieveMessageIdField(hit);
        if (mailboxId != null && uid != null) {
            Number uidAsNumber = uid.getValue();
            return Optional.of(
                new MessageSearchIndex.SearchResult(
                    id.map(field -> messageIdFactory.fromString(field.getValue())),
                    mailboxIdFactory.fromString(mailboxId.getValue()),
                    MessageUid.of(uidAsNumber.longValue())));
        } else {
            LOGGER.warn("Can not extract UID, MessageID and/or MailboxId for search result {}", hit.getId());
            return Optional.empty();
        }
    }

    private Optional<DocumentField> retrieveMessageIdField(SearchHit hit) {
        if (hit.getFields().containsKey(JsonMessageConstants.MESSAGE_ID)) {
            return Optional.ofNullable(hit.field(JsonMessageConstants.MESSAGE_ID));
        }
        return Optional.empty();
    }

}
