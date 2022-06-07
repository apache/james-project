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

import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.deleteFrom;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.insertInto;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom;
import static com.datastax.oss.driver.api.querybuilder.relation.Relation.column;
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
import java.io.InputStream;
import java.sql.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.commons.io.IOUtils;
import org.apache.james.backends.cassandra.init.CassandraTypesProvider;
import org.apache.james.backends.cassandra.init.configuration.JamesExecutionProfiles;
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.cassandra.table.CassandraMessageV2Table;
import org.apache.james.mailbox.cassandra.table.CassandraMessageV2Table.Attachments;
import org.apache.james.mailbox.cassandra.table.CassandraMessageV2Table.Properties;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.model.ByteContent;
import org.apache.james.mailbox.model.Cid;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.mailbox.model.MessageAttachmentMetadata;
import org.apache.james.mailbox.store.mail.MessageMapper.FetchType;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.Property;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.config.DriverExecutionProfile;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.data.UdtValue;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteSource;
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
    private final DriverExecutionProfile lwtProfile;

    @Inject
    public CassandraMessageDAO(CqlSession session,
                               CassandraTypesProvider typesProvider,
                               BlobStore blobStore,
                               BlobId.Factory blobIdFactory) {
        this.cassandraAsyncExecutor = new CassandraAsyncExecutor(session);
        this.lwtProfile = JamesExecutionProfiles.getLWTProfile(session);
        this.typesProvider = typesProvider;
        this.blobStore = blobStore;
        this.blobIdFactory = blobIdFactory;
        this.insert = prepareInsert(session);
        this.delete = prepareDelete(session);
        this.select = prepareSelect(session);
        this.selectAll = prepareSelectAll(session);
        this.cidParser = Cid.parser().relaxed();
    }

    private PreparedStatement prepareSelect(CqlSession session) {
        return session.prepare(selectFrom(TABLE_NAME)
            .all()
            .where(column(MESSAGE_ID).isEqualTo(bindMarker(MESSAGE_ID)))
            .build());
    }

    private PreparedStatement prepareSelectAll(CqlSession session) {
        return session.prepare(selectFrom(TABLE_NAME)
            .all().build());
    }

    private PreparedStatement prepareInsert(CqlSession session) {
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
            .value(ATTACHMENTS, bindMarker(ATTACHMENTS))
            .build());
    }

    private PreparedStatement prepareDelete(CqlSession session) {
        return session.prepare(deleteFrom(TABLE_NAME)
            .where(column(MESSAGE_ID).isEqualTo(bindMarker(MESSAGE_ID)))
            .build());
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
            ByteSource bodyByteSource = new ByteSource() {
                @Override
                public InputStream openStream() {
                    try {
                        return message.getBodyContent();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }

                @Override
                public long size() {
                    return message.getBodyOctets();
                }
            };

            Mono<BlobId> headerFuture = Mono.from(blobStore.save(blobStore.getDefaultBucketName(), headerContent, SIZE_BASED));
            Mono<BlobId> bodyFuture = Mono.from(blobStore.save(blobStore.getDefaultBucketName(), bodyByteSource, LOW_COST));

            return headerFuture.zipWith(bodyFuture);
        } catch (IOException e) {
            throw new MailboxException("Error saving mail content", e);
        }
    }

    private BoundStatement boundWriteStatement(MailboxMessage message, Tuple2<BlobId, BlobId> pair) {
        CassandraMessageId messageId = (CassandraMessageId) message.getMessageId();
        return insert.bind()
            .setUuid(MESSAGE_ID, messageId.get())
            .setInstant(INTERNAL_DATE, message.getInternalDate().toInstant())
            .setInt(BODY_START_OCTET, (int) (message.getHeaderOctets()))
            .setLong(FULL_CONTENT_OCTETS, message.getFullContentOctets())
            .setLong(BODY_OCTECTS, message.getBodyOctets())
            .setString(BODY_CONTENT, pair.getT2().asString())
            .setString(HEADER_CONTENT, pair.getT1().asString())
            .setLong(TEXTUAL_LINE_COUNT, Optional.ofNullable(message.getTextualLineCount()).orElse(DEFAULT_LONG_VALUE))
            .setList(PROPERTIES, buildPropertiesUdt(message), UdtValue.class)
            .setList(ATTACHMENTS, buildAttachmentUdt(message), UdtValue.class);
    }

    private ImmutableList<UdtValue> buildAttachmentUdt(MailboxMessage message) {
        return message.getAttachments().stream()
            .map(this::toUDT)
            .collect(ImmutableList.toImmutableList());
    }

    private UdtValue toUDT(MessageAttachmentMetadata messageAttachment) {
        UdtValue result = typesProvider.getDefinedUserType(ATTACHMENTS)
            .newValue()
            .setString(Attachments.ID, messageAttachment.getAttachmentId().getId())
            .setBoolean(Attachments.IS_INLINE, messageAttachment.isInline());

        Optional<UdtValue> maybeSetAttachmentsName = messageAttachment.getName()
            .map(name -> result.setString(Attachments.NAME, name));

        Optional<UdtValue> maybeSetAttachmentsCId = maybeSetAttachmentsName
            .map(udtValue -> messageAttachment.getCid()
                .map(cid -> udtValue.setString(Attachments.CID, cid.getValue()))
                .orElse(udtValue));

        return maybeSetAttachmentsCId.orElse(result);
    }

    private List<UdtValue> buildPropertiesUdt(MailboxMessage message) {
        return message.getProperties()
            .toProperties()
            .stream()
            .map(property -> typesProvider.getDefinedUserType(PROPERTIES)
                .newValue()
                .setString(Properties.NAMESPACE, property.getNamespace())
                .setString(Properties.NAME, property.getLocalName())
                .setString(Properties.VALUE, property.getValue()))
            .collect(ImmutableList.toImmutableList());
    }

    public Mono<MessageRepresentation> retrieveMessage(ComposedMessageIdWithMetaData id, FetchType fetchType) {
        CassandraMessageId cassandraMessageId = (CassandraMessageId) id.getComposedMessageId().getMessageId();
        return retrieveMessage(cassandraMessageId, fetchType);
    }

    public Mono<MessageRepresentation> retrieveMessage(CassandraMessageId cassandraMessageId, FetchType fetchType) {
        return retrieveRow(cassandraMessageId)
            .flatMap(resultSet -> message(resultSet, cassandraMessageId, fetchType));
    }

    private Mono<Row> retrieveRow(CassandraMessageId messageId) {
        return cassandraAsyncExecutor.executeSingleRow(select
            .bind()
            .setUuid(MESSAGE_ID, messageId.get())
            .setExecutionProfile(lwtProfile));
    }

    private Mono<MessageRepresentation> message(Row row, CassandraMessageId cassandraMessageId, FetchType fetchType) {
        BlobId headerId = retrieveBlobId(HEADER_CONTENT, row);
        BlobId bodyId = retrieveBlobId(BODY_CONTENT, row);
        int bodyStartOctet = row.getInt(BODY_START_OCTET);

        return buildContentRetriever(fetchType, headerId, bodyId, bodyStartOctet).map(content ->
            new MessageRepresentation(
                cassandraMessageId,
                Date.from(row.getInstant(INTERNAL_DATE)),
                row.getLong(FULL_CONTENT_OCTETS),
                row.getInt(BODY_START_OCTET),
                new ByteContent(content),
                getProperties(row),
                getAttachments(row).collect(ImmutableList.toImmutableList()),
                headerId,
                bodyId));
    }

    private MessageRepresentation message(Row row) {
        BlobId headerId = retrieveBlobId(HEADER_CONTENT, row);
        BlobId bodyId = retrieveBlobId(BODY_CONTENT, row);
        CassandraMessageId messageId = CassandraMessageId.Factory.of(row.getUuid(MESSAGE_ID));

        return new MessageRepresentation(
            messageId,
            Date.from(row.getInstant(INTERNAL_DATE)),
            row.getLong(FULL_CONTENT_OCTETS),
            row.getInt(BODY_START_OCTET),
            new ByteContent(EMPTY_BYTE_ARRAY),
            getProperties(row),
            getAttachments(row).collect(ImmutableList.toImmutableList()),
            headerId,
            bodyId);
    }

    private org.apache.james.mailbox.store.mail.model.impl.Properties getProperties(Row row) {
        PropertyBuilder property = new PropertyBuilder(
            row.getList(PROPERTIES, UdtValue.class).stream()
                .map(this::toProperty)
                .collect(Collectors.toList()));
        property.setTextualLineCount(row.getLong(TEXTUAL_LINE_COUNT));
        return property.build();
    }

    private Property toProperty(UdtValue udtValue) {
        return new Property(udtValue.getString(Properties.NAMESPACE), udtValue.getString(Properties.NAME), udtValue.getString(Properties.VALUE));
    }

    private Stream<MessageAttachmentRepresentation> getAttachments(Row row) {
        List<UdtValue> udtValues = row.getList(ATTACHMENTS, UdtValue.class);
        return attachmentByIds(udtValues);
    }

    private Stream<MessageAttachmentRepresentation> attachmentByIds(List<UdtValue> udtValues) {
        return udtValues.stream()
            .map(this::messageAttachmentByIdFrom);
    }

    private MessageAttachmentRepresentation messageAttachmentByIdFrom(UdtValue udtValue) {
        return MessageAttachmentRepresentation.builder()
            .attachmentId(AttachmentId.from(udtValue.getString(Attachments.ID)))
            .name(udtValue.getString(Attachments.NAME))
            .cid(cidParser.parse(udtValue.getString(CassandraMessageV2Table.Attachments.CID)))
            .isInline(udtValue.getBoolean(Attachments.IS_INLINE))
            .build();
    }

    public Mono<Void> delete(CassandraMessageId messageId) {
        return cassandraAsyncExecutor.executeVoid(delete.bind()
            .setUuid(MESSAGE_ID, messageId.get()));
    }

    private Mono<byte[]> buildContentRetriever(FetchType fetchType, BlobId headerId, BlobId bodyId, int bodyStartOctet) {
        switch (fetchType) {
            case FULL:
                return getFullContent(headerId, bodyId);
            case HEADERS:
                return getContent(headerId, SIZE_BASED);
            case BODY:
                return getContent(bodyId, LOW_COST)
                    .map(data -> Bytes.concat(new byte[bodyStartOctet], data));
            case METADATA:
                return Mono.just(EMPTY_BYTE_ARRAY);
            default:
                throw new RuntimeException("Unknown FetchType " + fetchType);
        }
    }

    private Mono<byte[]> getFullContent(BlobId headerId, BlobId bodyId) {
        return getContent(headerId, SIZE_BASED)
            .zipWith(getContent(bodyId, LOW_COST), Bytes::concat);
    }

    private Mono<byte[]> getContent(BlobId blobId, BlobStore.StoragePolicy storagePolicy) {
        return Mono.from(blobStore.readBytes(blobStore.getDefaultBucketName(), blobId, storagePolicy));
    }

    private BlobId retrieveBlobId(String field, Row row) {
        return blobIdFactory.from(row.getString(field));
    }
}
