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

package org.apache.james.backends.opensearch.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.util.List;

import org.apache.james.backends.opensearch.DockerOpenSearchExtension;
import org.apache.james.backends.opensearch.IndexCreationFactory;
import org.apache.james.backends.opensearch.IndexName;
import org.apache.james.backends.opensearch.OpenSearchConfiguration;
import org.apache.james.backends.opensearch.ReactorOpenSearchClient;
import org.apache.james.backends.opensearch.ReadAliasName;
import org.awaitility.Durations;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.opensearch.client.opensearch._types.Time;
import org.opensearch.client.opensearch._types.query_dsl.MatchAllQuery;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.search.Hit;

import com.fasterxml.jackson.databind.util.RawValue;

class ScrolledSearchTest {
    private static final Time TIMEOUT = new Time.Builder()
        .time("1m")
        .build();
    private static final int SIZE = 2;
    private static final String CONTENT = "{\"message\": \"Sample message\"}";
    private static final IndexName INDEX_NAME = new IndexName("index");
    private static final ReadAliasName ALIAS_NAME = new ReadAliasName("alias");

    private static final ConditionFactory WAIT_CONDITION = await().timeout(Durations.FIVE_SECONDS);

    @RegisterExtension
    public static DockerOpenSearchExtension openSearch = new DockerOpenSearchExtension();
    private ReactorOpenSearchClient client;

    @BeforeEach
    void setUp() {
        client = openSearch.getDockerOpenSearch().clientProvider().get();
        new IndexCreationFactory(OpenSearchConfiguration.DEFAULT_CONFIGURATION)
            .useIndex(INDEX_NAME)
            .addAlias(ALIAS_NAME)
            .createIndexAndAliases(client);
        openSearch.awaitForOpenSearch();
    }

    @AfterEach
    void tearDown() throws IOException {
        client.close();
    }

    @Test
    void scrollIterableShouldWorkWhenEmpty() {
        SearchRequest searchRequest = new SearchRequest.Builder()
            .index(INDEX_NAME.getValue())
            .scroll(TIMEOUT)
            .query(new MatchAllQuery.Builder().build()._toQuery())
            .size(SIZE)
            .build();

        assertThat(new ScrolledSearch(client, searchRequest).searchHits().collectList().block())
            .isEmpty();
    }

    @Test
    void scrollIterableShouldWorkWhenOneElement() throws IOException {
        String id = "1";
        client.index(new IndexRequest.Builder<>()
                .index(INDEX_NAME.getValue())
                .id(id)
                .document(new RawValue(CONTENT))
                .build())
            .block();

        openSearch.awaitForOpenSearch();
        WAIT_CONDITION.untilAsserted(() -> hasIdsInIndex(client, id));

        SearchRequest searchRequest = new SearchRequest.Builder()
            .index(INDEX_NAME.getValue())
            .scroll(TIMEOUT)
            .query(new MatchAllQuery.Builder().build()._toQuery())
            .size(SIZE)
            .build();

        assertThat(new ScrolledSearch(client, searchRequest).searchHits().collectList().block())
            .extracting(Hit::id)
            .containsOnly(id);
    }

    @Test
    void scrollIterableShouldWorkWhenSizeElement() throws IOException {
        String id1 = "1";
        client.index(new IndexRequest.Builder<>()
                .index(INDEX_NAME.getValue())
                .id(id1)
                .document(new RawValue(CONTENT))
                .build())
            .block();

        String id2 = "2";
        client.index(new IndexRequest.Builder<>()
                .index(INDEX_NAME.getValue())
                .id(id2)
                .document(new RawValue(CONTENT))
                .build())
            .block();

        openSearch.awaitForOpenSearch();
        WAIT_CONDITION.untilAsserted(() -> hasIdsInIndex(client, id1, id2));

        SearchRequest searchRequest = new SearchRequest.Builder()
            .index(INDEX_NAME.getValue())
            .scroll(TIMEOUT)
            .query(new MatchAllQuery.Builder().build()._toQuery())
            .size(SIZE)
            .build();

        assertThat(new ScrolledSearch(client, searchRequest).searchHits().collectList().block())
            .extracting(Hit::id)
            .containsOnly(id1, id2);
    }

    @Test
    void scrollIterableShouldWorkWhenMoreThanSizeElement() throws IOException {
        String id1 = "1";
        client.index(new IndexRequest.Builder<>()
                .index(INDEX_NAME.getValue())
                .id(id1)
                .document(new RawValue(CONTENT))
                .build())
            .block();

        String id2 = "2";
        client.index(new IndexRequest.Builder<>()
                .index(INDEX_NAME.getValue())
                .id(id2)
                .document(new RawValue(CONTENT))
                .build())
            .block();

        String id3 = "3";
        client.index(new IndexRequest.Builder<>()
                .index(INDEX_NAME.getValue())
                .id(id3)
                .document(new RawValue(CONTENT))
                .build())
            .block();

        openSearch.awaitForOpenSearch();
        WAIT_CONDITION.untilAsserted(() -> hasIdsInIndex(client, id1, id2, id3));

        SearchRequest searchRequest = new SearchRequest.Builder()
            .index(INDEX_NAME.getValue())
            .scroll(TIMEOUT)
            .query(new MatchAllQuery.Builder().build()._toQuery())
            .size(SIZE)
            .build();

        assertThat(new ScrolledSearch(client, searchRequest).searchHits().collectList().block())
            .extracting(Hit::id)
            .containsOnly(id1, id2, id3);
    }

    private void hasIdsInIndex(ReactorOpenSearchClient client, String... ids) throws IOException {
        SearchRequest searchRequest = new SearchRequest.Builder()
            .index(INDEX_NAME.getValue())
            .query(new MatchAllQuery.Builder().build()._toQuery())
            .build();

        List<String> hitIds = client.search(searchRequest)
            .flatMapIterable(response -> response.hits().hits())
            .map(Hit::id)
            .collectList()
            .block();

        assertThat(hitIds)
            .contains(ids);
    }
}
