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

import com.google.inject.Provides;
import com.google.inject.Singleton;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;
import com.google.inject.AbstractModule;
import org.apache.james.mailbox.cassandra.ClusterFactory;
import org.apache.james.mailbox.cassandra.ClusterWithKeyspaceCreatedFactory;
import org.apache.james.mailbox.cassandra.SessionFactory;
import org.apache.james.utils.PropertiesReader;

public class CassandraSessionModule extends AbstractModule {

    private final PropertiesReader propertiesReader;
    private final String keyspace;

    public CassandraSessionModule() {
        propertiesReader = new PropertiesReader("cassandra.properties");
        keyspace = propertiesReader.getProperty("cassandra.keyspace");
    }

    @Override
    protected void configure() {

    }

    @Provides
    @Singleton
    Session provideSession(Cluster cluster) {
        return SessionFactory.createSession(cluster, keyspace);
    }

    @Provides
    @Singleton
    Cluster provideCluster() {
        return ClusterWithKeyspaceCreatedFactory.clusterWithInitializedKeyspace(
            ClusterFactory.createClusterForSingleServerWithoutPassWord(
                propertiesReader.getProperty("cassandra.ip"),
                Integer.parseInt(propertiesReader.getProperty("cassandra.port"))),
            keyspace,
            Integer.parseInt(propertiesReader.getProperty("cassandra.replication.factor")));
    }

}