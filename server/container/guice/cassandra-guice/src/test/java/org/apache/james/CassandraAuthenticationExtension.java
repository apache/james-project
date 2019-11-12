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

package org.apache.james;

import org.apache.james.backends.cassandra.DockerCassandra;
import org.apache.james.backends.cassandra.init.configuration.ClusterConfiguration;
import org.apache.james.server.CassandraTruncateTableTask;

import com.google.inject.Module;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.util.Modules;

public class CassandraAuthenticationExtension implements GuiceModuleTestExtension {
    private static final String VALID_PASSWORD = "cassandra";
    private static final String INVALID_PASSWORD = "bad";
    private static final DockerCassandra authenticatedCassandra = new DockerCassandra("cassandra_3_11_3_auth",
        dockerfileBuilder -> dockerfileBuilder
            .run("echo 'authenticator: PasswordAuthenticator' >> /etc/cassandra/cassandra.yaml"));

    static {
        authenticatedCassandra.start();
    }

    public static CassandraAuthenticationExtension withValidCredentials() {
        return new CassandraAuthenticationExtension(ClusterConfiguration.builder()
            .password(VALID_PASSWORD)
            .maxRetry(20)
            .minDelay(5000));
    }

    public static CassandraAuthenticationExtension withInvalidCredentials() {
        return new CassandraAuthenticationExtension(ClusterConfiguration.builder()
            .password(INVALID_PASSWORD)
            .maxRetry(1)
            .minDelay(100));
    }

    private final ClusterConfiguration.Builder configurationBuilder;

    private CassandraAuthenticationExtension(ClusterConfiguration.Builder configurationBuilder) {
        this.configurationBuilder = configurationBuilder;
    }

    @Override
    public Module getModule() {
        return Modules.combine((binder) -> binder.bind(ClusterConfiguration.class)
                .toInstance(configurationBuilder
                    .host(authenticatedCassandra.getHost())
                    .keyspace("testing")
                    .createKeyspace()
                    .username("cassandra")
                    .replicationFactor(1)
                    .build()),
            binder -> Multibinder.newSetBinder(binder, CleanupTasksPerformer.CleanupTask.class)
                .addBinding()
                .to(CassandraTruncateTableTask.class));
    }
}
