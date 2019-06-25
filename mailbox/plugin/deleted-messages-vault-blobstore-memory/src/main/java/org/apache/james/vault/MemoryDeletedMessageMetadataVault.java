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

package org.apache.james.vault;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.james.blob.api.BucketName;
import org.apache.james.core.User;
import org.apache.james.mailbox.model.MessageId;
import org.reactivestreams.Publisher;

public class MemoryDeletedMessageMetadataVault implements DeletedMessageMetadataVault {
    @Override
    public Publisher<Void> store(DeletedMessageWithStorageInformation deletedMessage) {
        throw new NotImplementedException("Not yet implemented");
    }

    @Override
    public Publisher<Void> removeBucket(BucketName bucketName) {
        throw new NotImplementedException("Not yet implemented");
    }

    @Override
    public Publisher<Void> remove(BucketName bucketName, User user, MessageId messageId) {
        throw new NotImplementedException("Not yet implemented");
    }

    @Override
    public Publisher<StorageInformation> retrieveStorageInformation(User user, MessageId messageId) {
        throw new NotImplementedException("Not yet implemented");
    }

    @Override
    public Publisher<DeletedMessageWithStorageInformation> listMessages(BucketName bucketName, User user) {
        throw new NotImplementedException("Not yet implemented");
    }

    @Override
    public Publisher<BucketName> listBuckets() {
        throw new NotImplementedException("Not yet implemented");
    }
}
