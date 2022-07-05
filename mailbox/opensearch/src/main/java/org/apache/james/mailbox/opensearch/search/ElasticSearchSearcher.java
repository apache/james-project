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

package org.apache.james.mailbox.opensearch.search;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.apache.james.backends.opensearch.AliasName;
import org.apache.james.backends.opensearch.ReactorElasticSearchClient;
import org.apache.james.backends.opensearch.ReadAliasName;
import org.apache.james.backends.opensearch.RoutingKey;
import org.apache.james.backends.opensearch.search.ScrolledSearch;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.opensearch.query.QueryConverter;
import org.apache.james.mailbox.opensearch.query.SortConverter;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;

import reactor.core.publisher.Flux;

public class ElasticSearchSearcher {
    public static final int DEFAULT_SEARCH_SIZE = 100;
    private static final TimeValue TIMEOUT = TimeValue.timeValueMinutes(1);
    private static final int MAX_ROUTING_KEY = 5;

    private final ReactorElasticSearchClient client;
    private final QueryConverter queryConverter;
    private final int size;
    private final AliasName aliasName;
    private final RoutingKey.Factory<MailboxId> routingKeyFactory;

    public ElasticSearchSearcher(ReactorElasticSearchClient client, QueryConverter queryConverter, int size,
                                 ReadAliasName aliasName, RoutingKey.Factory<MailboxId> routingKeyFactory) {
        this.client = client;
        this.queryConverter = queryConverter;
        this.size = size;
        this.aliasName = aliasName;
        this.routingKeyFactory = routingKeyFactory;
    }

    public Flux<SearchHit> search(Collection<MailboxId> mailboxIds, SearchQuery query,
                                                        Optional<Integer> limit, List<String> fields) {
        SearchRequest searchRequest = prepareSearch(mailboxIds, query, limit, fields);
        return new ScrolledSearch(client, searchRequest)
            .searchHits();
    }

    private SearchRequest prepareSearch(Collection<MailboxId> mailboxIds, SearchQuery query, Optional<Integer> limit, List<String> fields) {
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
            .query(queryConverter.from(mailboxIds, query))
            .size(computeRequiredSize(limit))
            .storedFields(fields);

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

}
