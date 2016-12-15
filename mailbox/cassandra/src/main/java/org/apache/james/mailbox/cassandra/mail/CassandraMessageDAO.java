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
import static com.datastax.driver.core.querybuilder.QueryBuilder.in;
import static com.datastax.driver.core.querybuilder.QueryBuilder.insertInto;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageIds.MESSAGE_ID;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageTable.ATTACHMENTS;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageTable.BODY;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageTable.BODY_CONTENT;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageTable.BODY_OCTECTS;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageTable.BODY_START_OCTET;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageTable.FIELDS;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageTable.FULL_CONTENT_OCTETS;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageTable.HEADERS;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageTable.HEADER_CONTENT;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageTable.INTERNAL_DATE;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageTable.METADATA;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageTable.PROPERTIES;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageTable.TABLE_NAME;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageTable.TEXTUAL_LINE_COUNT;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.mail.util.SharedByteArrayInputStream;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.backends.cassandra.init.CassandraTypesProvider;
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.backends.cassandra.utils.CassandraUtils;
import org.apache.james.mailbox.cassandra.CassandraMessageId;
import org.apache.james.mailbox.cassandra.CassandraMessageId.Factory;
import org.apache.james.mailbox.cassandra.table.CassandraMessageTable.Attachments;
import org.apache.james.mailbox.cassandra.table.CassandraMessageTable.Properties;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.model.Cid;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.mailbox.store.mail.MessageMapper.FetchType;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.apache.james.mailbox.store.mail.model.impl.SimpleProperty;

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.Statement;
import com.datastax.driver.core.UDTValue;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.datastax.driver.core.querybuilder.Select;
import com.datastax.driver.core.querybuilder.Select.Where;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import com.google.common.primitives.Bytes;

public class CassandraMessageDAO {

    private final CassandraAsyncExecutor cassandraAsyncExecutor;
    private final CassandraTypesProvider typesProvider;
    private final Factory messageIdFactory;
    private final PreparedStatement insert;
    private final PreparedStatement delete;

    @Inject
    public CassandraMessageDAO(Session session, CassandraTypesProvider typesProvider, CassandraMessageId.Factory messageIdFactory) {
        this.cassandraAsyncExecutor = new CassandraAsyncExecutor(session);
        this.typesProvider = typesProvider;
        this.messageIdFactory = messageIdFactory;
        this.insert = prepareInsert(session);
        this.delete = prepareDelete(session);
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
        try {
            CassandraMessageId messageId = (CassandraMessageId) message.getMessageId();
            BoundStatement boundStatement = insert.bind()
                .setUUID(MESSAGE_ID, messageId.get())
                .setDate(INTERNAL_DATE, message.getInternalDate())
                .setInt(BODY_START_OCTET, (int) (message.getFullContentOctets() - message.getBodyOctets()))
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

    public CompletableFuture<Stream<Pair<MailboxMessage, Stream<MessageAttachmentRepresentation>>>> retrieveMessages(List<ComposedMessageIdWithMetaData> messageIds, FetchType fetchType, Optional<Integer> limit) {
        return retrieveRows(messageIds, fetchType, limit)
                .thenApply(resultSet -> CassandraUtils.convertToStream(resultSet)
                        .map(row -> message(row, messageIds, fetchType)));
    }

    private CompletableFuture<ResultSet> retrieveRows(List<ComposedMessageIdWithMetaData> messageIds, FetchType fetchType, Optional<Integer> limit) {
        return cassandraAsyncExecutor.execute(
                buildSelectQueryWithLimit(
                        buildQuery(messageIds, fetchType),
                        limit)
                );
    }
    
    private Where buildQuery(List<ComposedMessageIdWithMetaData> messageIds, FetchType fetchType) {
        return select(retrieveFields(fetchType))
                .from(TABLE_NAME)
                .where(in(MESSAGE_ID, messageIds.stream()
                        .map(ComposedMessageIdWithMetaData::getComposedMessageId)
                        .map(ComposedMessageId::getMessageId)
                        .map(messageId -> (CassandraMessageId) messageId)
                        .map(CassandraMessageId::get)
                        .collect(Collectors.toList())));
    }

    private Pair<MailboxMessage, Stream<MessageAttachmentRepresentation>> message(Row row, List<ComposedMessageIdWithMetaData> messageIds, FetchType fetchType) {
        try {
            ComposedMessageIdWithMetaData messageIdWithMetaData = retrieveComposedMessageId(messageIdFactory.of(row.getUUID(MESSAGE_ID)), messageIds);
            ComposedMessageId messageId = messageIdWithMetaData.getComposedMessageId();

            SimpleMailboxMessage message =
                    new SimpleMailboxMessage(
                            messageId.getMessageId(),
                            row.getDate(INTERNAL_DATE),
                            row.getLong(FULL_CONTENT_OCTETS),
                            row.getInt(BODY_START_OCTET),
                            buildContent(row, fetchType),
                            messageIdWithMetaData.getFlags(),
                            getPropertyBuilder(row),
                            messageId.getMailboxId(),
                            ImmutableList.of());
            message.setUid(messageId.getUid());
            message.setModSeq(messageIdWithMetaData.getModSeq());
            return Pair.of(message, getAttachments(row, fetchType));
        } catch (MailboxException e) {
            throw Throwables.propagate(e);
        }
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

    private ComposedMessageIdWithMetaData retrieveComposedMessageId(CassandraMessageId messageId, List<ComposedMessageIdWithMetaData> messageIds) throws MailboxException {
        return messageIds.stream()
            .filter(composedMessage -> composedMessage.isMatching(messageId))
            .findFirst()
            .orElseThrow(() -> new MailboxException("Message not found: " + messageId));
    }

    private String[] retrieveFields(FetchType fetchType) {
        switch (fetchType) {
            case Body:
                return BODY;
            case Full:
                return FIELDS;
            case Headers:
                return HEADERS;
            case Metadata:
                return METADATA;
            default:
                throw new RuntimeException("Unknown FetchType " + fetchType);
        }
    }

    private Statement buildSelectQueryWithLimit(Select.Where selectStatement, Optional<Integer> limit) {
        if (!limit.isPresent() || limit.get() <= 0) {
            return selectStatement;
        }
        return selectStatement.limit(limit.get());
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

    static class MessageAttachmentRepresentation {

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {

            private AttachmentId attachmentId;
            private Optional<String> name;
            private Optional<Cid> cid;
            private Optional<Boolean> isInline;

            private Builder() {
                name = Optional.empty();
                cid = Optional.empty();
                isInline = Optional.empty();
            }

            public Builder attachmentId(AttachmentId attachmentId) {
                Preconditions.checkArgument(attachmentId != null);
                this.attachmentId = attachmentId;
                return this;
            }

            public Builder name(String name) {
                this.name = Optional.ofNullable(name);
                return this;
            }

            public Builder cid(Optional<Cid> cid) {
                Preconditions.checkNotNull(cid);
                this.cid = cid;
                return this;
            }


            public Builder cid(Cid cid) {
                this.cid = Optional.ofNullable(cid);
                return this;
            }

            public Builder isInline(boolean isInline) {
                this.isInline = Optional.of(isInline);
                return this;
            }

            public MessageAttachmentRepresentation build() {
                Preconditions.checkState(attachmentId != null, "'attachmentId' is mandatory");
                boolean builtIsInLine = isInline.orElse(false);
                if (builtIsInLine && !cid.isPresent()) {
                    throw new IllegalStateException("'cid' is mandatory for inline attachments");
                }
                return new MessageAttachmentRepresentation(attachmentId, name, cid, builtIsInLine);
            }
        }

        private final AttachmentId attachmentId;
        private final Optional<String> name;
        private final Optional<Cid> cid;
        private final boolean isInline;

        @VisibleForTesting
        MessageAttachmentRepresentation(AttachmentId attachmentId, Optional<String> name, Optional<Cid> cid, boolean isInline) {
            this.attachmentId = attachmentId;
            this.name = name;
            this.cid = cid;
            this.isInline = isInline;
        }

        public AttachmentId getAttachmentId() {
            return attachmentId;
        }

        public Optional<String> getName() {
            return name;
        }

        public Optional<Cid> getCid() {
            return cid;
        }

        public boolean isInline() {
            return isInline;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof MessageAttachmentRepresentation) {
                MessageAttachmentRepresentation other = (MessageAttachmentRepresentation) obj;
                return Objects.equal(attachmentId, other.attachmentId)
                    && Objects.equal(name, other.name)
                    && Objects.equal(cid, other.cid)
                    && Objects.equal(isInline, other.isInline);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(attachmentId, name, cid, isInline);
        }

        @Override
        public String toString() {
            return MoreObjects
                    .toStringHelper(this)
                    .add("attachmentId", attachmentId)
                    .add("name", name)
                    .add("cid", cid)
                    .add("isInline", isInline)
                    .toString();
        }
    }
}
