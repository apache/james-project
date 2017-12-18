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

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import nl.jqno.equalsverifier.EqualsVerifier;

public class WebAdminConfigurationTest {

    public static final FixedPortSupplier PORT = new FixedPortSupplier(80);

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void buildShouldThrowWhenNoPortButEnabled() {
        expectedException.expect(IllegalStateException.class);

        WebAdminConfiguration.builder().enabled().build();
    }

    @Test
    public void buildShouldWorkWithoutPortWhenDisabled() {
        assertThat(WebAdminConfiguration.builder()
            .disabled()
            .build())
            .extracting(WebAdminConfiguration::isEnabled)
            .containsExactly(false);
    }

    @Test
    public void buildShouldFailOnNoEnable() {
        expectedException.expect(IllegalStateException.class);

        WebAdminConfiguration.builder().port(PORT).build();
    }

    @Test
    public void builderShouldBuildWithRightPort() {
        assertThat(
            WebAdminConfiguration.builder()
                .enabled()
                .port(PORT)
                .build())
            .extracting(WebAdminConfiguration::getPort)
            .containsExactly(PORT);
    }


    @Test
    public void builderShouldBuildWithEnable() {
        assertThat(
            WebAdminConfiguration.builder()
                .enabled()
                .port(PORT)
                .build())
            .extracting(WebAdminConfiguration::isEnabled)
            .containsExactly(true);
    }

    @Test
    public void builderShouldAcceptHttps() {
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
            .containsExactly(tlsConfiguration);
    }

    @Test
    public void builderShouldReturnTlsEnableWhenTlsConfiguration() {
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
            .containsExactly(tlsConfiguration);
    }

    @Test
    public void builderShouldReturnTlsDisableWhenNoTlsConfiguration() {
        assertThat(
            WebAdminConfiguration.builder()
                .enabled()
                .port(PORT)
                .build())
            .extracting(WebAdminConfiguration::isTlsEnabled)
            .containsExactly(false);
    }

    @Test
    public void builderShouldCORSEnabled() {
        assertThat(
            WebAdminConfiguration.builder()
                .enabled()
                .port(PORT)
                .CORSenabled()
                .build())
            .extracting(WebAdminConfiguration::isEnableCORS)
            .containsExactly(true);
    }

    @Test
    public void builderShouldAcceptAllOriginsByDefault() {
        assertThat(
            WebAdminConfiguration.builder()
                .enabled()
                .port(PORT)
                .CORSenabled()
                .build())
            .extracting(WebAdminConfiguration::getUrlCORSOrigin)
            .containsExactly("*");
    }

    @Test
    public void builderShouldCORSDisabled() {
        assertThat(
            WebAdminConfiguration.builder()
                .enabled()
                .port(PORT)
                .CORSdisabled()
                .build())
            .extracting(WebAdminConfiguration::isEnableCORS)
            .containsExactly(false);
    }

    @Test
    public void builderShouldCORSWithOrigin() {
        String origin = "linagora.com";
        assertThat(
            WebAdminConfiguration.builder()
                .enabled()
                .port(PORT)
                .CORSenabled()
                .urlCORSOrigin(origin)
                .build())
            .extracting(WebAdminConfiguration::getUrlCORSOrigin)
            .containsExactly(origin);
    }

    @Test
    public void builderShouldDefineHostWithDefault() {
        assertThat(
            WebAdminConfiguration.builder()
                .enabled()
                .port(PORT)
                .build())
            .extracting(WebAdminConfiguration::getHost)
            .containsExactly(WebAdminConfiguration.DEFAULT_HOST);
    }

    @Test
    public void builderShouldDefineHostWithSetting() {
        String host = "any.host";
        assertThat(
            WebAdminConfiguration.builder()
                .enabled()
                .port(PORT)
                .host(host)
                .build())
            .extracting(WebAdminConfiguration::getHost)
            .containsExactly(host);
    }

    @Test
    public void shouldMatchBeanContract() {
        EqualsVerifier.forClass(WebAdminConfiguration.class).verify();
    }

}
