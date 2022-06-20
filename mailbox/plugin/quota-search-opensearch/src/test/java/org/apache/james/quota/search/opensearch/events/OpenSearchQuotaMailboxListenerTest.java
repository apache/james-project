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

package org.apache.james.quota.search.opensearch.events;

import static org.apache.james.quota.search.QuotaSearchFixture.TestConstants.BOB_USERNAME;
import static org.apache.james.quota.search.QuotaSearchFixture.TestConstants.NOW;
import static org.apache.james.quota.search.QuotaSearchFixture.TestConstants.QUOTAROOT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;
import static org.mockito.Mockito.mock;

import java.io.IOException;

import org.apache.james.backends.opensearch.DockerElasticSearchExtension;
import org.apache.james.backends.opensearch.ElasticSearchConfiguration;
import org.apache.james.backends.opensearch.ElasticSearchIndexer;
import org.apache.james.backends.opensearch.ReactorElasticSearchClient;
import org.apache.james.events.Event;
import org.apache.james.events.Group;
import org.apache.james.mailbox.SessionProvider;
import org.apache.james.mailbox.quota.QuotaFixture.Counts;
import org.apache.james.mailbox.quota.QuotaFixture.Sizes;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.event.EventFactory;
import org.apache.james.mailbox.store.quota.DefaultUserQuotaRootResolver;
import org.apache.james.quota.search.opensearch.QuotaRatioOpenSearchConstants;
import org.apache.james.quota.search.opensearch.QuotaSearchIndexCreationUtil;
import org.apache.james.quota.search.opensearch.UserRoutingKeyFactory;
import org.apache.james.quota.search.opensearch.json.QuotaRatioToOpenSearchJson;
import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.opensearch.client.opensearch._types.query_dsl.MatchAllQuery;
import org.opensearch.client.opensearch.core.SearchRequest;

class OpenSearchQuotaMailboxListenerTest {

    private static final ConditionFactory CALMLY_AWAIT = Awaitility
        .with().pollInterval(ONE_HUNDRED_MILLISECONDS)
        .and().pollDelay(ONE_HUNDRED_MILLISECONDS)
        .await();
    static Event.EventId EVENT_ID = Event.EventId.of("6e0dd59d-660e-4d9b-b22f-0354479f47b4");

    @RegisterExtension
    DockerElasticSearchExtension openSearch = new DockerElasticSearchExtension();

    OpenSearchQuotaMailboxListener quotaMailboxListener;
    ReactorElasticSearchClient client;
    private DefaultUserQuotaRootResolver quotaRootResolver;

    @BeforeEach
    void setUp() {
        client = openSearch.getDockerElasticSearch().clientProvider().get();

        QuotaSearchIndexCreationUtil.prepareDefaultClient(client, ElasticSearchConfiguration.builder()
            .addHost(openSearch.getDockerElasticSearch().getHttpHost())
            .build());

        quotaRootResolver = new DefaultUserQuotaRootResolver(mock(SessionProvider.class), mock(MailboxSessionMapperFactory.class));
        quotaMailboxListener = new OpenSearchQuotaMailboxListener(
            new ElasticSearchIndexer(client,
                QuotaRatioOpenSearchConstants.DEFAULT_QUOTA_RATIO_WRITE_ALIAS),
            new QuotaRatioToOpenSearchJson(quotaRootResolver),
            new UserRoutingKeyFactory(), quotaRootResolver);
    }

    @AfterEach
    void tearDown() throws IOException {
        client.close();
    }

    @Test
    void deserializeOpenSearchQuotaMailboxListenerGroup() throws Exception {
        assertThat(Group.deserialize("org.apache.james.quota.search.opensearch.events.OpenSearchQuotaMailboxListener$OpenSearchQuotaMailboxListenerGroup"))
            .isEqualTo(new OpenSearchQuotaMailboxListener.OpenSearchQuotaMailboxListenerGroup());
    }

    @Test
    void eventShouldIndexEventWhenQuotaEvent() throws Exception {
        quotaMailboxListener.event(EventFactory.quotaUpdated()
            .eventId(EVENT_ID)
            .user(BOB_USERNAME)
            .quotaRoot(QUOTAROOT)
            .quotaCount(Counts._52_PERCENT)
            .quotaSize(Sizes._55_PERCENT)
            .instant(NOW)
            .build());

        CALMLY_AWAIT.atMost(Durations.TEN_SECONDS)
            .untilAsserted(() -> assertThat(client.search(
                new SearchRequest.Builder()
                    .index(QuotaRatioOpenSearchConstants.DEFAULT_QUOTA_RATIO_READ_ALIAS.getValue())
                    .query(new MatchAllQuery.Builder().build()._toQuery())
                    .build())
                .block()
                .hits().total().value()).isEqualTo(1));
    }
}