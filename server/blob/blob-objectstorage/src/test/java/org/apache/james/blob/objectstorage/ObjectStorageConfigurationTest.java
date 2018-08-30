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

package org.apache.james.blob.objectstorage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;

import org.junit.jupiter.api.Test;

class ObjectStorageConfigurationTest {

    private static final TenantName TENANT_NAME = TenantName.of("fake");
    private static final UserName USER_NAME = UserName.of("fake");
    private static URI ENDPOINT = URI.create("http://example.com");
    private static Credentials CREDENTIALS = Credentials.of("fake");
    private static SwiftIdentity SWIFT_IDENTITY = SwiftIdentity.of(TenantName.of("fake"),
        UserName.of("fake"));

    @Test
    void enpointIsMandatoryToBuildConfiguration() throws Exception {
        ObjectStorageConfiguration.Builder builder = new ObjectStorageConfiguration.Builder();
        builder
            .tenantName(TENANT_NAME)
            .userName(USER_NAME)
            .credentials(CREDENTIALS);

        assertThatThrownBy(builder::build).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void tenantNameIsMandatoryToBuildConfiguration() throws Exception {
        ObjectStorageConfiguration.Builder builder = new ObjectStorageConfiguration.Builder();
        builder
            .endpoint(ENDPOINT)
            .userName(USER_NAME)
            .credentials(CREDENTIALS);

        assertThatThrownBy(builder::build).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void userNameIsMandatoryToBuildConfiguration() throws Exception {
        ObjectStorageConfiguration.Builder builder = new ObjectStorageConfiguration.Builder();
        builder
            .endpoint(ENDPOINT)
            .tenantName(TENANT_NAME)
            .credentials(CREDENTIALS);

        assertThatThrownBy(builder::build).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void credentialsIsMandatoryToBuildConfiguration() throws Exception {
        ObjectStorageConfiguration.Builder builder = new ObjectStorageConfiguration.Builder();
        builder
            .endpoint(ENDPOINT)
            .tenantName(TENANT_NAME)
            .userName(USER_NAME);

        assertThatThrownBy(builder::build).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void configurationIsBuiltWhenAllMandatoryParamsAreProvided() throws Exception {
        ObjectStorageConfiguration.Builder builder = new ObjectStorageConfiguration.Builder();
        builder
            .endpoint(ENDPOINT)
            .tenantName(TENANT_NAME)
            .userName(USER_NAME)
            .credentials(CREDENTIALS);

        ObjectStorageConfiguration build = builder.build();

        assertThat(build.getEndpoint()).isEqualTo(ENDPOINT);
        assertThat(build.getSwiftIdentity()).isEqualTo(SWIFT_IDENTITY);
        assertThat(build.getCredentials()).isEqualTo(CREDENTIALS);
    }

    @Test
    void identityCanReplaceTenantAndUserName() throws Exception {
        ObjectStorageConfiguration.Builder builder = new ObjectStorageConfiguration.Builder();
        builder
            .endpoint(ENDPOINT)
            .identity(SWIFT_IDENTITY)
            .credentials(CREDENTIALS);

        ObjectStorageConfiguration build = builder.build();

        assertThat(build.getEndpoint()).isEqualTo(ENDPOINT);
        assertThat(build.getSwiftIdentity()).isEqualTo(SWIFT_IDENTITY);
        assertThat(build.getCredentials()).isEqualTo(CREDENTIALS);
    }
}