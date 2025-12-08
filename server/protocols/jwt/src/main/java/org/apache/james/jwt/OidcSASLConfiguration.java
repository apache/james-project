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

package org.apache.james.jwt;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Optional;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

public class OidcSASLConfiguration {
    private static final Logger LOGGER = LoggerFactory.getLogger(OidcSASLConfiguration.class);

    private static final boolean FORCE_INTROSPECT = Boolean.parseBoolean(System.getProperty("james.sasl.oidc.force.introspect", "true"));
    private static final boolean VALIDATE_AUD = Boolean.parseBoolean(System.getProperty("james.sasl.oidc.validate.aud", "true"));

    @VisibleForTesting
    static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private URL jwksURL;
        private String claim;
        private URL oidcConfigurationURL;
        private String scope;
        private Optional<URL> introspectionEndpoint = Optional.empty();
        private Optional<String> introspectionEndpointAuthorization = Optional.empty();
        private Optional<String> aud = Optional.empty();
        private Optional<URL> userInfoEndpoint = Optional.empty();

        private Builder() {
        }

        public Builder jwksURL(URL jwksURL) {
            this.jwksURL = jwksURL;
            return this;
        }

        public Builder claim(String claim) {
            this.claim = claim;
            return this;
        }

        public Builder oidcConfigurationURL(URL oidcConfigurationURL) {
            this.oidcConfigurationURL = oidcConfigurationURL;
            return this;
        }

        public Builder scope(String scope) {
            this.scope = scope;
            return this;
        }

        public Builder introspectionEndpoint(Optional<URL> introspectionEndpoint) {
            this.introspectionEndpoint = introspectionEndpoint;
            return this;
        }

        public Builder introspectionEndpoint(URL introspectionEndpoint) {
            this.introspectionEndpoint = Optional.ofNullable(introspectionEndpoint);
            return this;
        }

        public Builder introspectionEndpointAuthorization(Optional<String> introspectionEndpointAuthorization) {
            this.introspectionEndpointAuthorization = introspectionEndpointAuthorization;
            return this;
        }

        public Builder introspectionEndpointAuthorization(String introspectionEndpointAuthorization) {
            this.introspectionEndpointAuthorization = Optional.ofNullable(introspectionEndpointAuthorization);
            return this;
        }

        public Builder userInfoEndpoint(Optional<URL> userInfoEndpoint) {
            this.userInfoEndpoint = userInfoEndpoint;
            return this;
        }

        public Builder userInfoEndpoint(URL userInfoEndpoint) {
            this.userInfoEndpoint = Optional.ofNullable(userInfoEndpoint);
            return this;
        }

        public Builder aud(String aud) {
            this.aud = Optional.ofNullable(aud);
            return this;
        }

        public OidcSASLConfiguration build() {
            Preconditions.checkNotNull(jwksURL, "jwksURL is mandatory");
            Preconditions.checkNotNull(claim, "claim is mandatory");
            Preconditions.checkNotNull(oidcConfigurationURL, "oidcConfigurationURL is mandatory");
            Preconditions.checkNotNull(scope, "scope is mandatory");

            return new OidcSASLConfiguration(jwksURL, claim, oidcConfigurationURL, scope,
                introspectionEndpoint, introspectionEndpointAuthorization, userInfoEndpoint, aud);
        }
    }

    public static OidcSASLConfiguration parse(HierarchicalConfiguration<ImmutableNode> configuration) throws MalformedURLException {
        String jwksURL = configuration.getString("jwksURL", null);
        String claim = configuration.getString("claim", null);
        String oidcConfigurationURL = configuration.getString("oidcConfigurationURL", null);
        String scope = configuration.getString("scope", null);

        Preconditions.checkNotNull(jwksURL, "`jwksURL` property need to be specified inside the oidc tag");
        Preconditions.checkNotNull(claim, "`claim` property need to be specified inside the oidc tag");
        Preconditions.checkNotNull(oidcConfigurationURL, "`oidcConfigurationURL` property need to be specified inside the oidc tag");
        Preconditions.checkNotNull(scope, "`scope` property need to be specified inside the oidc tag");

        String introspectionUrl = configuration.getString("introspection.url", null);
        String userInfoUrl = configuration.getString("userinfo.url", null);
        String aud = configuration.getString("aud", null);

        if (introspectionUrl == null) {
            if (FORCE_INTROSPECT) {
                throw new IllegalArgumentException("'introspection.url' is mandatory for secure set up. Disable this check with -Djames.sasl.oidc.force.introspect=false.");
            } else {
                LOGGER.warn("'introspection.url' is mandatory for secure set up. This check was disabled with -Djames.sasl.oidc.force.introspect=false.");
            }
        }
        if (aud == null) {
            if (VALIDATE_AUD) {
                throw new IllegalArgumentException("'aud' is mandatory for secure set up. Disable this check with -Djames.sasl.oidc.validate.aud=false.");
            } else {
                LOGGER.warn("'aud' is mandatory for secure set up. This check was disabled with -Djames.sasl.oidc.validate.aud=false.");
            }
        }

        return new OidcSASLConfiguration(new URL(jwksURL), claim, new URL(oidcConfigurationURL), scope, Optional.ofNullable(introspectionUrl)
            .map(Throwing.function(URL::new)), Optional.ofNullable(configuration.getString("introspection.auth", null)),
            Optional.ofNullable(userInfoUrl).map(Throwing.function(URL::new)), Optional.ofNullable(aud));
    }

    private final URL jwksURL;
    private final String claim;
    private final URL oidcConfigurationURL;
    private final String scope;
    private final Optional<URL> introspectionEndpoint;
    private final Optional<String> aud;
    private final Optional<String> introspectionEndpointAuthorization;
    private final Optional<URL> userInfoEndpoint;

    private OidcSASLConfiguration(URL jwksURL,
                                  String claim,
                                  URL oidcConfigurationURL,
                                  String scope,
                                  Optional<URL> introspectionEndpoint,
                                  Optional<String> introspectionEndpointAuthorization,
                                  Optional<URL> userInfoEndpoint,
                                  Optional<String> aud) {
        this.jwksURL = jwksURL;
        this.claim = claim;
        this.oidcConfigurationURL = oidcConfigurationURL;
        this.scope = scope;
        this.introspectionEndpoint = introspectionEndpoint;
        this.aud = aud;
        this.introspectionEndpointAuthorization = introspectionEndpointAuthorization;
        this.userInfoEndpoint = userInfoEndpoint;
    }

    public URL getJwksURL() {
        return jwksURL;
    }

    public String getClaim() {
        return claim;
    }

    public URL getOidcConfigurationURL() {
        return oidcConfigurationURL;
    }

    public String getScope() {
        return scope;
    }

    public Optional<URL> getIntrospectionEndpoint() {
        return introspectionEndpoint;
    }

    public Optional<String> getIntrospectionEndpointAuthorization() {
        return introspectionEndpointAuthorization;
    }

    public Optional<URL> getUserInfoEndpoint() {
        return userInfoEndpoint;
    }

    public boolean isCheckTokenByIntrospectionEndpoint() {
        return getIntrospectionEndpoint().isPresent();
    }

    public Optional<String> getAud() {
        return aud;
    }

    public boolean isCheckTokenByUserinfoEndpoint() {
        return getUserInfoEndpoint().isPresent();
    }
}
