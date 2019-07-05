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

import org.apache.james.blob.api.BucketName;
import org.apache.james.core.User;
import org.apache.james.mailbox.model.MessageId;
import org.reactivestreams.Publisher;

public class CassandraDeletedMessageMetadataVault implements DeletedMessageMetadataVault {
    private final MetadataDAO metadataDAO;
    private final StorageInformationDAO storageInformationDAO;
    private final UserPerBucketDAO userPerBucketDAO;

    CassandraDeletedMessageMetadataVault(MetadataDAO metadataDAO, StorageInformationDAO storageInformationDAO, UserPerBucketDAO userPerBucketDAO) {
        this.metadataDAO = metadataDAO;
        this.storageInformationDAO = storageInformationDAO;
        this.userPerBucketDAO = userPerBucketDAO;
    }

    @Override
    public Publisher<Void> store(DeletedMessageWithStorageInformation deletedMessage) {
        BucketName bucketName = deletedMessage.getStorageInformation().getBucketName();
        User owner = deletedMessage.getDeletedMessage().getOwner();
        MessageId messageId = deletedMessage.getDeletedMessage().getMessageId();
        return storageInformationDAO.referenceStorageInformation(owner, messageId, deletedMessage.getStorageInformation())
            .then(metadataDAO.store(deletedMessage))
            .then(userPerBucketDAO.addUser(bucketName, owner));
    }

    @Override
    public Publisher<Void> removeMetadataRelatedToBucket(BucketName bucketName) {
        return userPerBucketDAO.retrieveUsers(bucketName)
            .concatMap(user -> metadataDAO.retrieveMessageIds(bucketName, user)
                .map(messageId -> new DeletedMessageIdentifier(user, messageId))
                .concatMap(deletedMessageIdentifier -> storageInformationDAO.deleteStorageInformation(
                    deletedMessageIdentifier.getOwner(),
                    deletedMessageIdentifier.getMessageId()))
                .then(metadataDAO.deleteInBucket(bucketName, user)))
            .then(userPerBucketDAO.deleteBucket(bucketName));
    }

    @Override
    public Publisher<Void> remove(BucketName bucketName, User user, MessageId messageId) {
        return storageInformationDAO.deleteStorageInformation(user, messageId)
            .then(metadataDAO.deleteMessage(bucketName, user, messageId));
    }

    @Override
    public Publisher<StorageInformation> retrieveStorageInformation(User user, MessageId messageId) {
        return storageInformationDAO.retrieveStorageInformation(user, messageId);
    }

    @Override
    public Publisher<DeletedMessageWithStorageInformation> listMessages(BucketName bucketName, User user) {
        return metadataDAO.retrieveMetadata(bucketName, user);
    }

    @Override
    public Publisher<BucketName> listRelatedBuckets() {
        return userPerBucketDAO.retrieveBuckets();
    }
}
