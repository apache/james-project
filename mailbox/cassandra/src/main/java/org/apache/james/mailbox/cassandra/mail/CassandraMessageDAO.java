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
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV1Table.ATTACHMENTS;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV1Table.BODY;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV1Table.BODY_CONTENT;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV1Table.BODY_OCTECTS;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV1Table.BODY_START_OCTET;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV1Table.FIELDS;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV1Table.FULL_CONTENT_OCTETS;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV1Table.HEADERS;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV1Table.HEADER_CONTENT;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV1Table.INTERNAL_DATE;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV1Table.METADATA;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV1Table.PROPERTIES;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV1Table.TABLE_NAME;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV1Table.TEXTUAL_LINE_COUNT;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.mail.util.SharedByteArrayInputStream;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.backends.cassandra.CassandraConfiguration;
import org.apache.james.backends.cassandra.init.CassandraTypesProvider;
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.backends.cassandra.utils.CassandraUtils;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.cassandra.mail.utils.Limit;
import org.apache.james.mailbox.cassandra.table.CassandraMessageV1Table.Attachments;
import org.apache.james.mailbox.cassandra.table.CassandraMessageV1Table.Properties;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.model.Cid;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.mail.MessageMapper.FetchType;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
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
import com.google.common.io.ByteStreams;
import com.google.common.primitives.Bytes;

public class CassandraMessageDAO {
    private final CassandraAsyncExecutor cassandraAsyncExecutor;
    private final CassandraTypesProvider typesProvider;
    private final PreparedStatement insert;
    private final PreparedStatement delete;
    private final PreparedStatement selectMetadata;
    private final PreparedStatement selectHeaders;
    private final PreparedStatement selectFields;
    private final PreparedStatement selectBody;
    private final PreparedStatement selectAll;
    private CassandraUtils cassandraUtils;
    private final CassandraConfiguration cassandraConfiguration;

    @Inject
    public CassandraMessageDAO(Session session, CassandraTypesProvider typesProvider, CassandraConfiguration cassandraConfiguration,
                               CassandraUtils cassandraUtils) {
        this.cassandraAsyncExecutor = new CassandraAsyncExecutor(session);
        this.typesProvider = typesProvider;
        this.insert = prepareInsert(session);
        this.delete = prepareDelete(session);
        this.selectMetadata = prepareSelect(session, METADATA);
        this.selectHeaders = prepareSelect(session, HEADERS);
        this.selectFields = prepareSelect(session, FIELDS);
        this.selectBody = prepareSelect(session, BODY);
        this.cassandraConfiguration = cassandraConfiguration;
        this.selectAll = prepareSelectAll(session);
        this.cassandraUtils = cassandraUtils;
    }

    @VisibleForTesting
    public CassandraMessageDAO(Session session, CassandraTypesProvider typesProvider) {
        this(session, typesProvider, CassandraConfiguration.DEFAULT_CONFIGURATION, CassandraUtils.WITH_DEFAULT_CONFIGURATION);
    }

    private PreparedStatement prepareSelectAll(Session session) {
        return session.prepare(select().from(TABLE_NAME));
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

    public Stream<RawMessage> readAll() {
        return cassandraUtils.convertToStream(
            cassandraAsyncExecutor.execute(selectAll.bind().setFetchSize(cassandraConfiguration.getV1ReadFetchSize()))
                .join())
            .map(this::fromRow);
    }

    public CompletableFuture<Void> save(MailboxMessage message) throws MailboxException {
        try {
            CassandraMessageId messageId = (CassandraMessageId) message.getMessageId();
            BoundStatement boundStatement = insert.bind()
                .setUUID(MESSAGE_ID, messageId.get())
                .setTimestamp(INTERNAL_DATE, message.getInternalDate())
                .setInt(BODY_START_OCTET, (int) (message.getHeaderOctets()))
                .setLong(FULL_CONTENT_OCTETS, message.getFullContentOctets())
                .setLong(BODY_OCTECTS, message.getBodyOctets())
                .setBytes(BODY_CONTENT, toByteBuffer(message.getBodyContent()))
                .setBytes(HEADER_CONTENT, toByteBuffer(message.getHeaderContent()))
                .setList(PROPERTIES, message.getProperties().stream()
                    .map(x -> typesProvider.getDefinedUserType(PROPERTIES)
                        .newValue()
                        .setString(Properties.NAMESPACE, x.getNamespace())
                        .setString(Properties.NAME, x.getLocalName())
                        .setString(Properties.VALUE, x.getValue()))
                    .collect(Collectors.toList()))
                .setList(ATTACHMENTS, message.getAttachments().stream()
                    .map(this::toUDT)
                    .collect(Collectors.toList()));

            return cassandraAsyncExecutor.executeVoid(setTextualLineCount(boundStatement, message.getTextualLineCount()));

        } catch (IOException e) {
            throw new MailboxException("Error saving mail", e);
        }
    }

    private BoundStatement setTextualLineCount(BoundStatement boundStatement, Long textualLineCount) {
        return Optional.ofNullable(textualLineCount)
               .map(value -> boundStatement.setLong(TEXTUAL_LINE_COUNT, value))
               .orElseGet(() -> boundStatement.setToNull(TEXTUAL_LINE_COUNT));
    }

    private UDTValue toUDT(org.apache.james.mailbox.model.MessageAttachment messageAttachment) {
        return typesProvider.getDefinedUserType(ATTACHMENTS)
            .newValue()
            .setString(Attachments.ID, messageAttachment.getAttachmentId().getId())
            .setString(Attachments.NAME, messageAttachment.getName().orNull())
            .setString(Attachments.CID, messageAttachment.getCid().transform(Cid::getValue).orNull())
            .setBool(Attachments.IS_INLINE, messageAttachment.isInline());
    }

    private ByteBuffer toByteBuffer(InputStream stream) throws IOException {
        return ByteBuffer.wrap(ByteStreams.toByteArray(stream));
    }

    public CompletableFuture<Stream<Pair<MessageWithoutAttachment, Stream<MessageAttachmentRepresentation>>>> retrieveMessages(
        List<ComposedMessageIdWithMetaData> messageIds,
        FetchType fetchType,
        Limit limit
    ) {
        return CompletableFutureUtil.chainAll(
            limit.applyOnStream(messageIds.stream().distinct())
                .collect(JamesCollectors.chunker(cassandraConfiguration.getMessageReadChunkSize())),
            ids -> FluentFutureStream.of(
                ids.stream()
                    .map(id -> retrieveRow(id, fetchType)
                        .thenApply((ResultSet resultSet) ->
                            message(resultSet.one(), id, fetchType))))
                .completableFuture())
            .thenApply(stream -> stream.flatMap(Function.identity()));
    }

    private CompletableFuture<ResultSet> retrieveRow(ComposedMessageIdWithMetaData messageId, FetchType fetchType) {
        CassandraMessageId cassandraMessageId = (CassandraMessageId) messageId.getComposedMessageId().getMessageId();

        return cassandraAsyncExecutor.execute(retrieveSelect(fetchType)
            .bind()
            .setUUID(MESSAGE_ID, cassandraMessageId.get()));
    }

    private Pair<MessageWithoutAttachment, Stream<MessageAttachmentRepresentation>> message(Row row, ComposedMessageIdWithMetaData messageIdWithMetaData, FetchType fetchType) {
        ComposedMessageId messageId = messageIdWithMetaData.getComposedMessageId();

        MessageWithoutAttachment messageWithoutAttachment =
            new MessageWithoutAttachment(
                messageId.getMessageId(),
                row.getTimestamp(INTERNAL_DATE),
                row.getLong(FULL_CONTENT_OCTETS),
                row.getInt(BODY_START_OCTET),
                buildContent(row, fetchType),
                messageIdWithMetaData.getFlags(),
                retrievePropertyBuilder(row),
                messageId.getMailboxId(),
                messageId.getUid(),
                messageIdWithMetaData.getModSeq());
        return Pair.of(messageWithoutAttachment, retrieveAttachments(row, fetchType));
    }

    private PropertyBuilder retrievePropertyBuilder(Row row) {
        PropertyBuilder property = new PropertyBuilder(
            row.getList(PROPERTIES, UDTValue.class).stream()
                .map(x -> new SimpleProperty(x.getString(Properties.NAMESPACE), x.getString(Properties.NAME), x.getString(Properties.VALUE)))
                .collect(Collectors.toList()));
        property.setTextualLineCount(row.getLong(TEXTUAL_LINE_COUNT));
        return property;
    }

    private Stream<MessageAttachmentRepresentation> retrieveAttachments(Row row, FetchType fetchType) {
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

    private SharedByteArrayInputStream buildContent(Row row, FetchType fetchType) {
        switch (fetchType) {
            case Full:
                return new SharedByteArrayInputStream(getFullContent(row));
            case Headers:
                return new SharedByteArrayInputStream(getFieldContent(HEADER_CONTENT, row));
            case Body:
                return new SharedByteArrayInputStream(getBodyContent(row));
            case Metadata:
                return new SharedByteArrayInputStream(new byte[]{});
            default:
                throw new RuntimeException("Unknown FetchType " + fetchType);
        }
    }

    private byte[] getFullContent(Row row) {
        return Bytes.concat(getFieldContent(HEADER_CONTENT, row), getFieldContent(BODY_CONTENT, row));
    }

    private byte[] getBodyContent(Row row) {
        return Bytes.concat(new byte[row.getInt(BODY_START_OCTET)], getFieldContent(BODY_CONTENT, row));
    }

    private byte[] getFieldContent(String field, Row row) {
        byte[] headerContent = new byte[row.getBytes(field).remaining()];
        row.getBytes(field).get(headerContent);
        return headerContent;
    }

    private RawMessage fromRow(Row row) {
        return new RawMessage(
            row.getTimestamp(INTERNAL_DATE),
            new CassandraMessageId.Factory().of(row.getUUID(MESSAGE_ID)),
            row.getInt(BODY_START_OCTET),
            row.getLong(FULL_CONTENT_OCTETS),
            getFieldContent(BODY_CONTENT, row),
            getFieldContent(HEADER_CONTENT, row),
            retrievePropertyBuilder(row),
            row.getLong(TEXTUAL_LINE_COUNT),
            retrieveAttachments(row, FetchType.Full).collect(Guavate.toImmutableList()));
    }

    public static class RawMessage {
        private final Date internalDate;
        private final MessageId messageId;
        private final int bodyStartOctet;
        private final long fullContentOctet;
        private final byte[] bodyContent;
        private final byte[] headerContent;
        private final PropertyBuilder propertyBuilder;
        private final long textuaLineCount;
        private final List<MessageAttachmentRepresentation> attachments;

        private RawMessage(Date internalDate, MessageId messageId, int bodyStartOctet, long fullContentOctet, byte[] bodyContent,
                          byte[] headerContent, PropertyBuilder propertyBuilder, long textuaLineCount,
                          List<MessageAttachmentRepresentation> attachments) {
            this.internalDate = internalDate;
            this.messageId = messageId;
            this.bodyStartOctet = bodyStartOctet;
            this.fullContentOctet = fullContentOctet;
            this.bodyContent = bodyContent;
            this.headerContent = headerContent;
            this.propertyBuilder = propertyBuilder;
            this.textuaLineCount = textuaLineCount;
            this.attachments = attachments;
        }

        public Date getInternalDate() {
            return internalDate;
        }

        public MessageId getMessageId() {
            return messageId;
        }

        public int getBodyStartOctet() {
            return bodyStartOctet;
        }

        public long getFullContentOctet() {
            return fullContentOctet;
        }

        public byte[] getBodyContent() {
            return bodyContent;
        }

        public byte[] getHeaderContent() {
            return headerContent;
        }

        public PropertyBuilder getPropertyBuilder() {
            return propertyBuilder;
        }

        public long getTextuaLineCount() {
            return textuaLineCount;
        }

        public List<MessageAttachmentRepresentation> getAttachments() {
            return attachments;
        }
    }
}
