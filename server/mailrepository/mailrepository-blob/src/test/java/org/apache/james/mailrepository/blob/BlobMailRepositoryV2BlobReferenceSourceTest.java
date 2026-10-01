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

package org.apache.james.mailrepository.blob;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.PlainBlobId;
import org.apache.james.blob.memory.MemoryBlobStoreDAO;
import org.apache.james.blob.memory.MemoryBlobStoreFactory;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.mailrepository.api.MailKey;
import org.apache.james.mailrepository.api.MailRepository;
import org.apache.james.mailrepository.api.MailRepositoryPath;
import org.apache.james.mailrepository.api.MailRepositoryStore;
import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.apache.james.mailrepository.api.Protocol;
import org.apache.james.server.core.MailImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BlobMailRepositoryV2BlobReferenceSourceTest {

    private static final Protocol BLOBV2_PROTOCOL = new Protocol("blobv2");
    private static final MailRepositoryPath PATH = MailRepositoryPath.from("var/mail/error");
    private static final MailRepositoryUrl URL = MailRepositoryUrl.fromPathAndProtocol(BLOBV2_PROTOCOL, PATH);

    private static class SimpleMailRepositoryStore implements MailRepositoryStore {
        private final MailRepository repository;
        private final boolean fail;

        SimpleMailRepositoryStore(MailRepository repository, boolean fail) {
            this.repository = repository;
            this.fail = fail;
        }

        @Override
        public MailRepository select(MailRepositoryUrl url) {
            return repository;
        }

        @Override
        public Optional<Protocol> defaultProtocol() {
            return Optional.of(BLOBV2_PROTOCOL);
        }

        @Override
        public Optional<MailRepository> get(MailRepositoryUrl url) {
            return Optional.of(repository);
        }

        @Override
        public Stream<MailRepository> getByPath(MailRepositoryPath path) {
            if (fail) {
                throw new RuntimeException("Store failure");
            }
            return Stream.of(repository);
        }

        @Override
        public Stream<MailRepositoryUrl> getUrls() {
            return Stream.of(URL);
        }
    }

    private BlobMailRepositoryV2BlobReferenceSource testee;
    private MailRepository repository;

    @BeforeEach
    void setup() throws Exception {
        PlainBlobId.Factory blobIdFactory = new PlainBlobId.Factory();
        MemoryBlobStoreDAO blobStore = new MemoryBlobStoreDAO();
        var mimeMessageBlobStore = MemoryBlobStoreFactory.builder()
                .blobIdFactory(blobIdFactory)
                .defaultBucketName()
                .passthrough();
        BlobMailRepositoryV2Factory factory = new BlobMailRepositoryV2Factory(blobStore, blobIdFactory, mimeMessageBlobStore.getDefaultBucketName());
        repository = factory.create(URL);

        testee = new BlobMailRepositoryV2BlobReferenceSource(new SimpleMailRepositoryStore(repository, false));
    }

    @Test
    void listReferencedBlobsShouldBeEmptyByDefault() {
        assertThat(testee.listReferencedBlobs().collectList().block())
            .isEmpty();
    }

    @Test
    void listReferencedBlobsShouldIncludeMetadataAndMimePartsBlobs() throws Exception {
        MailImpl mail = MailImpl.builder()
            .name("mail1")
            .sender("sender@localhost")
            .addRecipient("recipient@localhost")
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .setText("Test body"))
            .build();

        MailKey mailKey = repository.store(mail);

        List<BlobId> referencedBlobs = testee.listReferencedBlobs().collectList().block();

        // 1 metadata blob + 1 header blob + 1 body blob = 3 referenced blobs per mail
        assertThat(referencedBlobs).hasSize(3);
        assertThat(referencedBlobs)
            .extracting(BlobId::asString)
            .contains(mailKey.asString());
    }

    @Test
    void listReferencedBlobsShouldPropagateErrorsWhenStoreFails() {
        BlobMailRepositoryV2BlobReferenceSource faultyTestee =
            new BlobMailRepositoryV2BlobReferenceSource(new SimpleMailRepositoryStore(repository, true));

        assertThatThrownBy(() -> faultyTestee.listReferencedBlobs().collectList().block())
            .hasRootCauseInstanceOf(RuntimeException.class)
            .hasRootCauseMessage("Store failure");
    }
}
