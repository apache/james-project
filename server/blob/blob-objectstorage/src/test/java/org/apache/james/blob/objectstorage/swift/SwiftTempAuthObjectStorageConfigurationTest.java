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

package org.apache.james.blob.objectstorage.swift;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;

import org.jclouds.openstack.keystone.config.KeystoneProperties;
import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class SwiftTempAuthObjectStorageConfigurationTest {

    private static final TenantName TENANT_NAME = TenantName.of("fake");
    private static final UserName USER_NAME = UserName.of("fake");
    private static URI ENDPOINT = URI.create("http://example.com");
    private static Credentials CREDENTIALS = Credentials.of("fake");
    private static Identity SWIFT_IDENTITY = Identity.of(TenantName.of("fake"),
        UserName.of("fake"));

    @Test
    void enpointIsMandatoryToBuildConfiguration() throws Exception {
        SwiftTempAuthObjectStorage.Configuration.Builder builder =
            SwiftTempAuthObjectStorage.configBuilder()
            .tenantName(TENANT_NAME)
            .userName(USER_NAME)
            .credentials(CREDENTIALS);

        assertThatThrownBy(builder::build).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void tenantNameIsMandatoryToBuildConfiguration() throws Exception {
        SwiftTempAuthObjectStorage.Configuration.Builder builder =
            SwiftTempAuthObjectStorage.configBuilder()
            .endpoint(ENDPOINT)
            .userName(USER_NAME)
            .credentials(CREDENTIALS);

        assertThatThrownBy(builder::build).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void userNameIsMandatoryToBuildConfiguration() throws Exception {
        SwiftTempAuthObjectStorage.Configuration.Builder builder =
            SwiftTempAuthObjectStorage.configBuilder()
            .endpoint(ENDPOINT)
            .tenantName(TENANT_NAME)
            .credentials(CREDENTIALS);

        assertThatThrownBy(builder::build).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void credentialsIsMandatoryToBuildConfiguration() throws Exception {
        SwiftTempAuthObjectStorage.Configuration.Builder builder =
            SwiftTempAuthObjectStorage.configBuilder()
            .endpoint(ENDPOINT)
            .tenantName(TENANT_NAME)
            .userName(USER_NAME);

        assertThatThrownBy(builder::build).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void configurationIsBuiltWhenAllMandatoryParamsAreProvided() throws Exception {
        SwiftTempAuthObjectStorage.Configuration.Builder builder =
            SwiftTempAuthObjectStorage.configBuilder()
            .endpoint(ENDPOINT)
            .tenantName(TENANT_NAME)
            .userName(USER_NAME)
            .credentials(CREDENTIALS);

        SwiftTempAuthObjectStorage.Configuration build = builder.build();

        assertThat(build.getEndpoint()).isEqualTo(ENDPOINT);
        assertThat(build.getIdentity()).isEqualTo(SWIFT_IDENTITY);
        assertThat(build.getCredentials()).isEqualTo(CREDENTIALS);
        assertThat(build.getOverrides().getProperty(KeystoneProperties.CREDENTIAL_TYPE)).isEqualTo("tempAuthCredentials");
    }

    @Test
    void identityCanReplaceTenantAndUserName() throws Exception {
        SwiftTempAuthObjectStorage.Configuration.Builder builder =
            SwiftTempAuthObjectStorage.configBuilder()
            .endpoint(ENDPOINT)
            .identity(SWIFT_IDENTITY)
            .credentials(CREDENTIALS);

        SwiftTempAuthObjectStorage.Configuration build = builder.build();

        assertThat(build.getEndpoint()).isEqualTo(ENDPOINT);
        assertThat(build.getIdentity()).isEqualTo(SWIFT_IDENTITY);
        assertThat(build.getCredentials()).isEqualTo(CREDENTIALS);
    }

    @Test
    void configurationShouldEnforceBeanContract() {
        EqualsVerifier.forClass(SwiftTempAuthObjectStorage.Configuration.class);
    }
}