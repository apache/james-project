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
package org.apache.james.jmap;

import java.util.Optional;

import org.apache.james.util.Port;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

public class JMAPConfiguration {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Optional<Boolean> enabled = Optional.empty();
        private Optional<Boolean> emailQueryViewEnabled = Optional.empty();
        private Optional<Boolean> userProvisioningEnabled = Optional.empty();
        private Optional<Port> port = Optional.empty();
        private Optional<Version> defaultVersion = Optional.empty();
        private Optional<Long> maximumSendSize = Optional.empty();

        private Builder() {

        }

        public Builder enabled(boolean enabled) {
            this.enabled = Optional.of(enabled);
            return this;
        }

        public Builder enable() {
            return enabled(true);
        }

        public Builder disable() {
            return enabled(false);
        }

        public Builder enableEmailQueryView(boolean enabled) {
            return enableEmailQueryView(Optional.of(enabled));
        }

        public Builder enableEmailQueryView(Optional<Boolean> enabled) {
            this.emailQueryViewEnabled = enabled;
            return this;
        }

        public Builder userProvisioningEnabled(boolean enabled) {
            return userProvisioningEnabled(Optional.of(enabled));
        }

        public Builder userProvisioningEnabled(Optional<Boolean> enabled) {
            this.userProvisioningEnabled = enabled;
            return this;
        }

        public Builder enableEmailQueryView() {
            return enableEmailQueryView(true);
        }

        public Builder disableEmailQueryView() {
            return enableEmailQueryView(false);
        }

        public Builder enableUserProvisioning() {
            return userProvisioningEnabled(true);
        }

        public Builder disableUserProvisioning() {
            return userProvisioningEnabled(false);
        }

        public Builder port(Port port) {
            this.port = Optional.of(port);
            return this;
        }

        public Builder defaultVersion(Version defaultVersion) {
            return defaultVersion(Optional.of(defaultVersion));
        }

        public Builder defaultVersion(Optional<Version> defaultVersion) {
            this.defaultVersion = defaultVersion;
            return this;
        }

        public Builder randomPort() {
            this.port = Optional.empty();
            return this;
        }

        public Builder maximumSendSize(Optional<Long> maximumSendSize) {
            this.maximumSendSize = maximumSendSize;
            return this;
        }

        public JMAPConfiguration build() {
            Preconditions.checkState(enabled.isPresent(), "You should specify if JMAP server should be started");
            return new JMAPConfiguration(enabled.get(), port, emailQueryViewEnabled.orElse(false),
                userProvisioningEnabled.orElse(true),
                defaultVersion.orElse(Version.RFC8621), maximumSendSize);
        }
    }

    public static JMAPConfiguration DEFAULT = JMAPConfiguration.builder().enable().build();

    private final boolean enabled;
    private final Optional<Port> port;
    private final boolean emailQueryViewEnabled;
    private final boolean userProvisioningEnabled;
    private final Version defaultVersion;
    private final Optional<Long> maximumSendSize;

    @VisibleForTesting
    JMAPConfiguration(boolean enabled, Optional<Port> port, boolean emailQueryViewEnabled, boolean userProvisioningEnabled, Version defaultVersion, Optional<Long> maximumSendSize) {
        this.enabled = enabled;
        this.port = port;
        this.emailQueryViewEnabled = emailQueryViewEnabled;
        this.userProvisioningEnabled = userProvisioningEnabled;
        this.defaultVersion = defaultVersion;
        this.maximumSendSize = maximumSendSize;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Optional<Port> getPort() {
        return port;
    }

    public boolean isEmailQueryViewEnabled() {
        return emailQueryViewEnabled;
    }

    public boolean isUserProvisioningEnabled() {
        return userProvisioningEnabled;
    }

    public Version getDefaultVersion() {
        return defaultVersion;
    }

    public Optional<Long> getMaximumSendSize() {
        return maximumSendSize;
    }
}
