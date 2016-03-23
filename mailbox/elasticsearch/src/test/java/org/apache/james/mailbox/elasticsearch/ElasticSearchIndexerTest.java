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

package org.apache.james.mailbox.elasticsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import java.io.IOException;
import java.util.concurrent.Executors;

import org.apache.james.mailbox.elasticsearch.json.JsonMessageConstants;
import org.apache.james.mailbox.elasticsearch.utils.TestingClientProvider;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.node.Node;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;

import com.google.common.collect.Lists;

public class ElasticSearchIndexerTest {

    private TemporaryFolder temporaryFolder = new TemporaryFolder();
    private EmbeddedElasticSearch embeddedElasticSearch= new EmbeddedElasticSearch(temporaryFolder);

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(temporaryFolder).around(embeddedElasticSearch);

    private Node node;
    private ElasticSearchIndexer testee;
    private DeleteByQueryPerformer deleteByQueryPerformer;

    @Before
    public void setup() throws IOException {
        node = embeddedElasticSearch.getNode();
        TestingClientProvider clientProvider = new TestingClientProvider(node);
        deleteByQueryPerformer = new DeleteByQueryPerformer(clientProvider, Executors.newSingleThreadExecutor());
        testee = new ElasticSearchIndexer(clientProvider, deleteByQueryPerformer);
    }
    
    @Test
    public void indexMessageShouldWork() throws Exception {
        String messageId = "1";
        String content = "{\"message\": \"trying out Elasticsearch\"}";
        
        testee.indexMessage(messageId, content);
        embeddedElasticSearch.awaitForElasticSearch();
        
        try (Client client = node.client()) {
            SearchResponse searchResponse = client.prepareSearch(ElasticSearchIndexer.MAILBOX_INDEX)
                    .setTypes(ElasticSearchIndexer.MESSAGE_TYPE)
                    .setQuery(QueryBuilders.matchQuery("message", "trying"))
                    .get();
            assertThat(searchResponse.getHits().getTotalHits()).isEqualTo(1);
        }
    }
    
    @Test(expected=IllegalArgumentException.class)
    public void indexMessageShouldThrowWhenJsonIsNull() throws InterruptedException {
        testee.indexMessage("1", null);
    }
    
    @Test
    public void updateMessage() throws Exception {
        String messageId = "1";
        String content = "{\"message\": \"trying out Elasticsearch\",\"field\":\"Should be unchanged\"}";

        testee.indexMessage(messageId, content);
        embeddedElasticSearch.awaitForElasticSearch();

        testee.updateMessage(messageId, "{\"message\": \"mastering out Elasticsearch\"}");
        embeddedElasticSearch.awaitForElasticSearch();

        try (Client client = node.client()) {
            SearchResponse searchResponse = client.prepareSearch(ElasticSearchIndexer.MAILBOX_INDEX)
                .setTypes(ElasticSearchIndexer.MESSAGE_TYPE)
                .setQuery(QueryBuilders.matchQuery("message", "mastering"))
                .get();
            assertThat(searchResponse.getHits().getTotalHits()).isEqualTo(1);
        }

        try (Client client = node.client()) {
            SearchResponse searchResponse = client.prepareSearch(ElasticSearchIndexer.MAILBOX_INDEX)
                .setTypes(ElasticSearchIndexer.MESSAGE_TYPE)
                .setQuery(QueryBuilders.matchQuery("field", "unchanged"))
                .get();
            assertThat(searchResponse.getHits().getTotalHits()).isEqualTo(1);
        }
    }
    
    @Test(expected=IllegalArgumentException.class)
    public void updateMessageShouldThrowWhenJsonIsNull() throws InterruptedException {
        String messageId = "1:2";
        String content = "{\"message\": \"trying out Elasticsearch\"}";

        testee.indexMessage(messageId, content);
        embeddedElasticSearch.awaitForElasticSearch();

        testee.updateMessage("1", null);
    }

    @Test
    public void deleteByQueryShouldWorkOnSingleMessage() throws Exception {
        String messageId = "1:2";
        String content = "{\"message\": \"trying out Elasticsearch\", \"mailboxId\":\"1\"}";

        testee.indexMessage(messageId, content);
        embeddedElasticSearch.awaitForElasticSearch();
        
        testee.deleteAllMatchingQuery(termQuery(JsonMessageConstants.MAILBOX_ID, "1"));
        embeddedElasticSearch.awaitForElasticSearch();
        
        try (Client client = node.client()) {
            SearchResponse searchResponse = client.prepareSearch(ElasticSearchIndexer.MAILBOX_INDEX)
                    .setTypes(ElasticSearchIndexer.MESSAGE_TYPE)
                    .setQuery(QueryBuilders.matchAllQuery())
                    .get();
            assertThat(searchResponse.getHits().getTotalHits()).isEqualTo(0);
        }
    }

    @Test
    public void deleteByQueryShouldWorkWhenMultipleMessages() throws Exception {
        String messageId = "1:1";
        String content = "{\"message\": \"trying out Elasticsearch\", \"mailboxId\":\"1\"}";
        
        testee.indexMessage(messageId, content);
        
        String messageId2 = "1:2";
        String content2 = "{\"message\": \"trying out Elasticsearch 2\", \"mailboxId\":\"1\"}";
        
        testee.indexMessage(messageId2, content2);
        
        String messageId3 = "2:3";
        String content3 = "{\"message\": \"trying out Elasticsearch 3\", \"mailboxId\":\"2\"}";
        
        testee.indexMessage(messageId3, content3);
        embeddedElasticSearch.awaitForElasticSearch();

        testee.deleteAllMatchingQuery(termQuery(JsonMessageConstants.MAILBOX_ID, "1"));
        embeddedElasticSearch.awaitForElasticSearch();
        
        try (Client client = node.client()) {
            SearchResponse searchResponse = client.prepareSearch(ElasticSearchIndexer.MAILBOX_INDEX)
                    .setTypes(ElasticSearchIndexer.MESSAGE_TYPE)
                    .setQuery(QueryBuilders.matchAllQuery())
                    .get();
            assertThat(searchResponse.getHits().getTotalHits()).isEqualTo(1);
        }
    }
    
    @Test
    public void deleteMessage() throws Exception {
        String messageId = "1:2";
        String content = "{\"message\": \"trying out Elasticsearch\"}";

        testee.indexMessage(messageId, content);
        embeddedElasticSearch.awaitForElasticSearch();

        testee.deleteMessage(messageId);
        embeddedElasticSearch.awaitForElasticSearch();
        
        try (Client client = node.client()) {
            SearchResponse searchResponse = client.prepareSearch(ElasticSearchIndexer.MAILBOX_INDEX)
                    .setTypes(ElasticSearchIndexer.MESSAGE_TYPE)
                    .setQuery(QueryBuilders.matchAllQuery())
                    .get();
            assertThat(searchResponse.getHits().getTotalHits()).isEqualTo(0);
        }
    }

}
