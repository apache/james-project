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
package org.apache.james.backends.es.v6;

import java.net.InetAddress;
import java.util.Optional;

import org.apache.james.util.Host;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;

import com.github.fge.lambdas.Throwing;
import com.github.fge.lambdas.consumers.ConsumerChainer;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

public class ClientProviderImpl implements ClientProvider {

    public static ClientProviderImpl forHost(String address, Integer port, Optional<String> clusterName) {
        return new ClientProviderImpl(ImmutableList.of(Host.from(address, port)), clusterName);
    }

    public static ClientProviderImpl fromHostsString(String hostsString, Optional<String> clusterName) {
        Preconditions.checkNotNull(hostsString, "HostString should not be null");
        return new ClientProviderImpl(Host.parseHosts(hostsString), clusterName);
    }

    public static ClientProviderImpl fromHosts(ImmutableList<Host> hosts, Optional<String> clusterName) {
        Preconditions.checkNotNull(hosts, "Hosts should not be null");
        return new ClientProviderImpl(hosts, clusterName);
    }

    private static final String CLUSTER_NAME_SETTING = "cluster.name";

    private final ImmutableList<Host> hosts;
    private final Optional<String> clusterName;

    private ClientProviderImpl(ImmutableList<Host> hosts, Optional<String> clusterName) {
        Preconditions.checkArgument(!hosts.isEmpty(), "You should provide at least one host");
        this.hosts = hosts;
        this.clusterName = clusterName;
    }


    @Override
    public Client get() {
        TransportClient transportClient = TransportClient.builder()
                .settings(settings())
                .build();
        ConsumerChainer<Host> consumer = Throwing.consumer(host -> transportClient
            .addTransportAddress(
                new InetSocketTransportAddress(
                    InetAddress.getByName(host.getHostName()),
                    host.getPort())));
        hosts.forEach(consumer.sneakyThrow());
        return transportClient;
    }

    @VisibleForTesting Settings settings() {
        if (clusterName.isPresent()) {
            return Settings.builder()
                    .put(CLUSTER_NAME_SETTING, clusterName.get())
                    .build();
        }
        return Settings.EMPTY;
    }
}
