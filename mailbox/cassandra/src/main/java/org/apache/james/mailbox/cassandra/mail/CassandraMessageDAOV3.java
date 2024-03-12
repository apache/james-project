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
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV3Table.BODY_OCTECTS;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV3Table.BODY_START_OCTET;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV3Table.FULL_CONTENT_OCTETS;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV3Table.HEADER_CONTENT;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV3Table.INTERNAL_DATE;
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

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.inject.Inject;

import org.apache.commons.io.IOUtils;
import org.apache.james.backends.cassandra.init.CassandraTypesProvider;
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.cassandra.table.CassandraMessageV3Table.Attachments;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.model.ByteContent;
import org.apache.james.mailbox.model.Cid;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.mailbox.model.Content;
import org.apache.james.mailbox.model.HeaderAndBodyByteContent;
import org.apache.james.mailbox.model.MessageAttachmentMetadata;
import org.apache.james.mailbox.store.mail.MessageMapper.FetchType;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.impl.Properties;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.BoundStatementBuilder;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.data.UdtValue;
import com.datastax.oss.driver.api.core.type.UserDefinedType;
import com.datastax.oss.driver.api.core.type.codec.TypeCodec;
import com.datastax.oss.driver.api.core.type.codec.TypeCodecs;
import com.datastax.oss.driver.api.core.type.codec.registry.CodecRegistry;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteSource;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
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
    private final UserDefinedType attachmentsType;
    private final TypeCodec<List<UdtValue>> attachmentCodec;

    @Inject
    public CassandraMessageDAOV3(CqlSession session, CassandraTypesProvider typesProvider, BlobStore blobStore,
                                 BlobId.Factory blobIdFactory) {
        this.cassandraAsyncExecutor = new CassandraAsyncExecutor(session);
        this.blobStore = blobStore;
        this.blobIdFactory = blobIdFactory;

        this.insert = prepareInsert(session);
        this.delete = prepareDelete(session);
        this.select = prepareSelect(session);
        this.listBlobs = prepareSelectBlobs(session);
        this.cidParser = Cid.parser().relaxed();
        this.attachmentsType = typesProvider.getDefinedUserType(ATTACHMENTS.asCql(true));
        this.attachmentCodec = CodecRegistry.DEFAULT.codecFor(listOf(attachmentsType));
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

    public Mono<Tuple2<BlobId, BlobId>> save(MailboxMessage message) {
        return saveContent(message)
            .flatMap(pair -> cassandraAsyncExecutor.executeVoid(boundWriteStatement(message, pair)).thenReturn(pair));
    }

    private Mono<Tuple2<BlobId, BlobId>> saveContent(MailboxMessage message) {
        return Mono.fromCallable(() -> IOUtils.toByteArray(message.getHeaderContent(), message.getHeaderOctets()))
            .subscribeOn(Schedulers.boundedElastic())
            .flatMap(headerContent -> {
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
            });
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
            .set(CONTENT_LANGUAGE, message.getProperties().getContentLanguage(), LIST_OF_STRINGS_CODEC)
            .set(CONTENT_DISPOSITION_PARAMETERS, message.getProperties().getContentDispositionParameters(), MAP_OF_STRINGS_CODEC)
            .set(CONTENT_TYPE_PARAMETERS, message.getProperties().getContentTypeParameters(), MAP_OF_STRINGS_CODEC);

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
            .set(MESSAGE_ID, messageId.get(), TypeCodecs.TIMEUUID));
    }

    private Mono<MessageRepresentation> message(Row row, CassandraMessageId cassandraMessageId, FetchType fetchType) {
        BlobId headerId = retrieveBlobId(HEADER_CONTENT, row);
        BlobId bodyId = retrieveBlobId(BODY_CONTENT, row);

        return buildContentRetriever(fetchType, headerId, bodyId)
            .map(content ->
                new MessageRepresentation(
                    cassandraMessageId,
                    Optional.ofNullable(row.get(INTERNAL_DATE, TypeCodecs.TIMESTAMP)).map(Date::from).orElse(null),
                    row.getLong(FULL_CONTENT_OCTETS),
                    row.getInt(BODY_START_OCTET),
                    content,
                    getProperties(row),
                    getAttachments(row),
                    headerId,
                    bodyId));
    }

    private Properties getProperties(Row row) {
        PropertyBuilder property = new PropertyBuilder();
        property.setContentDescription(row.get(CONTENT_DESCRIPTION, TypeCodecs.TEXT));
        property.setContentDispositionType(row.get(CONTENT_DISPOSITION_TYPE, TypeCodecs.TEXT));
        property.setMediaType(row.get(MEDIA_TYPE, TypeCodecs.TEXT));
        property.setSubType(row.get(SUB_TYPE, TypeCodecs.TEXT));
        property.setContentID(row.get(CONTENT_ID, TypeCodecs.TEXT));
        property.setContentMD5(row.get(CONTENT_MD5, TypeCodecs.TEXT));
        property.setContentTransferEncoding(row.get(CONTENT_TRANSFER_ENCODING, TypeCodecs.TEXT));
        property.setContentLocation(row.get(CONTENT_LOCATION, TypeCodecs.TEXT));
        property.setContentLanguage(row.get(CONTENT_LANGUAGE, LIST_OF_STRINGS_CODEC));
        property.setContentDispositionParameters(row.get(CONTENT_DISPOSITION_PARAMETERS, MAP_OF_STRINGS_CODEC));
        property.setContentTypeParameters(row.get(CONTENT_TYPE_PARAMETERS, MAP_OF_STRINGS_CODEC));
        property.setTextualLineCount(row.getLong(TEXTUAL_LINE_COUNT));
        return property.build();
    }

    private List<MessageAttachmentRepresentation> getAttachments(Row row) {
        return Optional.ofNullable(row.get(ATTACHMENTS, attachmentCodec))
            .map(this::attachmentByIds)
            .orElseGet(ImmutableList::of);
    }

    private List<MessageAttachmentRepresentation> attachmentByIds(List<UdtValue> udtValues) {
        return udtValues.stream()
            .map(this::messageAttachmentByIdFrom)
            .collect(ImmutableList.toImmutableList());
    }

    private MessageAttachmentRepresentation messageAttachmentByIdFrom(UdtValue udtValue) {
        return MessageAttachmentRepresentation.builder()
            .attachmentId(AttachmentId.from(udtValue.get(Attachments.ID, TypeCodecs.TEXT)))
            .name(udtValue.get(Attachments.NAME, TypeCodecs.TEXT))
            .cid(cidParser.parse(udtValue.get(Attachments.CID, TypeCodecs.TEXT)))
            .isInline(udtValue.getBoolean(Attachments.IS_INLINE))
            .build();
    }

    public Mono<Void> delete(CassandraMessageId messageId) {
        return cassandraAsyncExecutor.executeVoid(delete.bind()
            .setUuid(MESSAGE_ID, messageId.get()));
    }

    private Mono<Content> buildContentRetriever(FetchType fetchType, BlobId headerId, BlobId bodyId) {
        switch (fetchType) {
            case FULL:
                return getFullContent(headerId, bodyId);
            case ATTACHMENTS_METADATA:
            case HEADERS:
                return getContent(headerId, SIZE_BASED)
                    .map(ByteContent::new);
            case METADATA:
                return Mono.just(new ByteContent(EMPTY_BYTE_ARRAY));
            default:
                throw new RuntimeException("Unknown FetchType " + fetchType);
        }
    }

    private Mono<Content> getFullContent(BlobId headerId, BlobId bodyId) {
        return getContent(headerId, SIZE_BASED)
            .zipWith(getContent(bodyId, LOW_COST), HeaderAndBodyByteContent::new);
    }

    private Mono<byte[]> getContent(BlobId blobId, BlobStore.StoragePolicy storagePolicy) {
        return Mono.from(blobStore.readBytes(blobStore.getDefaultBucketName(), blobId, storagePolicy));
    }

    private BlobId retrieveBlobId(CqlIdentifier field, Row row) {
        return blobIdFactory.from(row.get(field, TypeCodecs.TEXT));
    }

    Flux<BlobId> listBlobs() {
        return cassandraAsyncExecutor.executeRows(listBlobs.bind())
            .flatMapIterable(row -> ImmutableList.of(
                blobIdFactory.from(row.get(HEADER_CONTENT, TypeCodecs.TEXT)),
                blobIdFactory.from(row.get(BODY_CONTENT, TypeCodecs.TEXT))));
    }
}
