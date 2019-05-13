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
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.james.backends.es.v6.ClientProvider;
import org.apache.james.backends.es.v6.DockerElasticSearchRule;
import org.apache.james.backends.es.v6.ElasticSearchConfiguration;
import org.apache.james.backends.es.v6.IndexCreationFactory;
import org.apache.james.backends.es.v6.IndexName;
import org.apache.james.backends.es.v6.NodeMappingFactory;
import org.apache.james.backends.es.v6.ReadAliasName;
import org.apache.james.backends.es.v6.TypeName;
import org.awaitility.Duration;
import org.awaitility.core.ConditionFactory;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
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
    public void setUp() throws Exception {
        clientProvider = elasticSearch.clientProvider();
        new IndexCreationFactory(ElasticSearchConfiguration.DEFAULT_CONFIGURATION)
            .useIndex(INDEX_NAME)
            .addAlias(ALIAS_NAME)
            .createIndexAndAliases(clientProvider.get());
        elasticSearch.awaitForElasticSearch();
        NodeMappingFactory.applyMapping(clientProvider.get(), INDEX_NAME, TYPE_NAME, getMappingsSources());
    }

    private XContentBuilder getMappingsSources() throws IOException {
        return jsonBuilder()
            .startObject()
                .startObject(TYPE_NAME.getValue())
                    .startObject(NodeMappingFactory.PROPERTIES)
                        .startObject(MESSAGE)
                            .field(NodeMappingFactory.TYPE, NodeMappingFactory.STRING)
                        .endObject()
                    .endObject()
                .endObject()
            .endObject();
    }

    @Test
    public void scrollIterableShouldWorkWhenEmpty() {
        try (Client client = clientProvider.get()) {
            SearchRequestBuilder searchRequestBuilder = client.prepareSearch(INDEX_NAME.getValue())
                .setTypes(TYPE_NAME.getValue())
                .setScroll(TIMEOUT)
                .setQuery(matchAllQuery())
                .setSize(SIZE);

            assertThat(new ScrollIterable(client, searchRequestBuilder))
                .isEmpty();
        }
    }

    @Test
    public void scrollIterableShouldWorkWhenOneElement() {
        try (Client client = clientProvider.get()) {
            String id = "1";
            client.prepareIndex(INDEX_NAME.getValue(), TYPE_NAME.getValue(), id)
                .setSource(MESSAGE, "Sample message")
                .execute();

            elasticSearch.awaitForElasticSearch();
            WAIT_CONDITION.untilAsserted(() -> hasIdsInIndex(client, id));

            SearchRequestBuilder searchRequestBuilder = client.prepareSearch(INDEX_NAME.getValue())
                .setTypes(TYPE_NAME.getValue())
                .setScroll(TIMEOUT)
                .setQuery(matchAllQuery())
                .setSize(SIZE);

            assertThat(convertToIdList(new ScrollIterable(client, searchRequestBuilder)))
                .containsOnly(id);
        }
    }

    @Test
    public void scrollIterableShouldWorkWhenSizeElement() {
        try (Client client = clientProvider.get()) {
            String id1 = "1";
            client.prepareIndex(INDEX_NAME.getValue(), TYPE_NAME.getValue(), id1)
                .setSource(MESSAGE, "Sample message")
                .execute();

            String id2 = "2";
            client.prepareIndex(INDEX_NAME.getValue(), TYPE_NAME.getValue(), id2)
                .setSource(MESSAGE, "Sample message")
                .execute();

            elasticSearch.awaitForElasticSearch();
            WAIT_CONDITION.untilAsserted(() -> hasIdsInIndex(client, id1, id2));

            SearchRequestBuilder searchRequestBuilder = client.prepareSearch(INDEX_NAME.getValue())
                .setTypes(TYPE_NAME.getValue())
                .setScroll(TIMEOUT)
                .setQuery(matchAllQuery())
                .setSize(SIZE);

            assertThat(convertToIdList(new ScrollIterable(client, searchRequestBuilder)))
                .containsOnly(id1, id2);
        }
    }

    @Test
    public void scrollIterableShouldWorkWhenMoreThanSizeElement() {
        try (Client client = clientProvider.get()) {
            String id1 = "1";
            client.prepareIndex(INDEX_NAME.getValue(), TYPE_NAME.getValue(), id1)
                .setSource(MESSAGE, "Sample message")
                .execute();

            String id2 = "2";
            client.prepareIndex(INDEX_NAME.getValue(), TYPE_NAME.getValue(), id2)
                .setSource(MESSAGE, "Sample message")
                .execute();

            String id3 = "3";
            client.prepareIndex(INDEX_NAME.getValue(), TYPE_NAME.getValue(), id3)
                .setSource(MESSAGE, "Sample message")
                .execute();

            elasticSearch.awaitForElasticSearch();
            WAIT_CONDITION.untilAsserted(() -> hasIdsInIndex(client, id1, id2, id3));

            SearchRequestBuilder searchRequestBuilder = client.prepareSearch(INDEX_NAME.getValue())
                .setTypes(TYPE_NAME.getValue())
                .setScroll(TIMEOUT)
                .setQuery(matchAllQuery())
                .setSize(SIZE);

            assertThat(convertToIdList(new ScrollIterable(client, searchRequestBuilder)))
                .containsOnly(id1, id2, id3);
        }
    }

    private List<String> convertToIdList(ScrollIterable scrollIterable) {
        return scrollIterable.stream()
            .flatMap(searchResponse -> Arrays.stream(searchResponse.getHits().getHits()))
            .map(SearchHit::getId)
            .collect(Collectors.toList());
    }

    private void hasIdsInIndex(Client client, String... ids) {
        SearchHit[] hits = client.prepareSearch(INDEX_NAME.getValue())
            .setQuery(QueryBuilders.idsQuery(TYPE_NAME.getValue()).addIds(ids))
            .execute()
            .actionGet()
            .getHits()
            .hits();

        assertThat(hits)
            .extracting(SearchHit::getId)
            .contains(ids);
    }
}
