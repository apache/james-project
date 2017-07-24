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
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.mail.util.SharedByteArrayInputStream;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.backends.cassandra.CassandraConfiguration;
import org.apache.james.backends.cassandra.init.CassandraTypesProvider;
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.mailbox.cassandra.ids.BlobId;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.cassandra.mail.utils.Limit;
import org.apache.james.mailbox.cassandra.table.CassandraMessageV2Table.Attachments;
import org.apache.james.mailbox.cassandra.table.CassandraMessageV2Table.Properties;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.model.Cid;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.mailbox.model.MessageAttachment;
import org.apache.james.mailbox.store.mail.MessageMapper.FetchType;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.Property;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.mail.model.impl.SimpleProperty;
import org.apache.james.util.CompletableFutureUtil;
import org.apache.james.util.FluentFutureStream;
import org.apache.james.util.streams.JamesCollectors;

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.UDTValue;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.github.steveash.guavate.Guavate;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Bytes;

public class CassandraMessageDAOV2 {
    public static final long DEFAULT_LONG_VALUE = 0L;
    public static final String DEFAULT_OBJECT_VALUE = null;
    private static final byte[] EMPTY_BYTE_ARRAY = {};

    private final CassandraAsyncExecutor cassandraAsyncExecutor;
    private final CassandraTypesProvider typesProvider;
    private final CassandraBlobsDAO blobsDAO;
    private final CassandraConfiguration configuration;
    private final PreparedStatement insert;
    private final PreparedStatement delete;
    private final PreparedStatement selectMetadata;
    private final PreparedStatement selectHeaders;
    private final PreparedStatement selectFields;
    private final PreparedStatement selectBody;

    @Inject
    public CassandraMessageDAOV2(Session session, CassandraTypesProvider typesProvider, CassandraBlobsDAO blobsDAO, CassandraConfiguration cassandraConfiguration) {
        this.cassandraAsyncExecutor = new CassandraAsyncExecutor(session);
        this.typesProvider = typesProvider;
        this.blobsDAO = blobsDAO;
        this.configuration = cassandraConfiguration;
        this.insert = prepareInsert(session);
        this.delete = prepareDelete(session);
        this.selectMetadata = prepareSelect(session, METADATA);
        this.selectHeaders = prepareSelect(session, HEADERS);
        this.selectFields = prepareSelect(session, FIELDS);
        this.selectBody = prepareSelect(session, BODY);
    }

    @VisibleForTesting
    public CassandraMessageDAOV2(Session session, CassandraTypesProvider typesProvider, CassandraBlobsDAO blobsDAO) {
        this(session, typesProvider, blobsDAO, CassandraConfiguration.DEFAULT_CONFIGURATION);
    }

    private PreparedStatement prepareSelect(Session session, String[] fields) {
        return session.prepare(select(fields)
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
            .value(PROPERTIES, bindMarker(PROPERTIES))
            .value(TEXTUAL_LINE_COUNT, bindMarker(TEXTUAL_LINE_COUNT))
            .value(ATTACHMENTS, bindMarker(ATTACHMENTS)));
    }

    private PreparedStatement prepareDelete(Session session) {
        return session.prepare(QueryBuilder.delete()
            .from(TABLE_NAME)
            .where(eq(MESSAGE_ID, bindMarker(MESSAGE_ID))));
    }

    public CompletableFuture<Void> save(MailboxMessage message) throws MailboxException {
        return saveContent(message).thenCompose(pair ->
            cassandraAsyncExecutor.executeVoid(boundWriteStatement(message, pair)));
    }

    private CompletableFuture<Pair<Optional<BlobId>, Optional<BlobId>>> saveContent(MailboxMessage message) throws MailboxException {
        try {
            return CompletableFutureUtil.combine(
                blobsDAO.save(
                    IOUtils.toByteArray(
                        message.getHeaderContent())),
                blobsDAO.save(
                    IOUtils.toByteArray(
                        message.getBodyContent())),
                Pair::of);
        } catch (IOException e) {
            throw new MailboxException("Error saving mail content", e);
        }
    }

    private BoundStatement boundWriteStatement(MailboxMessage message, Pair<Optional<BlobId>, Optional<BlobId>> pair) {
        CassandraMessageId messageId = (CassandraMessageId) message.getMessageId();
        return insert.bind()
            .setUUID(MESSAGE_ID, messageId.get())
            .setTimestamp(INTERNAL_DATE, message.getInternalDate())
            .setInt(BODY_START_OCTET, (int) (message.getHeaderOctets()))
            .setLong(FULL_CONTENT_OCTETS, message.getFullContentOctets())
            .setLong(BODY_OCTECTS, message.getBodyOctets())
            .setString(BODY_CONTENT, pair.getRight().map(BlobId::getId).orElse(DEFAULT_OBJECT_VALUE))
            .setString(HEADER_CONTENT, pair.getLeft().map(BlobId::getId).orElse(DEFAULT_OBJECT_VALUE))
            .setLong(TEXTUAL_LINE_COUNT, Optional.ofNullable(message.getTextualLineCount()).orElse(DEFAULT_LONG_VALUE))
            .setList(PROPERTIES, buildPropertiesUdt(message))
            .setList(ATTACHMENTS, buildAttachmentUdt(message));
    }

    private ImmutableList<UDTValue> buildAttachmentUdt(MailboxMessage message) {
        return message.getAttachments().stream()
            .map(this::toUDT)
            .collect(Guavate.toImmutableList());
    }

    private List<UDTValue> buildPropertiesUdt(List<Property> properties) {
        return properties.stream()
            .map(property -> typesProvider.getDefinedUserType(PROPERTIES)
                .newValue()
                .setString(Properties.NAMESPACE, property.getNamespace())
                .setString(Properties.NAME, property.getLocalName())
                .setString(Properties.VALUE, property.getValue()))
            .collect(Guavate.toImmutableList());
    }

    private UDTValue toUDT(MessageAttachment messageAttachment) {
        return typesProvider.getDefinedUserType(ATTACHMENTS)
            .newValue()
            .setString(Attachments.ID, messageAttachment.getAttachmentId().getId())
            .setString(Attachments.NAME, messageAttachment.getName().orNull())
            .setString(Attachments.CID, messageAttachment.getCid().transform(Cid::getValue).orNull())
            .setBool(Attachments.IS_INLINE, messageAttachment.isInline());
    }

    public CompletableFuture<Void> save(CassandraMessageDAO.RawMessage rawMessage) {
        return CompletableFutureUtil.combine(
            blobsDAO.save(rawMessage.getHeaderContent()),
            blobsDAO.save(rawMessage.getBodyContent()),
            Pair::of)
            .thenCompose(pair ->
                cassandraAsyncExecutor.executeVoid(boundWriteStatement(rawMessage, pair)));
    }

    private BoundStatement boundWriteStatement(CassandraMessageDAO.RawMessage message, Pair<Optional<BlobId>, Optional<BlobId>> pair) {
        CassandraMessageId messageId = (CassandraMessageId) message.getMessageId();
        return insert.bind()
            .setUUID(MESSAGE_ID, messageId.get())
            .setTimestamp(INTERNAL_DATE, message.getInternalDate())
            .setInt(BODY_START_OCTET, message.getBodyStartOctet())
            .setLong(FULL_CONTENT_OCTETS, message.getFullContentOctet())
            .setLong(BODY_OCTECTS, message.getFullContentOctet() - message.getBodyStartOctet())
            .setString(BODY_CONTENT, pair.getRight().map(BlobId::getId).orElse(DEFAULT_OBJECT_VALUE))
            .setString(HEADER_CONTENT, pair.getLeft().map(BlobId::getId).orElse(DEFAULT_OBJECT_VALUE))
            .setLong(TEXTUAL_LINE_COUNT, message.getTextuaLineCount())
            .setList(PROPERTIES, buildPropertiesUdt(message.getPropertyBuilder().toProperties()))
            .setList(ATTACHMENTS, buildAttachmentUdt(message));
    }

    private List<UDTValue> buildPropertiesUdt(MailboxMessage message) {
        return message.getProperties().stream()
            .map(x -> typesProvider.getDefinedUserType(PROPERTIES)
                .newValue()
                .setString(Properties.NAMESPACE, x.getNamespace())
                .setString(Properties.NAME, x.getLocalName())
                .setString(Properties.VALUE, x.getValue()))
            .collect(Guavate.toImmutableList());
    }

    private ImmutableList<UDTValue> buildAttachmentUdt(CassandraMessageDAO.RawMessage message) {
        return message.getAttachments().stream()
            .map(this::toUDT)
            .collect(Guavate.toImmutableList());
    }

    private UDTValue toUDT(MessageAttachmentRepresentation messageAttachment) {
        return typesProvider.getDefinedUserType(ATTACHMENTS)
            .newValue()
            .setString(Attachments.ID, messageAttachment.getAttachmentId().getId())
            .setString(Attachments.NAME, messageAttachment.getName().orElse(null))
            .setString(Attachments.CID, messageAttachment.getCid().map(Cid::getValue).orElse(null))
            .setBool(Attachments.IS_INLINE, messageAttachment.isInline());
    }

    public CompletableFuture<Stream<MessageResult>> retrieveMessages(List<ComposedMessageIdWithMetaData> messageIds, FetchType fetchType, Limit limit) {
        return CompletableFutureUtil.chainAll(
                limit.applyOnStream(messageIds.stream().distinct())
                    .collect(JamesCollectors.chunker(configuration.getMessageReadChunkSize())),
            ids -> rowToMessages(fetchType, ids))
            .thenApply(stream -> stream.flatMap(Function.identity()));
    }

    private CompletableFuture<Stream<MessageResult>> rowToMessages(FetchType fetchType, Collection<ComposedMessageIdWithMetaData> ids) {
        return FluentFutureStream.of(
            ids.stream()
                .map(id -> retrieveRow(id, fetchType)
                    .thenCompose((ResultSet resultSet) -> message(resultSet, id, fetchType))))
            .completableFuture();
    }

    private CompletableFuture<ResultSet> retrieveRow(ComposedMessageIdWithMetaData messageId, FetchType fetchType) {
        CassandraMessageId cassandraMessageId = (CassandraMessageId) messageId.getComposedMessageId().getMessageId();

        return cassandraAsyncExecutor.execute(retrieveSelect(fetchType)
            .bind()
            .setUUID(MESSAGE_ID, cassandraMessageId.get()));
    }

    private CompletableFuture<MessageResult>
    message(ResultSet rows,ComposedMessageIdWithMetaData messageIdWithMetaData, FetchType fetchType) {
        ComposedMessageId messageId = messageIdWithMetaData.getComposedMessageId();

        if (rows.isExhausted()) {
            return CompletableFuture.completedFuture(notFound(messageIdWithMetaData));
        }

        Row row = rows.one();
        CompletableFuture<byte[]> contentFuture = buildContentRetriever(fetchType).apply(row);

        return contentFuture.thenApply(content -> {
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
                    messageIdWithMetaData.getModSeq());
            return found(Pair.of(messageWithoutAttachment, getAttachments(row, fetchType)));
        });
    }

    private PropertyBuilder getPropertyBuilder(Row row) {
        PropertyBuilder property = new PropertyBuilder(
            row.getList(PROPERTIES, UDTValue.class).stream()
                .map(x -> new SimpleProperty(x.getString(Properties.NAMESPACE), x.getString(Properties.NAME), x.getString(Properties.VALUE)))
                .collect(Collectors.toList()));
        property.setTextualLineCount(row.getLong(TEXTUAL_LINE_COUNT));
        return property;
    }

    private Stream<MessageAttachmentRepresentation> getAttachments(Row row, FetchType fetchType) {
        switch (fetchType) {
            case Full:
            case Body:
                List<UDTValue> udtValues = row.getList(ATTACHMENTS, UDTValue.class);

                return attachmentByIds(udtValues);
            default:
                return Stream.of();
        }
    }

    private Stream<MessageAttachmentRepresentation> attachmentByIds(List<UDTValue> udtValues) {
        return udtValues.stream()
            .map(this::messageAttachmentByIdFrom);
    }

    private MessageAttachmentRepresentation messageAttachmentByIdFrom(UDTValue udtValue) {
        return MessageAttachmentRepresentation.builder()
            .attachmentId(AttachmentId.from(udtValue.getString(Attachments.ID)))
            .name(udtValue.getString(Attachments.NAME))
            .cid(Optional.ofNullable(udtValue.getString(Attachments.CID)).map(Cid::from))
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

    public CompletableFuture<Void> delete(CassandraMessageId messageId) {
        return cassandraAsyncExecutor.executeVoid(delete.bind()
            .setUUID(MESSAGE_ID, messageId.get()));
    }

    private Function<Row, CompletableFuture<byte[]>> buildContentRetriever(FetchType fetchType) {
        switch (fetchType) {
            case Full:
                return this::getFullContent;
            case Headers:
                return this::getHeaderContent;
            case Body:
                return row -> getBodyContent(row)
                    .thenApply(data -> Bytes.concat(new byte[row.getInt(BODY_START_OCTET)], data));
            case Metadata:
                return row -> CompletableFuture.completedFuture(EMPTY_BYTE_ARRAY);
            default:
                throw new RuntimeException("Unknown FetchType " + fetchType);
        }
    }

    private CompletableFuture<byte[]> getFullContent(Row row) {
        return CompletableFutureUtil.combine(
            getHeaderContent(row),
            getBodyContent(row),
            Bytes::concat);
    }

    private CompletableFuture<byte[]> getBodyContent(Row row) {
        return getFieldContent(BODY_CONTENT, row);
    }

    private CompletableFuture<byte[]> getHeaderContent(Row row) {
        return getFieldContent(HEADER_CONTENT, row);
    }

    private CompletableFuture<byte[]> getFieldContent(String field, Row row) {
        return blobsDAO.read(BlobId.from(row.getString(field)));
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
}
