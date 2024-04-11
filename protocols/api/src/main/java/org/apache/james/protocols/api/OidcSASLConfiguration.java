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

package org.apache.james.protocols.api;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Optional;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.Preconditions;

public class OidcSASLConfiguration {

    public static OidcSASLConfiguration parse(HierarchicalConfiguration<ImmutableNode> configuration) throws MalformedURLException, URISyntaxException {
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

        return new OidcSASLConfiguration(new URI(jwksURL).toURL(), claim, new URI(oidcConfigurationURL).toURL(), scope, Optional.ofNullable(introspectionUrl)
            .map(Throwing.function(value -> new URI(value).toURL())), Optional.ofNullable(configuration.getString("introspection.auth", null)),
            Optional.ofNullable(userInfoUrl).map(Throwing.function(value -> new URI(value).toURL())));
    }

    private final URL jwksURL;
    private final String claim;
    private final URL oidcConfigurationURL;
    private final String scope;
    private final Optional<URL> introspectionEndpoint;
    private final Optional<String> introspectionEndpointAuthorization;
    private final Optional<URL> userInfoEndpoint;

    public OidcSASLConfiguration(URL jwksURL,
                                 String claim,
                                 URL oidcConfigurationURL,
                                 String scope,
                                 Optional<URL> introspectionEndpoint,
                                 Optional<String> introspectionEndpointAuthorization,
                                 Optional<URL> userInfoEndpoint) {
        this.jwksURL = jwksURL;
        this.claim = claim;
        this.oidcConfigurationURL = oidcConfigurationURL;
        this.scope = scope;
        this.introspectionEndpoint = introspectionEndpoint;
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


    public boolean isCheckTokenByUserinfoEndpoint() {
        return getUserInfoEndpoint().isPresent();
    }
}
