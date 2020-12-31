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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class WebAdminConfigurationTest {
    static final FixedPortSupplier PORT = new FixedPortSupplier(80);

    @Test
    void buildShouldThrowWhenNoPortButEnabled() {
        assertThatThrownBy(() -> WebAdminConfiguration.builder().enabled().build())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void buildShouldWorkWithoutPortWhenDisabled() {
        assertThat(WebAdminConfiguration.builder()
            .disabled()
            .build())
            .extracting(WebAdminConfiguration::isEnabled)
            .isEqualTo(false);
    }

    @Test
    void buildShouldFailOnNoEnable() {
        assertThatThrownBy(() -> WebAdminConfiguration.builder().port(PORT).build())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void builderShouldBuildWithRightPort() {
        assertThat(
            WebAdminConfiguration.builder()
                .enabled()
                .port(PORT)
                .build())
            .extracting(WebAdminConfiguration::getPort)
            .isEqualTo(PORT);
    }


    @Test
    void builderShouldBuildWithEnable() {
        assertThat(
            WebAdminConfiguration.builder()
                .enabled()
                .port(PORT)
                .build())
            .extracting(WebAdminConfiguration::isEnabled)
            .isEqualTo(true);
    }

    @Test
    void builderShouldAcceptHttps() {
        TlsConfiguration tlsConfiguration = TlsConfiguration.builder()
            .selfSigned("abcd", "efgh")
            .build();

        assertThat(
            WebAdminConfiguration.builder()
                .enabled()
                .tls(tlsConfiguration)
                .port(PORT)
                .build())
            .extracting(WebAdminConfiguration::getTlsConfiguration)
            .isEqualTo(tlsConfiguration);
    }

    @Test
    void builderShouldReturnTlsEnableWhenTlsConfiguration() {
        TlsConfiguration tlsConfiguration = TlsConfiguration.builder()
            .selfSigned("abcd", "efgh")
            .build();

        assertThat(
            WebAdminConfiguration.builder()
                .enabled()
                .tls(tlsConfiguration)
                .port(PORT)
                .build())
            .extracting(WebAdminConfiguration::getTlsConfiguration)
            .isEqualTo(tlsConfiguration);
    }

    @Test
    void builderShouldReturnTlsDisableWhenNoTlsConfiguration() {
        assertThat(
            WebAdminConfiguration.builder()
                .enabled()
                .port(PORT)
                .build())
            .extracting(WebAdminConfiguration::isTlsEnabled)
            .isEqualTo(false);
    }

    @Test
    void builderShouldCORSEnabled() {
        assertThat(
            WebAdminConfiguration.builder()
                .enabled()
                .port(PORT)
                .corsEnabled()
                .build())
            .extracting(WebAdminConfiguration::isEnableCORS)
            .isEqualTo(true);
    }

    @Test
    void builderShouldAcceptAllOriginsByDefault() {
        assertThat(
            WebAdminConfiguration.builder()
                .enabled()
                .port(PORT)
                .corsEnabled()
                .build())
            .extracting(WebAdminConfiguration::getUrlCORSOrigin)
            .isEqualTo("*");
    }

    @Test
    void builderShouldCORSDisabled() {
        assertThat(
            WebAdminConfiguration.builder()
                .enabled()
                .port(PORT)
                .corsDisabled()
                .build())
            .extracting(WebAdminConfiguration::isEnableCORS)
            .isEqualTo(false);
    }

    @Test
    void builderShouldCORSWithOrigin() {
        String origin = "linagora.com";
        assertThat(
            WebAdminConfiguration.builder()
                .enabled()
                .port(PORT)
                .corsEnabled()
                .urlCORSOrigin(origin)
                .build())
            .extracting(WebAdminConfiguration::getUrlCORSOrigin)
            .isEqualTo(origin);
    }

    @Test
    void builderShouldDefineHostWithDefault() {
        assertThat(
            WebAdminConfiguration.builder()
                .enabled()
                .port(PORT)
                .build())
            .extracting(WebAdminConfiguration::getHost)
            .isEqualTo(WebAdminConfiguration.DEFAULT_HOST);
    }

    @Test
    void builderShouldDefineHostWithSetting() {
        String host = "any.host";
        assertThat(
            WebAdminConfiguration.builder()
                .enabled()
                .port(PORT)
                .host(host)
                .build())
            .extracting(WebAdminConfiguration::getHost)
            .isEqualTo(host);
    }

    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(WebAdminConfiguration.class).verify();
    }
}
