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
import java.net.URL;
import java.util.List;

import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.jwt.introspection.IntrospectionEndpoint;
import org.apache.james.jwt.oidc.OidcEndpointsInfoResolver;
import org.apache.james.oidc.Aud;
import org.apache.james.oidc.OidcTokenCacheConfiguration;
import org.apache.james.oidc.TokenInfoResolver;
import org.apache.james.utils.PropertiesProvider;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

public class JMAPOidcModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(TokenInfoResolver.class).to(OidcEndpointsInfoResolver.class);
    }

    @Provides
    @Named("userInfo")
    URL provideUserInfoEndpoint(JMAPOidcConfiguration configuration) {
        return configuration.getOidcUserInfoUrl();
    }

    @Provides
    IntrospectionEndpoint provideIntrospectionEndpoint(JMAPOidcConfiguration configuration) {
        return configuration.getIntrospectionEndpoint();
    }

    @Provides
    List<Aud> provideAudience(JMAPOidcConfiguration configuration) {
        return configuration.getAud();
    }

    @Provides
    @Named("oidcClaim")
    String provideOidcClaim(JMAPOidcConfiguration configuration) {
        return configuration.getOidcClaim();
    }

    @Provides
    @Singleton
    OidcTokenCacheConfiguration oidcTokenCacheConfiguration(PropertiesProvider propertiesProvider) throws ConfigurationException {
        try {
            return OidcTokenCacheConfiguration.parse(propertiesProvider.getConfiguration("jmap"));
        } catch (FileNotFoundException e) {
            return OidcTokenCacheConfiguration.DEFAULT;
        }
    }
}
