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

import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.objectstorage.ObjectStorageBlobStore;
import org.apache.james.blob.objectstorage.ObjectStorageBlobStoreBuilder;
import org.apache.james.blob.objectstorage.ObjectStorageBlobStoreContract;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(DockerAwsS3Extension.class)
class AwsS3ObjectStorageBlobStoreBuilderTest implements ObjectStorageBlobStoreContract {

    private BucketName defaultBucketName;
    private AwsS3AuthConfiguration configuration;
    private AwsS3ObjectStorage awsS3ObjectStorage;

    @BeforeEach
    void setUp(DockerAwsS3Container dockerAwsS3Container) {
        awsS3ObjectStorage = new AwsS3ObjectStorage();
        defaultBucketName = BucketName.of("d1953ef8-cfe8-460b-bc29-3977f5b6656f");
        configuration = AwsS3AuthConfiguration.builder()
            .endpoint(dockerAwsS3Container.getEndpoint())
            .accessKeyId(DockerAwsS3Container.ACCESS_KEY_ID)
            .secretKey(DockerAwsS3Container.SECRET_ACCESS_KEY)
            .build();
    }

    @AfterEach
    void tearDown() {
        awsS3ObjectStorage.tearDown();
    }

    @Override
    public BucketName defaultBucketName() {
        return defaultBucketName;
    }

    @Test
    void blobIdFactoryIsMandatoryToBuildBlobStore() {
        ObjectStorageBlobStoreBuilder.ReadyToBuild builder = ObjectStorageBlobStore
            .builder(configuration)
            .blobIdFactory(null)
            .namespace(defaultBucketName);

        assertThatThrownBy(builder::build).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void builtBlobStoreCanStoreAndRetrieve() {
        ObjectStorageBlobStoreBuilder.ReadyToBuild builder = ObjectStorageBlobStore
            .builder(configuration)
            .blobIdFactory(new HashBlobId.Factory())
            .namespace(defaultBucketName)
            .blobPutter(awsS3ObjectStorage.putBlob(configuration));

        assertBlobStoreCanStoreAndRetrieve(builder);
    }
}