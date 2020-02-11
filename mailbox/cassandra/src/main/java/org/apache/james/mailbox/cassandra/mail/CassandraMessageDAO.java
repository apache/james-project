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
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV2Table.BODY;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV2Table.BODY_CONTENT;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV2Table.BODY_OCTECTS;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV2Table.BODY_START_OCTET;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV2Table.FIELDS;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV2Table.FULL_CONTENT_OCTETS;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV2Table.HEADERS;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV2Table.HEADER_CONTENT;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV2Table.INTERNAL_DATE;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV2Table.METADATA;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV2Table.PROPERTIES;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV2Table.TABLE_NAME;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV2Table.TEXTUAL_LINE_COUNT;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.mail.util.SharedByteArrayInputStream;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.backends.cassandra.init.CassandraTypesProvider;
import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;
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
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.mailbox.model.MessageAttachment;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.mail.MessageMapper.FetchType;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.Property;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.util.streams.Limit;

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.UDTValue;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.github.steveash.guavate.Guavate;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Bytes;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuple2;

public class CassandraMessageDAO {
    public static final long DEFAULT_LONG_VALUE = 0L;
    private static final byte[] EMPTY_BYTE_ARRAY = {};

    private final CassandraAsyncExecutor cassandraAsyncExecutor;
    private final CassandraTypesProvider typesProvider;
    private final BlobStore blobStore;
    private final BlobId.Factory blobIdFactory;
    private final CassandraConfiguration configuration;
    private final CassandraMessageId.Factory messageIdFactory;
    private final PreparedStatement insert;
    private final PreparedStatement delete;
    private final PreparedStatement selectMetadata;
    private final PreparedStatement selectHeaders;
    private final PreparedStatement selectFields;
    private final PreparedStatement selectBody;
    private final PreparedStatement selectAllMessagesWithAttachment;
    private final Cid.CidParser cidParser;

    @Inject
    public CassandraMessageDAO(Session session, CassandraTypesProvider typesProvider, BlobStore blobStore,
            BlobId.Factory blobIdFactory, CassandraConfiguration cassandraConfiguration,
            CassandraMessageId.Factory messageIdFactory) {
        this.cassandraAsyncExecutor = new CassandraAsyncExecutor(session);
        this.typesProvider = typesProvider;
        this.blobStore = blobStore;
        this.blobIdFactory = blobIdFactory;
        this.configuration = cassandraConfiguration;
        this.messageIdFactory = messageIdFactory;

        this.insert = prepareInsert(session);
        this.delete = prepareDelete(session);
        this.selectMetadata = prepareSelect(session, METADATA);
        this.selectHeaders = prepareSelect(session, HEADERS);
        this.selectFields = prepareSelect(session, FIELDS);
        this.selectBody = prepareSelect(session, BODY);
        this.selectAllMessagesWithAttachment = prepareSelectAllMessagesWithAttachment(session);
        this.cidParser = Cid.parser().relaxed();
    }

    @VisibleForTesting
    public CassandraMessageDAO(Session session, CassandraTypesProvider typesProvider, BlobStore blobStore,
                               BlobId.Factory blobIdFactory, CassandraMessageId.Factory messageIdFactory) {
        this(session, typesProvider, blobStore,  blobIdFactory, CassandraConfiguration.DEFAULT_CONFIGURATION, messageIdFactory);
    }

    private PreparedStatement prepareSelect(Session session, String[] fields) {
        return session.prepare(select(fields)
            .from(TABLE_NAME)
            .where(eq(MESSAGE_ID, bindMarker(MESSAGE_ID))));
    }

    private PreparedStatement prepareSelectAllMessagesWithAttachment(Session session) {
        return session.prepare(select(MESSAGE_ID, ATTACHMENTS)
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

    public Mono<Void> save(MailboxMessage message) throws MailboxException {
        return saveContent(message)
            .flatMap(pair -> cassandraAsyncExecutor.executeVoid(boundWriteStatement(message, pair)))
            .then();
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

    private UDTValue toUDT(MessageAttachment messageAttachment) {
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
        return message.getProperties().stream()
            .map(property -> typesProvider.getDefinedUserType(PROPERTIES)
                .newValue()
                .setString(Properties.NAMESPACE, property.getNamespace())
                .setString(Properties.NAME, property.getLocalName())
                .setString(Properties.VALUE, property.getValue()))
            .collect(Guavate.toImmutableList());
    }

    public Flux<MessageResult> retrieveMessages(List<ComposedMessageIdWithMetaData> messageIds, FetchType fetchType, Limit limit) {
        return Flux.fromStream(limit.applyOnStream(messageIds.stream().distinct()))
            .publishOn(Schedulers.elastic())
            .flatMap(id -> retrieveRow(id, fetchType)
                .flatMap(resultSet -> message(resultSet, id, fetchType)), configuration.getMessageReadChunkSize());
    }

    private Mono<ResultSet> retrieveRow(ComposedMessageIdWithMetaData messageId, FetchType fetchType) {
        CassandraMessageId cassandraMessageId = (CassandraMessageId) messageId.getComposedMessageId().getMessageId();

        return cassandraAsyncExecutor.execute(retrieveSelect(fetchType)
            .bind()
            .setUUID(MESSAGE_ID, cassandraMessageId.get()));
    }

    private Mono<MessageResult>
    message(ResultSet rows,ComposedMessageIdWithMetaData messageIdWithMetaData, FetchType fetchType) {
        ComposedMessageId messageId = messageIdWithMetaData.getComposedMessageId();

        if (rows.isExhausted()) {
            return Mono.just(notFound(messageIdWithMetaData));
        }

        Row row = rows.one();
        return buildContentRetriever(fetchType, row).map(content -> {
            MessageWithoutAttachment messageWithoutAttachment =
                new MessageWithoutAttachment(
                    messageId.getMessageId(),
                    row.getTimestamp(INTERNAL_DATE),
                    row.getLong(FULL_CONTENT_OCTETS),
                    row.getInt(BODY_START_OCTET),
                    new SharedByteArrayInputStream(content),
                    messageIdWithMetaData.getFlags(),
                    getPropertyBuilder(row),
                    messageId.getMailboxId(),
                    messageId.getUid(),
                    messageIdWithMetaData.getModSeq(),
                    hasAttachment(row));
            return found(Pair.of(messageWithoutAttachment, getAttachments(row)));
        });
    }

    private PropertyBuilder getPropertyBuilder(Row row) {
        PropertyBuilder property = new PropertyBuilder(
            row.getList(PROPERTIES, UDTValue.class).stream()
                .map(this::toProperty)
                .collect(Collectors.toList()));
        property.setTextualLineCount(row.getLong(TEXTUAL_LINE_COUNT));
        return property;
    }

    private Property toProperty(UDTValue udtValue) {
        return new Property(udtValue.getString(Properties.NAMESPACE), udtValue.getString(Properties.NAME), udtValue.getString(Properties.VALUE));
    }

    private Stream<MessageAttachmentRepresentation> getAttachments(Row row) {
        List<UDTValue> udtValues = row.getList(ATTACHMENTS, UDTValue.class);
        return attachmentByIds(udtValues);
    }

    private boolean hasAttachment(Row row) {
        List<UDTValue> udtValues = row.getList(ATTACHMENTS, UDTValue.class);
        return !udtValues.isEmpty();
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

    private PreparedStatement retrieveSelect(FetchType fetchType) {
        switch (fetchType) {
            case Body:
                return selectBody;
            case Full:
                return selectFields;
            case Headers:
                return selectHeaders;
            case Metadata:
                return selectMetadata;
            default:
                throw new RuntimeException("Unknown FetchType " + fetchType);
        }
    }

    public Mono<Void> delete(CassandraMessageId messageId) {
        return cassandraAsyncExecutor.executeVoid(delete.bind()
            .setUUID(MESSAGE_ID, messageId.get()));
    }

    private Mono<byte[]> buildContentRetriever(FetchType fetchType, Row row) {
        switch (fetchType) {
            case Full:
                return getFullContent(row);
            case Headers:
                return getHeaderContent(row);
            case Body:
                return getBodyContent(row)
                    .map(data -> Bytes.concat(new byte[row.getInt(BODY_START_OCTET)], data));
            case Metadata:
                return Mono.just(EMPTY_BYTE_ARRAY);
            default:
                throw new RuntimeException("Unknown FetchType " + fetchType);
        }
    }

    private Mono<byte[]> getFullContent(Row row) {
        return getHeaderContent(row)
            .zipWith(getBodyContent(row), Bytes::concat);
    }

    private Mono<byte[]> getBodyContent(Row row) {
        return getFieldContent(BODY_CONTENT, row);
    }

    private Mono<byte[]> getHeaderContent(Row row) {
        return getFieldContent(HEADER_CONTENT, row);
    }

    private Mono<byte[]> getFieldContent(String field, Row row) {
        return Mono.from(blobStore.readBytes(blobStore.getDefaultBucketName(), blobIdFactory.from(row.getString(field))));
    }

    public static MessageResult notFound(ComposedMessageIdWithMetaData id) {
        return new MessageResult(id, Optional.empty());
    }

    public static MessageResult found(Pair<MessageWithoutAttachment, Stream<MessageAttachmentRepresentation>> message) {
        return new MessageResult(message.getLeft().getMetadata(), Optional.of(message));
    }

    public static class MessageResult {
        private final ComposedMessageIdWithMetaData metaData;
        private final Optional<Pair<MessageWithoutAttachment, Stream<MessageAttachmentRepresentation>>> message;

        public MessageResult(ComposedMessageIdWithMetaData metaData, Optional<Pair<MessageWithoutAttachment, Stream<MessageAttachmentRepresentation>>> message) {
            this.metaData = metaData;
            this.message = message;
        }

        public ComposedMessageIdWithMetaData getMetadata() {
            return metaData;
        }

        public boolean isFound() {
            return message.isPresent();
        }

        public Pair<MessageWithoutAttachment, Stream<MessageAttachmentRepresentation>> message() {
            return message.get();
        }
    }

    public Flux<MessageIdAttachmentIds> retrieveAllMessageIdAttachmentIds() {
        return cassandraAsyncExecutor.executeRows(
            selectAllMessagesWithAttachment.bind()
                .setReadTimeoutMillis(configuration.getMessageAttachmentIdsReadTimeout()))
            .map(this::fromRow)
            .filter(MessageIdAttachmentIds::hasAttachment);
    }

    private MessageIdAttachmentIds fromRow(Row row) {
        MessageId messageId = messageIdFactory.of(row.getUUID(MESSAGE_ID));
        Set<AttachmentId> attachmentIds = attachmentByIds(row.getList(ATTACHMENTS, UDTValue.class))
            .map(MessageAttachmentRepresentation::getAttachmentId)
            .collect(Guavate.toImmutableSet());
        return new MessageIdAttachmentIds(messageId, attachmentIds);
    }

    public static class MessageIdAttachmentIds {
        private final MessageId messageId;
        private final Set<AttachmentId> attachmentIds;
        
        public MessageIdAttachmentIds(MessageId messageId, Set<AttachmentId> attachmentIds) {
            Preconditions.checkNotNull(messageId);
            Preconditions.checkNotNull(attachmentIds);
            this.messageId = messageId;
            this.attachmentIds = ImmutableSet.copyOf(attachmentIds);
        }
        
        public MessageId getMessageId() {
            return messageId;
        }
        
        public Set<AttachmentId> getAttachmentId() {
            return attachmentIds;
        }

        public boolean hasAttachment() {
            return ! attachmentIds.isEmpty();
        }
        
        @Override
        public final boolean equals(Object o) {
            if (o instanceof MessageIdAttachmentIds) {
                MessageIdAttachmentIds other = (MessageIdAttachmentIds) o;
                return Objects.equals(messageId, other.messageId)
                    && Objects.equals(attachmentIds, other.attachmentIds);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(messageId, attachmentIds);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("messageId", messageId)
                .add("attachmentIds", attachmentIds)
                .toString();
        }
    }
}
