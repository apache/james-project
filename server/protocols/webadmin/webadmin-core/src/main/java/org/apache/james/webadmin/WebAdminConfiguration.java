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

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

public class WebAdminConfiguration {

    public static final boolean DEFAULT_CORS_DISABLED = false;
    public static final String CORS_ALL_ORIGINS = "*";
    public static final String DEFAULT_HOST = "localhost";

    public static final WebAdminConfiguration DISABLED_CONFIGURATION = WebAdminConfiguration.builder()
        .disabled()
        .build();

    public static final WebAdminConfiguration TEST_CONFIGURATION = WebAdminConfiguration.builder()
        .enabled()
        .corsDisabled()
        .host("127.0.0.1")
        .port(new RandomPortSupplier())
        .build();

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Optional<Boolean> enabled = Optional.empty();
        private Optional<PortSupplier> port = Optional.empty();
        private Optional<Boolean> enableCORS = Optional.empty();
        private Optional<TlsConfiguration> tlsConfiguration = Optional.empty();
        private Optional<String> urlCORSOrigin = Optional.empty();
        private Optional<String> host = Optional.empty();
        private ImmutableList.Builder<String> additionalRoutes = ImmutableList.builder();
        private Optional<String> jwtPublicKey = Optional.empty();

        public Builder jwtPublicKeyPEM(String jwtPublicKeyPEM) {
            this.jwtPublicKey = Optional.of(jwtPublicKeyPEM);
            return this;
        }

        public Builder jwtPublicKeyPEM(Optional<String> jwtPublicKeyPEM) {
            jwtPublicKeyPEM.ifPresent(this::jwtPublicKeyPEM);
            return this;
        }

        public Builder tls(TlsConfiguration tlsConfiguration) {
            this.tlsConfiguration = Optional.of(tlsConfiguration);
            return this;
        }

        public Builder tls(Optional<TlsConfiguration> tlsConfiguration) {
            this.tlsConfiguration = tlsConfiguration;
            return this;
        }

        public Builder port(PortSupplier portSupplier) {
            this.port = Optional.of(portSupplier);
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

        public Builder corsEnabled() {
            return enableCORS(true);
        }

        public Builder corsDisabled() {
            return enableCORS(false);
        }

        public Builder host(String host) {
            this.host = Optional.ofNullable(host);
            return this;
        }

        public Builder additionalRoute(String additionalRoute) {
            this.additionalRoutes.add(additionalRoute);
            return this;
        }

        public Builder additionalRoutes(Collection<String> additionalRoutes) {
            this.additionalRoutes.addAll(additionalRoutes);
            return this;
        }

        public WebAdminConfiguration build() {
            Preconditions.checkState(enabled.isPresent(), "You need to explicitly enable or disable WebAdmin server");
            Preconditions.checkState(!enabled.get() || port.isPresent(), "You need to specify a port for WebAdminConfiguration");

            return new WebAdminConfiguration(enabled.get(),
                port,
                tlsConfiguration,
                enableCORS.orElse(DEFAULT_CORS_DISABLED),
                urlCORSOrigin.orElse(CORS_ALL_ORIGINS),
                host.orElse(DEFAULT_HOST),
                additionalRoutes.build(),
                jwtPublicKey);
        }
    }

    private final boolean enabled;
    private final Optional<PortSupplier> port;
    private final Optional<TlsConfiguration> tlsConfiguration;
    private final boolean enableCORS;
    private final String urlCORSOrigin;
    private final String host;
    private final List<String> additionalRoutes;
    private final Optional<String> jwtPublicKey;

    @VisibleForTesting
    WebAdminConfiguration(boolean enabled, Optional<PortSupplier> port, Optional<TlsConfiguration> tlsConfiguration,
                          boolean enableCORS, String urlCORSOrigin, String host, List<String> additionalRoutes, Optional<String> jwtPublicKey) {
        this.enabled = enabled;
        this.port = port;
        this.tlsConfiguration = tlsConfiguration;
        this.enableCORS = enableCORS;
        this.urlCORSOrigin = urlCORSOrigin;
        this.host = host;
        this.additionalRoutes = additionalRoutes;
        this.jwtPublicKey = jwtPublicKey;
    }

    public Optional<String> getJwtPublicKey() {
        return jwtPublicKey;
    }

    public List<String> getAdditionalRoutes() {
        return additionalRoutes;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getUrlCORSOrigin() {
        return urlCORSOrigin;
    }

    public PortSupplier getPort() {
        return port.orElseThrow(() -> new IllegalStateException("No port was specified"));
    }

    public TlsConfiguration getTlsConfiguration() {
        return tlsConfiguration.orElseThrow(() -> new IllegalStateException("No tls configuration"));
    }

    public boolean isEnableCORS() {
        return enableCORS;
    }

    public boolean isTlsEnabled() {
        return tlsConfiguration.isPresent();
    }

    public String getHost() {
        return host;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof WebAdminConfiguration) {
            WebAdminConfiguration that = (WebAdminConfiguration) o;

            return Objects.equals(this.enabled, that.enabled)
                && Objects.equals(this.port, that.port)
                && Objects.equals(this.tlsConfiguration, that.tlsConfiguration)
                && Objects.equals(this.enableCORS, that.enableCORS)
                && Objects.equals(this.jwtPublicKey, that.jwtPublicKey)
                && Objects.equals(this.urlCORSOrigin, that.urlCORSOrigin)
                && Objects.equals(this.host, that.host)
                && Objects.equals(this.additionalRoutes, that.additionalRoutes);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(enabled, port, tlsConfiguration, enableCORS, jwtPublicKey, urlCORSOrigin, host, additionalRoutes);
    }
}
