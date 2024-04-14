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

import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.deleteFrom;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.insertInto;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom;
import static org.apache.james.vault.metadata.DeletedMessageMetadataModule.StorageInformationTable.BLOB_ID;
import static org.apache.james.vault.metadata.DeletedMessageMetadataModule.StorageInformationTable.BUCKET_NAME;
import static org.apache.james.vault.metadata.DeletedMessageMetadataModule.StorageInformationTable.MESSAGE_ID;
import static org.apache.james.vault.metadata.DeletedMessageMetadataModule.StorageInformationTable.OWNER;
import static org.apache.james.vault.metadata.DeletedMessageMetadataModule.StorageInformationTable.TABLE;

import jakarta.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BucketName;
import org.apache.james.core.Username;
import org.apache.james.mailbox.model.MessageId;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;

import reactor.core.publisher.Mono;

public class StorageInformationDAO {
    private final CassandraAsyncExecutor cassandraAsyncExecutor;
    private final PreparedStatement addStatement;
    private final PreparedStatement removeStatement;
    private final PreparedStatement readStatement;
    private final BlobId.Factory blobIdFactory;

    @Inject
    StorageInformationDAO(CqlSession session, BlobId.Factory blobIdFactory) {
        this.cassandraAsyncExecutor = new CassandraAsyncExecutor(session);
        this.addStatement = prepareAdd(session);
        this.removeStatement = prepareRemove(session);
        this.readStatement = prepareRead(session);
        this.blobIdFactory = blobIdFactory;
    }

    private PreparedStatement prepareRead(CqlSession session) {
        return session.prepare(selectFrom(TABLE)
            .columns(BUCKET_NAME, BLOB_ID)
            .whereColumn(OWNER).isEqualTo(bindMarker(OWNER))
            .whereColumn(MESSAGE_ID).isEqualTo(bindMarker(MESSAGE_ID))
            .build());
    }

    private PreparedStatement prepareRemove(CqlSession session) {
        return session.prepare(deleteFrom(TABLE)
            .whereColumn(OWNER).isEqualTo(bindMarker(OWNER))
            .whereColumn(MESSAGE_ID).isEqualTo(bindMarker(MESSAGE_ID))
            .build());
    }

    private PreparedStatement prepareAdd(CqlSession session) {
        return session.prepare(insertInto(TABLE)
            .value(OWNER, bindMarker(OWNER))
            .value(MESSAGE_ID, bindMarker(MESSAGE_ID))
            .value(BUCKET_NAME, bindMarker(BUCKET_NAME))
            .value(BLOB_ID, bindMarker(BLOB_ID))
            .build());
    }

    Mono<Void> referenceStorageInformation(Username username, MessageId messageId, StorageInformation storageInformation) {
        return cassandraAsyncExecutor.executeVoid(addStatement.bind()
            .setString(OWNER, username.asString())
            .setString(MESSAGE_ID, messageId.serialize())
            .setString(BUCKET_NAME, storageInformation.getBucketName().asString())
            .setString(BLOB_ID, storageInformation.getBlobId().asString()));
    }

    Mono<Void> deleteStorageInformation(Username username, MessageId messageId) {
        return cassandraAsyncExecutor.executeVoid(removeStatement.bind()
            .setString(OWNER, username.asString())
            .setString(MESSAGE_ID, messageId.serialize()));
    }

    Mono<StorageInformation> retrieveStorageInformation(Username username, MessageId messageId) {
        return cassandraAsyncExecutor.executeSingleRow(readStatement.bind()
            .setString(OWNER, username.asString())
            .setString(MESSAGE_ID, messageId.serialize()))
            .map(row -> StorageInformation.builder()
                    .bucketName(BucketName.of(row.getString(BUCKET_NAME)))
                    .blobId(blobIdFactory.from(row.getString(BLOB_ID))));
    }
}
