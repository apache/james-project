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
import java.util.Optional;

import org.apache.commons.configuration2.Configuration;
import org.apache.james.util.Host;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;

public class JmxConfiguration {

    public static final String LOCALHOST = "localhost";
    public static final int DEFAULT_PORT = 9999;
    public static final boolean ENABLED = true;
    public static final String JMX_CREDENTIAL_GENERATION_ENABLE_PROPERTY_KEY = "james.jmx.credential.generation";
    public static final String JMX_CREDENTIAL_GENERATION_ENABLE_DEFAULT_VALUE = "false";
    public static final String PASSWORD_FILE_NAME = "jmxremote.password";
    public static final String ACCESS_FILE_NAME = "jmxremote.access";
    public static final String JAMES_ADMIN_USER_DEFAULT = "james-admin";

    public static final JmxConfiguration DEFAULT_CONFIGURATION = new JmxConfiguration(ENABLED, Optional.of(Host.from(LOCALHOST, DEFAULT_PORT)));
    public static final JmxConfiguration DISABLED = new JmxConfiguration(!ENABLED, Optional.empty());

    public static JmxConfiguration fromProperties(Configuration configuration) {
        boolean jmxEnabled = configuration.getBoolean("jmx.enabled", true);
        if (!jmxEnabled) {
            return DISABLED;
        }

        String address = configuration.getString("jmx.address", LOCALHOST);
        int port = configuration.getInt("jmx.port", DEFAULT_PORT);
        return new JmxConfiguration(ENABLED, Optional.of(Host.from(address, port)));
    }

    private final boolean enabled;
    private final Optional<Host> host;

    @VisibleForTesting
    JmxConfiguration(boolean enabled, Optional<Host> host) {
        Preconditions.checkArgument(disabledOrHasHost(enabled, host), "Specifying a host is compulsory when JMX is enabled");
        this.enabled = enabled;
        this.host = host;
    }

    private boolean disabledOrHasHost(boolean enabled, Optional<Host> host) {
        return !enabled || host.isPresent();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Host getHost() {
        Preconditions.checkState(isEnabled(), "Trying to access JMX host while JMX is not enabled");
        return host.get();
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof JmxConfiguration) {
            JmxConfiguration that = (JmxConfiguration) o;

            return Objects.equals(this.host, that.host)
                && Objects.equals(this.enabled, that.enabled);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(host, enabled);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("host", host)
            .toString();
    }
}
