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

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;

public class ClusterWithKeyspaceCreatedFactory {

    private final static int DEFAULT_REPLICATION_FACTOR = 1;

    public static Cluster clusterWithInitializedKeyspaceWithoutDurableWrites(Cluster cluster, String keyspace, int replicationFactor) {
        return clusterWithInitializedKeyspace(cluster, keyspace, replicationFactor, false);
    }

    public static Cluster clusterWithInitializedKeyspace(Cluster cluster, String keyspace, int replicationFactor) {
        return clusterWithInitializedKeyspace(cluster, keyspace, replicationFactor, true);
    }

    private static Cluster clusterWithInitializedKeyspace(Cluster cluster, String keyspace, int replicationFactor, boolean durableWrites) {
        if (isKeyspacePresent(cluster, keyspace)) {
            createKeyspace(cluster, keyspace, replicationFactor, durableWrites);
        }
        return cluster;
    }

    public static Cluster clusterWithInitializedKeyspace(Cluster cluster, String keyspace) {
        return clusterWithInitializedKeyspace(cluster, keyspace, DEFAULT_REPLICATION_FACTOR);
    }

    private static boolean isKeyspacePresent(Cluster cluster, String keyspace) {
        return cluster.getMetadata().getKeyspace(keyspace) == null;
    }

    private static void createKeyspace(Cluster cluster, String keyspace, int replicationFactor, boolean durableWrites) {
        try (Session session = cluster.connect()) {
            session.execute("CREATE KEYSPACE IF NOT EXISTS " + keyspace
                + " WITH replication = {'class':'SimpleStrategy', 'replication_factor':" + replicationFactor + "}"
                + " AND durable_writes = " + String.valueOf(durableWrites)
                + ";");
        }
    }

}
