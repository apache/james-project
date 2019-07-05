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

import static com.datastax.driver.core.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.driver.core.querybuilder.QueryBuilder.delete;
import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static com.datastax.driver.core.querybuilder.QueryBuilder.insertInto;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;
import static org.apache.james.vault.metadata.DeletedMessageMetadataModule.DeletedMessageMetadataTable.BUCKET_NAME;
import static org.apache.james.vault.metadata.DeletedMessageMetadataModule.DeletedMessageMetadataTable.MESSAGE_ID;
import static org.apache.james.vault.metadata.DeletedMessageMetadataModule.DeletedMessageMetadataTable.OWNER;
import static org.apache.james.vault.metadata.DeletedMessageMetadataModule.DeletedMessageMetadataTable.PAYLOAD;
import static org.apache.james.vault.metadata.DeletedMessageMetadataModule.DeletedMessageMetadataTable.TABLE;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.blob.api.BucketName;
import org.apache.james.core.User;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.vault.dto.DeletedMessageWithStorageInformationConverter;
import org.apache.james.vault.dto.DeletedMessageWithStorageInformationDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Session;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.github.fge.lambdas.Throwing;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class MetadataDAO {
    private static final Logger LOGGER = LoggerFactory.getLogger(MetadataDAO.class);

    private final CassandraAsyncExecutor cassandraAsyncExecutor;
    private final PreparedStatement addStatement;
    private final PreparedStatement removeStatement;
    private final PreparedStatement removeAllStatement;
    private final PreparedStatement readStatement;
    private final PreparedStatement readMessageIdStatement;
    private final MessageId.Factory messageIdFactory;
    private final ObjectMapper objectMapper;
    private final DeletedMessageWithStorageInformationConverter dtoConverter;


    MetadataDAO(Session session, MessageId.Factory messageIdFactory, DeletedMessageWithStorageInformationConverter dtoConverter) {
        this.cassandraAsyncExecutor = new CassandraAsyncExecutor(session);
        this.addStatement = prepareAdd(session);
        this.removeStatement = prepareRemove(session);
        this.removeAllStatement = prepareRemoveAll(session);
        this.readStatement = prepareRead(session, PAYLOAD);
        this.readMessageIdStatement = prepareRead(session, MESSAGE_ID);
        this.messageIdFactory = messageIdFactory;
        this.dtoConverter = dtoConverter;
        this.objectMapper = new ObjectMapper()
            .registerModule(new Jdk8Module())
            .setSerializationInclusion(JsonInclude.Include.NON_ABSENT);
    }

    private PreparedStatement prepareRead(Session session, String fieldName) {
        return session.prepare(select(fieldName).from(TABLE)
            .where(eq(BUCKET_NAME, bindMarker(BUCKET_NAME)))
            .and(eq(OWNER, bindMarker(OWNER))));
    }

    private PreparedStatement prepareAdd(Session session) {
        return session.prepare(insertInto(TABLE)
            .value(BUCKET_NAME, bindMarker(BUCKET_NAME))
            .value(OWNER, bindMarker(OWNER))
            .value(MESSAGE_ID, bindMarker(MESSAGE_ID))
            .value(PAYLOAD, bindMarker(PAYLOAD)));
    }

    private PreparedStatement prepareRemove(Session session) {
        return session.prepare(delete().from(TABLE)
            .where(eq(OWNER, bindMarker(OWNER)))
            .and(eq(BUCKET_NAME, bindMarker(BUCKET_NAME)))
            .and(eq(MESSAGE_ID, bindMarker(MESSAGE_ID))));
    }

    private PreparedStatement prepareRemoveAll(Session session) {
        return session.prepare(delete().from(TABLE)
            .where(eq(OWNER, bindMarker(OWNER)))
            .and(eq(BUCKET_NAME, bindMarker(BUCKET_NAME))));
    }


    Mono<Void> store(DeletedMessageWithStorageInformation metadata) {
        return Mono.just(metadata)
            .map(DeletedMessageWithStorageInformationDTO::toDTO)
            .map(Throwing.function(objectMapper::writeValueAsString))
            .flatMap(payload -> cassandraAsyncExecutor.executeVoid(addStatement.bind()
                .setString(BUCKET_NAME, metadata.getStorageInformation().getBucketName().asString())
                .setString(OWNER, metadata.getDeletedMessage().getOwner().asString())
                .setString(MESSAGE_ID, metadata.getDeletedMessage().getMessageId().serialize())
                .setString(PAYLOAD, payload)));
    }

    Flux<DeletedMessageWithStorageInformation> retrieveMetadata(BucketName bucketName, User user) {
        return cassandraAsyncExecutor.executeRows(
            readStatement.bind()
                .setString(BUCKET_NAME, bucketName.asString())
                .setString(OWNER, user.asString()))
            .map(row -> row.getString(PAYLOAD))
            .flatMap(string -> Mono.fromCallable(() -> objectMapper.readValue(string, DeletedMessageWithStorageInformationDTO.class))
                .onErrorResume(e -> Mono.fromRunnable(() -> LOGGER.error("Error deserializing JSON metadata", e))))
            .flatMap(dto -> Mono.fromCallable(() -> dtoConverter.toDomainObject(dto))
                .onErrorResume(e -> Mono.fromRunnable(() -> LOGGER.error("Error deserializing DTO", e))));
    }

    Flux<MessageId> retrieveMessageIds(BucketName bucketName, User user) {
        return cassandraAsyncExecutor.executeRows(
            readMessageIdStatement.bind()
                .setString(BUCKET_NAME, bucketName.asString())
                .setString(OWNER, user.asString()))
            .map(row -> row.getString(MESSAGE_ID))
            .map(messageIdFactory::fromString);
    }

    Mono<Void> deleteMessage(BucketName bucketName, User user, MessageId messageId) {
        return cassandraAsyncExecutor.executeVoid(removeStatement.bind()
            .setString(BUCKET_NAME, bucketName.asString())
            .setString(OWNER, user.asString())
            .setString(MESSAGE_ID, messageId.serialize()));
    }

    Mono<Void> deleteInBucket(BucketName bucketName, User user) {
        return cassandraAsyncExecutor.executeVoid(removeAllStatement.bind()
            .setString(BUCKET_NAME, bucketName.asString())
            .setString(OWNER, user.asString()));
    }
}
