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

import java.time.Instant;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.PlainBlobId;
import org.apache.james.blob.mail.MimeMessageStore;
import org.apache.james.blob.memory.MemoryBlobStoreDAO;
import org.apache.james.blob.memory.MemoryBlobStoreFactory;
import org.apache.james.mailrepository.MailRepositoryContract;
import org.apache.james.mailrepository.api.MailRepository;
import org.apache.james.mailrepository.api.MailRepositoryPath;
import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.apache.james.mailrepository.api.Protocol;
import org.apache.james.server.blob.deduplication.GenerationAwareBlobId;
import org.apache.james.utils.UpdatableTickingClock;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GenerationAwareBlobMailRepositoryV2Test implements MailRepositoryContract {

    private static final Instant NOW = Instant.parse("2021-08-19T10:15:30.00Z");

    private MailRepository blobMailRepositoryV2;
    private BlobId.Factory blobIdFactory;
    private MemoryBlobStoreDAO blobStore;
    private MimeMessageStore.Factory mimeMessageStoreFactory;
    private BlobMailRepositoryV2Factory blobMailRepositoryV2Factory;

    @BeforeEach
    void setup() {
        blobIdFactory = new GenerationAwareBlobId.Factory(
            new UpdatableTickingClock(NOW),
            new PlainBlobId.Factory(),
            GenerationAwareBlobId.Configuration.DEFAULT);
        blobStore = new MemoryBlobStoreDAO();
        var mimeMessageBlobStore = MemoryBlobStoreFactory.builder()
                .blobIdFactory(blobIdFactory)
                .defaultBucketName()
                .passthrough();
        mimeMessageStoreFactory = new MimeMessageStore.Factory(mimeMessageBlobStore);
        MailRepositoryPath path = MailRepositoryPath.from("var/mail/error");
        blobMailRepositoryV2Factory = new BlobMailRepositoryV2Factory(blobStore, blobIdFactory, mimeMessageBlobStore.getDefaultBucketName());
        blobMailRepositoryV2 = buildBlobMailRepositoryV2(path);
    }

    @NotNull
    private MailRepository buildBlobMailRepositoryV2(MailRepositoryPath path) {
        return blobMailRepositoryV2Factory.create(MailRepositoryUrl.fromPathAndProtocol(new Protocol("blobv2"), path));
    }

    @Override
    public MailRepository retrieveRepository() {
        return blobMailRepositoryV2;
    }

    @Override
    public MailRepository retrieveRepository(MailRepositoryPath path) {
        return buildBlobMailRepositoryV2(path);
    }

    @Test
    @Override
    public void removeAllShouldReportTheRemovedMails() throws Exception {
        MailRepositoryContract.super.removeAllShouldReportTheRemovedMails();
    }
}
