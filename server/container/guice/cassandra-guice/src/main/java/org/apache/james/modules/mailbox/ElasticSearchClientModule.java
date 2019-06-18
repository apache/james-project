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

package org.apache.james.modules.mailbox;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;

import javax.inject.Singleton;

import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.james.backends.es.ClientProviderImpl;
import org.apache.james.backends.es.ElasticSearchConfiguration;
import org.elasticsearch.client.RestHighLevelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class ElasticSearchClientModule extends AbstractModule {

    private static final Logger LOGGER = LoggerFactory.getLogger(ElasticSearchClientModule.class);

    @Override
    protected void configure() {
    }

    @Provides
    @Singleton
    protected RestHighLevelClient provideClient(ElasticSearchConfiguration configuration) {
        Duration waitDelay = Duration.ofMillis(configuration.getMinDelay());
        return Mono.fromCallable(() -> connectToCluster(configuration))
            .doOnError(e -> LOGGER.warn("Error establishing ElasticSearch connection. Next retry scheduled in {}",
                DurationFormatUtils.formatDurationWords(waitDelay.toMillis(), true, true), e))
            .retryBackoff(configuration.getMaxRetries(), waitDelay, waitDelay)
            .publishOn(Schedulers.elastic())
            .block();
    }

    private RestHighLevelClient connectToCluster(ElasticSearchConfiguration configuration) throws IOException {
        LOGGER.info("Trying to connect to ElasticSearch service at {}", LocalDateTime.now());

        return ClientProviderImpl.fromHosts(configuration.getHosts(), configuration.getClusterName())
            .get();
    }
}
