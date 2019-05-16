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

package org.apache.james.quota.search.elasticsearch.events;

import static org.apache.james.quota.search.QuotaSearchFixture.TestConstants.BOB_USER;
import static org.apache.james.quota.search.QuotaSearchFixture.TestConstants.NOW;
import static org.apache.james.quota.search.QuotaSearchFixture.TestConstants.QUOTAROOT;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.apache.james.backends.es.v6.DockerElasticSearchRule;
import org.apache.james.backends.es.v6.ElasticSearchConfiguration;
import org.apache.james.backends.es.v6.ElasticSearchIndexer;
import org.apache.james.backends.es.v6.NodeMappingFactory;
import org.apache.james.mailbox.events.Event;
import org.apache.james.mailbox.events.Group;
import org.apache.james.mailbox.quota.QuotaFixture.Counts;
import org.apache.james.mailbox.quota.QuotaFixture.Sizes;
import org.apache.james.mailbox.store.event.EventFactory;
import org.apache.james.quota.search.elasticsearch.QuotaRatioElasticSearchConstants;
import org.apache.james.quota.search.elasticsearch.QuotaSearchIndexCreationUtil;
import org.apache.james.quota.search.elasticsearch.json.QuotaRatioToElasticSearchJson;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class ElasticSearchQuotaMailboxListenerTest {
    private static Event.EventId EVENT_ID = Event.EventId.of("6e0dd59d-660e-4d9b-b22f-0354479f47b4");

    private static final int BATCH_SIZE = 1;

    @Rule
    public DockerElasticSearchRule elasticSearch = new DockerElasticSearchRule();
    private ElasticSearchQuotaMailboxListener quotaMailboxListener;
    private RestHighLevelClient client;

    @Before
    public void setUp() throws IOException {
        client = elasticSearch.clientProvider().get();

        QuotaSearchIndexCreationUtil.prepareDefaultClient(client, ElasticSearchConfiguration.builder()
            .addHost(elasticSearch.getDockerElasticSearch().getHttpHost())
            .build());

        quotaMailboxListener = new ElasticSearchQuotaMailboxListener(
            new ElasticSearchIndexer(client,
                QuotaRatioElasticSearchConstants.DEFAULT_QUOTA_RATIO_WRITE_ALIAS,
                BATCH_SIZE),
            new QuotaRatioToElasticSearchJson());
    }

    @Test
    public void deserializeElasticSearchQuotaMailboxListenerGroup() throws Exception {
        assertThat(Group.deserialize("org.apache.james.quota.search.elasticsearch.events.ElasticSearchQuotaMailboxListener$ElasticSearchQuotaMailboxListenerGroup"))
            .isEqualTo(new ElasticSearchQuotaMailboxListener.ElasticSearchQuotaMailboxListenerGroup());
    }

    @Test
    public void eventShouldIndexEventWhenQuotaEvent() throws Exception {
        quotaMailboxListener.event(EventFactory.quotaUpdated()
            .eventId(EVENT_ID)
            .user(BOB_USER)
            .quotaRoot(QUOTAROOT)
            .quotaCount(Counts._52_PERCENT)
            .quotaSize(Sizes._55_PERCENT)
            .instant(NOW)
            .build());

        elasticSearch.awaitForElasticSearch();

        SearchResponse searchResponse = client.search(new SearchRequest(QuotaRatioElasticSearchConstants.DEFAULT_QUOTA_RATIO_READ_ALIAS.getValue())
                .types(NodeMappingFactory.DEFAULT_MAPPING_NAME)
                .source(new SearchSourceBuilder()
                    .query(QueryBuilders.matchAllQuery())),
            RequestOptions.DEFAULT);

        assertThat(searchResponse.getHits().getTotalHits()).isEqualTo(1);
    }
}