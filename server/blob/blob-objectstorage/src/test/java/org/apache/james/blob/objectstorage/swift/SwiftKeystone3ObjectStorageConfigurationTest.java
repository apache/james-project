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

class SwiftKeystone3ObjectStorageConfigurationTest {

    private static final ProjectName PROJECT_NAME = ProjectName.of("project");
    private static URI ENDPOINT = URI.create("http://example.com");
    private static Credentials CREDENTIALS = Credentials.of("fake");
    private static final DomainName DOMAIN_NAME = DomainName.of("fake");
    private static final DomainId DOMAIN_ID = DomainId.of("fake");
    private static IdentityV3 SWIFT_IDENTITY =
        IdentityV3.of(DOMAIN_NAME, UserName.of("fake"));

    @Test
    void enpointIsMandatoryToBuildConfiguration() throws Exception {
        SwiftKeystone3ObjectStorage.Configuration.Builder builder =
            SwiftKeystone3ObjectStorage.configBuilder()
            .identity(SWIFT_IDENTITY)
            .credentials(CREDENTIALS);
        assertThatThrownBy(builder::build).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void identityIsMandatoryToBuildConfiguration() throws Exception {
        SwiftKeystone3ObjectStorage.Configuration.Builder builder =
            SwiftKeystone3ObjectStorage.configBuilder()
            .endpoint(ENDPOINT)
            .credentials(CREDENTIALS);

        assertThatThrownBy(builder::build).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void credentialsIsMandatoryToBuildConfiguration() throws Exception {
        SwiftKeystone3ObjectStorage.Configuration.Builder builder =
            SwiftKeystone3ObjectStorage.configBuilder()
            .endpoint(ENDPOINT)
            .identity(SWIFT_IDENTITY);

        assertThatThrownBy(builder::build).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void configurationIsBuiltWhenAllMandatoryParamsAreProvided() throws Exception {
        SwiftKeystone3ObjectStorage.Configuration.Builder builder =
            SwiftKeystone3ObjectStorage.configBuilder()
            .endpoint(ENDPOINT)
            .identity(SWIFT_IDENTITY)
            .credentials(CREDENTIALS);

        SwiftKeystone3ObjectStorage.Configuration build = builder.build();

        assertThat(build.getEndpoint()).isEqualTo(ENDPOINT);
        assertThat(build.getIdentity()).isEqualTo(SWIFT_IDENTITY);
        assertThat(build.getCredentials()).isEqualTo(CREDENTIALS);
        assertThat(build.getOverrides().getProperty(KeystoneProperties.KEYSTONE_VERSION)).isEqualTo("3");
    }

    @Test
    void authCanBeProjectScoped() throws Exception {
        SwiftKeystone3ObjectStorage.Configuration.Builder builder =
            SwiftKeystone3ObjectStorage.configBuilder()
            .endpoint(ENDPOINT)
            .identity(SWIFT_IDENTITY)
            .project(Project.of(PROJECT_NAME))
            .credentials(CREDENTIALS);

        SwiftKeystone3ObjectStorage.Configuration build = builder.build();

        assertThat(build.getEndpoint()).isEqualTo(ENDPOINT);
        assertThat(build.getIdentity()).isEqualTo(SWIFT_IDENTITY);
        assertThat(build.getCredentials()).isEqualTo(CREDENTIALS);
        assertThat(build.getOverrides().getProperty(KeystoneProperties.SCOPE)).isEqualTo(PROJECT_NAME.asString());
    }

    @Test
    void authCanBeProjectAndDomainNameScoped() throws Exception {
        SwiftKeystone3ObjectStorage.Configuration.Builder builder =
            SwiftKeystone3ObjectStorage.configBuilder()
            .endpoint(ENDPOINT)
            .identity(SWIFT_IDENTITY)
            .project(Project.of(PROJECT_NAME, DOMAIN_NAME))
            .credentials(CREDENTIALS);

        SwiftKeystone3ObjectStorage.Configuration build = builder.build();

        assertThat(build.getEndpoint()).isEqualTo(ENDPOINT);
        assertThat(build.getIdentity()).isEqualTo(SWIFT_IDENTITY);
        assertThat(build.getCredentials()).isEqualTo(CREDENTIALS);
        assertThat(build.getOverrides().getProperty(KeystoneProperties.SCOPE)).isEqualTo(PROJECT_NAME.asString());
        assertThat(build.getOverrides().getProperty(KeystoneProperties.PROJECT_DOMAIN_NAME)).isEqualTo(DOMAIN_NAME.value());
    }

    @Test
    void authCanBeProjectAndDomainIdScoped() throws Exception {
        SwiftKeystone3ObjectStorage.Configuration.Builder builder =
            SwiftKeystone3ObjectStorage.configBuilder()
            .endpoint(ENDPOINT)
            .identity(SWIFT_IDENTITY)
            .project(Project.of(PROJECT_NAME, DOMAIN_ID))
            .credentials(CREDENTIALS);

        SwiftKeystone3ObjectStorage.Configuration build = builder.build();

        assertThat(build.getEndpoint()).isEqualTo(ENDPOINT);
        assertThat(build.getIdentity()).isEqualTo(SWIFT_IDENTITY);
        assertThat(build.getCredentials()).isEqualTo(CREDENTIALS);
        assertThat(build.getOverrides().getProperty(KeystoneProperties.SCOPE)).isEqualTo(PROJECT_NAME.asString());
        assertThat(build.getOverrides().getProperty(KeystoneProperties.PROJECT_DOMAIN_ID)).isEqualTo(DOMAIN_ID.value());
    }

    @Test
    void authCanBeDomainIdScoped() throws Exception {
        SwiftKeystone3ObjectStorage.Configuration.Builder builder =
            SwiftKeystone3ObjectStorage.configBuilder()
            .endpoint(ENDPOINT)
            .identity(SWIFT_IDENTITY)
            .domainId(DOMAIN_ID)
            .credentials(CREDENTIALS);

        SwiftKeystone3ObjectStorage.Configuration build = builder.build();

        assertThat(build.getEndpoint()).isEqualTo(ENDPOINT);
        assertThat(build.getIdentity()).isEqualTo(SWIFT_IDENTITY);
        assertThat(build.getCredentials()).isEqualTo(CREDENTIALS);
        assertThat(build.getOverrides().getProperty(KeystoneProperties.SCOPE)).isEqualTo(DOMAIN_ID.asString());
    }

    @Test
    void configurationShouldEnforceBeanContract() {
        EqualsVerifier.forClass(SwiftKeystone3ObjectStorage.Configuration.class);
    }
}