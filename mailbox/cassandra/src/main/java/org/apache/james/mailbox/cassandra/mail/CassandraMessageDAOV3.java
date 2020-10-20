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
import java.util.List;
import java.util.Optional;
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
import org.apache.james.mailbox.cassandra.table.CassandraMessageV3Table.Attachments;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.model.Cid;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.mailbox.model.MessageAttachmentMetadata;
import org.apache.james.mailbox.store.mail.MessageMapper.FetchType;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
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

import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

public class CassandraMessageDAOV3 {
    public static final long DEFAULT_LONG_VALUE = 0L;
    private static final byte[] EMPTY_BYTE_ARRAY = {};

    private final CassandraAsyncExecutor cassandraAsyncExecutor;
    private final CassandraTypesProvider typesProvider;
    private final BlobStore blobStore;
    private final BlobId.Factory blobIdFactory;
    private final PreparedStatement insert;
    private final PreparedStatement delete;
    private final PreparedStatement select;
    private final Cid.CidParser cidParser;
    private final ConsistencyLevel consistencyLevel;

    @Inject
    public CassandraMessageDAOV3(Session session, CassandraTypesProvider typesProvider, BlobStore blobStore,
                                 BlobId.Factory blobIdFactory,
                                 CassandraConsistenciesConfiguration consistenciesConfiguration) {
        this.cassandraAsyncExecutor = new CassandraAsyncExecutor(session);
        this.consistencyLevel = consistenciesConfiguration.getRegular();
        this.typesProvider = typesProvider;
        this.blobStore = blobStore;
        this.blobIdFactory = blobIdFactory;

        this.insert = prepareInsert(session);
        this.delete = prepareDelete(session);
        this.select = prepareSelect(session);
        this.cidParser = Cid.parser().relaxed();
    }

    private PreparedStatement prepareSelect(Session session) {
        return session.prepare(select()
            .from(TABLE_NAME)
            .where(eq(MESSAGE_ID, bindMarker(MESSAGE_ID))));
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
            .value(CONTENT_DESCRIPTION, bindMarker(CONTENT_DESCRIPTION))
            .value(CONTENT_DISPOSITION_TYPE, bindMarker(CONTENT_DISPOSITION_TYPE))
            .value(MEDIA_TYPE, bindMarker(MEDIA_TYPE))
            .value(SUB_TYPE, bindMarker(SUB_TYPE))
            .value(CONTENT_ID, bindMarker(CONTENT_ID))
            .value(CONTENT_MD5, bindMarker(CONTENT_MD5))
            .value(CONTENT_TRANSFER_ENCODING, bindMarker(CONTENT_TRANSFER_ENCODING))
            .value(CONTENT_LOCATION, bindMarker(CONTENT_LOCATION))
            .value(CONTENT_LANGUAGE, bindMarker(CONTENT_LANGUAGE))
            .value(CONTENT_DISPOSITION_PARAMETERS, bindMarker(CONTENT_DISPOSITION_PARAMETERS))
            .value(CONTENT_TYPE_PARAMETERS, bindMarker(CONTENT_TYPE_PARAMETERS))
            .value(TEXTUAL_LINE_COUNT, bindMarker(TEXTUAL_LINE_COUNT))
            .value(ATTACHMENTS, bindMarker(ATTACHMENTS)));
    }

    private PreparedStatement prepareDelete(Session session) {
        return session.prepare(QueryBuilder.delete()
            .from(TABLE_NAME)
            .where(eq(MESSAGE_ID, bindMarker(MESSAGE_ID))));
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
        PropertyBuilder propertyBuilder = new PropertyBuilder(message.getProperties());
        return insert.bind()
            .setUUID(MESSAGE_ID, messageId.get())
            .setTimestamp(INTERNAL_DATE, message.getInternalDate())
            .setInt(BODY_START_OCTET, (int) (message.getHeaderOctets()))
            .setLong(FULL_CONTENT_OCTETS, message.getFullContentOctets())
            .setLong(BODY_OCTECTS, message.getBodyOctets())
            .setString(BODY_CONTENT, pair.getT2().asString())
            .setString(HEADER_CONTENT, pair.getT1().asString())
            .setLong(TEXTUAL_LINE_COUNT, Optional.ofNullable(message.getTextualLineCount()).orElse(DEFAULT_LONG_VALUE))
            .setString(CONTENT_DESCRIPTION, propertyBuilder.getContentDescription())
            .setString(CONTENT_DISPOSITION_TYPE, propertyBuilder.getContentDispositionType())
            .setString(MEDIA_TYPE, propertyBuilder.getMediaType())
            .setString(SUB_TYPE, propertyBuilder.getSubType())
            .setString(CONTENT_ID, propertyBuilder.getContentID())
            .setString(CONTENT_MD5, propertyBuilder.getContentMD5())
            .setString(CONTENT_TRANSFER_ENCODING, propertyBuilder.getContentTransferEncoding())
            .setString(CONTENT_LOCATION, propertyBuilder.getContentLocation())
            .setList(CONTENT_LANGUAGE, propertyBuilder.getContentLanguage())
            .setMap(CONTENT_DISPOSITION_PARAMETERS, propertyBuilder.getContentDispositionParameters())
            .setMap(CONTENT_TYPE_PARAMETERS, propertyBuilder.getContentTypeParameters())
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
                getPropertyBuilder(row),
                getAttachments(row).collect(Guavate.toImmutableList()),
                headerId,
                bodyId));
    }

    private PropertyBuilder getPropertyBuilder(Row row) {
        PropertyBuilder property = new PropertyBuilder();
        property.setContentDescription(row.getString(CONTENT_DESCRIPTION));
        property.setContentDispositionType(row.getString(CONTENT_DISPOSITION_TYPE));
        property.setMediaType(row.getString(MEDIA_TYPE));
        property.setSubType(row.getString(SUB_TYPE));
        property.setContentID(row.getString(CONTENT_ID));
        property.setContentMD5(row.getString(CONTENT_MD5));
        property.setContentTransferEncoding(row.getString(CONTENT_TRANSFER_ENCODING));
        property.setContentLocation(row.getString(CONTENT_LOCATION));
        property.setContentLanguage(row.getList(CONTENT_LANGUAGE, String.class));
        property.setContentDispositionParameters(row.getMap(CONTENT_DISPOSITION_PARAMETERS, String.class, String.class));
        property.setContentTypeParameters(row.getMap(CONTENT_TYPE_PARAMETERS, String.class, String.class));
        property.setTextualLineCount(row.getLong(TEXTUAL_LINE_COUNT));
        return property;
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
            .cid(cidParser.parse(udtValue.getString(Attachments.CID)))
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
