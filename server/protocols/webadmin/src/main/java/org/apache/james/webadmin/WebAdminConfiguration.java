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


package org.apache.james.webadmin;

import java.util.Objects;
import java.util.Optional;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

public class WebAdminConfiguration {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Optional<Boolean> enabled = Optional.empty();
        private Port port;
        private Optional<HttpsConfiguration> httpsConfiguration = Optional.empty();

        public Builder https(HttpsConfiguration httpsConfiguration) {
            this.httpsConfiguration = Optional.of(httpsConfiguration);
            return this;
        }

        public Builder port(Port port) {
            this.port = port;
            return this;
        }

        public Builder enable(boolean isEnabled) {
            this.enabled = Optional.of(isEnabled);
            return this;
        }

        public Builder enabled() {
            return enable(true);
        }

        public Builder disabled() {
            return enable(false);
        }

        public WebAdminConfiguration build() {
            Preconditions.checkState(enabled.isPresent(), "You need to explicitly enable or disable WebAdmin server");
            Preconditions.checkState(!enabled.get() || port != null, "You need to specify a port for WebAdminConfiguration");
            return new WebAdminConfiguration(enabled.get(),
                port,
                httpsConfiguration.orElse(
                    HttpsConfiguration.builder()
                        .disabled()
                        .build()));
        }
    }

    private final boolean enabled;
    private final Port port;
    private final HttpsConfiguration httpsConfiguration;

    @VisibleForTesting
    WebAdminConfiguration(boolean enabled, Port port, HttpsConfiguration httpsConfiguration) {
        this.enabled = enabled;
        this.port = port;
        this.httpsConfiguration = httpsConfiguration;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Port getPort() {
        return port;
    }

    public HttpsConfiguration getHttpsConfiguration() {
        return httpsConfiguration;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof WebAdminConfiguration) {
            WebAdminConfiguration that = (WebAdminConfiguration) o;

            return Objects.equals(this.enabled, that.enabled)
                && Objects.equals(this.port, that.port);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(enabled, port);
    }
}
