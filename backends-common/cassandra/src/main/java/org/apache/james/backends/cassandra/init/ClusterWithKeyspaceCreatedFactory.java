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

    private static final int DEFAULT_REPLICATION_FACTOR = 1;
    
    public static Configuration config(Cluster cluster, String keyspace) {
        return new Configuration(cluster, keyspace);
    }
    
    public static class Configuration {
        private Cluster cluster;
        private String keyspace;
        private boolean durableWrites;
        private int replicationFactor;
        
        private Configuration(Cluster cluster, String keyspace) {
            this.cluster = cluster;
            this.keyspace = keyspace;
            this.durableWrites = true;
            this.replicationFactor = DEFAULT_REPLICATION_FACTOR;
        }
        
        public Configuration disableDurableWrites() {
            this.durableWrites = false;
            return this;
        }
        
        public Configuration replicationFactor(int replicationFactor) {
            this.replicationFactor = replicationFactor;
            return this;
        }
        
        public Cluster clusterWithInitializedKeyspace() {
            createKeyspace(cluster, keyspace, replicationFactor, durableWrites);
            return cluster;
        }
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
