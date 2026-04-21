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

package org.apache.james;

import static org.apache.james.blob.api.BlobStoreDAO.ContentTransferEncoding.ZSTD;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.nio.charset.StandardCharsets;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.apache.james.blob.aes.CryptoConfig;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.zstd.CompressionConfiguration;
import org.apache.james.blob.zstd.ZstdBlobStoreDAO;
import org.apache.james.modules.AwsS3BlobStoreExtension;
import org.apache.james.modules.RabbitMQExtension;
import org.apache.james.modules.TestJMAPServerModule;
import org.apache.james.modules.blobstore.BlobStoreConfiguration;
import org.apache.james.utils.GuiceProbe;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.luben.zstd.Zstd;
import com.google.inject.multibindings.Multibinder;

import reactor.core.publisher.Mono;

public class WithEncryptedAndZstdBlobStoreTest implements MailsShouldBeWellReceivedConcreteContract {
    private record BlobSnapshot(byte[] originalPayload, byte[] readPayload, BlobStoreDAO.BytesBlob encryptionLayerBlob,
                                BlobStoreDAO.BytesBlob rawS3StoredBlob) {
    }

    public static class EncryptedZstdBlobStoreProbe implements GuiceProbe {
        private final BlobStore blobStore;
        private final BlobStoreDAO encryptionBlobStoreDAO;
        private final BlobStoreDAO rawBlobStoreDAO;

        @Inject
        public EncryptedZstdBlobStoreProbe(BlobStore blobStore, @Named("encryption") BlobStoreDAO encryptionBlobStoreDAO,
                                    @Named("raw") BlobStoreDAO rawBlobStoreDAO) {
            this.blobStore = blobStore;
            this.encryptionBlobStoreDAO = encryptionBlobStoreDAO;
            this.rawBlobStoreDAO = rawBlobStoreDAO;
        }

        public BlobSnapshot saveAndRead(String payload) {
            byte[] originalPayload = payload.getBytes(StandardCharsets.UTF_8);
            BlobId blobId = Mono.from(blobStore.save(blobStore.getDefaultBucketName(), originalPayload, BlobStore.StoragePolicy.LOW_COST))
                .block();
            byte[] readPayload = Mono.from(blobStore.readBytes(blobStore.getDefaultBucketName(), blobId))
                .block();
            BlobStoreDAO.BytesBlob encryptionLayerBlob = Mono.from(encryptionBlobStoreDAO.readBytes(blobStore.getDefaultBucketName(), blobId))
                .block();
            BlobStoreDAO.BytesBlob rawS3StoredBlob = Mono.from(rawBlobStoreDAO.readBytes(blobStore.getDefaultBucketName(), blobId))
                .block();

            return new BlobSnapshot(originalPayload, readPayload, encryptionLayerBlob, rawS3StoredBlob);
        }
    }

    private static final String COMPRESSIBLE_PAYLOAD = "James encrypted zstd blob store integration payload.\n".repeat(2048);

    @RegisterExtension
    static JamesServerExtension jamesServerExtension = new JamesServerBuilder<CassandraRabbitMQJamesConfiguration>(tmpDir ->
        CassandraRabbitMQJamesConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .blobStore(BlobStoreConfiguration.s3()
                .disableCache()
                .passthrough()
                .withCryptoConfig(CryptoConfig.builder()
                    .password("myPass".toCharArray())
                    .salt("73616c7479")
                    .build())
                .compressionConfig(CompressionConfiguration.builder()
                    .enabled(true)
                    .threshold(1)
                    .build()))
            .searchConfiguration(SearchConfiguration.openSearch())
            .build())
        .extension(new DockerOpenSearchExtension())
        .extension(new CassandraExtension())
        .extension(new RabbitMQExtension())
        .server(configuration -> CassandraRabbitMQJamesServerMain.createServer(configuration)
            .overrideWith(new TestJMAPServerModule())
            .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class)
                .addBinding()
                .to(EncryptedZstdBlobStoreProbe.class)))
        .extension(new AwsS3BlobStoreExtension())
        .lifeCycle(JamesServerExtension.Lifecycle.PER_TEST)
        .build();

    @Test
    void blobStoreShouldCompressBeforeEncryptingBlobs(GuiceJamesServer server) {
        BlobSnapshot blobSnapshot = server.getProbe(EncryptedZstdBlobStoreProbe.class).saveAndRead(COMPRESSIBLE_PAYLOAD);

        assertSoftly(softly -> {
            // read should round trip to the original saving payload
            softly.assertThat(blobSnapshot.readPayload()).isEqualTo(blobSnapshot.originalPayload());

            // The intermediate encryption layer returns bytes that were zstd-compressed:
            // they differ from the original payload, keep zstd metadata, and round-trip through zstd decompression back to the original payload.
            softly.assertThat(blobSnapshot.encryptionLayerBlob().payload()).isNotEqualTo(blobSnapshot.originalPayload());
            softly.assertThat(blobSnapshot.encryptionLayerBlob().metadata().contentTransferEncoding()).contains(ZSTD);
            softly.assertThat(Zstd.decompress(blobSnapshot.encryptionLayerBlob().payload(), blobSnapshot.originalPayload().length))
                .isEqualTo(blobSnapshot.originalPayload());

            // Raw S3 storage must therefore be the encrypted form of those compressed bytes.
            softly.assertThat(blobSnapshot.rawS3StoredBlob().payload()).isNotEqualTo(blobSnapshot.originalPayload());
            softly.assertThat(blobSnapshot.rawS3StoredBlob().payload()).isNotEqualTo(blobSnapshot.encryptionLayerBlob().payload());
            softly.assertThat(blobSnapshot.rawS3StoredBlob().metadata().contentTransferEncoding()).contains(ZSTD);
            softly.assertThat(blobSnapshot.rawS3StoredBlob().metadata().get(ZstdBlobStoreDAO.CONTENT_ORIGINAL_SIZE))
                .contains(new BlobStoreDAO.BlobMetadataValue(String.valueOf(blobSnapshot.originalPayload().length)));
        });
    }
}
