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

import static com.datastax.oss.driver.api.core.type.DataTypes.TEXT;
import static com.datastax.oss.driver.api.core.type.DataTypes.frozenListOf;
import static com.datastax.oss.driver.api.core.type.DataTypes.frozenMapOf;
import static com.datastax.oss.driver.api.core.type.DataTypes.listOf;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.deleteFrom;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.update;
import static com.datastax.oss.driver.api.querybuilder.relation.Relation.column;
import static com.datastax.oss.driver.api.querybuilder.update.Assignment.prepend;
import static com.datastax.oss.driver.api.querybuilder.update.Assignment.setColumn;
import static org.apache.james.blob.api.BlobStore.StoragePolicy.LOW_COST;
import static org.apache.james.blob.api.BlobStore.StoragePolicy.SIZE_BASED;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageIds.MESSAGE_ID;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV3Table.ATTACHMENTS;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV3Table.BODY_CONTENT;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV3Table.BODY_CONTENT_LOWERCASE;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV3Table.BODY_OCTECTS;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV3Table.BODY_START_OCTET;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV3Table.BODY_START_OCTET_LOWERCASE;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV3Table.FULL_CONTENT_OCTETS;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV3Table.FULL_CONTENT_OCTETS_LOWERCASE;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV3Table.HEADER_CONTENT;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV3Table.HEADER_CONTENT_LOWERCASE;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV3Table.INTERNAL_DATE;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV3Table.INTERNAL_DATE_LOWERCASE;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV3Table.Properties.CONTENT_DESCRIPTION;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV3Table.Properties.CONTENT_DISPOSITION_PARAMETERS;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV3Table.Properties.CONTENT_DISPOSITION_TYPE;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV3Table.Properties.CONTENT_ID;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV3Table.Properties.CONTENT_LANGUAGE;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV3Table.Properties.CONTENT_LOCATION;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV3Table.Properties.CONTENT_MD5;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV3Table.Properties.CONTENT_TRANSFER_ENCODING;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV3Table.Properties.CONTENT_TYPE_PARAMETERS;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV3Table.Properties.MEDIA_TYPE;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV3Table.Properties.SUB_TYPE;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV3Table.TABLE_NAME;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV3Table.TEXTUAL_LINE_COUNT;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV3Table.TEXTUAL_LINE_COUNT_LOWERCASE;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.commons.io.IOUtils;
import org.apache.james.backends.cassandra.init.CassandraTypesProvider;
import org.apache.james.backends.cassandra.init.configuration.JamesExecutionProfiles;
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.cassandra.table.CassandraMessageV3Table.Attachments;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.model.ByteContent;
import org.apache.james.mailbox.model.Cid;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.mailbox.model.MessageAttachmentMetadata;
import org.apache.james.mailbox.store.mail.MessageMapper.FetchType;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.impl.Properties;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.config.DriverExecutionProfile;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.BoundStatementBuilder;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.data.UdtValue;
import com.datastax.oss.driver.api.core.type.UserDefinedType;
import com.datastax.oss.driver.api.core.type.codec.TypeCodec;
import com.datastax.oss.driver.api.core.type.codec.registry.CodecRegistry;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteSource;
import com.google.common.primitives.Bytes;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

public class CassandraMessageDAOV3 {
    public static final long DEFAULT_LONG_VALUE = 0L;
    private static final byte[] EMPTY_BYTE_ARRAY = {};
    private static final TypeCodec<Map<String, String>> MAP_OF_STRINGS_CODEC = CodecRegistry.DEFAULT.codecFor(frozenMapOf(TEXT, TEXT));
    private static final TypeCodec<List<String>> LIST_OF_STRINGS_CODEC = CodecRegistry.DEFAULT.codecFor(frozenListOf(TEXT));

    private final CassandraAsyncExecutor cassandraAsyncExecutor;
    private final BlobStore blobStore;
    private final BlobId.Factory blobIdFactory;
    private final PreparedStatement insert;
    private final PreparedStatement delete;
    private final PreparedStatement select;
    private final PreparedStatement listBlobs;
    private final Cid.CidParser cidParser;
    private final DriverExecutionProfile lwtProfile;
    private final UserDefinedType attachmentsType;

    @Inject
    public CassandraMessageDAOV3(CqlSession session, CassandraTypesProvider typesProvider, BlobStore blobStore,
                                 BlobId.Factory blobIdFactory) {
        this.cassandraAsyncExecutor = new CassandraAsyncExecutor(session);
        this.lwtProfile = JamesExecutionProfiles.getLWTProfile(session);
        this.blobStore = blobStore;
        this.blobIdFactory = blobIdFactory;

        this.insert = prepareInsert(session);
        this.delete = prepareDelete(session);
        this.select = prepareSelect(session);
        this.listBlobs = prepareSelectBlobs(session);
        this.cidParser = Cid.parser().relaxed();
        this.attachmentsType = typesProvider.getDefinedUserType(ATTACHMENTS);
    }

    private PreparedStatement prepareSelect(CqlSession session) {
        return session.prepare(selectFrom(TABLE_NAME)
            .all()
            .where(column(MESSAGE_ID).isEqualTo(bindMarker(MESSAGE_ID)))
            .build());
    }

    private PreparedStatement prepareSelectBlobs(CqlSession session) {
        return session.prepare(selectFrom(TABLE_NAME)
            .columns(HEADER_CONTENT, BODY_CONTENT)
            .build());
    }

    private PreparedStatement prepareInsert(CqlSession session) {
        return session.prepare(update(TABLE_NAME)
            .set(setColumn(INTERNAL_DATE, bindMarker(INTERNAL_DATE)),
                setColumn(BODY_START_OCTET, bindMarker(BODY_START_OCTET)),
                setColumn(FULL_CONTENT_OCTETS, bindMarker(FULL_CONTENT_OCTETS)),
                setColumn(BODY_OCTECTS, bindMarker(BODY_OCTECTS)),
                setColumn(BODY_CONTENT, bindMarker(BODY_CONTENT)),
                setColumn(HEADER_CONTENT, bindMarker(HEADER_CONTENT)),
                setColumn(CONTENT_DESCRIPTION, bindMarker(CONTENT_DESCRIPTION)),
                setColumn(CONTENT_DISPOSITION_TYPE, bindMarker(CONTENT_DISPOSITION_TYPE)),
                setColumn(MEDIA_TYPE, bindMarker(MEDIA_TYPE)),
                setColumn(SUB_TYPE, bindMarker(SUB_TYPE)),
                setColumn(CONTENT_ID, bindMarker(CONTENT_ID)),
                setColumn(CONTENT_MD5, bindMarker(CONTENT_MD5)),
                setColumn(CONTENT_TRANSFER_ENCODING, bindMarker(CONTENT_TRANSFER_ENCODING)),
                setColumn(CONTENT_LOCATION, bindMarker(CONTENT_LOCATION)),
                setColumn(CONTENT_LANGUAGE, bindMarker(CONTENT_LANGUAGE)),
                setColumn(CONTENT_DISPOSITION_PARAMETERS, bindMarker(CONTENT_DISPOSITION_PARAMETERS)),
                setColumn(CONTENT_TYPE_PARAMETERS, bindMarker(CONTENT_TYPE_PARAMETERS)),
                setColumn(TEXTUAL_LINE_COUNT, bindMarker(TEXTUAL_LINE_COUNT)),
                prepend(ATTACHMENTS, bindMarker(ATTACHMENTS)))
            .where(column(MESSAGE_ID).isEqualTo(bindMarker(MESSAGE_ID)))
            .build());
    }

    private PreparedStatement prepareDelete(CqlSession session) {
        return session.prepare(deleteFrom(TABLE_NAME)
            .where(column(MESSAGE_ID).isEqualTo(bindMarker(MESSAGE_ID)))
            .build());
    }

    public Mono<Tuple2<BlobId, BlobId>> save(MailboxMessage message) throws MailboxException {
        return saveContent(message)
            .flatMap(pair -> cassandraAsyncExecutor.executeVoid(boundWriteStatement(message, pair)).thenReturn(pair));
    }

    public Mono<Void> save(MessageRepresentation message) {
        CassandraMessageId messageId = (CassandraMessageId) message.getMessageId();
        BoundStatementBuilder boundStatement = insert.boundStatementBuilder()
            .setUuid(MESSAGE_ID, messageId.get())
            .setInstant(INTERNAL_DATE, message.getInternalDate().toInstant())
            .setInt(BODY_START_OCTET, message.getBodyStartOctet())
            .setLong(FULL_CONTENT_OCTETS, message.getSize())
            .setLong(BODY_OCTECTS, message.getSize() - message.getBodyStartOctet())
            .setString(BODY_CONTENT, message.getBodyId().asString())
            .setString(HEADER_CONTENT, message.getHeaderId().asString())
            .setLong(TEXTUAL_LINE_COUNT, Optional.ofNullable(message.getProperties().getTextualLineCount()).orElse(DEFAULT_LONG_VALUE))
            .setString(CONTENT_DESCRIPTION, message.getProperties().getContentDescription())
            .setString(CONTENT_DISPOSITION_TYPE, message.getProperties().getContentDispositionType())
            .setString(MEDIA_TYPE, message.getProperties().getMediaType())
            .setString(SUB_TYPE, message.getProperties().getSubType())
            .setString(CONTENT_ID, message.getProperties().getContentID())
            .setString(CONTENT_MD5, message.getProperties().getContentMD5())
            .setString(CONTENT_TRANSFER_ENCODING, message.getProperties().getContentTransferEncoding())
            .setString(CONTENT_LOCATION, message.getProperties().getContentLocation())
            .setList(CONTENT_LANGUAGE, message.getProperties().getContentLanguage(), String.class)
            .setMap(CONTENT_DISPOSITION_PARAMETERS, message.getProperties().getContentDispositionParameters(), String.class, String.class)
            .setMap(CONTENT_TYPE_PARAMETERS, message.getProperties().getContentTypeParameters(), String.class, String.class);

        if (message.getAttachments().isEmpty()) {
            return cassandraAsyncExecutor.executeVoid(boundStatement.unset(ATTACHMENTS).build());
        } else {
            return cassandraAsyncExecutor.executeVoid(boundStatement.setList(ATTACHMENTS, buildAttachmentUdt(message.getAttachments()), UdtValue.class).build());
        }

    }

    private Mono<Tuple2<BlobId, BlobId>> saveContent(MailboxMessage message) throws MailboxException {
        try {
            byte[] headerContent = IOUtils.toByteArray(message.getHeaderContent(), message.getHeaderOctets());
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
        BoundStatement boundStatement = insert.bind()
            .setUuid(MESSAGE_ID, messageId.get())
            .setInstant(INTERNAL_DATE, message.getInternalDate().toInstant())
            .setInt(BODY_START_OCTET, (int) (message.getHeaderOctets()))
            .setLong(FULL_CONTENT_OCTETS, message.getFullContentOctets())
            .setLong(BODY_OCTECTS, message.getBodyOctets())
            .setString(BODY_CONTENT, pair.getT2().asString())
            .setString(HEADER_CONTENT, pair.getT1().asString())
            .setLong(TEXTUAL_LINE_COUNT, Optional.ofNullable(message.getTextualLineCount()).orElse(DEFAULT_LONG_VALUE))
            .setString(CONTENT_DESCRIPTION, message.getProperties().getContentDescription())
            .setString(CONTENT_DISPOSITION_TYPE, message.getProperties().getContentDispositionType())
            .setString(MEDIA_TYPE, message.getProperties().getMediaType())
            .setString(SUB_TYPE, message.getProperties().getSubType())
            .setString(CONTENT_ID, message.getProperties().getContentID())
            .setString(CONTENT_MD5, message.getProperties().getContentMD5())
            .setString(CONTENT_TRANSFER_ENCODING, message.getProperties().getContentTransferEncoding())
            .setString(CONTENT_LOCATION, message.getProperties().getContentLocation())
            .setList(CONTENT_LANGUAGE, message.getProperties().getContentLanguage(), String.class)
            .setMap(CONTENT_DISPOSITION_PARAMETERS, message.getProperties().getContentDispositionParameters(), String.class, String.class)
            .setMap(CONTENT_TYPE_PARAMETERS, message.getProperties().getContentTypeParameters(), String.class, String.class);

        if (message.getAttachments().isEmpty()) {
            return boundStatement.unset(ATTACHMENTS);
        } else {
            return boundStatement.setList(ATTACHMENTS, buildAttachmentUdt(message), UdtValue.class);
        }
    }

    private ImmutableList<UdtValue> buildAttachmentUdt(MailboxMessage message) {
        return message.getAttachments().stream()
            .map(this::toUDT)
            .collect(ImmutableList.toImmutableList());
    }

    private ImmutableList<UdtValue> buildAttachmentUdt(List<MessageAttachmentRepresentation> attachments) {
        return attachments.stream()
            .map(this::toUDT)
            .collect(ImmutableList.toImmutableList());
    }

    private UdtValue toUDT(MessageAttachmentMetadata messageAttachment) {
        UdtValue result = attachmentsType
            .newValue()
            .setString(Attachments.ID, messageAttachment.getAttachmentId().getId())
            .setBoolean(Attachments.IS_INLINE, messageAttachment.isInline());

        UdtValue setNameUdtValue = messageAttachment.getName()
            .map(name -> result.setString(Attachments.NAME, name))
            .orElse(result);

        return messageAttachment.getCid()
                .map(cid -> setNameUdtValue.setString(Attachments.CID, cid.getValue()))
                .orElse(setNameUdtValue);
    }

    private UdtValue toUDT(MessageAttachmentRepresentation messageAttachment) {
        UdtValue result = attachmentsType
            .newValue()
            .setString(Attachments.ID, messageAttachment.getAttachmentId().getId())
            .setBoolean(Attachments.IS_INLINE, messageAttachment.isInline());

        Optional<UdtValue> maybeSetNameUdtValue = messageAttachment.getName()
            .map(name -> result.setString(Attachments.NAME, name));

        Optional<UdtValue> maybeSetCidUdtValue = maybeSetNameUdtValue
            .map(setNameUdtValue -> messageAttachment.getCid()
                .map(cid -> setNameUdtValue.setString(Attachments.CID, cid.getValue()))
                .orElse(setNameUdtValue));

        return maybeSetCidUdtValue.orElse(result);
    }

    public Mono<MessageRepresentation> retrieveMessage(ComposedMessageIdWithMetaData id, FetchType fetchType) {
        CassandraMessageId cassandraMessageId = (CassandraMessageId) id.getComposedMessageId().getMessageId();
        return retrieveMessage(cassandraMessageId, fetchType);
    }

    public Mono<MessageRepresentation> retrieveMessage(CassandraMessageId cassandraMessageId, FetchType fetchType) {
        return retrieveRow(cassandraMessageId)
            .flatMap(row -> message(row, cassandraMessageId, fetchType));
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
                Date.from(row.getInstant(INTERNAL_DATE_LOWERCASE)),
                row.getLong(FULL_CONTENT_OCTETS_LOWERCASE),
                row.getInt(BODY_START_OCTET_LOWERCASE),
                new ByteContent(content),
                getProperties(row),
                getAttachments(row).collect(ImmutableList.toImmutableList()),
                headerId,
                bodyId));
    }

    private Properties getProperties(Row row) {
        PropertyBuilder property = new PropertyBuilder();
        property.setContentDescription(row.getString(CONTENT_DESCRIPTION));
        property.setContentDispositionType(row.getString(CONTENT_DISPOSITION_TYPE));
        property.setMediaType(row.getString(MEDIA_TYPE));
        property.setSubType(row.getString(SUB_TYPE));
        property.setContentID(row.getString(CONTENT_ID));
        property.setContentMD5(row.getString(CONTENT_MD5));
        property.setContentTransferEncoding(row.getString(CONTENT_TRANSFER_ENCODING));
        property.setContentLocation(row.getString(CONTENT_LOCATION));
        property.setContentLanguage(row.get(CONTENT_LANGUAGE, LIST_OF_STRINGS_CODEC));
        property.setContentDispositionParameters(row.get(CONTENT_DISPOSITION_PARAMETERS, MAP_OF_STRINGS_CODEC));
        property.setContentTypeParameters(row.get(CONTENT_TYPE_PARAMETERS, MAP_OF_STRINGS_CODEC));
        property.setTextualLineCount(row.getLong(TEXTUAL_LINE_COUNT_LOWERCASE));
        return property.build();
    }

    private Stream<MessageAttachmentRepresentation> getAttachments(Row row) {
        List<UdtValue> udtValues = row.get(ATTACHMENTS, CodecRegistry.DEFAULT.codecFor(listOf(attachmentsType)));
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
            .cid(cidParser.parse(udtValue.getString(Attachments.CID)))
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

    Flux<BlobId> listBlobs() {
        return cassandraAsyncExecutor.executeRows(listBlobs.bind())
            .flatMapIterable(row -> ImmutableList.of(
                blobIdFactory.from(row.getString(HEADER_CONTENT_LOWERCASE)),
                blobIdFactory.from(row.getString(BODY_CONTENT_LOWERCASE))));
    }
}
