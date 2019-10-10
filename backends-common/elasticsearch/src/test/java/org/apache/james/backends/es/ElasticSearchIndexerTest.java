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
import static org.assertj.core.api.Assertions.assertThatCode;
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
    public static RoutingKey useDocumentId(DocumentId documentId) {
        return RoutingKey.fromString(documentId.asString());
    }

    private static final int MINIMUM_BATCH_SIZE = 1;
    private static final IndexName INDEX_NAME = new IndexName("index_name");
    private static final WriteAliasName ALIAS_NAME = new WriteAliasName("alias_name");

    private static final ConditionFactory CALMLY_AWAIT = Awaitility
        .with().pollInterval(ONE_HUNDRED_MILLISECONDS)
        .and().pollDelay(ONE_HUNDRED_MILLISECONDS)
        .await();
    private static final RoutingKey ROUTING = RoutingKey.fromString("routing");
    private static final DocumentId DOCUMENT_ID = DocumentId.fromString("1");

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
        DocumentId documentId = DocumentId.fromString("1");
        String content = "{\"message\": \"trying out Elasticsearch\"}";
        
        testee.index(documentId, content, useDocumentId(documentId));
        elasticSearch.awaitForElasticSearch();
        
        SearchResponse searchResponse = client.search(
            new SearchRequest(INDEX_NAME.getValue())
                .source(new SearchSourceBuilder().query(QueryBuilders.matchQuery("message", "trying"))),
            RequestOptions.DEFAULT);
        assertThat(searchResponse.getHits().getTotalHits()).isEqualTo(1);
    }
    
    @Test
    public void indexMessageShouldThrowWhenJsonIsNull() {
        assertThatThrownBy(() -> testee.index(DOCUMENT_ID, null, ROUTING))
            .isInstanceOf(IllegalArgumentException.class);
    }
    
    @Test
    public void updateMessages() throws Exception {
        String content = "{\"message\": \"trying out Elasticsearch\",\"field\":\"Should be unchanged\"}";

        testee.index(DOCUMENT_ID, content, useDocumentId(DOCUMENT_ID));
        elasticSearch.awaitForElasticSearch();

        testee.update(ImmutableList.of(new UpdatedRepresentation(DOCUMENT_ID, "{\"message\": \"mastering out Elasticsearch\"}")), useDocumentId(DOCUMENT_ID));
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
        assertThatThrownBy(() -> testee.update(ImmutableList.of(
                new UpdatedRepresentation(DOCUMENT_ID, null)), ROUTING))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void updateMessageShouldThrowWhenIdIsNull() {
        assertThatThrownBy(() -> testee.update(ImmutableList.of(
                new UpdatedRepresentation(null, "{\"message\": \"mastering out Elasticsearch\"}")), ROUTING))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void updateMessageShouldThrowWhenJsonIsEmpty() {
        assertThatThrownBy(() -> testee.update(ImmutableList.of(
                new UpdatedRepresentation(DOCUMENT_ID, "")), ROUTING))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void updateMessageShouldThrowWhenRoutingKeyIsNull() {
        assertThatThrownBy(() -> testee.update(ImmutableList.of(
                new UpdatedRepresentation(DOCUMENT_ID, "{\"message\": \"mastering out Elasticsearch\"}")), null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void deleteByQueryShouldWorkOnSingleMessage() throws Exception {
        DocumentId documentId =  DocumentId.fromString("1:2");
        String content = "{\"message\": \"trying out Elasticsearch\", \"property\":\"1\"}";
        RoutingKey routingKey = useDocumentId(documentId);

        testee.index(documentId, content, routingKey);
        elasticSearch.awaitForElasticSearch();
        
        testee.deleteAllMatchingQuery(termQuery("property", "1"), routingKey);
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
        DocumentId documentId = DocumentId.fromString("1:1");
        String content = "{\"message\": \"trying out Elasticsearch\", \"property\":\"1\"}";
        
        testee.index(documentId, content, ROUTING);

        DocumentId documentId2 = DocumentId.fromString("1:2");
        String content2 = "{\"message\": \"trying out Elasticsearch 2\", \"property\":\"1\"}";
        
        testee.index(documentId2, content2, ROUTING);

        DocumentId documentId3 = DocumentId.fromString("2:3");
        String content3 = "{\"message\": \"trying out Elasticsearch 3\", \"property\":\"2\"}";
        
        testee.index(documentId3, content3, ROUTING);
        elasticSearch.awaitForElasticSearch();

        testee.deleteAllMatchingQuery(termQuery("property", "1"), ROUTING);
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
        DocumentId documentId = DocumentId.fromString("1:2");
        String content = "{\"message\": \"trying out Elasticsearch\"}";

        testee.index(documentId, content, useDocumentId(documentId));
        elasticSearch.awaitForElasticSearch();

        testee.delete(ImmutableList.of(documentId), useDocumentId(documentId));
        elasticSearch.awaitForElasticSearch();
        
        SearchResponse searchResponse = client.search(
            new SearchRequest(INDEX_NAME.getValue())
                .source(new SearchSourceBuilder().query(QueryBuilders.matchAllQuery())),
            RequestOptions.DEFAULT);
        assertThat(searchResponse.getHits().getTotalHits()).isEqualTo(0);
    }

    @Test
    public void deleteShouldWorkWhenMultipleMessages() throws Exception {
        DocumentId documentId = DocumentId.fromString("1:1");
        String content = "{\"message\": \"trying out Elasticsearch\", \"mailboxId\":\"1\"}";

        testee.index(documentId, content, ROUTING);

        DocumentId documentId2 = DocumentId.fromString("1:2");
        String content2 = "{\"message\": \"trying out Elasticsearch 2\", \"mailboxId\":\"1\"}";

        testee.index(documentId2, content2, ROUTING);

        DocumentId documentId3 = DocumentId.fromString("2:3");
        String content3 = "{\"message\": \"trying out Elasticsearch 3\", \"mailboxId\":\"2\"}";

        testee.index(documentId3, content3, ROUTING);
        elasticSearch.awaitForElasticSearch();

        testee.delete(ImmutableList.of(documentId, documentId3), ROUTING);
        elasticSearch.awaitForElasticSearch();

        SearchResponse searchResponse = client.search(
            new SearchRequest(INDEX_NAME.getValue())
                .source(new SearchSourceBuilder().query(QueryBuilders.matchAllQuery())),
            RequestOptions.DEFAULT);
        assertThat(searchResponse.getHits().getTotalHits()).isEqualTo(1);
    }
    
    @Test
    public void updateMessagesShouldNotThrowWhenEmptyList() {
        assertThatCode(() -> testee.update(ImmutableList.of(), ROUTING))
            .doesNotThrowAnyException();
    }
    
    @Test
    public void deleteMessagesShouldNotThrowWhenEmptyList() {
        assertThatCode(() -> testee.delete(ImmutableList.of(), ROUTING))
            .doesNotThrowAnyException();
    }
}
