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
import java.util.stream.Collectors;

import org.apache.james.backends.opensearch.AliasName;
import org.apache.james.backends.opensearch.ReactorOpenSearchClient;
import org.apache.james.backends.opensearch.ReadAliasName;
import org.apache.james.backends.opensearch.RoutingKey;
import org.apache.james.backends.opensearch.search.ScrolledSearch;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.opensearch.query.QueryConverter;
import org.apache.james.mailbox.opensearch.query.SortConverter;
import org.opensearch.client.opensearch._types.SortOptions;
import org.opensearch.client.opensearch._types.Time;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.search.Hit;

import com.fasterxml.jackson.databind.node.ObjectNode;

import reactor.core.publisher.Flux;

public class OpenSearchSearcher {
    public static final int DEFAULT_SEARCH_SIZE = 100;
    private static final Time TIMEOUT = new Time.Builder().time("1m").build();
    private static final int MAX_ROUTING_KEY = 5;

    private final ReactorOpenSearchClient client;
    private final QueryConverter queryConverter;
    private final int size;
    private final AliasName aliasName;
    private final RoutingKey.Factory<MailboxId> routingKeyFactory;

    public OpenSearchSearcher(ReactorOpenSearchClient client, QueryConverter queryConverter, int size,
                              ReadAliasName aliasName, RoutingKey.Factory<MailboxId> routingKeyFactory) {
        this.client = client;
        this.queryConverter = queryConverter;
        this.size = size;
        this.aliasName = aliasName;
        this.routingKeyFactory = routingKeyFactory;
    }

    public Flux<Hit<ObjectNode>> search(Collection<MailboxId> mailboxIds, SearchQuery query,
                                        Optional<Integer> limit, List<String> fields) {
        SearchRequest searchRequest = prepareSearch(mailboxIds, query, limit, fields);
        return new ScrolledSearch(client, searchRequest)
            .searchHits();
    }

    private SearchRequest prepareSearch(Collection<MailboxId> mailboxIds, SearchQuery query, Optional<Integer> limit, List<String> fields) {
        List<SortOptions> sorts = query.getSorts()
            .stream()
            .flatMap(SortConverter::convertSort)
            .map(fieldSort -> new SortOptions.Builder().field(fieldSort).build())
            .collect(Collectors.toList());

        SearchRequest.Builder request = new SearchRequest.Builder()
            .index(aliasName.getValue())
            .scroll(TIMEOUT)
            .query(queryConverter.from(mailboxIds, query))
            .size(computeRequiredSize(limit))
            .storedFields(fields)
            .sort(sorts);

        return toRoutingKey(mailboxIds)
            .map(request::routing)
            .orElse(request)
            .build();
    }

    private Optional<String> toRoutingKey(Collection<MailboxId> mailboxIds) {
        if (mailboxIds.size() < MAX_ROUTING_KEY) {
            return Optional.of(mailboxIds.stream()
                .map(routingKeyFactory::from)
                .map(RoutingKey::asString)
                .collect(Collectors.joining(",")));
        }
        return Optional.empty();
    }

    private int computeRequiredSize(Optional<Integer> limit) {
        return limit.map(value -> Math.min(value.intValue(), size))
            .orElse(size);
    }

}
