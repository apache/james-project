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

package org.apache.james.mailbox.cassandra.mail;

import static com.datastax.driver.core.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static com.datastax.driver.core.querybuilder.QueryBuilder.insertInto;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;
import static org.apache.james.blob.api.BlobStore.StoragePolicy.LOW_COST;
import static org.apache.james.blob.api.BlobStore.StoragePolicy.SIZE_BASED;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageIds.MESSAGE_ID;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV2Table.ATTACHMENTS;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV2Table.BODY_CONTENT;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV2Table.BODY_OCTECTS;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV2Table.BODY_START_OCTET;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV2Table.FULL_CONTENT_OCTETS;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV2Table.HEADER_CONTENT;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV2Table.INTERNAL_DATE;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV2Table.PROPERTIES;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV2Table.TABLE_NAME;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV2Table.TEXTUAL_LINE_COUNT;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.mail.util.SharedByteArrayInputStream;

import org.apache.commons.io.IOUtils;
import org.apache.james.backends.cassandra.init.CassandraTypesProvider;
import org.apache.james.backends.cassandra.init.configuration.CassandraConsistenciesConfiguration;
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.cassandra.table.CassandraMessageV2Table;
import org.apache.james.mailbox.cassandra.table.CassandraMessageV2Table.Attachments;
import org.apache.james.mailbox.cassandra.table.CassandraMessageV2Table.Properties;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.model.Cid;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.mailbox.model.MessageAttachmentMetadata;
import org.apache.james.mailbox.store.mail.MessageMapper.FetchType;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.Property;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.ConsistencyLevel;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.UDTValue;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.github.steveash.guavate.Guavate;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Bytes;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

public class CassandraMessageDAO {
    public static final long DEFAULT_LONG_VALUE = 0L;
    private static final byte[] EMPTY_BYTE_ARRAY = {};

    private final CassandraAsyncExecutor cassandraAsyncExecutor;
    private final CassandraTypesProvider typesProvider;
    private final BlobStore blobStore;
    private final BlobId.Factory blobIdFactory;
    private final PreparedStatement insert;
    private final PreparedStatement delete;
    private final PreparedStatement select;
    private final PreparedStatement selectAll;
    private final Cid.CidParser cidParser;
    private final CassandraMessageId.Factory messageIdFactory;
    private final ConsistencyLevel consistencyLevel;

    @Inject
    public CassandraMessageDAO(Session session,
                               CassandraTypesProvider typesProvider,
                               BlobStore blobStore,
                               BlobId.Factory blobIdFactory,
                               CassandraMessageId.Factory messageIdFactory,
                               CassandraConsistenciesConfiguration consistenciesConfiguration) {
        this.cassandraAsyncExecutor = new CassandraAsyncExecutor(session);
        this.messageIdFactory = messageIdFactory;
        this.consistencyLevel = consistenciesConfiguration.getRegular();
        this.typesProvider = typesProvider;
        this.blobStore = blobStore;
        this.blobIdFactory = blobIdFactory;

        this.insert = prepareInsert(session);
        this.delete = prepareDelete(session);
        this.select = prepareSelect(session);
        this.selectAll = prepareSelectAll(session);
        this.cidParser = Cid.parser().relaxed();
    }

    private PreparedStatement prepareSelect(Session session) {
        return session.prepare(select()
            .from(TABLE_NAME)
            .where(eq(MESSAGE_ID, bindMarker(MESSAGE_ID))));
    }

    private PreparedStatement prepareSelectAll(Session session) {
        return session.prepare(select()
            .from(TABLE_NAME));
    }

    private PreparedStatement prepareInsert(Session session) {
        return session.prepare(insertInto(TABLE_NAME)
            .value(MESSAGE_ID, bindMarker(MESSAGE_ID))
            .value(INTERNAL_DATE, bindMarker(INTERNAL_DATE))
            .value(BODY_START_OCTET, bindMarker(BODY_START_OCTET))
            .value(FULL_CONTENT_OCTETS, bindMarker(FULL_CONTENT_OCTETS))
            .value(BODY_OCTECTS, bindMarker(BODY_OCTECTS))
            .value(BODY_CONTENT, bindMarker(BODY_CONTENT))
            .value(HEADER_CONTENT, bindMarker(HEADER_CONTENT))
            .value(PROPERTIES, bindMarker(PROPERTIES))
            .value(TEXTUAL_LINE_COUNT, bindMarker(TEXTUAL_LINE_COUNT))
            .value(ATTACHMENTS, bindMarker(ATTACHMENTS)));
    }

    private PreparedStatement prepareDelete(Session session) {
        return session.prepare(QueryBuilder.delete()
            .from(TABLE_NAME)
            .where(eq(MESSAGE_ID, bindMarker(MESSAGE_ID))));
    }

    public Flux<MessageRepresentation> list() {
        return cassandraAsyncExecutor.executeRows(selectAll.bind())
            .map(this::message);
    }

    public Mono<Void> save(MailboxMessage message) throws MailboxException {
        return saveContent(message)
            .flatMap(pair -> cassandraAsyncExecutor.executeVoid(boundWriteStatement(message, pair)));
    }

    private Mono<Tuple2<BlobId, BlobId>> saveContent(MailboxMessage message) throws MailboxException {
        try {
            byte[] headerContent = IOUtils.toByteArray(message.getHeaderContent());
            byte[] bodyContent = IOUtils.toByteArray(message.getBodyContent());

            Mono<BlobId> bodyFuture = Mono.from(blobStore.save(blobStore.getDefaultBucketName(), bodyContent, LOW_COST));
            Mono<BlobId> headerFuture = Mono.from(blobStore.save(blobStore.getDefaultBucketName(), headerContent, SIZE_BASED));

            return headerFuture.zipWith(bodyFuture);
        } catch (IOException e) {
            throw new MailboxException("Error saving mail content", e);
        }
    }

    private BoundStatement boundWriteStatement(MailboxMessage message, Tuple2<BlobId, BlobId> pair) {
        CassandraMessageId messageId = (CassandraMessageId) message.getMessageId();
        return insert.bind()
            .setUUID(MESSAGE_ID, messageId.get())
            .setTimestamp(INTERNAL_DATE, message.getInternalDate())
            .setInt(BODY_START_OCTET, (int) (message.getHeaderOctets()))
            .setLong(FULL_CONTENT_OCTETS, message.getFullContentOctets())
            .setLong(BODY_OCTECTS, message.getBodyOctets())
            .setString(BODY_CONTENT, pair.getT2().asString())
            .setString(HEADER_CONTENT, pair.getT1().asString())
            .setLong(TEXTUAL_LINE_COUNT, Optional.ofNullable(message.getTextualLineCount()).orElse(DEFAULT_LONG_VALUE))
            .setList(PROPERTIES, buildPropertiesUdt(message))
            .setList(ATTACHMENTS, buildAttachmentUdt(message));
    }

    private ImmutableList<UDTValue> buildAttachmentUdt(MailboxMessage message) {
        return message.getAttachments().stream()
            .map(this::toUDT)
            .collect(Guavate.toImmutableList());
    }

    private UDTValue toUDT(MessageAttachmentMetadata messageAttachment) {
        UDTValue result = typesProvider.getDefinedUserType(ATTACHMENTS)
            .newValue()
            .setString(Attachments.ID, messageAttachment.getAttachmentId().getId())
            .setBool(Attachments.IS_INLINE, messageAttachment.isInline());
        messageAttachment.getName()
            .ifPresent(name -> result.setString(Attachments.NAME, name));
        messageAttachment.getCid()
            .ifPresent(cid -> result.setString(Attachments.CID, cid.getValue()));
        return result;
    }

    private List<UDTValue> buildPropertiesUdt(MailboxMessage message) {
        return message.getProperties()
            .toProperties()
            .stream()
            .map(property -> typesProvider.getDefinedUserType(PROPERTIES)
                .newValue()
                .setString(Properties.NAMESPACE, property.getNamespace())
                .setString(Properties.NAME, property.getLocalName())
                .setString(Properties.VALUE, property.getValue()))
            .collect(Guavate.toImmutableList());
    }

    public Mono<MessageRepresentation> retrieveMessage(ComposedMessageIdWithMetaData id, FetchType fetchType) {
        CassandraMessageId cassandraMessageId = (CassandraMessageId) id.getComposedMessageId().getMessageId();
        return retrieveMessage(cassandraMessageId, fetchType);
    }

    public Mono<MessageRepresentation> retrieveMessage(CassandraMessageId cassandraMessageId, FetchType fetchType) {
        return retrieveRow(cassandraMessageId)
                .flatMap(resultSet -> message(resultSet, cassandraMessageId, fetchType));
    }

    private Mono<ResultSet> retrieveRow(CassandraMessageId messageId) {
        return cassandraAsyncExecutor.execute(select
            .bind()
            .setUUID(MESSAGE_ID, messageId.get())
            .setConsistencyLevel(consistencyLevel));
    }

    private Mono<MessageRepresentation>
    message(ResultSet rows, CassandraMessageId cassandraMessageId, FetchType fetchType) {
        if (rows.isExhausted()) {
            return Mono.empty();
        }

        Row row = rows.one();
        BlobId headerId = retrieveBlobId(HEADER_CONTENT, row);
        BlobId bodyId = retrieveBlobId(BODY_CONTENT, row);
        int bodyStartOctet = row.getInt(BODY_START_OCTET);

        return buildContentRetriever(fetchType, headerId, bodyId, bodyStartOctet).map(content ->
            new MessageRepresentation(
                cassandraMessageId,
                row.getTimestamp(INTERNAL_DATE),
                row.getLong(FULL_CONTENT_OCTETS),
                row.getInt(BODY_START_OCTET),
                new SharedByteArrayInputStream(content),
                getProperties(row),
                getAttachments(row).collect(Guavate.toImmutableList()),
                headerId,
                bodyId));
    }

    private MessageRepresentation message(Row row) {
        BlobId headerId = retrieveBlobId(HEADER_CONTENT, row);
        BlobId bodyId = retrieveBlobId(BODY_CONTENT, row);
        CassandraMessageId messageId = messageIdFactory.of(row.getUUID(MESSAGE_ID));

        return new MessageRepresentation(
                messageId,
                row.getTimestamp(INTERNAL_DATE),
                row.getLong(FULL_CONTENT_OCTETS),
                row.getInt(BODY_START_OCTET),
                new SharedByteArrayInputStream(EMPTY_BYTE_ARRAY),
                getProperties(row),
                getAttachments(row).collect(Guavate.toImmutableList()),
                headerId,
                bodyId);
    }

    private org.apache.james.mailbox.store.mail.model.impl.Properties getProperties(Row row) {
        PropertyBuilder property = new PropertyBuilder(
            row.getList(PROPERTIES, UDTValue.class).stream()
                .map(this::toProperty)
                .collect(Collectors.toList()));
        property.setTextualLineCount(row.getLong(TEXTUAL_LINE_COUNT));
        return property.build();
    }

    private Property toProperty(UDTValue udtValue) {
        return new Property(udtValue.getString(Properties.NAMESPACE), udtValue.getString(Properties.NAME), udtValue.getString(Properties.VALUE));
    }

    private Stream<MessageAttachmentRepresentation> getAttachments(Row row) {
        List<UDTValue> udtValues = row.getList(ATTACHMENTS, UDTValue.class);
        return attachmentByIds(udtValues);
    }

    private Stream<MessageAttachmentRepresentation> attachmentByIds(List<UDTValue> udtValues) {
        return udtValues.stream()
            .map(this::messageAttachmentByIdFrom);
    }

    private MessageAttachmentRepresentation messageAttachmentByIdFrom(UDTValue udtValue) {
        return MessageAttachmentRepresentation.builder()
            .attachmentId(AttachmentId.from(udtValue.getString(Attachments.ID)))
            .name(udtValue.getString(Attachments.NAME))
            .cid(cidParser.parse(udtValue.getString(CassandraMessageV2Table.Attachments.CID)))
            .isInline(udtValue.getBool(Attachments.IS_INLINE))
            .build();
    }

    public Mono<Void> delete(CassandraMessageId messageId) {
        return cassandraAsyncExecutor.executeVoid(delete.bind()
            .setUUID(MESSAGE_ID, messageId.get()));
    }

    private Mono<byte[]> buildContentRetriever(FetchType fetchType, BlobId headerId, BlobId bodyId, int bodyStartOctet) {
        switch (fetchType) {
            case Full:
                return getFullContent(headerId, bodyId);
            case Headers:
                return getContent(headerId);
            case Body:
                return getContent(bodyId)
                    .map(data -> Bytes.concat(new byte[bodyStartOctet], data));
            case Metadata:
                return Mono.just(EMPTY_BYTE_ARRAY);
            default:
                throw new RuntimeException("Unknown FetchType " + fetchType);
        }
    }

    private Mono<byte[]> getFullContent(BlobId headerId, BlobId bodyId) {
        return getContent(headerId)
            .zipWith(getContent(bodyId), Bytes::concat);
    }

    private Mono<byte[]> getContent(BlobId blobId) {
        return Mono.from(blobStore.readBytes(blobStore.getDefaultBucketName(), blobId));
    }

    private BlobId retrieveBlobId(String field, Row row) {
        return blobIdFactory.from(row.getString(field));
    }
}
