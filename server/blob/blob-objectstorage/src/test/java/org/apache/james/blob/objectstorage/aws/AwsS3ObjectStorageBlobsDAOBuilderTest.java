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

import java.util.UUID;

import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.api.TestBlobId;
import org.apache.james.blob.objectstorage.ContainerName;
import org.apache.james.blob.objectstorage.ObjectStorageBlobsDAO;
import org.apache.james.blob.objectstorage.ObjectStorageBlobsDAOBuilder;
import org.apache.james.blob.objectstorage.ObjectStorageBlobsDAOContract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(DockerAwsS3Extension.class)
class AwsS3ObjectStorageBlobsDAOBuilderTest implements ObjectStorageBlobsDAOContract {

    private ContainerName containerName;
    private AwsS3AuthConfiguration configuration;

    @BeforeEach
    void setUp(DockerAwsS3Container dockerAwsS3Container) {
        containerName = ContainerName.of(UUID.randomUUID().toString());
        configuration = AwsS3AuthConfiguration.builder()
            .endpoint(dockerAwsS3Container.getEndpoint())
            .accessKeyId(DockerAwsS3Container.ACCESS_KEY_ID)
            .secretKey(DockerAwsS3Container.SECRET_ACCESS_KEY)
            .build();
    }

    @Override
    public ContainerName containerName() {
        return containerName;
    }

    @Test
    void containerNameIsMandatoryToBuildBlobsDAO() {
        ObjectStorageBlobsDAOBuilder.ReadyToBuild builder = ObjectStorageBlobsDAO
            .builder(configuration)
            .container(null)
            .blobIdFactory(new HashBlobId.Factory());

        assertThatThrownBy(builder::build).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void blobIdFactoryIsMandatoryToBuildBlobsDAO() {
        ObjectStorageBlobsDAOBuilder.ReadyToBuild builder = ObjectStorageBlobsDAO
            .builder(configuration)
            .container(containerName)
            .blobIdFactory(null);

        assertThatThrownBy(builder::build).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void builtBlobsDAOCanStoreAndRetrieve() {
        ObjectStorageBlobsDAOBuilder.ReadyToBuild builder = ObjectStorageBlobsDAO
            .builder(configuration)
            .container(containerName)
            .blobIdFactory(new HashBlobId.Factory())
            .putBlob(AwsS3ObjectStorage.putBlob(new TestBlobId.Factory(), containerName, configuration));

        assertBlobsDAOCanStoreAndRetrieve(builder);
    }
}