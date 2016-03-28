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

package org.apache.james.mailbox.elasticsearch.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.james.mailbox.elasticsearch.ClientProvider;
import org.apache.james.mailbox.elasticsearch.ElasticSearchIndexer;
import org.apache.james.mailbox.elasticsearch.EmbeddedElasticSearch;
import org.apache.james.mailbox.elasticsearch.IndexCreationFactory;
import org.apache.james.mailbox.elasticsearch.NodeMappingFactory;
import org.apache.james.mailbox.elasticsearch.utils.TestingClientProvider;
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

    private TemporaryFolder temporaryFolder = new TemporaryFolder();
    private EmbeddedElasticSearch embeddedElasticSearch= new EmbeddedElasticSearch(temporaryFolder);

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(temporaryFolder).around(embeddedElasticSearch);

    private ClientProvider clientProvider;

    @Before
    public void setUp() throws Exception {
        clientProvider = new TestingClientProvider(embeddedElasticSearch.getNode());
        IndexCreationFactory.createIndex(clientProvider);
        embeddedElasticSearch.awaitForElasticSearch();
        NodeMappingFactory.applyMapping(clientProvider, getMappingsSources());
    }

    private XContentBuilder getMappingsSources() throws IOException {
        return jsonBuilder()
            .startObject()
                .startObject(ElasticSearchIndexer.MESSAGE_TYPE)
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
            SearchRequestBuilder searchRequestBuilder = client.prepareSearch(ElasticSearchIndexer.MAILBOX_INDEX)
                .setTypes(ElasticSearchIndexer.MESSAGE_TYPE)
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
            client.prepareIndex(ElasticSearchIndexer.MAILBOX_INDEX, ElasticSearchIndexer.MESSAGE_TYPE, id)
                .setSource(MESSAGE, "Sample message")
                .execute();

            embeddedElasticSearch.awaitForElasticSearch();

            SearchRequestBuilder searchRequestBuilder = client.prepareSearch(ElasticSearchIndexer.MAILBOX_INDEX)
                .setTypes(ElasticSearchIndexer.MESSAGE_TYPE)
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
            client.prepareIndex(ElasticSearchIndexer.MAILBOX_INDEX, ElasticSearchIndexer.MESSAGE_TYPE, id1)
                .setSource(MESSAGE, "Sample message")
                .execute();

            String id2 = "2";
            client.prepareIndex(ElasticSearchIndexer.MAILBOX_INDEX, ElasticSearchIndexer.MESSAGE_TYPE, id2)
                .setSource(MESSAGE, "Sample message")
                .execute();

            embeddedElasticSearch.awaitForElasticSearch();

            SearchRequestBuilder searchRequestBuilder = client.prepareSearch(ElasticSearchIndexer.MAILBOX_INDEX)
                .setTypes(ElasticSearchIndexer.MESSAGE_TYPE)
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
            client.prepareIndex(ElasticSearchIndexer.MAILBOX_INDEX, ElasticSearchIndexer.MESSAGE_TYPE, id1)
                .setSource(MESSAGE, "Sample message")
                .execute();

            String id2 = "2";
            client.prepareIndex(ElasticSearchIndexer.MAILBOX_INDEX, ElasticSearchIndexer.MESSAGE_TYPE, id2)
                .setSource(MESSAGE, "Sample message")
                .execute();

            String id3 = "3";
            client.prepareIndex(ElasticSearchIndexer.MAILBOX_INDEX, ElasticSearchIndexer.MESSAGE_TYPE, id3)
                .setSource(MESSAGE, "Sample message")
                .execute();

            embeddedElasticSearch.awaitForElasticSearch();

            SearchRequestBuilder searchRequestBuilder = client.prepareSearch(ElasticSearchIndexer.MAILBOX_INDEX)
                .setTypes(ElasticSearchIndexer.MESSAGE_TYPE)
                .setScroll(TIMEOUT)
                .setQuery(matchAllQuery())
                .setSize(SIZE);
            assertThat(convertToIdList(new ScrollIterable(client, searchRequestBuilder))).containsOnly(id1, id2, id3);
        }
    }

    private List<String> convertToIdList(ScrollIterable scrollIterable) {
        return scrollIterable.stream()
            .flatMap(searchResponse -> Arrays.asList(searchResponse.getHits().getHits()).stream())
            .map(SearchHit::getId)
            .collect(Collectors.toList());
    }
}
