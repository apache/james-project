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

package org.apache.james.modules.server;

import java.util.Objects;

import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.james.util.Host;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;

public class JmxConfiguration {

    public static final String LOCALHOST = "localhost";
    public static final int DEFAULT_PORT = 9999;

    public static final JmxConfiguration DEFAULT_CONFIGURATION = new JmxConfiguration(Host.from(LOCALHOST, DEFAULT_PORT));

    public static JmxConfiguration fromProperties(PropertiesConfiguration configuration) {
        String address = configuration.getString("jmx.address", LOCALHOST);
        int port = configuration.getInt("jmx.port", DEFAULT_PORT);
        return new JmxConfiguration(Host.from(address, port));
    }

    private final Host host;

    @VisibleForTesting
    JmxConfiguration(Host host) {
        this.host = host;
    }

    public Host getHost() {
        return host;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof JmxConfiguration) {
            JmxConfiguration that = (JmxConfiguration) o;

            return Objects.equals(this.host, that.host);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(host);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("host", host)
            .toString();
    }
}
