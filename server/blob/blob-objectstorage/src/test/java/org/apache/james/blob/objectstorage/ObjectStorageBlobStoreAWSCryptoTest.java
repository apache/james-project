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

import java.io.IOException;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.DeduplicationBlobStoreContract;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.api.MetricableBlobStore;
import org.apache.james.blob.api.MetricableBlobStoreContract;
import org.apache.james.blob.objectstorage.aws.AwsS3AuthConfiguration;
import org.apache.james.blob.objectstorage.aws.AwsS3ObjectStorage;
import org.apache.james.blob.objectstorage.aws.DockerAwsS3Container;
import org.apache.james.blob.objectstorage.aws.DockerAwsS3Extension;
import org.apache.james.blob.objectstorage.crypto.CryptoConfig;
import org.apache.james.blob.objectstorage.swift.Credentials;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(DockerAwsS3Extension.class)
public class ObjectStorageBlobStoreAWSCryptoTest implements MetricableBlobStoreContract, DeduplicationBlobStoreContract {
    private static final HashBlobId.Factory BLOB_ID_FACTORY = new HashBlobId.Factory();
    private static final Credentials PASSWORD = Credentials.of("testing");
    private static final String SAMPLE_SALT = "c603a7327ee3dcbc031d8d34b1096c605feca5e1";
    private static final CryptoConfig CRYPTO_CONFIG = CryptoConfig.builder()
        .salt(SAMPLE_SALT)
        .password(PASSWORD.value().toCharArray())
        .build();

    private ObjectStorageBlobStore objectStorageBlobStore;
    private BlobStore testee;
    private AwsS3ObjectStorage awsS3ObjectStorage;

    @BeforeEach
    void setUp(DockerAwsS3Container dockerAwsS3) {
        awsS3ObjectStorage = new AwsS3ObjectStorage();
        AwsS3AuthConfiguration configuration = AwsS3AuthConfiguration.builder()
            .endpoint(dockerAwsS3.getEndpoint())
            .accessKeyId(DockerAwsS3Container.ACCESS_KEY_ID)
            .secretKey(DockerAwsS3Container.SECRET_ACCESS_KEY)
            .build();

        ObjectStorageBlobStoreBuilder.ReadyToBuild builder = ObjectStorageBlobStore
            .builder(configuration)
            .blobIdFactory(BLOB_ID_FACTORY)
            .payloadCodec(new AESPayloadCodec(CRYPTO_CONFIG))
            .blobPutter(awsS3ObjectStorage.putBlob(configuration));

        objectStorageBlobStore = builder.build();
        testee = new MetricableBlobStore(metricsTestExtension.getMetricFactory(), objectStorageBlobStore);
    }

    @AfterEach
    void tearDown() throws IOException {
        objectStorageBlobStore.deleteAllBuckets().block();
        objectStorageBlobStore.close();
        awsS3ObjectStorage.tearDown();
    }

    @Override
    public BlobStore testee() {
        return testee;
    }

    @Override
    public BlobId.Factory blobIdFactory() {
        return BLOB_ID_FACTORY;
    }
}
