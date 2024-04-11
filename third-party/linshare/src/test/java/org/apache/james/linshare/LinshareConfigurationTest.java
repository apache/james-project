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
import java.net.URI;
import java.util.UUID;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class LinshareConfigurationTest {

    private static final String EMPTY_STRING = "";
    private static final String SOME_RANDOM_STRING = "laksdhfdksd";
    private static final String DEFAULT_URL = "http://127.0.0.1:8080";
    private static final String DEFAULT_UUID = UUID.randomUUID().toString();

    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(LinshareConfiguration.class)
            .verify();
    }

    @Test
    void fromShouldThrowWhenUrlIsNull() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty(LinshareConfiguration.URL_PROPERTY, null);
        configuration.addProperty(LinshareConfiguration.UUID_PROPERTY, DEFAULT_UUID);
        configuration.addProperty(LinshareConfiguration.PASSWORD_PROPERTY, LinshareFixture.TECHNICAL_ACCOUNT.getPassword());

        assertThatThrownBy(() -> LinshareConfiguration.from(configuration)).isInstanceOf(MalformedURLException.class);
    }

    @Test
    void fromShouldThrowWhenBasicAuthIsNull() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty(LinshareConfiguration.URL_PROPERTY, DEFAULT_URL);
        configuration.addProperty(LinshareConfiguration.UUID_PROPERTY, null);
        configuration.addProperty(LinshareConfiguration.PASSWORD_PROPERTY, null);

        assertThatThrownBy(() -> LinshareConfiguration.from(configuration)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void fromShouldThrowWhenUUIDIsNull() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty(LinshareConfiguration.PASSWORD_PROPERTY, SOME_RANDOM_STRING);
        configuration.addProperty(LinshareConfiguration.UUID_PROPERTY, null);
        configuration.addProperty(LinshareConfiguration.URL_PROPERTY, DEFAULT_URL);

        assertThatThrownBy(() -> LinshareConfiguration.from(configuration)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void fromShouldThrowWhenUUIDIsEmpty() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty(LinshareConfiguration.UUID_PROPERTY, EMPTY_STRING);
        configuration.addProperty(LinshareConfiguration.PASSWORD_PROPERTY, SOME_RANDOM_STRING);
        configuration.addProperty(LinshareConfiguration.URL_PROPERTY, DEFAULT_URL);

        assertThatThrownBy(() -> LinshareConfiguration.from(configuration)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromShouldThrowWhenUUIDIsWrongFormat() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty(LinshareConfiguration.UUID_PROPERTY, SOME_RANDOM_STRING);
        configuration.addProperty(LinshareConfiguration.PASSWORD_PROPERTY, SOME_RANDOM_STRING);
        configuration.addProperty(LinshareConfiguration.URL_PROPERTY, DEFAULT_URL);

        assertThatThrownBy(() -> LinshareConfiguration.from(configuration)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromShouldThrowWhenUUIDIsTooLong() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty(LinshareConfiguration.UUID_PROPERTY, "way-too-long-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        configuration.addProperty(LinshareConfiguration.PASSWORD_PROPERTY, SOME_RANDOM_STRING);
        configuration.addProperty(LinshareConfiguration.URL_PROPERTY, DEFAULT_URL);

        assertThatThrownBy(() -> LinshareConfiguration.from(configuration)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromShouldThrowWhenURLIsInvalid() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty(LinshareConfiguration.UUID_PROPERTY, DEFAULT_UUID);
        configuration.addProperty(LinshareConfiguration.PASSWORD_PROPERTY, LinshareFixture.TECHNICAL_ACCOUNT.getPassword());
        configuration.addProperty(LinshareConfiguration.URL_PROPERTY, "invalid");

        assertThatThrownBy(() -> LinshareConfiguration.from(configuration)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromShouldThrowWhenPasswordIsNull() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty(LinshareConfiguration.UUID_PROPERTY, DEFAULT_UUID);
        configuration.addProperty(LinshareConfiguration.PASSWORD_PROPERTY, null);
        configuration.addProperty(LinshareConfiguration.URL_PROPERTY, DEFAULT_URL);

        assertThatThrownBy(() -> LinshareConfiguration.from(configuration)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void fromShouldThrowWhenPasswordIsEmpty() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty(LinshareConfiguration.UUID_PROPERTY, DEFAULT_UUID);
        configuration.addProperty(LinshareConfiguration.PASSWORD_PROPERTY, EMPTY_STRING);
        configuration.addProperty(LinshareConfiguration.URL_PROPERTY, DEFAULT_URL);

        assertThatThrownBy(() -> LinshareConfiguration.from(configuration)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromShouldReturnProvidedConfiguration() throws Exception {
        String password = LinshareFixture.TECHNICAL_ACCOUNT.getPassword();
        String url = DEFAULT_URL;

        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty(LinshareConfiguration.UUID_PROPERTY, DEFAULT_UUID);
        configuration.addProperty(LinshareConfiguration.PASSWORD_PROPERTY, password);
        configuration.addProperty(LinshareConfiguration.URL_PROPERTY, url);

        assertThat(LinshareConfiguration.from(configuration)).isEqualTo(LinshareConfiguration.builder()
            .url(new URI(url).toURL())
            .basicAuthorization(DEFAULT_UUID, password)
            .build());
    }
}
