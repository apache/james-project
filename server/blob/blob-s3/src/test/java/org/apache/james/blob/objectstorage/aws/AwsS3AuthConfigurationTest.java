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

package org.apache.james.blob.objectstorage.aws;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.net.URI;

import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public class AwsS3AuthConfigurationTest {

    private static final URI ENDPOINT = URI.create("http://myEndpoint");
    private static final String ACCESS_KEY_ID = "myAccessKeyId";
    private static final String SECRET_KEY = "mySecretKey";
    private static final String TRUST_STORE_PATH = "/where/ever/truststore.p12";
    private static final String TRUST_STORE_TYPE = "PKCS12";
    private static final String TRUST_STORE_SECRET = "myTrustStoreSecret";
    private static final String TRUST_STORE_ALGORITHM = "myTrustStoreAlgorithm";

    @Test
    public void credentialsShouldRespectBeanContract() {
        EqualsVerifier.forClass(AwsS3AuthConfiguration.class).verify();
    }

    @Test
    public void builderShouldThrowWhenEndpointIsNull() {
        assertThatThrownBy(() -> AwsS3AuthConfiguration.builder()
                                    .endpoint(null)
                                    .accessKeyId(ACCESS_KEY_ID)
                                    .secretKey(SECRET_KEY)
                                    .build())
            .isInstanceOf(NullPointerException.class)
            .hasMessage("'endpoint' is mandatory");
    }

    @Test
    public void builderShouldThrowWhenAccessKeyIdIsNull() {
        assertThatThrownBy(() -> AwsS3AuthConfiguration.builder()
                                    .endpoint(ENDPOINT)
                                    .accessKeyId(null)
                                    .secretKey(SECRET_KEY)
                                    .build())
            .isInstanceOf(NullPointerException.class)
            .hasMessage("'accessKeyId' is mandatory");
    }

    @Test
    public void builderShouldThrowWhenAccessKeyIdIsEmpty() {
        assertThatThrownBy(() -> AwsS3AuthConfiguration.builder()
                                    .endpoint(ENDPOINT)
                                    .accessKeyId("")
                                    .secretKey(SECRET_KEY)
                                    .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("'accessKeyId' is mandatory");
    }

    @Test
    public void builderShouldThrowWhenSecretKeyIsNull() {
        assertThatThrownBy(() -> AwsS3AuthConfiguration.builder()
                                    .endpoint(ENDPOINT)
                                    .accessKeyId(ACCESS_KEY_ID)
                                    .secretKey(null)
                                    .build())
            .isInstanceOf(NullPointerException.class)
            .hasMessage("'secretKey' is mandatory");
    }

    @Test
    public void builderShouldThrowWhenSecretKeyIsEmpty() {
        assertThatThrownBy(() -> AwsS3AuthConfiguration.builder()
                                    .endpoint(ENDPOINT)
                                    .accessKeyId(ACCESS_KEY_ID)
                                    .secretKey("")
                                    .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("'secretKey' is mandatory");
    }

    @Test
    public void builderShouldWork() {
        AwsS3AuthConfiguration configuration = AwsS3AuthConfiguration.builder()
            .endpoint(ENDPOINT)
            .accessKeyId(ACCESS_KEY_ID)
            .secretKey(SECRET_KEY)
            .trustStorePath(TRUST_STORE_PATH)
            .trustStoreType(TRUST_STORE_TYPE)
            .trustStoreSecret(TRUST_STORE_SECRET)
            .trustStoreAlgorithm(TRUST_STORE_ALGORITHM)
            .build();

        assertSoftly(softly -> {
            softly.assertThat(configuration.getEndpoint()).isEqualTo(ENDPOINT);
            softly.assertThat(configuration.getAccessKeyId()).isEqualTo(ACCESS_KEY_ID);
            softly.assertThat(configuration.getSecretKey()).isEqualTo(SECRET_KEY);
            softly.assertThat(configuration.getTrustStorePath()).hasValue(TRUST_STORE_PATH);
            softly.assertThat(configuration.getTrustStoreType()).hasValue(TRUST_STORE_TYPE);
            softly.assertThat(configuration.getTrustStoreSecret()).hasValue(TRUST_STORE_SECRET);
            softly.assertThat(configuration.getTrustStoreAlgorithm()).hasValue(TRUST_STORE_ALGORITHM);
        });
    }

    @Test
    public void builderShouldWorkWithoutOptionals() {
        AwsS3AuthConfiguration configuration = AwsS3AuthConfiguration.builder()
                .endpoint(ENDPOINT)
                .accessKeyId(ACCESS_KEY_ID)
                .secretKey(SECRET_KEY)
                .build();

        assertSoftly(softly -> {
            softly.assertThat(configuration.getEndpoint()).isEqualTo(ENDPOINT);
            softly.assertThat(configuration.getAccessKeyId()).isEqualTo(ACCESS_KEY_ID);
            softly.assertThat(configuration.getSecretKey()).isEqualTo(SECRET_KEY);
            softly.assertThat(configuration.getTrustStorePath()).isNotPresent();
            softly.assertThat(configuration.getTrustStoreType()).isNotPresent();
            softly.assertThat(configuration.getTrustStoreSecret()).isNotPresent();
            softly.assertThat(configuration.getTrustStoreAlgorithm()).isNotPresent();
        });
    }
}
