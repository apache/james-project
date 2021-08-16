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

import java.net.InetSocketAddress;

import org.apache.james.backends.cassandra.init.configuration.ClusterConfiguration;
import org.apache.james.backends.cassandra.init.configuration.KeyspaceConfiguration;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import com.google.common.base.Preconditions;

public class ClusterFactory {

    public static CqlSession create(ClusterConfiguration configuration, KeyspaceConfiguration keyspaceConfiguration) {
        Preconditions.checkState(configuration.getUsername().isPresent() == configuration.getPassword().isPresent(), "If you specify username, you must specify password");

        CqlSessionBuilder sessionBuilder = CqlSession.builder();

        configuration.getHosts().forEach(server -> sessionBuilder
            .addContactPoint(InetSocketAddress.createUnresolved(server.getHostName(), server.getPort())));


        configuration.getUsername().ifPresent(username ->
            configuration.getPassword().ifPresent(password ->
                sessionBuilder.withAuthCredentials(username, password)));

        sessionBuilder.withLocalDatacenter(configuration.getLocalDC().orElse("datacenter1"));

        try (CqlSession session = sessionBuilder.build()) {
            KeyspaceFactory.createKeyspace(keyspaceConfiguration, session);
        }
        sessionBuilder.withKeyspace(keyspaceConfiguration.getKeyspace());
        CqlSession session = sessionBuilder.build();

        try {
            ensureContactable(session);
            return session;
        } catch (Exception e) {
            session.close();
            throw e;
        }
    }

    private static void ensureContactable(CqlSession session) {
        session.execute("SELECT dateof(now()) FROM system.local ;");
    }
}
