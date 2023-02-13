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

package org.apache.james.util;

import java.util.List;
import java.util.Optional;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

public class Host {

    public static ImmutableList<Host> parseHosts(String hostsString) {
        return parseHosts(hostsString, Optional.empty());
    }

    public static ImmutableList<Host> parseHosts(String hostsString, int defaultPort) {
        return parseHosts(hostsString, Optional.of(defaultPort));
    }

    private static ImmutableList<Host> parseHosts(String hostsString, Optional<Integer> defaultPort) {
        return Splitter.on(',')
            .omitEmptyStrings()
            .splitToStream(hostsString)
            .map(string -> Host.parse(string, defaultPort))
            .distinct()
            .collect(ImmutableList.toImmutableList());
    }

    public static Host from(String hostname, int port) {
        return new Host(hostname, port);
    }

    public static Host parseConfString(String ipAndPort, int defaultPort) {
        return parse(ipAndPort, Optional.of(defaultPort));
    }

    public static Host parseConfString(String ipAndPort) {
        return parse(ipAndPort, Optional.empty());
    }

    public static Host parse(String ipAndPort, Optional<Integer> defaultPort) {
        Preconditions.checkNotNull(ipAndPort);
        Preconditions.checkArgument(!ipAndPort.isEmpty());

        List<String> parts = retrieveHostParts(ipAndPort);

        String ip = parts.get(0);
        int port = getPortFromConfPart(parts, defaultPort);

        return new Host(ip, port);
    }

    private static List<String> retrieveHostParts(String ipAndPort) {
        List<String> parts = Splitter.on(':')
                .trimResults()
                .splitToList(ipAndPort);

        if (parts.size() < 1 || parts.size() > 2) {
            throw new IllegalArgumentException(ipAndPort + " is not a valid host string");
        }
        return parts;
    }

    private static int getPortFromConfPart(List<String> parts, Optional<Integer> defaultPort) {
        if (parts.size() == 2) {
            return Integer.parseInt(parts.get(1));
        }
        if (parts.size() == 1) {
            return defaultPort.orElseThrow(() -> new IllegalArgumentException("Host do not have port part but no default port provided"));
        }
        throw new RuntimeException("A host should be either a hostname or a hostname and a port separated by a ':'");
    }

    private final String hostName;
    private final int port;

    @VisibleForTesting
    Host(String hostName, int port) {
        Preconditions.checkNotNull(hostName, "Hostname could not be null");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(hostName), "Hostname could not be empty");
        Port.assertValid(port);
        this.hostName = hostName;
        this.port = port;
    }

    public String getHostName() {
        return hostName;
    }

    public int getPort() {
        return port;
    }

    @Override
    public final int hashCode() {
        return Objects.hashCode(hostName, port);
    }

    @Override
    public final boolean equals(Object object) {
        if (object instanceof Host) {
            Host that = (Host) object;
            return Objects.equal(this.hostName, that.hostName)
                && Objects.equal(this.port, that.port);
        }
        return false;
    }

    public String asString() {
        return this.hostName + ":" + this.port;
    }

    @Override
    public String toString() {
        return asString();
    }
}
