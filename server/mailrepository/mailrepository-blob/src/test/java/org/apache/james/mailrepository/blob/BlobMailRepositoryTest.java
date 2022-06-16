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

import javax.mail.internet.MimeMessage;

import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.api.Store;
import org.apache.james.blob.mail.MimeMessagePartsId;
import org.apache.james.blob.mail.MimeMessageStore;
import org.apache.james.blob.memory.MemoryBlobStoreDAO;
import org.apache.james.blob.memory.MemoryBlobStoreFactory;
import org.apache.james.mailrepository.MailRepositoryContract;
import org.apache.james.mailrepository.api.MailRepository;
import org.apache.james.mailrepository.api.MailRepositoryPath;
import org.jetbrains.annotations.NotNull;
import org.apache.james.mailrepository.api.MailRepositoryPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;

class BlobMailRepositoryTest implements MailRepositoryContract {

    private BlobMailRepository blobMailRepository;
    private HashBlobId.Factory blobIdFactory;
    private MemoryBlobStoreDAO blobStore;
    private MimeMessageStore.Factory mimeMessageStoreFactory;

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

        blobMailRepository = buildBlobMailRepository(blobIdFactory, blobStore, path, mimeMessageStoreFactory);
    }

    @NotNull
    private BlobMailRepository buildBlobMailRepository(HashBlobId.Factory blobIdFactory,
                                                       MemoryBlobStoreDAO blobStore,
                                                       MailRepositoryPath path,
                                                       MimeMessageStore.Factory mimeMessageStoreFactory) {
        Store<MimeMessage, MimeMessagePartsId> mimeMessageStore =
                mimeMessageStoreFactory.mimeMessageStore(BucketName.of(path+"/mimeMessageData"));
        return new BlobMailRepository(
                blobStore,
                blobIdFactory,
                mimeMessageStore,
                BucketName.of(path + "/mailMetadata")
        );
    }

    @Override
    public MailRepository retrieveRepository() {
        return blobMailRepository;
    }

    @Override
    public MailRepository retrieveRepository(MailRepositoryPath path) {
        return buildBlobMailRepository(blobIdFactory, blobStore, path, mimeMessageStoreFactory);
    }
}