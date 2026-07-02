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
 * KIND, either express or implied. See the License for the     *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.jmap.oidc;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.james.jmap.http.OidcAuthenticationStrategy;
import org.junit.jupiter.api.Test;

class JMAPOidcConfigurationTest {
    @Test
    void parseConfigurationShouldEnableOidcWhenSimpleNameIsConfigured() {
        BaseHierarchicalConfiguration configuration = oidcConfiguration(OidcAuthenticationStrategy.class.getSimpleName());

        assertThat(JMAPOidcConfiguration.parseConfiguration(configuration).getOidcEnabled())
            .isTrue();
    }

    @Test
    void parseConfigurationShouldEnableOidcWhenJamesCanonicalNameIsConfigured() {
        BaseHierarchicalConfiguration configuration = oidcConfiguration(OidcAuthenticationStrategy.class.getCanonicalName());

        assertThat(JMAPOidcConfiguration.parseConfiguration(configuration).getOidcEnabled())
            .isTrue();
    }

    @Test
    void parseConfigurationShouldEnableOidcWhenTmailCanonicalNameIsConfigured() {
        BaseHierarchicalConfiguration configuration = oidcConfiguration("com.linagora.tmail.james.jmap.oidc.OidcAuthenticationStrategy");

        assertThat(JMAPOidcConfiguration.parseConfiguration(configuration).getOidcEnabled())
            .isTrue();
    }

    @Test
    void parseConfigurationShouldNotEnableOidcWhenStrategyNameOnlyContainsOidcAuthenticationStrategy() {
        BaseHierarchicalConfiguration configuration = oidcConfiguration("com.example.DisabledOidcAuthenticationStrategy");

        assertThat(JMAPOidcConfiguration.parseConfiguration(configuration).getOidcEnabled())
            .isFalse();
    }

    private BaseHierarchicalConfiguration oidcConfiguration(String authenticationStrategy) {
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty("authentication.strategy.rfc8621", authenticationStrategy);
        configuration.addProperty("oidc.userInfo.url", "http://127.0.0.1/userinfo");
        configuration.addProperty("oidc.introspect.url", "http://127.0.0.1/introspect");
        configuration.addProperty("oidc.claim", "email");
        return configuration;
    }
}
