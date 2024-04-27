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

import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.mail.MimeMessageStore;
import org.apache.james.blob.memory.MemoryBlobStoreDAO;
import org.apache.james.blob.memory.MemoryBlobStoreFactory;
import org.apache.james.mailrepository.MailRepositoryContract;
import org.apache.james.mailrepository.api.MailRepository;
import org.apache.james.mailrepository.api.MailRepositoryPath;
import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.apache.james.mailrepository.api.Protocol;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;

class BlobMailRepositoryTest implements MailRepositoryContract {

    private MailRepository blobMailRepository;
    private HashBlobId.Factory blobIdFactory;
    private MemoryBlobStoreDAO blobStore;
    private MimeMessageStore.Factory mimeMessageStoreFactory;
    private BlobMailRepositoryFactory blobMailRepositoryFactory;

    @BeforeEach
    void setup() {
        blobIdFactory = new HashBlobId.Factory();
        blobStore = new MemoryBlobStoreDAO();
        var mimeMessageBlobStore  = MemoryBlobStoreFactory.builder()
                .blobIdFactory(blobIdFactory)
                .defaultBucketName()
                .passthrough();
        mimeMessageStoreFactory = new MimeMessageStore.Factory(mimeMessageBlobStore);
        MailRepositoryPath path = MailRepositoryPath.from("/foo");
        blobMailRepositoryFactory = new BlobMailRepositoryFactory(blobStore, blobIdFactory, mimeMessageStoreFactory);
        blobMailRepository = buildBlobMailRepository(path);
    }

    @NotNull
    private MailRepository buildBlobMailRepository(MailRepositoryPath path) {
        return blobMailRepositoryFactory.create(MailRepositoryUrl.fromPathAndProtocol(new Protocol("blob"), path));
    }

    @Override
    public MailRepository retrieveRepository() {
        return blobMailRepository;
    }

    @Override
    public MailRepository retrieveRepository(MailRepositoryPath path) {
        return buildBlobMailRepository(path);
    }
}