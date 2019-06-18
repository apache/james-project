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

package org.apache.james.backends.es.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.IOException;

import org.apache.james.backends.es.ClientProvider;
import org.apache.james.backends.es.DockerElasticSearchRule;
import org.apache.james.backends.es.ElasticSearchConfiguration;
import org.apache.james.backends.es.IndexCreationFactory;
import org.apache.james.backends.es.IndexName;
import org.apache.james.backends.es.NodeMappingFactory;
import org.apache.james.backends.es.ReadAliasName;
import org.awaitility.Duration;
import org.awaitility.core.ConditionFactory;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class ScrolledSearchTest {
    private static final TimeValue TIMEOUT = TimeValue.timeValueMinutes(1);
    private static final int SIZE = 2;
    private static final String MESSAGE = "message";
    private static final IndexName INDEX_NAME = new IndexName("index");
    private static final ReadAliasName ALIAS_NAME = new ReadAliasName("alias");

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
                .scroll(TIMEOUT)
                .source(new SearchSourceBuilder()
                    .query(QueryBuilders.matchAllQuery())
                    .size(SIZE));

            assertThat(new ScrolledSearch(client, searchRequest).searchHits())
                .isEmpty();
        }
    }

    @Test
    public void scrollIterableShouldWorkWhenOneElement() throws Exception {
        try (RestHighLevelClient client = clientProvider.get()) {
            String id = "1";
            client.index(new IndexRequest(INDEX_NAME.getValue())
                    .type(NodeMappingFactory.DEFAULT_MAPPING_NAME)
                    .id(id)
                    .source(MESSAGE, "Sample message"));

            elasticSearch.awaitForElasticSearch();
            WAIT_CONDITION.untilAsserted(() -> hasIdsInIndex(client, id));

            SearchRequest searchRequest = new SearchRequest(INDEX_NAME.getValue())
                .scroll(TIMEOUT)
                .source(new SearchSourceBuilder()
                    .query(QueryBuilders.matchAllQuery())
                    .size(SIZE));

            assertThat(new ScrolledSearch(client, searchRequest).searchHits())
                .extracting(SearchHit::getId)
                .containsOnly(id);
        }
    }

    @Test
    public void scrollIterableShouldWorkWhenSizeElement() throws Exception {
        try (RestHighLevelClient client = clientProvider.get()) {
            String id1 = "1";
            client.index(new IndexRequest(INDEX_NAME.getValue())
                    .type(NodeMappingFactory.DEFAULT_MAPPING_NAME)
                    .id(id1)
                    .source(MESSAGE, "Sample message"));

            String id2 = "2";
            client.index(new IndexRequest(INDEX_NAME.getValue())
                    .type(NodeMappingFactory.DEFAULT_MAPPING_NAME)
                    .id(id2)
                    .source(MESSAGE, "Sample message"));

            elasticSearch.awaitForElasticSearch();
            WAIT_CONDITION.untilAsserted(() -> hasIdsInIndex(client, id1, id2));

            SearchRequest searchRequest = new SearchRequest(INDEX_NAME.getValue())
                .scroll(TIMEOUT)
                .source(new SearchSourceBuilder()
                    .query(QueryBuilders.matchAllQuery())
                    .size(SIZE));

            assertThat(new ScrolledSearch(client, searchRequest).searchHits())
                .extracting(SearchHit::getId)
                .containsOnly(id1, id2);
        }
    }

    @Test
    public void scrollIterableShouldWorkWhenMoreThanSizeElement() throws Exception {
        try (RestHighLevelClient client = clientProvider.get()) {
            String id1 = "1";
            client.index(new IndexRequest(INDEX_NAME.getValue())
                    .type(NodeMappingFactory.DEFAULT_MAPPING_NAME)
                    .id(id1)
                    .source(MESSAGE, "Sample message"));

            String id2 = "2";
            client.index(new IndexRequest(INDEX_NAME.getValue())
                    .type(NodeMappingFactory.DEFAULT_MAPPING_NAME)
                    .id(id2)
                    .source(MESSAGE, "Sample message"));

            String id3 = "3";
            client.index(new IndexRequest(INDEX_NAME.getValue())
                    .type(NodeMappingFactory.DEFAULT_MAPPING_NAME)
                    .id(id3)
                    .source(MESSAGE, "Sample message"));

            elasticSearch.awaitForElasticSearch();
            WAIT_CONDITION.untilAsserted(() -> hasIdsInIndex(client, id1, id2, id3));

            SearchRequest searchRequest = new SearchRequest(INDEX_NAME.getValue())
                .scroll(TIMEOUT)
                .source(new SearchSourceBuilder()
                    .query(QueryBuilders.matchAllQuery())
                    .size(SIZE));

            assertThat(new ScrolledSearch(client, searchRequest).searchHits())
                .extracting(SearchHit::getId)
                .containsOnly(id1, id2, id3);
        }
    }

    private void hasIdsInIndex(RestHighLevelClient client, String... ids) throws IOException {
        SearchRequest searchRequest = new SearchRequest(INDEX_NAME.getValue())
            .scroll(TIMEOUT)
            .source(new SearchSourceBuilder()
                .query(QueryBuilders.matchAllQuery()));

        SearchHit[] hits = client.search(searchRequest)
            .getHits()
            .getHits();

        assertThat(hits)
            .extracting(SearchHit::getId)
            .contains(ids);
    }
}
