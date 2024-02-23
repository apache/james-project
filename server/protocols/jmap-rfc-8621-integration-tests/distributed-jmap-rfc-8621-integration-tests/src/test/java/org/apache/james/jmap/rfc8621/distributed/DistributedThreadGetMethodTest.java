/******************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one     *
 * or more contributor license agreements.  See the NOTICE file   *
 * distributed with this work for additional information          *
 * regarding copyright ownership.  The ASF licenses this file     *
 * to you under the Apache License, Version 2.0 (the              *
 * "License"); you may not use this file except in compliance     *
 * with the License.  You may obtain a copy of the License at     *
 *                                                                *
 * http://www.apache.org/licenses/LICENSE-2.0                     *
 *                                                                *
 * Unless required by applicable law or agreed to in writing,     *
 * software distributed under the License is distributed on an    *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY         *
 * KIND, either express or implied.  See the License for the      *
 * specific language governing permissions and limitations        *
 * under the License.                                             *
 ******************************************************************/

package org.apache.james.jmap.rfc8621.distributed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;

import java.io.IOException;
import java.util.List;

import org.apache.james.CassandraExtension;
import org.apache.james.CassandraRabbitMQJamesConfiguration;
import org.apache.james.CassandraRabbitMQJamesServerMain;
import org.apache.james.DockerOpenSearchExtension;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.SearchConfiguration;
import org.apache.james.backends.opensearch.ReactorOpenSearchClient;
import org.apache.james.backends.redis.RedisExtension;
import org.apache.james.jmap.rfc8621.contract.ThreadGetContract;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.opensearch.MailboxIndexCreationUtil;
import org.apache.james.mailbox.opensearch.MailboxOpenSearchConstants;
import org.apache.james.mailbox.opensearch.query.CriterionConverter;
import org.apache.james.mailbox.opensearch.query.QueryConverter;
import org.apache.james.modules.AwsS3BlobStoreExtension;
import org.apache.james.modules.RabbitMQExtension;
import org.apache.james.modules.TestJMAPServerModule;
import org.apache.james.modules.blobstore.BlobStoreConfiguration;
import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchRequest;

public class DistributedThreadGetMethodTest implements ThreadGetContract {
    private static final ConditionFactory CALMLY_AWAIT = Awaitility
        .with().pollInterval(ONE_HUNDRED_MILLISECONDS)
        .and().pollDelay(ONE_HUNDRED_MILLISECONDS)
        .await();

    private final QueryConverter queryConverter = new QueryConverter(new CriterionConverter());
    private ReactorOpenSearchClient client;

    @RegisterExtension
    org.apache.james.backends.opensearch.DockerOpenSearchExtension openSearch = new org.apache.james.backends.opensearch.DockerOpenSearchExtension();

    @RegisterExtension
    static RedisExtension redisExtension = new RedisExtension();

    @RegisterExtension
    JamesServerExtension testExtension = new JamesServerBuilder<CassandraRabbitMQJamesConfiguration>(tmpDir ->
        CassandraRabbitMQJamesConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .blobStore(BlobStoreConfiguration.builder()
                .s3()
                .disableCache()
                .deduplication()
                .noCryptoConfig())
            .searchConfiguration(SearchConfiguration.openSearch())
            .build())
        .extension(redisExtension)
        .extension(new DockerOpenSearchExtension())
        .extension(new CassandraExtension())
        .extension(new RabbitMQExtension())
        .extension(new AwsS3BlobStoreExtension())
        .server(configuration -> CassandraRabbitMQJamesServerMain.createServer(configuration)
            .overrideWith(new TestJMAPServerModule()))
        .build();

    @AfterEach
    void tearDown() throws IOException {
        client.close();
    }

    @Override
    public void awaitMessageCount(List<MailboxId> mailboxIds, SearchQuery query, long messageCount) {
        awaitForOpenSearch(queryConverter.from(mailboxIds, query), messageCount);
    }

    @Override
    public void initOpenSearchClient() {
        client = MailboxIndexCreationUtil.prepareDefaultClient(
            openSearch.getDockerOpenSearch().clientProvider().get(),
            openSearch.getDockerOpenSearch().configuration());
    }

    private void awaitForOpenSearch(Query query, long totalHits) {
        CALMLY_AWAIT.atMost(Durations.TEN_SECONDS)
            .untilAsserted(() -> assertThat(client.search(
                new SearchRequest.Builder()
                    .index(MailboxOpenSearchConstants.DEFAULT_MAILBOX_INDEX.getValue())
                    .query(query)
                    .build())
                .block()
                .hits().total().value()).isEqualTo(totalHits));
    }
}