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

package org.apache.james.quota.search.elasticsearch;

import static org.apache.james.quota.search.elasticsearch.json.JsonMessageConstants.USER;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.apache.james.backends.es.v6.AliasName;
import org.apache.james.backends.es.v6.NodeMappingFactory;
import org.apache.james.backends.es.v6.ReadAliasName;
import org.apache.james.backends.es.v6.search.ScrollIterable;
import org.apache.james.core.User;
import org.apache.james.quota.search.QuotaQuery;
import org.apache.james.quota.search.QuotaSearcher;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;

import com.github.steveash.guavate.Guavate;

public class ElasticSearchQuotaSearcher implements QuotaSearcher {
    private static final TimeValue TIMEOUT = new TimeValue(60000);

    private final RestHighLevelClient client;
    private final AliasName readAlias;
    private final QuotaQueryConverter quotaQueryConverter;

    public ElasticSearchQuotaSearcher(RestHighLevelClient client, ReadAliasName readAlias) {
        this.client = client;
        this.readAlias = readAlias;
        this.quotaQueryConverter = new QuotaQueryConverter();
    }

    @Override
    public List<User> search(QuotaQuery query) {
        Stream<User> results = new ScrollIterable(client, prepareSearch(query))
            .stream()
            .flatMap(searchResponse -> Arrays.stream(searchResponse.getHits()
                .getHits()))
            .map(SearchHit::getId)
            .map(User::fromUsername)
            .skip(query.getOffset().getValue());

        return query.getLimit().getValue()
            .map(results::limit)
            .orElse(results)
            .collect(Guavate.toImmutableList());
    }

    public SearchRequest prepareSearch(QuotaQuery query) {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder()
            .query(quotaQueryConverter.from(query))
            .sort(SortBuilders.fieldSort(USER)
                .order(SortOrder.ASC));

        query.getLimit()
            .getValue()
            .ifPresent(sourceBuilder::size);

        return new SearchRequest(readAlias.getValue())
            .types(NodeMappingFactory.DEFAULT_MAPPING_NAME)
            .scroll(TIMEOUT)
            .source(sourceBuilder);
    }

}