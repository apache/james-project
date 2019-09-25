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
import static org.awaitility.Duration.ONE_HUNDRED_MILLISECONDS;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import java.io.IOException;

import org.awaitility.Awaitility;
import org.awaitility.Duration;
import org.awaitility.core.ConditionFactory;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import com.google.common.collect.ImmutableList;

public class ElasticSearchIndexerTest {

    private static final int MINIMUM_BATCH_SIZE = 1;
    private static final IndexName INDEX_NAME = new IndexName("index_name");
    private static final WriteAliasName ALIAS_NAME = new WriteAliasName("alias_name");

    private static final ConditionFactory CALMLY_AWAIT = Awaitility
        .with().pollInterval(ONE_HUNDRED_MILLISECONDS)
        .and().pollDelay(ONE_HUNDRED_MILLISECONDS)
        .await();

    @Rule
    public DockerElasticSearchRule elasticSearch = new DockerElasticSearchRule();
    private ElasticSearchIndexer testee;
    private RestHighLevelClient client;

    @Before
    public void setup() {
        client = elasticSearch.clientProvider().get();
        new IndexCreationFactory(ElasticSearchConfiguration.DEFAULT_CONFIGURATION)
            .useIndex(INDEX_NAME)
            .addAlias(ALIAS_NAME)
            .createIndexAndAliases(client);
        testee = new ElasticSearchIndexer(client, ALIAS_NAME, MINIMUM_BATCH_SIZE);
    }

    @After
    public void tearDown() throws IOException {
        client.close();
    }

    @Test
    public void indexMessageShouldWork() throws Exception {
        String messageId = "1";
        String content = "{\"message\": \"trying out Elasticsearch\"}";
        
        testee.index(messageId, content);
        elasticSearch.awaitForElasticSearch();
        
        SearchResponse searchResponse = client.search(
            new SearchRequest(INDEX_NAME.getValue())
                .source(new SearchSourceBuilder().query(QueryBuilders.matchQuery("message", "trying"))),
            RequestOptions.DEFAULT);
        assertThat(searchResponse.getHits().getTotalHits()).isEqualTo(1);
    }
    
    @Test
    public void indexMessageShouldThrowWhenJsonIsNull() {
        assertThatThrownBy(() -> testee.index("1", null))
            .isInstanceOf(IllegalArgumentException.class);
    }
    
    @Test
    public void updateMessages() throws Exception {
        String messageId = "1";
        String content = "{\"message\": \"trying out Elasticsearch\",\"field\":\"Should be unchanged\"}";

        testee.index(messageId, content);
        elasticSearch.awaitForElasticSearch();

        testee.update(ImmutableList.of(new UpdatedRepresentation(messageId, "{\"message\": \"mastering out Elasticsearch\"}")));
        elasticSearch.awaitForElasticSearch();


        SearchResponse searchResponse = client.search(
            new SearchRequest(INDEX_NAME.getValue())
                .source(new SearchSourceBuilder().query(QueryBuilders.matchQuery("message", "mastering"))),
            RequestOptions.DEFAULT);
        assertThat(searchResponse.getHits().getTotalHits()).isEqualTo(1);

        SearchResponse searchResponse2 = client.search(
            new SearchRequest(INDEX_NAME.getValue())
                .source(new SearchSourceBuilder().query(QueryBuilders.matchQuery("field", "unchanged"))),
            RequestOptions.DEFAULT);
        assertThat(searchResponse2.getHits().getTotalHits()).isEqualTo(1);
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
        
        testee.deleteAllMatchingQuery(termQuery("property", "1"));
        elasticSearch.awaitForElasticSearch();
        
        CALMLY_AWAIT.atMost(Duration.TEN_SECONDS)
            .until(() -> client.search(
                    new SearchRequest(INDEX_NAME.getValue())
                        .source(new SearchSourceBuilder().query(QueryBuilders.matchAllQuery())),
                    RequestOptions.DEFAULT)
                .getHits().getTotalHits() == 0);
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

        testee.deleteAllMatchingQuery(termQuery("property", "1"));
        elasticSearch.awaitForElasticSearch();
        
        CALMLY_AWAIT.atMost(Duration.TEN_SECONDS)
            .until(() -> client.search(
                    new SearchRequest(INDEX_NAME.getValue())
                        .source(new SearchSourceBuilder().query(QueryBuilders.matchAllQuery())),
                    RequestOptions.DEFAULT)
                .getHits().getTotalHits() == 1);
    }
    
    @Test
    public void deleteMessage() throws Exception {
        String messageId = "1:2";
        String content = "{\"message\": \"trying out Elasticsearch\"}";

        testee.index(messageId, content);
        elasticSearch.awaitForElasticSearch();

        testee.delete(ImmutableList.of(messageId));
        elasticSearch.awaitForElasticSearch();
        
        SearchResponse searchResponse = client.search(
            new SearchRequest(INDEX_NAME.getValue())
                .source(new SearchSourceBuilder().query(QueryBuilders.matchAllQuery())),
            RequestOptions.DEFAULT);
        assertThat(searchResponse.getHits().getTotalHits()).isEqualTo(0);
    }

    @Test
    public void deleteShouldWorkWhenMultipleMessages() throws Exception {
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

        SearchResponse searchResponse = client.search(
            new SearchRequest(INDEX_NAME.getValue())
                .source(new SearchSourceBuilder().query(QueryBuilders.matchAllQuery())),
            RequestOptions.DEFAULT);
        assertThat(searchResponse.getHits().getTotalHits()).isEqualTo(1);
    }
    
    @Test
    public void updateMessagesShouldNotThrowWhenEmptyList() throws Exception {
        testee.update(ImmutableList.of());
    }
    
    @Test
    public void deleteMessagesShouldNotThrowWhenEmptyList() throws Exception {
        testee.delete(ImmutableList.of());
    }
}
