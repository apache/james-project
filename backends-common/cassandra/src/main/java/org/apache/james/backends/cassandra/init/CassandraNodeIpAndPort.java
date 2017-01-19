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

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;

public class CassandraNodeIpAndPort {
    public static final int DEFAULT_CASSANDRA_PORT = 9042;

    public static CassandraNodeIpAndPort parseConfString(String ipAndPort) {
        Preconditions.checkNotNull(ipAndPort);
        Preconditions.checkArgument(!ipAndPort.isEmpty());

        List<String> parts = Splitter.on(':')
                .trimResults()
                .splitToList(ipAndPort);

        if (parts.size() < 1 || parts.size() > 2) {
            throw new IllegalArgumentException(ipAndPort + " is not a valid cassandra node");
        }

        String ip = parts.get(0);
        int port = getPortFromConfPart(parts);

        return new CassandraNodeIpAndPort(ip, port);
    }

    private static int getPortFromConfPart(List<String> parts) {
        if (parts.size() == 2) {
            return Integer.valueOf(parts.get(1));
        } else {
            return DEFAULT_CASSANDRA_PORT;
        }
    }

    private final String ip;
    private final int port;

    public CassandraNodeIpAndPort(String ip, int port) {
        this.ip = ip;
        this.port = port;
    }

    public CassandraNodeIpAndPort(String ip) {
        this(ip, DEFAULT_CASSANDRA_PORT);
    }

    public String getIp() {
        return ip;
    }

    public int getPort() {
        return port;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(ip, port);
    }

    @Override
    public boolean equals(Object object) {
        if (object instanceof CassandraNodeIpAndPort) {
            CassandraNodeIpAndPort that = (CassandraNodeIpAndPort) object;
            return Objects.equal(this.ip, that.ip) && Objects.equal(this.port, that.port);
        }
        return false;
    }

    @Override
    public String toString() {
        return this.ip + ":" + this.port;
    }
}
