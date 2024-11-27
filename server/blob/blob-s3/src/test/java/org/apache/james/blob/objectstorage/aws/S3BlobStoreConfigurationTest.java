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

import static org.apache.james.blob.objectstorage.aws.S3BlobStoreConfiguration.UPLOAD_RETRY_EXCEPTION_PREDICATE;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.util.Optional;

import org.apache.james.blob.objectstorage.aws.sse.S3SSECConfiguration;
import org.junit.jupiter.api.Test;

import com.github.fge.lambdas.Throwing;

import nl.jqno.equalsverifier.EqualsVerifier;
import reactor.util.retry.Retry;

public class S3BlobStoreConfigurationTest {

    @Test
    void configurationShouldRespectBeanContract() {
        EqualsVerifier.forClass(S3BlobStoreConfiguration.class)
            .verify();
    }

    @Test
    void shouldThrowWhenSSECEnableAndNoSSECConfiguration() {
        S3SSECConfiguration ssecConfiguration = null;

        assertThatThrownBy(() -> S3BlobStoreConfiguration.builder()
            .authConfiguration(AwsS3AuthConfiguration.builder()
                .endpoint(Throwing.supplier(() -> new URI("http://localhost:1234")).get())
                .accessKeyId("accessKeyId")
                .secretKey("secretKey1")
                .build())
            .region(Region.of("af-south-1"))
            .uploadRetrySpec(Optional.of(Retry.backoff(3, java.time.Duration.ofSeconds(1))
                .filter(UPLOAD_RETRY_EXCEPTION_PREDICATE)))
            .ssecEnabled()
            .ssecConfiguration(ssecConfiguration)
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("SSEC configuration is mandatory when SSEC is enabled");
    }
}