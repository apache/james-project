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

package org.apache.james.backends.opensearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;

import java.io.IOException;

import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.query_dsl.MatchAllQuery;
import org.opensearch.client.opensearch._types.query_dsl.MatchQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.TermQuery;
import org.opensearch.client.opensearch.core.GetResponse;
import org.opensearch.client.opensearch.core.SearchRequest;

import com.google.common.collect.ImmutableList;

class OpenSearchIndexerTest {
    public static RoutingKey useDocumentId(DocumentId documentId) {
        return RoutingKey.fromString(documentId.asString());
    }

    private static final IndexName INDEX_NAME = new IndexName("index_name");
    private static final WriteAliasName ALIAS_NAME = new WriteAliasName("alias_name");

    private static final ConditionFactory CALMLY_AWAIT = Awaitility
        .with().pollInterval(ONE_HUNDRED_MILLISECONDS)
        .and().pollDelay(ONE_HUNDRED_MILLISECONDS)
        .await();
    private static final RoutingKey ROUTING = RoutingKey.fromString("routing");
    private static final DocumentId DOCUMENT_ID = DocumentId.fromString("1");

    @RegisterExtension
    public static DockerOpenSearchExtension elasticSearch = new DockerOpenSearchExtension();
    private OpenSearchIndexer testee;
    private ReactorOpenSearchClient client;

    @BeforeEach
    void setup() {
        client = elasticSearch.getDockerOpenSearch().clientProvider().get();
        new IndexCreationFactory(OpenSearchConfiguration.DEFAULT_CONFIGURATION)
            .useIndex(INDEX_NAME)
            .addAlias(ALIAS_NAME)
            .createIndexAndAliases(client);
        testee = new OpenSearchIndexer(client, ALIAS_NAME);
    }

    @AfterEach
    void tearDown() throws IOException {
        client.close();
    }

    @Test
    void indexMessageShouldWork() throws IOException {
        DocumentId documentId = DocumentId.fromString("1");
        String content = "{\"message\": \"trying out Elasticsearch\"}";
        
        testee.index(documentId, content, useDocumentId(documentId)).block();

        awaitForOpenSearch(new MatchQuery.Builder()
            .field("message")
            .query(new FieldValue.Builder().stringValue("trying").build())
            .build()
            ._toQuery(), 1L);
    }
    
    @Test
    void indexMessageShouldThrowWhenJsonIsNull() {
        assertThatThrownBy(() -> testee.index(DOCUMENT_ID, null, ROUTING).block())
            .isInstanceOf(IllegalArgumentException.class);
    }
    
    @Test
    void updateMessages() throws IOException {
        String content = "{\"message\": \"trying out Elasticsearch\",\"field\":\"Should be unchanged\"}";

        testee.index(DOCUMENT_ID, content, useDocumentId(DOCUMENT_ID)).block();
        awaitForOpenSearch(new MatchAllQuery.Builder().build()._toQuery(), 1L);

        testee.update(ImmutableList.of(new UpdatedRepresentation(DOCUMENT_ID, "{\"message\": \"mastering out Elasticsearch\"}")), useDocumentId(DOCUMENT_ID)).block();
        awaitForOpenSearch(new MatchQuery.Builder().field("message").query(new FieldValue.Builder().stringValue("mastering").build()).build()._toQuery(), 1L);

        awaitForOpenSearch(new MatchQuery.Builder().field("field").query(new FieldValue.Builder().stringValue("unchanged").build()).build()._toQuery(), 1L);
    }

    @Test
    void updateMessageShouldThrowWhenJsonIsNull() {
        assertThatThrownBy(() -> testee.update(ImmutableList.of(
                new UpdatedRepresentation(DOCUMENT_ID, null)), ROUTING).block())
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateMessageShouldThrowWhenIdIsNull() {
        assertThatThrownBy(() -> testee.update(ImmutableList.of(
                new UpdatedRepresentation(null, "{\"message\": \"mastering out Elasticsearch\"}")), ROUTING).block())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void updateMessageShouldThrowWhenJsonIsEmpty() {
        assertThatThrownBy(() -> testee.update(ImmutableList.of(
                new UpdatedRepresentation(DOCUMENT_ID, "")), ROUTING).block())
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateMessageShouldThrowWhenRoutingKeyIsNull() {
        assertThatThrownBy(() -> testee.update(ImmutableList.of(
                new UpdatedRepresentation(DOCUMENT_ID, "{\"message\": \"mastering out Elasticsearch\"}")), null).block())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void deleteByQueryShouldWorkOnSingleMessage() throws IOException {
        DocumentId documentId =  DocumentId.fromString("1:2");
        String content = "{\"message\": \"trying out Elasticsearch\", \"property\":\"1\"}";
        RoutingKey routingKey = useDocumentId(documentId);

        testee.index(documentId, content, routingKey).block();
        awaitForOpenSearch(new MatchAllQuery.Builder().build()._toQuery(), 1L);

        testee.deleteAllMatchingQuery(new TermQuery.Builder().field("property").value(new FieldValue.Builder().stringValue("1").build()).build()._toQuery(), routingKey).block();

        awaitForOpenSearch(new MatchAllQuery.Builder().build()._toQuery(), 0L);
    }

    @Test
    void deleteByQueryShouldWorkWhenMultipleMessages() throws IOException {
        DocumentId documentId = DocumentId.fromString("1:1");
        String content = "{\"message\": \"trying out Elasticsearch\", \"property\":\"1\"}";
        
        testee.index(documentId, content, ROUTING).block();

        DocumentId documentId2 = DocumentId.fromString("1:2");
        String content2 = "{\"message\": \"trying out Elasticsearch 2\", \"property\":\"1\"}";
        
        testee.index(documentId2, content2, ROUTING).block();

        DocumentId documentId3 = DocumentId.fromString("2:3");
        String content3 = "{\"message\": \"trying out Elasticsearch 3\", \"property\":\"2\"}";
        
        testee.index(documentId3, content3, ROUTING).block();
        awaitForOpenSearch(new MatchAllQuery.Builder().build()._toQuery(), 3L);

        testee.deleteAllMatchingQuery(new TermQuery.Builder().field("property").value(new FieldValue.Builder().stringValue("1").build()).build()._toQuery(), ROUTING).block();

        awaitForOpenSearch(new MatchAllQuery.Builder().build()._toQuery(), 1L);
    }
    
    @Test
    void deleteMessage() throws IOException {
        DocumentId documentId = DocumentId.fromString("1:2");
        String content = "{\"message\": \"trying out Elasticsearch\"}";

        testee.index(documentId, content, useDocumentId(documentId)).block();
        awaitForOpenSearch(new MatchAllQuery.Builder().build()._toQuery(), 1L);

        testee.delete(ImmutableList.of(documentId), useDocumentId(documentId)).block();

        awaitForOpenSearch(new MatchAllQuery.Builder().build()._toQuery(), 0L);
    }

    @Test
    void deleteShouldWorkWhenMultipleMessages() throws IOException {
        DocumentId documentId = DocumentId.fromString("1:1");
        String content = "{\"message\": \"trying out Elasticsearch\", \"mailboxId\":\"1\"}";
        testee.index(documentId, content, ROUTING).block();

        DocumentId documentId2 = DocumentId.fromString("1:2");
        String content2 = "{\"message\": \"trying out Elasticsearch 2\", \"mailboxId\":\"1\"}";
        testee.index(documentId2, content2, ROUTING).block();

        DocumentId documentId3 = DocumentId.fromString("2:3");
        String content3 = "{\"message\": \"trying out Elasticsearch 3\", \"mailboxId\":\"2\"}";
        testee.index(documentId3, content3, ROUTING).block();

        awaitForOpenSearch(new MatchAllQuery.Builder().build()._toQuery(), 3L);

        testee.delete(ImmutableList.of(documentId, documentId3), ROUTING).block();

        awaitForOpenSearch(new MatchAllQuery.Builder().build()._toQuery(), 1L);
    }
    
    @Test
    void updateMessagesShouldNotThrowWhenEmptyList() {
        assertThatCode(() -> testee.update(ImmutableList.of(), ROUTING).block())
            .doesNotThrowAnyException();
    }
    
    @Test
    void deleteMessagesShouldNotThrowWhenEmptyList() {
        assertThatCode(() -> testee.delete(ImmutableList.of(), ROUTING).block())
            .doesNotThrowAnyException();
    }

    @Test
    void getShouldWork() throws IOException {
        DocumentId documentId = DocumentId.fromString("1");
        String content = "{\"message\":\"trying out Elasticsearch\"}";

        testee.index(documentId, content, useDocumentId(documentId)).block();
        awaitForOpenSearch(new MatchAllQuery.Builder().build()._toQuery(), 1L);

        GetResponse getResponse = testee.get(documentId, useDocumentId(documentId)).block();

        assertThat(getResponse.source().toString()).isEqualTo(content);
    }

    @Test
    void getShouldThrowWhenIdIsNull() {
        assertThatThrownBy(() -> testee.get(null, ROUTING).block())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void getShouldThrowWhenRoutingKeyIsNull() {
        assertThatThrownBy(() -> testee.get(DOCUMENT_ID, null).block())
            .isInstanceOf(NullPointerException.class);
    }

    private void awaitForOpenSearch(Query query, long totalHits) {
        CALMLY_AWAIT.atMost(Durations.TEN_SECONDS)
            .untilAsserted(() -> assertThat(client.search(
                new SearchRequest.Builder()
                    .query(query)
                    .build())
                .block()
                .hits().total().value()).isEqualTo(totalHits));
    }
}
