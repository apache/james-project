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

package org.apache.james.vault.metadata;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.vault.DeletedMessageFixture;

public interface DeletedMessageVaultMetadataFixture {
    BlobId BLOB_ID = new HashBlobId.Factory().from("05dcb33b-8382-4744-923a-bc593ad84d23");
    BlobId BLOB_ID_2 = new HashBlobId.Factory().from("05dcb33b-8382-4744-923a-bc593ad84d24");
    BucketName BUCKET_NAME = BucketName.of("bucket-2019-06-01");
    BucketName OTHER_BUCKET_NAME = BucketName.of("other");

    StorageInformation STORAGE_INFORMATION = StorageInformation.builder()
        .bucketName(BUCKET_NAME)
        .blobId(BLOB_ID);
    StorageInformation OTHER_STORAGE_INFORMATION = StorageInformation.builder()
        .bucketName(OTHER_BUCKET_NAME)
        .blobId(BLOB_ID_2);

    DeletedMessageWithStorageInformation DELETED_MESSAGE_2_OTHER_BUCKET = new DeletedMessageWithStorageInformation(DeletedMessageFixture.DELETED_MESSAGE_2,
        OTHER_STORAGE_INFORMATION);
    DeletedMessageWithStorageInformation DELETED_MESSAGE = new DeletedMessageWithStorageInformation(DeletedMessageFixture.DELETED_MESSAGE, STORAGE_INFORMATION);
    DeletedMessageWithStorageInformation DELETED_MESSAGE_2 = new DeletedMessageWithStorageInformation(DeletedMessageFixture.DELETED_MESSAGE_2, STORAGE_INFORMATION);
}
