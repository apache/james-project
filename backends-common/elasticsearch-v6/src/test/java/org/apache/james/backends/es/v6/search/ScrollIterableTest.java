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

package org.apache.james.backends.es.v6.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.james.backends.es.v6.ClientProvider;
import org.apache.james.backends.es.v6.DockerElasticSearchRule;
import org.apache.james.backends.es.v6.ElasticSearchConfiguration;
import org.apache.james.backends.es.v6.IndexCreationFactory;
import org.apache.james.backends.es.v6.IndexName;
import org.apache.james.backends.es.v6.ReadAliasName;
import org.apache.james.backends.es.v6.TypeName;
import org.awaitility.Duration;
import org.awaitility.core.ConditionFactory;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class ScrollIterableTest {

    private static final TimeValue TIMEOUT = new TimeValue(6000);
    private static final int SIZE = 2;
    private static final String MESSAGE = "message";
    private static final IndexName INDEX_NAME = new IndexName("index");
    private static final ReadAliasName ALIAS_NAME = new ReadAliasName("alias");
    private static final TypeName TYPE_NAME = new TypeName("messages");

    private static final ConditionFactory WAIT_CONDITION = await().timeout(Duration.FIVE_SECONDS);

    @Rule
    public DockerElasticSearchRule elasticSearch = new DockerElasticSearchRule();
    private ClientProvider clientProvider;

    @Before
    public void setUp() {
        clientProvider = elasticSearch.clientProvider();
        new IndexCreationFactory(ElasticSearchConfiguration.DEFAULT_CONFIGURATION)
            .useIndex(INDEX_NAME)
            .addAlias(ALIAS_NAME)
            .createIndexAndAliases(clientProvider.get());
        elasticSearch.awaitForElasticSearch();
    }

    @Test
    public void scrollIterableShouldWorkWhenEmpty() throws Exception {
        try (RestHighLevelClient client = clientProvider.get()) {
            SearchRequest searchRequest = new SearchRequest(INDEX_NAME.getValue())
                .types(TYPE_NAME.getValue())
                .scroll(TIMEOUT)
                .source(new SearchSourceBuilder()
                    .query(QueryBuilders.matchAllQuery())
                    .size(SIZE));

            assertThat(new ScrollIterable(client, searchRequest))
                .isEmpty();
        }
    }

    @Test
    public void scrollIterableShouldWorkWhenOneElement() throws Exception {
        try (RestHighLevelClient client = clientProvider.get()) {
            String id = "1";
            client.index(new IndexRequest(INDEX_NAME.getValue(), TYPE_NAME.getValue(), id)
                    .source(MESSAGE, "Sample message"),
                RequestOptions.DEFAULT);

            elasticSearch.awaitForElasticSearch();
            WAIT_CONDITION.untilAsserted(() -> hasIdsInIndex(client, id));

            SearchRequest searchRequest = new SearchRequest(INDEX_NAME.getValue())
                .types(TYPE_NAME.getValue())
                .scroll(TIMEOUT)
                .source(new SearchSourceBuilder()
                    .query(QueryBuilders.matchAllQuery())
                    .size(SIZE));

            assertThat(convertToIdList(new ScrollIterable(client, searchRequest)))
                .containsOnly(id);
        }
    }

    @Test
    public void scrollIterableShouldWorkWhenSizeElement() throws Exception {
        try (RestHighLevelClient client = clientProvider.get()) {
            String id1 = "1";
            client.index(new IndexRequest(INDEX_NAME.getValue(), TYPE_NAME.getValue(), id1)
                    .source(MESSAGE, "Sample message"),
                RequestOptions.DEFAULT);

            String id2 = "2";
            client.index(new IndexRequest(INDEX_NAME.getValue(), TYPE_NAME.getValue(), id2)
                    .source(MESSAGE, "Sample message"),
                RequestOptions.DEFAULT);

            elasticSearch.awaitForElasticSearch();
            WAIT_CONDITION.untilAsserted(() -> hasIdsInIndex(client, id1, id2));

            SearchRequest searchRequest = new SearchRequest(INDEX_NAME.getValue())
                .types(TYPE_NAME.getValue())
                .scroll(TIMEOUT)
                .source(new SearchSourceBuilder()
                    .query(QueryBuilders.matchAllQuery())
                    .size(SIZE));

            assertThat(convertToIdList(new ScrollIterable(client, searchRequest)))
                .containsOnly(id1, id2);
        }
    }

    @Test
    public void scrollIterableShouldWorkWhenMoreThanSizeElement() throws Exception {
        try (RestHighLevelClient client = clientProvider.get()) {
            String id1 = "1";
            client.index(new IndexRequest(INDEX_NAME.getValue(), TYPE_NAME.getValue(), id1)
                    .source(MESSAGE, "Sample message"),
                RequestOptions.DEFAULT);

            String id2 = "2";
            client.index(new IndexRequest(INDEX_NAME.getValue(), TYPE_NAME.getValue(), id2)
                    .source(MESSAGE, "Sample message"),
                RequestOptions.DEFAULT);

            String id3 = "3";
            client.index(new IndexRequest(INDEX_NAME.getValue(), TYPE_NAME.getValue(), id3)
                    .source(MESSAGE, "Sample message"),
                RequestOptions.DEFAULT);

            elasticSearch.awaitForElasticSearch();
            WAIT_CONDITION.untilAsserted(() -> hasIdsInIndex(client, id1, id2, id3));

            SearchRequest searchRequest = new SearchRequest(INDEX_NAME.getValue())
                .types(TYPE_NAME.getValue())
                .scroll(TIMEOUT)
                .source(new SearchSourceBuilder()
                    .query(QueryBuilders.matchAllQuery())
                    .size(SIZE));

            assertThat(convertToIdList(new ScrollIterable(client, searchRequest)))
                .containsOnly(id1, id2, id3);
        }
    }

    private List<String> convertToIdList(ScrollIterable scrollIterable) {
        return scrollIterable.stream()
            .flatMap(searchResponse -> Arrays.stream(searchResponse.getHits().getHits()))
            .map(SearchHit::getId)
            .collect(Collectors.toList());
    }

    private void hasIdsInIndex(RestHighLevelClient client, String... ids) throws IOException {
        SearchRequest searchRequest = new SearchRequest(INDEX_NAME.getValue())
            .types(TYPE_NAME.getValue())
            .scroll(TIMEOUT)
            .source(new SearchSourceBuilder()
                .query(QueryBuilders.matchAllQuery()));

        SearchHit[] hits = client.search(searchRequest, RequestOptions.DEFAULT)
            .getHits()
            .getHits();

        assertThat(hits)
            .extracting(SearchHit::getId)
            .contains(ids);
    }
}
