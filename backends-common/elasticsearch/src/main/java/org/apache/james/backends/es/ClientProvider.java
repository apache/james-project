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

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Provider;

import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class ClientProvider implements Provider<RestHighLevelClient> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientProvider.class);

    private static final String HTTP_HOST_SCHEME = "http";
    private final ElasticSearchConfiguration configuration;
    private final RestHighLevelClient client;

    @Inject
    @VisibleForTesting
    ClientProvider(ElasticSearchConfiguration configuration) {
        this.configuration = configuration;
        this.client = connect(configuration);
    }

    private RestHighLevelClient connect(ElasticSearchConfiguration configuration) {
        Duration waitDelay = Duration.ofMillis(configuration.getMinDelay());
        Duration forever = Duration.ofMillis(Long.MAX_VALUE);
        boolean suppressLeadingZeroElements = true;
        boolean suppressTrailingZeroElements = true;
        return Mono.fromCallable(() -> connectToCluster(configuration))
            .doOnError(e -> LOGGER.warn("Error establishing ElasticSearch connection. Next retry scheduled in {}",
                DurationFormatUtils.formatDurationWords(waitDelay.toMillis(), suppressLeadingZeroElements, suppressTrailingZeroElements), e))
            .retryBackoff(configuration.getMaxRetries(), waitDelay, forever, Schedulers.elastic())
            .publishOn(Schedulers.elastic())
            .block();
    }

    private RestHighLevelClient connectToCluster(ElasticSearchConfiguration configuration) throws IOException {
        LOGGER.info("Trying to connect to ElasticSearch service at {}", LocalDateTime.now());
        return new RestHighLevelClient(
            RestClient.builder(hostsToHttpHosts())
                .setMaxRetryTimeoutMillis(Math.toIntExact(configuration.getRequestTimeout().toMillis())));
    }

    private HttpHost[] hostsToHttpHosts() {
        return configuration.getHosts().stream()
            .map(host -> new HttpHost(host.getHostName(), host.getPort(), HTTP_HOST_SCHEME))
            .toArray(HttpHost[]::new);
    }

    @Override
    public RestHighLevelClient get() {
        return client;
    }

    @PreDestroy
    public void close() throws IOException {
        client.close();
    }
}
