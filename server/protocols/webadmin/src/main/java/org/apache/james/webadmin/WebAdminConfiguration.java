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

    public static final boolean DEFAULT_CORS_DISABLED = false;
    public static final String CORS_ALL_ORIGINS = "*";

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Optional<Boolean> enabled = Optional.empty();
        private Port port;
        private Optional<Boolean> enableCORS = Optional.empty();
        private Optional<TlsConfiguration> httpsConfiguration = Optional.empty();
        private Optional<String> urlCORSOrigin = Optional.empty();

        public Builder https(TlsConfiguration tlsConfiguration) {
            this.httpsConfiguration = Optional.of(tlsConfiguration);
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

        public Builder urlCORSOrigin(String origin) {
            this.urlCORSOrigin = Optional.ofNullable(origin);
            return this;
        }

        public Builder enableCORS(boolean isEnabled) {
            this.enableCORS = Optional.of(isEnabled);
            return this;
        }

        public Builder CORSenabled() {
            return enableCORS(true);
        }

        public Builder CORSdisabled() {
            return enableCORS(false);
        }

        public WebAdminConfiguration build() {
            Preconditions.checkState(enabled.isPresent(), "You need to explicitly enable or disable WebAdmin server");
            Preconditions.checkState(!enabled.get() || port != null, "You need to specify a port for WebAdminConfiguration");
            return new WebAdminConfiguration(enabled.get(),
                port,
                httpsConfiguration.orElse(
                    TlsConfiguration.builder()
                        .disabled()
                        .build()),
                enableCORS.orElse(DEFAULT_CORS_DISABLED),
                urlCORSOrigin.orElse(CORS_ALL_ORIGINS));
        }
    }

    private final boolean enabled;
    private final Port port;
    private final TlsConfiguration tlsConfiguration;
    private final boolean enableCORS;
    private final String urlCORSOrigin;

    @VisibleForTesting
    WebAdminConfiguration(boolean enabled, Port port, TlsConfiguration tlsConfiguration, boolean enableCORS, String urlCORSOrigin) {
        this.enabled = enabled;
        this.port = port;
        this.tlsConfiguration = tlsConfiguration;
        this.enableCORS = enableCORS;
        this.urlCORSOrigin = urlCORSOrigin;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getUrlCORSOrigin() {
        return urlCORSOrigin;
    }

    public Port getPort() {
        return port;
    }

    public TlsConfiguration getTlsConfiguration() {
        return tlsConfiguration;
    }

    public boolean isEnableCORS() {
        return enableCORS;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof WebAdminConfiguration) {
            WebAdminConfiguration that = (WebAdminConfiguration) o;

            return Objects.equals(this.enabled, that.enabled)
                && Objects.equals(this.port, that.port)
                && Objects.equals(this.tlsConfiguration, that.tlsConfiguration)
                && Objects.equals(this.enableCORS, that.enableCORS)
                && Objects.equals(this.urlCORSOrigin, that.urlCORSOrigin);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(enabled, port, tlsConfiguration, enableCORS, urlCORSOrigin);
    }
}
