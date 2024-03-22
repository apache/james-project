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
import static org.apache.james.util.ReactorUtils.publishIfPresent;
import static org.apache.james.vault.metadata.DeletedMessageMetadataModule.DeletedMessageMetadataTable.BUCKET_NAME;
import static org.apache.james.vault.metadata.DeletedMessageMetadataModule.DeletedMessageMetadataTable.MESSAGE_ID;
import static org.apache.james.vault.metadata.DeletedMessageMetadataModule.DeletedMessageMetadataTable.OWNER;
import static org.apache.james.vault.metadata.DeletedMessageMetadataModule.DeletedMessageMetadataTable.PAYLOAD;
import static org.apache.james.vault.metadata.DeletedMessageMetadataModule.DeletedMessageMetadataTable.TABLE;

import jakarta.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.blob.api.BucketName;
import org.apache.james.core.Username;
import org.apache.james.mailbox.model.MessageId;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class MetadataDAO {
    private final CassandraAsyncExecutor cassandraAsyncExecutor;
    private final PreparedStatement addStatement;
    private final PreparedStatement removeStatement;
    private final PreparedStatement removeAllStatement;
    private final PreparedStatement readStatement;
    private final PreparedStatement readMessageIdStatement;
    private final MessageId.Factory messageIdFactory;
    private final MetadataSerializer metadataSerializer;

    @Inject
    MetadataDAO(CqlSession session, MessageId.Factory messageIdFactory, MetadataSerializer metadataSerializer) {
        this.cassandraAsyncExecutor = new CassandraAsyncExecutor(session);
        this.addStatement = prepareAdd(session);
        this.removeStatement = prepareRemove(session);
        this.removeAllStatement = prepareRemoveAll(session);
        this.readStatement = prepareRead(session, PAYLOAD);
        this.readMessageIdStatement = prepareRead(session, MESSAGE_ID);
        this.messageIdFactory = messageIdFactory;
        this.metadataSerializer = metadataSerializer;
    }

    private PreparedStatement prepareRead(CqlSession session, String fieldName) {
        return session.prepare(selectFrom(TABLE)
            .columns(fieldName)
            .whereColumn(BUCKET_NAME).isEqualTo(bindMarker(BUCKET_NAME))
            .whereColumn(OWNER).isEqualTo(bindMarker(OWNER))
            .build());
    }

    private PreparedStatement prepareAdd(CqlSession session) {
        return session.prepare(insertInto(TABLE)
            .value(BUCKET_NAME, bindMarker(BUCKET_NAME))
            .value(OWNER, bindMarker(OWNER))
            .value(MESSAGE_ID, bindMarker(MESSAGE_ID))
            .value(PAYLOAD, bindMarker(PAYLOAD))
            .build());
    }

    private PreparedStatement prepareRemove(CqlSession session) {
        return session.prepare(deleteFrom(TABLE)
            .whereColumn(BUCKET_NAME).isEqualTo(bindMarker(BUCKET_NAME))
            .whereColumn(OWNER).isEqualTo(bindMarker(OWNER))
            .whereColumn(MESSAGE_ID).isEqualTo(bindMarker(MESSAGE_ID))
            .build());
    }

    private PreparedStatement prepareRemoveAll(CqlSession session) {
        return session.prepare(deleteFrom(TABLE)
            .whereColumn(BUCKET_NAME).isEqualTo(bindMarker(BUCKET_NAME))
            .whereColumn(OWNER).isEqualTo(bindMarker(OWNER))
            .build());
    }


    Mono<Void> store(DeletedMessageWithStorageInformation metadata) {
        return Mono.just(metadata)
            .map(metadataSerializer::serialize)
            .flatMap(payload -> cassandraAsyncExecutor.executeVoid(addStatement.bind()
                .setString(BUCKET_NAME, metadata.getStorageInformation().getBucketName().asString())
                .setString(OWNER, metadata.getDeletedMessage().getOwner().asString())
                .setString(MESSAGE_ID, metadata.getDeletedMessage().getMessageId().serialize())
                .setString(PAYLOAD, payload)));
    }

    Flux<DeletedMessageWithStorageInformation> retrieveMetadata(BucketName bucketName, Username username) {
        return cassandraAsyncExecutor.executeRows(
            readStatement.bind()
                .setString(BUCKET_NAME, bucketName.asString())
                .setString(OWNER, username.asString()))
            .map(row -> row.getString(PAYLOAD))
            .map(metadataSerializer::deserialize)
            .handle(publishIfPresent());
    }

    Flux<MessageId> retrieveMessageIds(BucketName bucketName, Username username) {
        return cassandraAsyncExecutor.executeRows(
            readMessageIdStatement.bind()
                .setString(BUCKET_NAME, bucketName.asString())
                .setString(OWNER, username.asString()))
            .map(row -> row.getString(MESSAGE_ID))
            .map(messageIdFactory::fromString);
    }

    Mono<Void> deleteMessage(BucketName bucketName, Username username, MessageId messageId) {
        return cassandraAsyncExecutor.executeVoid(removeStatement.bind()
            .setString(BUCKET_NAME, bucketName.asString())
            .setString(OWNER, username.asString())
            .setString(MESSAGE_ID, messageId.serialize()));
    }

    Mono<Void> deleteInBucket(BucketName bucketName, Username username) {
        return cassandraAsyncExecutor.executeVoid(removeAllStatement.bind()
            .setString(BUCKET_NAME, bucketName.asString())
            .setString(OWNER, username.asString()));
    }
}
