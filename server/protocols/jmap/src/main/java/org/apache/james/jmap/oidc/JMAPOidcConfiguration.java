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

package org.apache.james.jmap.oidc;

import java.io.FileNotFoundException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Optional;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.jmap.http.OidcAuthenticationStrategy;
import org.apache.james.jwt.introspection.IntrospectionEndpoint;
import org.apache.james.oidc.Aud;
import org.apache.james.utils.PropertiesProvider;

import com.google.common.collect.ImmutableList;

public class JMAPOidcConfiguration {
    private static final String AUTHENTICATION_STRATEGIES_PROPERTY = "authentication.strategy.rfc8621";
    private static final String USERINFO_URL_PROPERTY = "oidc.userInfo.url";
    private static final String INTROSPECT_URL_PROPERTY = "oidc.introspect.url";
    private static final String INTROSPECT_CREDENTIALS_PROPERTY = "oidc.introspect.credentials";
    private static final String AUDIENCE_PROPERTY = "oidc.audience";
    private static final String CLAIM_PROPERTY = "oidc.claim";

    @FunctionalInterface
    public interface RequireOidcEnabled {
        Builder oidcEnabled(boolean oidcEnabled);
    }

    public static class Builder {
        private final boolean oidcEnabled;
        private Optional<URL> oidcUserInfoUrl = Optional.empty();
        private Optional<IntrospectionEndpoint> oidcIntrospectionEndpoint = Optional.empty();
        private Optional<String> oidcIntrospectionClaim = Optional.empty();
        private Optional<List<Aud>> oidcAudience = Optional.empty();

        private Builder(boolean oidcEnabled) {
            this.oidcEnabled = oidcEnabled;
        }

        public Builder oidcUserInfoUrl(Optional<URL> url) {
            this.oidcUserInfoUrl = url;
            return this;
        }

        public Builder oidcIntrospectionEndpoint(Optional<IntrospectionEndpoint> endpoint) {
            this.oidcIntrospectionEndpoint = endpoint;
            return this;
        }

        public Builder oidcClaim(Optional<String> claim) {
            this.oidcIntrospectionClaim = claim;
            return this;
        }

        public Builder oidcAudience(Optional<List<Aud>> aud) {
            this.oidcAudience = aud;
            return this;
        }

        public JMAPOidcConfiguration build() {
            if (oidcEnabled) {
                checkOidcFieldsPresence();
            }

            return new JMAPOidcConfiguration(
                oidcEnabled,
                oidcUserInfoUrl,
                oidcIntrospectionEndpoint,
                oidcIntrospectionClaim,
                oidcAudience);
        }

        private void checkOidcFieldsPresence() {
            if (oidcUserInfoUrl.isEmpty() ||
                oidcIntrospectionEndpoint.isEmpty() ||
                oidcIntrospectionClaim.isEmpty()) {
                throw new IllegalStateException("All OIDC fields must be defined when OIDC is enabled.");
            }
        }
    }

    public static RequireOidcEnabled builder() {
        return oidcEnabled -> new Builder(oidcEnabled);
    }

    public static JMAPOidcConfiguration parseConfiguration(PropertiesProvider propertiesProvider) throws ConfigurationException, FileNotFoundException {
        return parseConfiguration(propertiesProvider.getConfiguration("jmap"));
    }

    public static JMAPOidcConfiguration parseConfiguration(Configuration configuration) {
        List<String> authenticationStrategies = configuration.getList(String.class, AUTHENTICATION_STRATEGIES_PROPERTY, ImmutableList.of());
        boolean oidcEnabled = authenticationStrategies.stream()
            .anyMatch(JMAPOidcConfiguration::isOidcAuthenticationStrategy);

        Optional<URL> oidcUserInfoUrl = Optional.ofNullable(configuration.getString(USERINFO_URL_PROPERTY, null))
            .map(JMAPOidcConfiguration::parseUrl);
        Optional<URL> oidcIntrospectUrl = Optional.ofNullable(configuration.getString(INTROSPECT_URL_PROPERTY, null))
            .map(JMAPOidcConfiguration::parseUrl);
        Optional<String> oidcIntrospectCreds = Optional.ofNullable(configuration.getString(INTROSPECT_CREDENTIALS_PROPERTY, null));
        Optional<List<Aud>> oidcAudience = Optional.of(configuration.getList(String.class, AUDIENCE_PROPERTY, ImmutableList.of()).stream().map(Aud::new).toList());
        Optional<String> oidcIntrospectionClaim = Optional.ofNullable(configuration.getString(CLAIM_PROPERTY, null));

        Optional<IntrospectionEndpoint> introspectionEndpoint = oidcIntrospectUrl.map(url -> new IntrospectionEndpoint(url, oidcIntrospectCreds));

        return JMAPOidcConfiguration.builder()
            .oidcEnabled(oidcEnabled)
            .oidcUserInfoUrl(oidcUserInfoUrl)
            .oidcIntrospectionEndpoint(introspectionEndpoint)
            .oidcAudience(oidcAudience)
            .oidcClaim(oidcIntrospectionClaim)
            .build();
    }

    private static boolean isOidcAuthenticationStrategy(String authenticationStrategy) {
        return authenticationStrategy.equals(OidcAuthenticationStrategy.class.getSimpleName()) ||
            authenticationStrategy.endsWith("." + OidcAuthenticationStrategy.class.getSimpleName());
    }

    private static URL parseUrl(String rawUrl) {
        try {
            return new URL(rawUrl);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Invalid OIDC URL: " + rawUrl, e);
        }
    }

    private final boolean oidcEnabled;
    private final Optional<URL> oidcUserInfoUrl;
    private final Optional<IntrospectionEndpoint> introspectionEndpoint;
    private final Optional<String> oidcClaim;
    private final Optional<List<Aud>> aud;

    JMAPOidcConfiguration(boolean oidcEnabled, Optional<URL> oidcUserInfoUrl, Optional<IntrospectionEndpoint> introspectionEndpoint,
                          Optional<String> oidcClaim, Optional<List<Aud>> aud) {
        this.oidcEnabled = oidcEnabled;
        this.oidcUserInfoUrl = oidcUserInfoUrl;
        this.introspectionEndpoint = introspectionEndpoint;
        this.oidcClaim = oidcClaim;
        this.aud = aud;
    }

    public boolean getOidcEnabled() {
        return oidcEnabled;
    }

    public URL getOidcUserInfoUrl() {
        return oidcUserInfoUrl.get();
    }

    public String getOidcClaim() {
        return oidcClaim.get();
    }

    public IntrospectionEndpoint getIntrospectionEndpoint() {
        return introspectionEndpoint.get();
    }

    public List<Aud> getAud() {
        return aud.get();
    }
}
