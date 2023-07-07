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

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.ObjectStoreHealthCheck;
import org.apache.james.blob.api.TestBlobId;
import org.apache.james.core.healthcheck.Result;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(DockerAwsS3Extension.class)
public class S3HealthCheckTest {

    private ObjectStoreHealthCheck s3HealthCheck;

    @BeforeEach
    void setUp(DockerAwsS3Container dockerAwsS3) {
        AwsS3AuthConfiguration authConfiguration = AwsS3AuthConfiguration.builder()
            .endpoint(dockerAwsS3.getEndpoint())
            .accessKeyId(DockerAwsS3Container.ACCESS_KEY_ID)
            .secretKey(DockerAwsS3Container.SECRET_ACCESS_KEY)
            .build();

        S3BlobStoreConfiguration s3Configuration = S3BlobStoreConfiguration.builder()
            .authConfiguration(authConfiguration)
            .region(dockerAwsS3.dockerAwsS3().region())
            .build();

        BlobStoreDAO s3BlobStoreDAO = new S3BlobStoreDAO(s3Configuration, new TestBlobId.Factory());
        s3HealthCheck = new ObjectStoreHealthCheck(s3BlobStoreDAO);
    }

    @AfterEach
    void reset(DockerAwsS3Container dockerAwsS3) {
        if (dockerAwsS3.isPaused()) {
            dockerAwsS3.unpause();
        }
    }

    @Test
    void checkShouldReturnHealthyWhenS3IsRunning() {
        Result check = s3HealthCheck.check().block();
        assertThat(check.isHealthy()).isTrue();
    }

    @Test
    void checkShouldReturnUnhealthyWhenS3IsNotRunning(DockerAwsS3Container dockerAwsS3) {
        dockerAwsS3.pause();
        Result check = s3HealthCheck.check().block();
        assertThat(check.isUnHealthy()).isTrue();
    }

    @Test
    void checkShouldDetectWhenS3Recovered(DockerAwsS3Container dockerAwsS3) {
        dockerAwsS3.pause();
        dockerAwsS3.unpause();
        Result check = s3HealthCheck.check().block();
        assertThat(check.isHealthy()).isTrue();
    }
}
