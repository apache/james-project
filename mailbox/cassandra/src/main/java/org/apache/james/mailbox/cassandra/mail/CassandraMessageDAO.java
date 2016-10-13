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
import static com.datastax.driver.core.querybuilder.QueryBuilder.set;
import static com.datastax.driver.core.querybuilder.QueryBuilder.update;
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
import static org.apache.james.mailbox.cassandra.table.CassandraMessageTable.MOD_SEQ;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageTable.PROPERTIES;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageTable.TABLE_NAME;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageTable.TEXTUAL_LINE_COUNT;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageTable.Flag.ANSWERED;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageTable.Flag.DELETED;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageTable.Flag.DRAFT;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageTable.Flag.FLAGGED;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageTable.Flag.RECENT;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageTable.Flag.SEEN;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageTable.Flag.USER;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageTable.Flag.USER_FLAGS;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.mail.Flags;
import javax.mail.Flags.Flag;
import javax.mail.util.SharedByteArrayInputStream;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.backends.cassandra.init.CassandraTypesProvider;
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.backends.cassandra.utils.CassandraConstants;
import org.apache.james.backends.cassandra.utils.CassandraUtils;
import org.apache.james.mailbox.cassandra.CassandraMessageId;
import org.apache.james.mailbox.cassandra.CassandraMessageId.Factory;
import org.apache.james.mailbox.cassandra.table.CassandraMessageTable;
import org.apache.james.mailbox.cassandra.table.CassandraMessageTable.Attachments;
import org.apache.james.mailbox.cassandra.table.CassandraMessageTable.Properties;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.model.Cid;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.MessageAttachment;
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
import com.github.fge.lambdas.Throwing;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import com.google.common.primitives.Bytes;

public class CassandraMessageDAO {

    private final CassandraAsyncExecutor cassandraAsyncExecutor;
    private final CassandraTypesProvider typesProvider;
    private final Factory messageIdFactory;
    private final CassandraMessageIdToImapUidDAO messageIdToImapUidDAO;
    private final PreparedStatement insert;
    private final PreparedStatement update;
    private final PreparedStatement delete;

    @Inject
    public CassandraMessageDAO(Session session, CassandraTypesProvider typesProvider, CassandraMessageId.Factory messageIdFactory, CassandraMessageIdToImapUidDAO messageIdToImapUidDAO) {
        this.cassandraAsyncExecutor = new CassandraAsyncExecutor(session);
        this.typesProvider = typesProvider;
        this.messageIdFactory = messageIdFactory;
        this.messageIdToImapUidDAO = messageIdToImapUidDAO;
        this.insert = prepareInsert(session);
        this.update = prepareUpdate(session);
        this.delete = prepareDelete(session);
    }

    private PreparedStatement prepareInsert(Session session) {
        return session.prepare(insertInto(TABLE_NAME)
                .value(MESSAGE_ID, bindMarker(MESSAGE_ID))
                .value(MOD_SEQ, bindMarker(MOD_SEQ))
                .value(INTERNAL_DATE, bindMarker(INTERNAL_DATE))
                .value(BODY_START_OCTET, bindMarker(BODY_START_OCTET))
                .value(FULL_CONTENT_OCTETS, bindMarker(FULL_CONTENT_OCTETS))
                .value(BODY_OCTECTS, bindMarker(BODY_OCTECTS))
                .value(ANSWERED, bindMarker(ANSWERED))
                .value(DELETED, bindMarker(DELETED))
                .value(DRAFT, bindMarker(DRAFT))
                .value(FLAGGED, bindMarker(FLAGGED))
                .value(RECENT, bindMarker(RECENT))
                .value(SEEN, bindMarker(SEEN))
                .value(USER, bindMarker(USER))
                .value(USER_FLAGS, bindMarker(USER_FLAGS))
                .value(BODY_CONTENT, bindMarker(BODY_CONTENT))
                .value(HEADER_CONTENT, bindMarker(HEADER_CONTENT))
                .value(PROPERTIES, bindMarker(PROPERTIES))
                .value(TEXTUAL_LINE_COUNT, bindMarker(TEXTUAL_LINE_COUNT))
                .value(ATTACHMENTS, bindMarker(ATTACHMENTS)));
    }

    private PreparedStatement prepareUpdate(Session session) {
        return session.prepare(update(TABLE_NAME)
                .with(set(ANSWERED, bindMarker(ANSWERED)))
                .and(set(DELETED, bindMarker(DELETED)))
                .and(set(DRAFT, bindMarker(DRAFT)))
                .and(set(FLAGGED, bindMarker(FLAGGED)))
                .and(set(RECENT, bindMarker(RECENT)))
                .and(set(SEEN, bindMarker(SEEN)))
                .and(set(USER, bindMarker(USER)))
                .and(set(USER_FLAGS, bindMarker(USER_FLAGS)))
                .and(set(MOD_SEQ, bindMarker(MOD_SEQ)))
                .where(eq(MESSAGE_ID, bindMarker(MESSAGE_ID)))
                .onlyIf(eq(MOD_SEQ, bindMarker(MOD_SEQ))));
    }

    private PreparedStatement prepareDelete(Session session) {
        return session.prepare(QueryBuilder.delete()
                .from(TABLE_NAME)
                .where(eq(MESSAGE_ID, bindMarker(MESSAGE_ID))));
    }

    public CompletableFuture<Void> save(Mailbox mailbox, MailboxMessage message) throws MailboxException {
        try {
            CassandraMessageId messageId = (CassandraMessageId) message.getMessageId();
            BoundStatement boundStatement = insert.bind()
                .setUUID(MESSAGE_ID, messageId.get())
                .setLong(MOD_SEQ, message.getModSeq())
                .setDate(INTERNAL_DATE, message.getInternalDate())
                .setInt(BODY_START_OCTET, (int) (message.getFullContentOctets() - message.getBodyOctets()))
                .setLong(FULL_CONTENT_OCTETS, message.getFullContentOctets())
                .setLong(BODY_OCTECTS, message.getBodyOctets())
                .setBool(ANSWERED, message.isAnswered())
                .setBool(DELETED, message.isDeleted())
                .setBool(DRAFT, message.isDraft())
                .setBool(FLAGGED, message.isFlagged())
                .setBool(RECENT, message.isRecent())
                .setBool(SEEN, message.isSeen())
                .setBool(USER, message.createFlags().contains(Flag.USER))
                .setSet(USER_FLAGS, userFlagsSet(message))
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

    private UDTValue toUDT(MessageAttachment messageAttachment) {
        return typesProvider.getDefinedUserType(ATTACHMENTS)
            .newValue()
            .setString(Attachments.ID, messageAttachment.getAttachmentId().getId())
            .setString(Attachments.NAME, messageAttachment.getName().orNull())
            .setString(Attachments.CID, messageAttachment.getCid().transform(Cid::getValue).orNull())
            .setBool(Attachments.IS_INLINE, messageAttachment.isInline());
    }

    private Set<String> userFlagsSet(MailboxMessage message) {
        return Arrays.stream(message.createFlags().getUserFlags()).collect(Collectors.toSet());
    }

    private ByteBuffer toByteBuffer(InputStream stream) throws IOException {
        return ByteBuffer.wrap(ByteStreams.toByteArray(stream));
    }
    
    public CompletableFuture<Boolean> conditionalSave(MailboxMessage message, long oldModSeq) {
        CassandraMessageId messageId = (CassandraMessageId) message.getMessageId();
        return cassandraAsyncExecutor.executeSingleRow(update.bind()
                .setBool(ANSWERED, message.isAnswered())
                .setBool(DELETED, message.isDeleted())
                .setBool(DRAFT, message.isDraft())
                .setBool(FLAGGED, message.isFlagged())
                .setBool(RECENT, message.isRecent())
                .setBool(SEEN, message.isSeen())
                .setBool(USER, message.createFlags().contains(Flag.USER))
                .setSet(USER_FLAGS, userFlagsSet(message))
                .setLong(MOD_SEQ, message.getModSeq())
                .setUUID(MESSAGE_ID, messageId.get())
                .setLong(MOD_SEQ, oldModSeq))
            .thenApply(optional -> optional
                    .map(row -> row.getBool(CassandraConstants.LIGHTWEIGHT_TRANSACTION_APPLIED))
                    .orElse(false));
    }
    
    public CompletableFuture<Stream<Pair<MailboxMessage, Stream<MessageAttachmentById>>>> retrieveMessages(List<CassandraMessageId> messageIds, FetchType fetchType, Optional<Integer> limit) {
        return retrieveRows(messageIds, fetchType, limit)
                .thenApply(resultSet -> CassandraUtils.convertToStream(resultSet)
                        .map(row -> message(row, fetchType)));
    }

    private CompletableFuture<ResultSet> retrieveRows(List<CassandraMessageId> messageIds, FetchType fetchType, Optional<Integer> limit) {
        return cassandraAsyncExecutor.execute(
                buildSelectQueryWithLimit(
                        buildQuery(messageIds, fetchType), 
                        limit)
                );
    }
    
    private Where buildQuery(List<CassandraMessageId> messageIds, FetchType fetchType) {
        return select(retrieveFields(fetchType))
                .from(TABLE_NAME)
                .where(in(MESSAGE_ID, messageIds.stream()
                        .map(CassandraMessageId::get)
                        .collect(Collectors.toList())));
    }

    private Pair<MailboxMessage, Stream<MessageAttachmentById>> message(Row row, FetchType fetchType) {
        try {
            ComposedMessageId messageId = retrieveComposedMessageId(messageIdFactory.of(row.getUUID(MESSAGE_ID))).join();

            SimpleMailboxMessage message =
                    new SimpleMailboxMessage(
                            messageId.getMessageId(),
                            row.getDate(INTERNAL_DATE),
                            row.getLong(FULL_CONTENT_OCTETS),
                            row.getInt(BODY_START_OCTET),
                            buildContent(row, fetchType),
                            getFlags(row),
                            getPropertyBuilder(row),
                            messageId.getMailboxId(),
                            ImmutableList.of());
            message.setUid(messageId.getUid());
            message.setModSeq(row.getLong(MOD_SEQ));
            return Pair.of(message, getAttachments(row, fetchType));
        } catch (MailboxException e) {
            throw Throwables.propagate(e);
        }
    }


    private Flags getFlags(Row row) {
        Flags flags = new Flags();
        for (String flag : CassandraMessageTable.Flag.ALL) {
            if (row.getBool(flag)) {
                flags.add(CassandraMessageTable.Flag.JAVAX_MAIL_FLAG.get(flag));
            }
        }
        row.getSet(CassandraMessageTable.Flag.USER_FLAGS, String.class)
            .stream()
            .forEach(flags::add);
        return flags;
    }

    private PropertyBuilder getPropertyBuilder(Row row) {
        PropertyBuilder property = new PropertyBuilder(
            row.getList(PROPERTIES, UDTValue.class).stream()
                .map(x -> new SimpleProperty(x.getString(Properties.NAMESPACE), x.getString(Properties.NAME), x.getString(Properties.VALUE)))
                .collect(Collectors.toList()));
        property.setTextualLineCount(row.getLong(TEXTUAL_LINE_COUNT));
        return property;
    }

    private Stream<MessageAttachmentById> getAttachments(Row row, FetchType fetchType) {
        switch (fetchType) {
        case Full:
        case Body:
            List<UDTValue> udtValues = row.getList(ATTACHMENTS, UDTValue.class);

            return attachmentByIds(udtValues);
        default:
            return Stream.of();
        }
    }

    private Stream<MessageAttachmentById> attachmentByIds(List<UDTValue> udtValues) {
        return udtValues.stream()
            .map(this::messageAttachmentByIdFrom);
    }

    private MessageAttachmentById messageAttachmentByIdFrom(UDTValue udtValue) {
        return MessageAttachmentById.builder()
                .attachmentId(AttachmentId.from(udtValue.getString(Attachments.ID)))
                .name(udtValue.getString(Attachments.NAME))
                .cid(Optional.ofNullable(udtValue.getString(Attachments.CID)).map(Cid::from))
                .isInline(udtValue.getBool(Attachments.IS_INLINE))
                .build();
    }

    private CompletableFuture<ComposedMessageId> retrieveComposedMessageId(CassandraMessageId messageId) throws MailboxException {
        return messageIdToImapUidDAO.retrieve(messageId, Optional.empty())
                .thenApply(Throwing.function(stream -> {
                    return stream.findFirst()
                        .orElseThrow(() -> new MailboxException("Message not found: " + messageId));
                }));
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
}
