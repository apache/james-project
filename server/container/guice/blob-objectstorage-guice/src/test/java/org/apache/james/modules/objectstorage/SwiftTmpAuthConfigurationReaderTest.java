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

package org.apache.james.modules.objectstorage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.StringReader;
import java.net.URI;
import java.util.Optional;

import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.lang3.StringUtils;
import org.apache.james.blob.objectstorage.swift.Credentials;
import org.apache.james.blob.objectstorage.swift.PassHeaderName;
import org.apache.james.blob.objectstorage.swift.Region;
import org.apache.james.blob.objectstorage.swift.SwiftTempAuthObjectStorage;
import org.apache.james.blob.objectstorage.swift.TenantName;
import org.apache.james.blob.objectstorage.swift.UserHeaderName;
import org.apache.james.blob.objectstorage.swift.UserName;
import org.apache.james.modules.objectstorage.swift.SwiftTmpAuthConfigurationReader;
import org.junit.jupiter.api.Test;

class SwiftTmpAuthConfigurationReaderTest {
    private static final String ENDPOINT = "http://auth.example.com/v2.0";
    private static final String CONFIG_ENDPOINT =
        "objectstorage.swift.endpoint=" + ENDPOINT;

    private static final String CREDENTIALS = "this_is_a_secret";
    private static final String CONFIG_CREDENTIALS =
        "objectstorage.swift.credentials=" + CREDENTIALS;

    private static final String REGION = "EMEA";
    private static final String CONFIG_REGION =
        "objectstorage.swift.region=" + REGION;

    private static final String USER_NAME = "user";
    private static final String CONFIG_USER_NAME =
        "objectstorage.swift.tempauth.username=" + USER_NAME;

    private static final String TENANT_NAME = "tenant";
    private static final String CONFIG_TENANT_NAME =
        "objectstorage.swift.tempauth.tenantname=" + TENANT_NAME;

    private static final String PASS_HEADER_NAME = "X-Pass-Header";
    private static final String CONFIG_PASS_HEADER_NAME =
        "objectstorage.swift.tempauth.passheadername=" + PASS_HEADER_NAME;

    private static final String USER_HEADER_NAME = "X-User-Header";
    private static final String CONFIG_USER_HEADER_NAME =
        "objectstorage.swift.tempauth.userheadername=" + USER_HEADER_NAME;

    @Test
    void readBasicTempAuthConfiguration() throws Exception {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.load(new StringReader(StringUtils.joinWith("\n",
            CONFIG_ENDPOINT,
            CONFIG_CREDENTIALS,
            CONFIG_USER_NAME,
            CONFIG_TENANT_NAME)));
        assertThat(SwiftTmpAuthConfigurationReader.readSwiftConfiguration(configuration))
            .isEqualTo(
                SwiftTempAuthObjectStorage.configBuilder()
                    .endpoint(URI.create(ENDPOINT))
                    .credentials(Credentials.of(CREDENTIALS))
                    .tenantName(TenantName.of(TENANT_NAME))
                    .userName(UserName.of(USER_NAME))
                    .build()
            );
    }

    @Test
    void readTempAuthConfigurationWithRegion() throws Exception {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.load(new StringReader(StringUtils.joinWith("\n",
            CONFIG_ENDPOINT,
            CONFIG_CREDENTIALS,
            CONFIG_USER_NAME,
            CONFIG_TENANT_NAME,
            CONFIG_REGION)));
        assertThat(SwiftTmpAuthConfigurationReader.readSwiftConfiguration(configuration))
            .isEqualTo(
                SwiftTempAuthObjectStorage.configBuilder()
                    .endpoint(URI.create(ENDPOINT))
                    .credentials(Credentials.of(CREDENTIALS))
                    .tenantName(TenantName.of(TENANT_NAME))
                    .userName(UserName.of(USER_NAME))
                    .region(Optional.of(Region.of(REGION)))
                    .build()
            );
    }

    @Test
    void readTempAuthConfigurationWithCustomTempAuthHeaders() throws Exception {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.load(new StringReader(StringUtils.joinWith("\n",
            CONFIG_ENDPOINT,
            CONFIG_CREDENTIALS,
            CONFIG_USER_NAME,
            CONFIG_TENANT_NAME,
            CONFIG_USER_HEADER_NAME,
            CONFIG_PASS_HEADER_NAME)));
        assertThat(SwiftTmpAuthConfigurationReader.readSwiftConfiguration(configuration))
            .isEqualTo(
                SwiftTempAuthObjectStorage.configBuilder()
                    .endpoint(URI.create(ENDPOINT))
                    .credentials(Credentials.of(CREDENTIALS))
                    .tenantName(TenantName.of(TENANT_NAME))
                    .userName(UserName.of(USER_NAME))
                    .tempAuthHeaderUserName(UserHeaderName.of(USER_HEADER_NAME))
                    .tempAuthHeaderPassName(PassHeaderName.of(PASS_HEADER_NAME))
                    .build()
            );
    }

    @Test
    void failToReadSwiftTempAuthConfigurationWhenMissingEndpoint() throws Exception {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.load(new StringReader(StringUtils.joinWith("\n",
            CONFIG_CREDENTIALS,
            CONFIG_USER_NAME,
            CONFIG_TENANT_NAME,
            CONFIG_REGION)));
        assertThatThrownBy(() ->
            SwiftTmpAuthConfigurationReader.readSwiftConfiguration(configuration))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void failToReadSwiftTempAuthConfigurationWhenMissingCrendentials() throws Exception {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.load(new StringReader(StringUtils.joinWith("\n",
            CONFIG_ENDPOINT,
            CONFIG_USER_NAME,
            CONFIG_TENANT_NAME,
            CONFIG_REGION)));
        assertThatThrownBy(() ->
            SwiftTmpAuthConfigurationReader.readSwiftConfiguration(configuration))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void failToReadSwiftTempAuthConfigurationWhenMissingUserName() throws Exception {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.load(new StringReader(StringUtils.joinWith("\n",
            CONFIG_ENDPOINT,
            CONFIG_CREDENTIALS,
            CONFIG_TENANT_NAME,
            CONFIG_REGION)));
        assertThatThrownBy(() ->
            SwiftTmpAuthConfigurationReader.readSwiftConfiguration(configuration))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void failToReadSwiftTempAuthConfigurationWhenMissingTenantName() throws Exception {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.load(new StringReader(StringUtils.joinWith("\n",
            CONFIG_ENDPOINT,
            CONFIG_CREDENTIALS,
            CONFIG_USER_NAME,
            CONFIG_REGION)));
        assertThatThrownBy(() ->
            SwiftTmpAuthConfigurationReader.readSwiftConfiguration(configuration))
            .isInstanceOf(IllegalArgumentException.class);
    }
}