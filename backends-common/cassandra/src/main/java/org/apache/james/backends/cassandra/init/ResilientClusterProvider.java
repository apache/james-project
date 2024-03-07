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

package org.apache.james.backends.cassandra.init;

import static org.apache.james.backends.cassandra.init.KeyspaceFactory.keyspaceExist;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.Callable;

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.apache.james.backends.cassandra.init.configuration.ClusterConfiguration;
import org.apache.james.backends.cassandra.init.configuration.KeyspaceConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.oss.driver.api.core.CqlSession;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

@Singleton
public class ResilientClusterProvider implements Provider<CqlSession> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ResilientClusterProvider.class);

    private final CqlSession cluster;
    private final ClusterConfiguration clusterConfiguration;

    @VisibleForTesting
    @Inject
    ResilientClusterProvider(ClusterConfiguration configuration, KeyspaceConfiguration keyspaceConfiguration) {
        clusterConfiguration = configuration;
        Duration waitDelay = Duration.ofMillis(clusterConfiguration.getMinDelay());
        cluster = Mono.fromCallable(getClusterRetryCallable(configuration, keyspaceConfiguration))
            .doOnError(e -> LOGGER.warn("Error establishing Cassandra connection. Next retry scheduled in {} ms", waitDelay, e))
            .retryWhen(Retry.backoff(configuration.getMaxRetry(), waitDelay).scheduler(Schedulers.boundedElastic()))
            .block();
    }

    private Callable<CqlSession> getClusterRetryCallable(ClusterConfiguration configuration, KeyspaceConfiguration keyspaceConfiguration) {
        LOGGER.info("Trying to connect to Cassandra service at {} (list {})", LocalDateTime.now(),
            ImmutableList.copyOf(configuration.getHosts()).toString());

        return () -> {
            CqlSession cluster = ClusterFactory.create(configuration, keyspaceConfiguration);
            try {
                keyspaceExist(cluster, "any"); // plays a sample query to ensure we can contact the cluster
                return cluster;
            } catch (Exception e) {
                cluster.close();
                throw e;
            }
        };
    }

    @Override
    public CqlSession get() {
        return cluster;
    }

    public CqlSession get(KeyspaceConfiguration keyspaceConfiguration) {
        Duration waitDelay = Duration.ofMillis(clusterConfiguration.getMinDelay());
        return Mono.fromCallable(getClusterRetryCallable(clusterConfiguration, keyspaceConfiguration))
            .doOnError(e -> LOGGER.warn("Error establishing Cassandra connection. Next retry scheduled in {} ms", waitDelay, e))
            .retryWhen(Retry.backoff(clusterConfiguration.getMaxRetry(), waitDelay).scheduler(Schedulers.boundedElastic()))
            .block();
    }

    @PreDestroy
    public void stop() {
        cluster.closeAsync();
    }
    
}
