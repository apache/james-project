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
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.james.backends.es.AliasName;
import org.apache.james.backends.es.ClientProvider;
import org.apache.james.backends.es.EmbeddedElasticSearch;
import org.apache.james.backends.es.IndexCreationFactory;
import org.apache.james.backends.es.IndexName;
import org.apache.james.backends.es.NodeMappingFactory;
import org.apache.james.backends.es.TypeName;
import org.apache.james.backends.es.utils.TestingClientProvider;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.search.SearchHit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;

public class ScrollIterableTest {

    public static final TimeValue TIMEOUT = new TimeValue(6000);
    public static final int SIZE = 2;
    public static final String MESSAGE = "message";
    public static final IndexName INDEX_NAME = new IndexName("index");
    public static final AliasName ALIAS_NAME = new AliasName("alias");
    public static final TypeName TYPE_NAME = new TypeName("messages");

    private TemporaryFolder temporaryFolder = new TemporaryFolder();
    private EmbeddedElasticSearch embeddedElasticSearch= new EmbeddedElasticSearch(temporaryFolder, INDEX_NAME);

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(temporaryFolder).around(embeddedElasticSearch);

    private ClientProvider clientProvider;

    @Before
    public void setUp() throws Exception {
        clientProvider = new TestingClientProvider(embeddedElasticSearch.getNode());
        IndexCreationFactory.createIndexAndAlias(clientProvider.get(), INDEX_NAME, ALIAS_NAME);
        embeddedElasticSearch.awaitForElasticSearch();
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
            assertThat(new ScrollIterable(client, searchRequestBuilder)).isEmpty();
        }
    }

    @Test
    public void scrollIterableShouldWorkWhenOneElement() {
        try (Client client = clientProvider.get()) {
            String id = "1";
            client.prepareIndex(INDEX_NAME.getValue(), TYPE_NAME.getValue(), id)
                .setSource(MESSAGE, "Sample message")
                .execute();

            embeddedElasticSearch.awaitForElasticSearch();

            SearchRequestBuilder searchRequestBuilder = client.prepareSearch(INDEX_NAME.getValue())
                .setTypes(TYPE_NAME.getValue())
                .setScroll(TIMEOUT)
                .setQuery(matchAllQuery())
                .setSize(SIZE);
            assertThat(convertToIdList(new ScrollIterable(client, searchRequestBuilder))).containsOnly(id);
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

            embeddedElasticSearch.awaitForElasticSearch();

            SearchRequestBuilder searchRequestBuilder = client.prepareSearch(INDEX_NAME.getValue())
                .setTypes(TYPE_NAME.getValue())
                .setScroll(TIMEOUT)
                .setQuery(matchAllQuery())
                .setSize(SIZE);
            assertThat(convertToIdList(new ScrollIterable(client, searchRequestBuilder))).containsOnly(id1, id2);
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

            embeddedElasticSearch.awaitForElasticSearch();

            SearchRequestBuilder searchRequestBuilder = client.prepareSearch(INDEX_NAME.getValue())
                .setTypes(TYPE_NAME.getValue())
                .setScroll(TIMEOUT)
                .setQuery(matchAllQuery())
                .setSize(SIZE);
            assertThat(convertToIdList(new ScrollIterable(client, searchRequestBuilder))).containsOnly(id1, id2, id3);
        }
    }

    private List<String> convertToIdList(ScrollIterable scrollIterable) {
        return scrollIterable.stream()
            .flatMap(searchResponse -> Arrays.stream(searchResponse.getHits().getHits()))
            .map(SearchHit::getId)
            .collect(Collectors.toList());
    }
}
