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

package org.apache.james.backends.es;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import java.util.concurrent.Executors;

import org.apache.james.util.concurrent.NamedThreadFactory;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.index.query.QueryBuilders;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import com.google.common.collect.ImmutableList;

public class ElasticSearchIndexerTest {

    private static final int MINIMUM_BATCH_SIZE = 1;
    private static final IndexName INDEX_NAME = new IndexName("index_name");
    private static final WriteAliasName ALIAS_NAME = new WriteAliasName("alias_name");
    private static final TypeName TYPE_NAME = new TypeName("type_name");

    @Rule
    public DockerElasticSearchRule elasticSearch = new DockerElasticSearchRule();
    private ElasticSearchIndexer testee;

    @Before
    public void setup() {
        new IndexCreationFactory(ElasticSearchConfiguration.DEFAULT_CONFIGURATION)
            .useIndex(INDEX_NAME)
            .addAlias(ALIAS_NAME)
            .createIndexAndAliases(getESClient());
        testee = new ElasticSearchIndexer(getESClient(),
            Executors.newSingleThreadExecutor(NamedThreadFactory.withClassName(getClass())),
            ALIAS_NAME, TYPE_NAME, MINIMUM_BATCH_SIZE);
    }

    private Client getESClient() {
        return elasticSearch.clientProvider().get();
    }

    @Test
    public void indexMessageShouldWork() {
        String messageId = "1";
        String content = "{\"message\": \"trying out Elasticsearch\"}";
        
        testee.index(messageId, content);
        elasticSearch.awaitForElasticSearch();
        
        try (Client client = getESClient()) {
            SearchResponse searchResponse = client.prepareSearch(INDEX_NAME.getValue())
                    .setTypes(TYPE_NAME.getValue())
                    .setQuery(QueryBuilders.matchQuery("message", "trying"))
                    .get();
            assertThat(searchResponse.getHits().getTotalHits()).isEqualTo(1);
        }
    }
    
    @Test
    public void indexMessageShouldThrowWhenJsonIsNull() {
        assertThatThrownBy(() -> testee.index("1", null))
            .isInstanceOf(IllegalArgumentException.class);
    }
    
    @Test
    public void updateMessages() {
        String messageId = "1";
        String content = "{\"message\": \"trying out Elasticsearch\",\"field\":\"Should be unchanged\"}";

        testee.index(messageId, content);
        elasticSearch.awaitForElasticSearch();

        testee.update(ImmutableList.of(new UpdatedRepresentation(messageId, "{\"message\": \"mastering out Elasticsearch\"}")));
        elasticSearch.awaitForElasticSearch();

        try (Client client = getESClient()) {
            SearchResponse searchResponse = client.prepareSearch(INDEX_NAME.getValue())
                .setTypes(TYPE_NAME.getValue())
                .setQuery(QueryBuilders.matchQuery("message", "mastering"))
                .get();
            assertThat(searchResponse.getHits().getTotalHits()).isEqualTo(1);
        }

        try (Client client = getESClient()) {
            SearchResponse searchResponse = client.prepareSearch(INDEX_NAME.getValue())
                .setTypes(TYPE_NAME.getValue())
                .setQuery(QueryBuilders.matchQuery("field", "unchanged"))
                .get();
            assertThat(searchResponse.getHits().getTotalHits()).isEqualTo(1);
        }
    }

    @Test
    public void updateMessageShouldThrowWhenJsonIsNull() {
        assertThatThrownBy(() -> testee.update(ImmutableList.of(new UpdatedRepresentation("1", null))))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void updateMessageShouldThrowWhenIdIsNull() {
        assertThatThrownBy(() -> testee.update(ImmutableList.of(new UpdatedRepresentation(null, "{\"message\": \"mastering out Elasticsearch\"}"))))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void updateMessageShouldThrowWhenJsonIsEmpty() {
        assertThatThrownBy(() -> testee.update(ImmutableList.of(new UpdatedRepresentation("1", ""))))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void updateMessageShouldThrowWhenIdIsEmpty() {
        assertThatThrownBy(() -> testee.update(ImmutableList.of(new UpdatedRepresentation("", "{\"message\": \"mastering out Elasticsearch\"}"))))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void deleteByQueryShouldWorkOnSingleMessage() throws Exception {
        String messageId = "1:2";
        String content = "{\"message\": \"trying out Elasticsearch\", \"property\":\"1\"}";

        testee.index(messageId, content);
        elasticSearch.awaitForElasticSearch();
        
        testee.deleteAllMatchingQuery(termQuery("property", "1")).get();
        elasticSearch.awaitForElasticSearch();
        
        try (Client client = getESClient()) {
            SearchResponse searchResponse = client.prepareSearch(INDEX_NAME.getValue())
                    .setTypes(TYPE_NAME.getValue())
                    .setQuery(QueryBuilders.matchAllQuery())
                    .get();
            assertThat(searchResponse.getHits().getTotalHits()).isEqualTo(0);
        }
    }

    @Test
    public void deleteByQueryShouldWorkWhenMultipleMessages() throws Exception {
        String messageId = "1:1";
        String content = "{\"message\": \"trying out Elasticsearch\", \"property\":\"1\"}";
        
        testee.index(messageId, content);
        
        String messageId2 = "1:2";
        String content2 = "{\"message\": \"trying out Elasticsearch 2\", \"property\":\"1\"}";
        
        testee.index(messageId2, content2);
        
        String messageId3 = "2:3";
        String content3 = "{\"message\": \"trying out Elasticsearch 3\", \"property\":\"2\"}";
        
        testee.index(messageId3, content3);
        elasticSearch.awaitForElasticSearch();

        testee.deleteAllMatchingQuery(termQuery("property", "1")).get();
        elasticSearch.awaitForElasticSearch();
        
        try (Client client = getESClient()) {
            SearchResponse searchResponse = client.prepareSearch(INDEX_NAME.getValue())
                    .setTypes(TYPE_NAME.getValue())
                    .setQuery(QueryBuilders.matchAllQuery())
                    .get();
            assertThat(searchResponse.getHits().getTotalHits()).isEqualTo(1);
        }
    }
    
    @Test
    public void deleteMessage() {
        String messageId = "1:2";
        String content = "{\"message\": \"trying out Elasticsearch\"}";

        testee.index(messageId, content);
        elasticSearch.awaitForElasticSearch();

        testee.delete(ImmutableList.of(messageId));
        elasticSearch.awaitForElasticSearch();
        
        try (Client client = getESClient()) {
            SearchResponse searchResponse = client.prepareSearch(INDEX_NAME.getValue())
                    .setTypes(TYPE_NAME.getValue())
                    .setQuery(QueryBuilders.matchAllQuery())
                    .get();
            assertThat(searchResponse.getHits().getTotalHits()).isEqualTo(0);
        }
    }

    @Test
    public void deleteShouldWorkWhenMultipleMessages() {
        String messageId = "1:1";
        String content = "{\"message\": \"trying out Elasticsearch\", \"mailboxId\":\"1\"}";

        testee.index(messageId, content);

        String messageId2 = "1:2";
        String content2 = "{\"message\": \"trying out Elasticsearch 2\", \"mailboxId\":\"1\"}";

        testee.index(messageId2, content2);

        String messageId3 = "2:3";
        String content3 = "{\"message\": \"trying out Elasticsearch 3\", \"mailboxId\":\"2\"}";

        testee.index(messageId3, content3);
        elasticSearch.awaitForElasticSearch();

        testee.delete(ImmutableList.of(messageId, messageId3));
        elasticSearch.awaitForElasticSearch();

        try (Client client = getESClient()) {
            SearchResponse searchResponse = client.prepareSearch(INDEX_NAME.getValue())
                .setTypes(TYPE_NAME.getValue())
                .setQuery(QueryBuilders.matchAllQuery())
                .get();
            assertThat(searchResponse.getHits().getTotalHits()).isEqualTo(1);
        }
    }
    
    @Test
    public void updateMessagesShouldNotThrowWhenEmptyList() {
        testee.update(ImmutableList.of());
    }
    
    @Test
    public void deleteMessagesShouldNotThrowWhenEmptyList() {
        testee.delete(ImmutableList.of());
    }
}
