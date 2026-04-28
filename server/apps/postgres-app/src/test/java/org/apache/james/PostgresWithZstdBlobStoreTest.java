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

import static org.apache.james.blob.api.BlobStoreDAO.ContentEncoding.ZSTD;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.nio.charset.StandardCharsets;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.zstd.CompressionConfiguration;
import org.apache.james.blob.zstd.ZstdBlobStoreDAO;
import org.apache.james.modules.TestJMAPServerModule;
import org.apache.james.modules.blobstore.BlobStoreConfiguration;
import org.apache.james.utils.GuiceProbe;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.inject.multibindings.Multibinder;

import reactor.core.publisher.Mono;

public class PostgresWithZstdBlobStoreTest {
    private record BlobSnapshot(byte[] originalPayload, byte[] readPayload, BlobStoreDAO.BytesBlob storedBlob) {
    }

    public static class ZstdBlobStoreProbe implements GuiceProbe {
        private final BlobStore blobStore;
        private final BlobStoreDAO rawBlobStoreDAO;

        @Inject
        public ZstdBlobStoreProbe(BlobStore blobStore, @Named("raw") BlobStoreDAO rawBlobStoreDAO) {
            this.blobStore = blobStore;
            this.rawBlobStoreDAO = rawBlobStoreDAO;
        }

        public BlobSnapshot saveAndRead(String payload) {
            byte[] originalPayload = payload.getBytes(StandardCharsets.UTF_8);
            BlobId blobId = Mono.from(blobStore.save(blobStore.getDefaultBucketName(), originalPayload, BlobStore.StoragePolicy.LOW_COST))
                .block();
            byte[] readPayload = Mono.from(blobStore.readBytes(blobStore.getDefaultBucketName(), blobId))
                .block();
            BlobStoreDAO.BytesBlob storedBlob = Mono.from(rawBlobStoreDAO.readBytes(blobStore.getDefaultBucketName(), blobId))
                .block();

            return new BlobSnapshot(originalPayload, readPayload, storedBlob);
        }
    }

    private static final String COMPRESSIBLE_PAYLOAD = "James postgres zstd blob store integration payload.\n".repeat(2048);

    @RegisterExtension
    static JamesServerExtension jamesServerExtension = new JamesServerBuilder<PostgresJamesConfiguration>(tmpDir ->
        PostgresJamesConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .searchConfiguration(SearchConfiguration.scanning())
            .blobStore(BlobStoreConfiguration.builder()
                .postgres()
                .disableCache()
                .passthrough()
                .noCryptoConfig()
                .compressionConfig(CompressionConfiguration.builder()
                    .enabled(true)
                    .threshold(1)
                    .build()))
            .build())
        .server(configuration -> PostgresJamesServerMain.createServer(configuration)
            .overrideWith(new TestJMAPServerModule())
            .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class)
                .addBinding()
                .to(ZstdBlobStoreProbe.class)))
        .extension(PostgresExtension.empty())
        .lifeCycle(JamesServerExtension.Lifecycle.PER_TEST)
        .build();

    @Test
    void blobStoreDAOShouldPersistCompressedBlobsWithZstdMetadata(GuiceJamesServer server) {
        BlobSnapshot blobSnapshot = server.getProbe(ZstdBlobStoreProbe.class).saveAndRead(COMPRESSIBLE_PAYLOAD);

        assertSoftly(softly -> {
            softly.assertThat(blobSnapshot.readPayload()).isEqualTo(blobSnapshot.originalPayload());
            softly.assertThat(blobSnapshot.storedBlob().payload().length).isLessThan(blobSnapshot.originalPayload().length);
            softly.assertThat(blobSnapshot.storedBlob().metadata().contentEncoding()).contains(ZSTD);
            softly.assertThat(blobSnapshot.storedBlob().metadata().get(ZstdBlobStoreDAO.CONTENT_ORIGINAL_SIZE))
                .contains(new BlobStoreDAO.BlobMetadataValue(String.valueOf(blobSnapshot.originalPayload().length)));
        });
    }
}
