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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.api.MetricableBlobStore;
import org.apache.james.blob.api.MetricableBlobStoreContract;
import org.apache.james.blob.objectstorage.crypto.CryptoConfig;
import org.apache.james.blob.objectstorage.swift.Credentials;
import org.apache.james.blob.objectstorage.swift.Identity;
import org.apache.james.blob.objectstorage.swift.PassHeaderName;
import org.apache.james.blob.objectstorage.swift.SwiftTempAuthObjectStorage;
import org.apache.james.blob.objectstorage.swift.TenantName;
import org.apache.james.blob.objectstorage.swift.UserHeaderName;
import org.apache.james.blob.objectstorage.swift.UserName;
import org.jclouds.blobstore.domain.StorageType;
import org.jclouds.domain.Location;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.google.common.base.Charsets;
import com.google.common.base.Strings;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@ExtendWith(DockerSwiftExtension.class)
public class ObjectStorageBlobStoreTest implements MetricableBlobStoreContract {

    private static final String BIG_STRING = Strings.repeat("big blob content", 10 * 1024);
    private static final TenantName TENANT_NAME = TenantName.of("test");
    private static final UserName USER_NAME = UserName.of("tester");
    private static final Credentials PASSWORD = Credentials.of("testing");
    private static final Identity SWIFT_IDENTITY = Identity.of(TENANT_NAME, USER_NAME);
    private static final String SAMPLE_SALT = "c603a7327ee3dcbc031d8d34b1096c605feca5e1";
    private static final CryptoConfig CRYPTO_CONFIG = CryptoConfig.builder()
        .salt(SAMPLE_SALT)
        .password(PASSWORD.value().toCharArray())
        .build();

    private BucketName defaultBucketName;
    private org.jclouds.blobstore.BlobStore blobStore;
    private SwiftTempAuthObjectStorage.Configuration testConfig;
    private ObjectStorageBlobStore objectStorageBlobStore;
    private BlobStore testee;

    @BeforeEach
    void setUp(DockerSwift dockerSwift) {
        defaultBucketName = BucketName.of("e4fc2427-f2aa-422a-a535-3df0d2a086c4");
        testConfig = SwiftTempAuthObjectStorage.configBuilder()
            .endpoint(dockerSwift.swiftEndpoint())
            .identity(SWIFT_IDENTITY)
            .credentials(PASSWORD)
            .tempAuthHeaderUserName(UserHeaderName.of("X-Storage-User"))
            .tempAuthHeaderPassName(PassHeaderName.of("X-Storage-Pass"))
            .build();
        BlobId.Factory blobIdFactory = blobIdFactory();
        ObjectStorageBlobStoreBuilder.ReadyToBuild blobStoreBuilder = ObjectStorageBlobStore
            .builder(testConfig)
            .blobIdFactory(blobIdFactory)
            .namespace(defaultBucketName);
        blobStore = blobStoreBuilder.getSupplier().get();
        objectStorageBlobStore = blobStoreBuilder.build();
        testee = new MetricableBlobStore(metricsTestExtension.getMetricFactory(), objectStorageBlobStore);
    }

    @AfterEach
    void tearDown() {
        objectStorageBlobStore.deleteAllBuckets().block();
        objectStorageBlobStore.close();
    }

    @Override
    public BlobStore testee() {
        return testee;
    }

    @Override
    public BlobId.Factory blobIdFactory() {
        return new HashBlobId.Factory();
    }

    @Test
    void supportsEncryptionWithCustomPayloadCodec() throws IOException {
        ObjectStorageBlobStore encryptedBlobStore = ObjectStorageBlobStore
            .builder(testConfig)
            .blobIdFactory(blobIdFactory())
            .payloadCodec(new AESPayloadCodec(CRYPTO_CONFIG))
            .namespace(defaultBucketName)
            .build();
        String content = "James is the best!";
        BlobId blobId = encryptedBlobStore.save(encryptedBlobStore.getDefaultBucketName(), content).block();

        InputStream read = encryptedBlobStore.read(encryptedBlobStore.getDefaultBucketName(), blobId);
        String expectedContent = IOUtils.toString(read, Charsets.UTF_8);
        assertThat(content).isEqualTo(expectedContent);
    }

    @Test
    void encryptionWithCustomPayloadCodeCannotBeReadFromUnencryptedBlobStore() throws Exception {
        ObjectStorageBlobStore encryptedBlobStore = ObjectStorageBlobStore
            .builder(testConfig)
            .blobIdFactory(blobIdFactory())
            .payloadCodec(new AESPayloadCodec(CRYPTO_CONFIG))
            .namespace(defaultBucketName)
            .build();
        String content = "James is the best!";
        BlobId blobId = encryptedBlobStore.save(encryptedBlobStore.getDefaultBucketName(), content).block();

        InputStream encryptedIs = testee.read(encryptedBlobStore.getDefaultBucketName(), blobId);
        assertThat(encryptedIs).isNotNull();
        String encryptedString = IOUtils.toString(encryptedIs, Charsets.UTF_8);
        assertThat(encryptedString).isNotEqualTo(content);

        InputStream clearTextIs = encryptedBlobStore.read(encryptedBlobStore.getDefaultBucketName(), blobId);
        String expectedContent = IOUtils.toString(clearTextIs, Charsets.UTF_8);
        assertThat(content).isEqualTo(expectedContent);
    }

    @Test
    void deleteBucketShouldDeleteSwiftContainer() {
        BucketName bucketName = BucketName.of("azerty");
        objectStorageBlobStore.save(bucketName, "data").block();

        objectStorageBlobStore.deleteBucket(bucketName).block();

        assertThat(blobStore.containerExists(bucketName.asString()))
            .isFalse();
    }

    @Test
    void deleteAllBucketsShouldDeleteAllContainersInBlobStore() {
        Location defaultLocation = null;
        blobStore.createContainerInLocation(defaultLocation, "bucket1");
        blobStore.createContainerInLocation(defaultLocation, "bucket2");
        blobStore.createContainerInLocation(defaultLocation, "bucket3");

        objectStorageBlobStore.deleteAllBuckets().block();

        assertThat(blobStore.list()
                .stream()
                .filter(storageMetadata -> storageMetadata.getType().equals(StorageType.CONTAINER)))
            .isEmpty();
    }

    @Test
    void saveBytesShouldNotCompleteWhenDoesNotAwait() {
        // String need to be big enough to get async thread busy hence could not return result instantly
        Mono<BlobId> blobIdFuture = testee
            .save(testee.getDefaultBucketName(), BIG_STRING.getBytes(StandardCharsets.UTF_8))
            .subscribeOn(Schedulers.boundedElastic());
        assertThat(blobIdFuture.toFuture()).isNotCompleted();
    }

    @Test
    void saveStringShouldNotCompleteWhenDoesNotAwait() {
        Mono<BlobId> blobIdFuture = testee
            .save(testee.getDefaultBucketName(), BIG_STRING)
            .subscribeOn(Schedulers.boundedElastic());
        assertThat(blobIdFuture.toFuture()).isNotCompleted();
    }

    @Test
    void saveInputStreamShouldNotCompleteWhenDoesNotAwait() {
        Mono<BlobId> blobIdFuture = testee
            .save(testee.getDefaultBucketName(), new ByteArrayInputStream(BIG_STRING.getBytes(StandardCharsets.UTF_8)))
            .subscribeOn(Schedulers.boundedElastic());
        assertThat(blobIdFuture.toFuture()).isNotCompleted();
    }

    @Test
    void readBytesShouldNotCompleteWhenDoesNotAwait() {
        BlobId blobId = testee().save(testee.getDefaultBucketName(), BIG_STRING).block();
        Mono<byte[]> resultFuture = testee.readBytes(testee.getDefaultBucketName(), blobId).subscribeOn(Schedulers.boundedElastic());
        assertThat(resultFuture.toFuture()).isNotCompleted();
    }
}

