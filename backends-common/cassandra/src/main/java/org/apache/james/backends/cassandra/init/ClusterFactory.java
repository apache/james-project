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

import java.util.List;
import java.util.Optional;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.QueryOptions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

public class ClusterFactory {

    private final static String DEFAULT_CLUSTER_IP = "localhost";

    public static Cluster createClusterForClusterWithPassWord(List<CassandraNodeIpAndPort> servers, String userName, String password,
            Optional<Integer> refreshSchemaIntervalMillis) {

        Cluster.Builder clusterBuilder = Cluster.builder();
        servers.forEach(
            (server) -> clusterBuilder.addContactPoint(server.getIp()).withPort(server.getPort())
        );
        if(!Strings.isNullOrEmpty(userName) && !Strings.isNullOrEmpty(password)) {
            clusterBuilder.withCredentials(userName, password);
        }
        if (refreshSchemaIntervalMillis.isPresent()) {
            clusterBuilder.withQueryOptions(new QueryOptions().setRefreshSchemaIntervalMillis(refreshSchemaIntervalMillis.get()));
        }
        return clusterBuilder.build();
    }

    public static Cluster createClusterForClusterWithoutPassWord(List<CassandraNodeIpAndPort> servers) {
        return createClusterForClusterWithPassWord(servers, null, null, Optional.empty());
    }

    public static Cluster createClusterForSingleServerWithPassWord(String ip, int port, String userName, String password) {
        return createClusterForClusterWithPassWord(ImmutableList.of(new CassandraNodeIpAndPort(ip, port)), userName, password, Optional.empty());
    }

    public static Cluster createClusterForSingleServerWithoutPassWord(String ip, int port) {
        return createClusterForClusterWithPassWord(ImmutableList.of(new CassandraNodeIpAndPort(ip, port)), null, null, Optional.empty());
    }

    public static Cluster createTestingCluster(String ip, int port) {
        return createClusterForClusterWithPassWord(ImmutableList.of(new CassandraNodeIpAndPort(ip, port)), null, null, Optional.of(0));
    }

    public static Cluster createDefaultSession() {
        return createClusterForSingleServerWithoutPassWord(DEFAULT_CLUSTER_IP, CassandraNodeIpAndPort.DEFAULT_CASSANDRA_PORT);
    }
}
