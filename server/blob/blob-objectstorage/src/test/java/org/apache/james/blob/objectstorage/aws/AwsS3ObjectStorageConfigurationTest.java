/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.james.blob.objectstorage.aws;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public class AwsS3ObjectStorageConfigurationTest {

    @Test
    public void credentialsShouldRespectBeanContract() {
        EqualsVerifier.forClass(AwsS3ObjectStorageConfiguration.class).verify();
    }

    @Test
    public void builderShouldThrowWhenEndpointIsNull() {
        assertThatThrownBy(() -> AwsS3ObjectStorageConfiguration.builder()
                                    .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("'endpoint' is mandatory");
    }

    @Test
    public void builderShouldThrowWhenEndpointIsEmpty() {
        assertThatThrownBy(() -> AwsS3ObjectStorageConfiguration.builder()
                                    .endpoint("")
                                    .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("'endpoint' is mandatory");
    }

    @Test
    public void builderShouldThrowWhenAccessKeyIdIsNull() {
        assertThatThrownBy(() -> AwsS3ObjectStorageConfiguration.builder()
                                    .endpoint("myEndpoint")
                                    .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("'accessKeyId' is mandatory");
    }

    @Test
    public void builderShouldThrowWhenAccessKeyIdIsEmpty() {
        assertThatThrownBy(() -> AwsS3ObjectStorageConfiguration.builder()
                                    .endpoint("myEndpoint")
                                    .accessKeyId("")
                                    .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("'accessKeyId' is mandatory");
    }

    @Test
    public void builderShouldThrowWhenSecretKeyIsNull() {
        assertThatThrownBy(() -> AwsS3ObjectStorageConfiguration.builder()
                                    .endpoint("myEndpoint")
                                    .accessKeyId("myAccessKeyId")
                                    .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("'secretKey' is mandatory");
    }

    @Test
    public void builderShouldThrowWhenSecretKeyIsEmpty() {
        assertThatThrownBy(() -> AwsS3ObjectStorageConfiguration.builder()
                                    .endpoint("myEndpoint")
                                    .accessKeyId("myAccessKeyId")
                                    .secretKey("")
                                    .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("'secretKey' is mandatory");
    }

    @Test
    public void builderShouldWork() {
        String endpoint = "myEndpoint";
        String accessKeyId = "myAccessKeyId";
        String secretKey = "mySecretKey";
        AwsS3ObjectStorageConfiguration configuration = AwsS3ObjectStorageConfiguration.builder()
            .endpoint(endpoint)
            .accessKeyId(accessKeyId)
            .secretKey(secretKey)
            .build();

        assertSoftly(softly -> {
            softly.assertThat(configuration.getEndpoint()).isEqualTo(endpoint);
            softly.assertThat(configuration.getAccessKeyId()).isEqualTo(accessKeyId);
            softly.assertThat(configuration.getSecretKey()).isEqualTo(secretKey);
        });
    }
}
