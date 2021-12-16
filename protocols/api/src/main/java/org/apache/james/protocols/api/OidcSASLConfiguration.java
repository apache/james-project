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
import java.net.URL;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;

import com.google.common.base.Preconditions;

public class OidcSASLConfiguration {

    public static OidcSASLConfiguration parse(HierarchicalConfiguration<ImmutableNode> configuration) throws MalformedURLException {
        String jwksURL = configuration.getString("jwksURL", null);
        String claim = configuration.getString("claim", null);
        String oidcConfigurationURL = configuration.getString("oidcConfigurationURL", null);
        String scope = configuration.getString("scope", null);

        Preconditions.checkNotNull(jwksURL, "`jwksURL` property need to be specified inside the oidc tag");
        Preconditions.checkNotNull(claim, "`claim` property need to be specified inside the oidc tag");
        Preconditions.checkNotNull(oidcConfigurationURL, "`oidcConfigurationURL` property need to be specified inside the oidc tag");
        Preconditions.checkNotNull(scope, "`scope` property need to be specified inside the oidc tag");

        return new OidcSASLConfiguration(jwksURL, claim, oidcConfigurationURL, scope);
    }

    private final URL jwksURL;
    private final String claim;
    private final URL oidcConfigurationURL;
    private final String scope;

    public OidcSASLConfiguration(URL jwksURL, String claim, URL oidcConfigurationURL, String scope) {
        this.jwksURL = jwksURL;
        this.claim = claim;
        this.oidcConfigurationURL = oidcConfigurationURL;
        this.scope = scope;
    }

    public OidcSASLConfiguration(String jwksURL, String claim, String oidcConfigurationURL, String scope) throws MalformedURLException {
        this(new URL(jwksURL), claim, new URL(oidcConfigurationURL), scope);
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
}
