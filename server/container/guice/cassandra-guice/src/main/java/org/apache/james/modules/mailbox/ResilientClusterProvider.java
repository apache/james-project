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

import java.time.LocalDateTime;
import java.util.concurrent.ExecutionException;

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.apache.james.backends.cassandra.init.ClusterBuilder;
import org.apache.james.backends.cassandra.init.ClusterWithKeyspaceCreatedFactory;
import org.apache.james.backends.cassandra.init.configuration.ClusterConfiguration;
import org.apache.james.utils.RetryExecutorUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.exceptions.NoHostAvailableException;
import com.google.common.collect.ImmutableList;
import com.nurkiewicz.asyncretry.AsyncRetryExecutor;
import com.nurkiewicz.asyncretry.function.RetryCallable;

@Singleton
public class ResilientClusterProvider implements Provider<Cluster> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ResilientClusterProvider.class);

    private final Cluster cluster;

    @Inject
    private ResilientClusterProvider(ClusterConfiguration configuration, AsyncRetryExecutor executor) throws ExecutionException, InterruptedException {
        cluster = RetryExecutorUtil.retryOnExceptions(executor, configuration.getMaxRetry(), configuration.getMinDelay(), NoHostAvailableException.class)
                .getWithRetry(getClusterRetryCallable(configuration))
                .get();
    }

    private RetryCallable<Cluster> getClusterRetryCallable(ClusterConfiguration configuration) {
        LOGGER.info("Trying to connect to Cassandra service at {} (list {})", LocalDateTime.now(),
            ImmutableList.copyOf(configuration.getHosts()).toString());

        return context -> {
            Cluster cluster = ClusterBuilder.builder()
                    .servers(configuration.getHosts())
                    .poolingOptions(configuration.getPoolingOptions())
                    .queryLoggerConfiguration(configuration.getQueryLoggerConfiguration())
                    .readTimeoutMillis(configuration.getReadTimeoutMillis())
                    .connectTimeoutMillis(configuration.getConnectTimeoutMillis())
                    .build();
            try {
                return ClusterWithKeyspaceCreatedFactory
                    .config(cluster, configuration.getKeyspace())
                    .replicationFactor(configuration.getReplicationFactor())
                    .clusterWithInitializedKeyspace();
            } catch (Exception e) {
                cluster.close();
                throw e;
            }
        };
    }

    @Override
    public Cluster get() {
        return cluster;
    }

    @PreDestroy
    public void stop() {
        cluster.closeAsync();
    }
    
}
