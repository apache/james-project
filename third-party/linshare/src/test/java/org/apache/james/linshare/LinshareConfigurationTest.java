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

package org.apache.james.linshare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.MalformedURLException;
import java.net.URL;

import org.apache.commons.configuration.PropertiesConfiguration;
import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class LinshareConfigurationTest {
    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(LinshareConfiguration.class)
            .verify();
    }

    @Test
    void fromShouldThrowWhenUrlIsNull() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("blob.export.linshare.token", "token");
        configuration.addProperty("blob.export.linshare.url", null);

        assertThatThrownBy(() -> LinshareConfiguration.from(configuration)).isInstanceOf(MalformedURLException.class);
    }

    @Test
    void fromShouldThrowWhenTokenIsNull() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("blob.export.linshare.token", null);
        configuration.addProperty("blob.export.linshare.url", "http://127.0.0.1:8080");

        assertThatThrownBy(() -> LinshareConfiguration.from(configuration)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromShouldThrowWhenURLIsInvalid() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("blob.export.linshare.token", "token");
        configuration.addProperty("blob.export.linshare.url", "invalid");

        assertThatThrownBy(() -> LinshareConfiguration.from(configuration)).isInstanceOf(MalformedURLException.class);
    }

    @Test
    void fromShouldThrowWhenTokenIsEmpty() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("blob.export.linshare.token", "");
        configuration.addProperty("blob.export.linshare.url", "http://127.0.0.1:8080");

        assertThatThrownBy(() -> LinshareConfiguration.from(configuration)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromShouldReturnProvidedConfiguration() throws Exception {
        String token = "token";
        String url = "http://127.0.0.1:8080";

        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("blob.export.linshare.token", token);
        configuration.addProperty("blob.export.linshare.url", url);

        assertThat(LinshareConfiguration.from(configuration)).isEqualTo(LinshareConfiguration.builder()
            .url(new URL(url))
            .authorizationToken(new AuthorizationToken(token))
            .build());
    }
}
