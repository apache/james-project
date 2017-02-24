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
package org.apache.james.backends.es;

import java.net.InetAddress;
import java.util.Objects;

import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.transport.InetSocketTransportAddress;

import com.github.fge.lambdas.Throwing;
import com.github.fge.lambdas.consumers.ConsumerChainer;
import com.github.steveash.guavate.Guavate;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;

public class ClientProviderImpl implements ClientProvider {

    public static class Host {
        private final String host;
        private final int port;

        public Host(String host, int port) {
            Preconditions.checkNotNull(host, "Host address can not be null");
            Preconditions.checkArgument(!host.isEmpty(), "Host address can not be empty");
            Preconditions.checkArgument(isValidPort(port), "Port should be between ]0, 65535]");
            this.host = host;
            this.port = port;
        }

        public String getHost() {
            return host;
        }

        public int getPort() {
            return port;
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof Host) {
                Host that = (Host) o;

                return Objects.equals(this.host, that.host)
                    && Objects.equals(this.port, that.port);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(host, port);
        }
    }

    public static ClientProviderImpl forHost(String address, Integer port) {
        isValidPort(port);
        return new ClientProviderImpl(ImmutableList.of(new Host(address, port)));
    }

    public static ClientProviderImpl fromHostsString(String hostsString) {
        Preconditions.checkNotNull(hostsString, "HostString should not be null");
        return new ClientProviderImpl(parseHosts(hostsString));
    }

    @VisibleForTesting
    static ImmutableList<Host> parseHosts(String hostsString) {
        return Splitter.on(',').splitToList(hostsString)
                .stream()
                .map(hostSting -> Splitter.on(':').splitToList(hostSting))
                .map(hostParts -> {
                    Preconditions.checkArgument(hostParts.size() == 2, "A host should be defined as a : separated pair of address and port");
                    return new Host(hostParts.get(0), Integer.valueOf(hostParts.get(1)));
                })
                .distinct()
                .collect(Guavate.toImmutableList());
    }

    private static boolean isValidPort(Integer port) {
        return port > 0 && port <= 65535;
    }

    private final ImmutableList<Host> hosts;

    private ClientProviderImpl(ImmutableList<Host> hosts) {
        Preconditions.checkArgument(!hosts.isEmpty(), "You should provide at least one host");
        this.hosts = hosts;
    }


    public Client get() {
        TransportClient transportClient = TransportClient.builder().build();
        ConsumerChainer<Host> consumer = Throwing.consumer(host -> transportClient
            .addTransportAddress(
                new InetSocketTransportAddress(
                    InetAddress.getByName(host.getHost()),
                    host.getPort())));
        hosts.forEach(consumer.sneakyThrow());
        return transportClient;
    }
}
