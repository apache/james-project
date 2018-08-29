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

    private static URI ENDPOINT = URI.create("http://example.com");
    private static Credentials CREDENTIALS = Credentials.of("fake");
    private static Identity IDENTITY = Identity.of("fake");

    @Test
    void enpointIsMandatoryToBuildConfiguration() throws Exception {
        ObjectStorageConfiguration.Builder builder = new ObjectStorageConfiguration.Builder();
        assertThatThrownBy(() -> builder.build()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void identityIsMandatoryToBuildConfiguration() throws Exception {
        ObjectStorageConfiguration.Builder builder = new ObjectStorageConfiguration.Builder();
        builder
            .endpoint(new URI("http", "example.com", null, null));
        assertThatThrownBy(() -> builder.build()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void credentialsIsMandatoryToBuildConfiguration() throws Exception {
        ObjectStorageConfiguration.Builder builder = new ObjectStorageConfiguration.Builder();
        builder
            .endpoint(new URI("http", "example.com", null, null))
            .identity(Identity.of("fake"));
        assertThatThrownBy(() -> builder.build()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void configurationIsBuiltWhenAllMandatoryParamsAreProvided() throws Exception {
        ObjectStorageConfiguration.Builder builder = new ObjectStorageConfiguration.Builder();
        builder
            .endpoint(ENDPOINT)
            .identity(IDENTITY)
            .credentials(CREDENTIALS);

        ObjectStorageConfiguration build = builder.build();

        assertThat(build.getEndpoint()).isEqualTo(ENDPOINT);
        assertThat(build.getIdentity()).isEqualTo(IDENTITY);
        assertThat(build.getCredentials()).isEqualTo(CREDENTIALS);
    }
}