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

import static com.datastax.driver.core.querybuilder.QueryBuilder.decr;
import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static com.datastax.driver.core.querybuilder.QueryBuilder.gte;
import static com.datastax.driver.core.querybuilder.QueryBuilder.incr;
import static com.datastax.driver.core.querybuilder.QueryBuilder.insertInto;
import static com.datastax.driver.core.querybuilder.QueryBuilder.lte;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;
import static com.datastax.driver.core.querybuilder.QueryBuilder.set;
import static com.datastax.driver.core.querybuilder.QueryBuilder.update;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageTable.ATTACHMENTS_IDS;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageTable.BODY;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageTable.BODY_CONTENT;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageTable.BODY_OCTECTS;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageTable.BODY_START_OCTET;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageTable.FIELDS;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageTable.FULL_CONTENT_OCTETS;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageTable.HEADERS;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageTable.HEADER_CONTENT;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageTable.IMAP_UID;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageTable.INTERNAL_DATE;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageTable.MAILBOX_ID;
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
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.mail.Flags;
import javax.mail.Flags.Flag;
import javax.mail.util.SharedByteArrayInputStream;

import org.apache.james.backends.cassandra.init.CassandraTypesProvider;
import org.apache.james.backends.cassandra.utils.CassandraConstants;
import org.apache.james.backends.cassandra.utils.CassandraUtils;
import org.apache.james.backends.cassandra.utils.FunctionRunnerWithRetry;
import org.apache.james.mailbox.FlagsBuilder;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.cassandra.CassandraId;
import org.apache.james.mailbox.cassandra.mail.utils.MessageDeletedDuringFlagsUpdateException;
import org.apache.james.mailbox.cassandra.table.CassandraMailboxCountersTable;
import org.apache.james.mailbox.cassandra.table.CassandraMessageTable;
import org.apache.james.mailbox.cassandra.table.CassandraMessageTable.Properties;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.store.FlagsUpdateCalculator;
import org.apache.james.mailbox.store.SimpleMessageMetaData;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.ModSeqProvider;
import org.apache.james.mailbox.store.mail.UidProvider;
import org.apache.james.mailbox.store.mail.model.AttachmentId;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.apache.james.mailbox.store.mail.model.impl.SimpleProperty;

import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.Statement;
import com.datastax.driver.core.UDTValue;
import com.datastax.driver.core.querybuilder.Assignment;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.datastax.driver.core.querybuilder.Select;
import com.datastax.driver.core.querybuilder.Select.Where;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import com.google.common.primitives.Bytes;

public class CassandraMessageMapper implements MessageMapper {

    private final Session session;
    private final ModSeqProvider modSeqProvider;
    private final MailboxSession mailboxSession;
    private final UidProvider uidProvider;
    private final CassandraTypesProvider typesProvider;
    private final int maxRetries;

    public CassandraMessageMapper(Session session, UidProvider uidProvider, ModSeqProvider modSeqProvider, MailboxSession mailboxSession, int maxRetries, CassandraTypesProvider typesProvider) {
        this.session = session;
        this.uidProvider = uidProvider;
        this.modSeqProvider = modSeqProvider;
        this.mailboxSession = mailboxSession;
        this.maxRetries = maxRetries;
        this.typesProvider = typesProvider;
    }

    @Override
    public long countMessagesInMailbox(Mailbox mailbox) throws MailboxException {
        CassandraId mailboxId = (CassandraId) mailbox.getMailboxId();
        ResultSet results = session.execute(
            select(CassandraMailboxCountersTable.COUNT)
                .from(CassandraMailboxCountersTable.TABLE_NAME)
                .where(eq(CassandraMailboxCountersTable.MAILBOX_ID, mailboxId.asUuid())));
        return results.isExhausted() ? 0 : results.one().getLong(CassandraMailboxCountersTable.COUNT);
    }

    @Override
    public long countUnseenMessagesInMailbox(Mailbox mailbox) throws MailboxException {
        CassandraId mailboxId = (CassandraId) mailbox.getMailboxId();
        ResultSet results = session.execute(
            select(CassandraMailboxCountersTable.UNSEEN)
                .from(CassandraMailboxCountersTable.TABLE_NAME)
                .where(eq(CassandraMailboxCountersTable.MAILBOX_ID, mailboxId.asUuid())));
        if (!results.isExhausted()) {
            Row row = results.one();
            if (row.getColumnDefinitions().contains(CassandraMailboxCountersTable.UNSEEN)) {
                return row.getLong(CassandraMailboxCountersTable.UNSEEN);
            }
        }
        return 0;
    }

    @Override
    public void delete(Mailbox mailbox, MailboxMessage message) {
        deleteUsingMailboxId(((CassandraId) mailbox.getMailboxId()), message);
    }

    private void deleteUsingMailboxId(CassandraId mailboxId, MailboxMessage message) {
        session.execute(
            QueryBuilder.delete()
                .from(TABLE_NAME)
                .where(eq(MAILBOX_ID, mailboxId.asUuid()))
                .and(eq(IMAP_UID, message.getUid())));
        decrementCount(mailboxId);
        if (!message.isSeen()) {
            decrementUnseen(mailboxId);
        }
    }

    @Override
    public Iterator<MailboxMessage> findInMailbox(Mailbox mailbox, MessageRange set, FetchType ftype, int max) throws MailboxException {
        CassandraId mailboxId = (CassandraId) mailbox.getMailboxId();
        return CassandraUtils.convertToStream(session.execute(buildSelectQueryWithLimit(buildQuery(mailboxId, set, ftype), max)))
            .map(row -> message(row, ftype))
            .sorted(Comparator.comparingLong(MailboxMessage::getUid))
            .iterator();
    }

    private Statement buildSelectQueryWithLimit(Select.Where selectStatement, int max) {
        if (max <= 0) {
            return selectStatement;
        }
        return selectStatement.limit(max);
    }

    @Override
    public List<Long> findRecentMessageUidsInMailbox(Mailbox mailbox) throws MailboxException {
        CassandraId mailboxId = (CassandraId) mailbox.getMailboxId();
        return CassandraUtils.convertToStream(session.execute(selectAll(mailboxId, FetchType.Metadata).and((eq(RECENT, true)))))
            .map((row) -> row.getLong(IMAP_UID))
            .sorted()
            .collect(Collectors.toList());
    }

    @Override
    public Long findFirstUnseenMessageUid(Mailbox mailbox) throws MailboxException {
        CassandraId mailboxId = (CassandraId) mailbox.getMailboxId();
        return CassandraUtils.convertToStream(session.execute(selectAll(mailboxId, FetchType.Metadata).and((eq(SEEN, false)))))
            .map((row) -> row.getLong(IMAP_UID))
            .sorted()
            .findFirst()
            .orElse(null);
    }

    @Override
    public Map<Long, MessageMetaData> expungeMarkedForDeletionInMailbox(Mailbox mailbox, MessageRange set) throws MailboxException {
        CassandraId mailboxId = (CassandraId) mailbox.getMailboxId();
        return CassandraUtils.convertToStream(session.execute(buildQuery(mailboxId, set, FetchType.Metadata).and(eq(DELETED, true))))
            .map(row -> message(row, FetchType.Metadata))
            .peek((message) -> delete(mailbox, message))
            .collect(Collectors.toMap(MailboxMessage::getUid, SimpleMessageMetaData::new));
    }

    @Override
    public MessageMetaData move(Mailbox destinationMailbox, MailboxMessage original) throws MailboxException {
        MessageMetaData messageMetaData = copy(destinationMailbox, original);
        deleteUsingMailboxId((CassandraId) original.getMailboxId(), original);
        return messageMetaData;
    }

    @Override
    public void endRequest() {
        // Do nothing
    }

    @Override
    public long getHighestModSeq(Mailbox mailbox) throws MailboxException {
        return modSeqProvider.highestModSeq(mailboxSession, mailbox);
    }

    @Override
    public MessageMetaData add(Mailbox mailbox, MailboxMessage message) throws MailboxException {
        message.setUid(uidProvider.nextUid(mailboxSession, mailbox));
        message.setModSeq(modSeqProvider.nextModSeq(mailboxSession, mailbox));
        MessageMetaData messageMetaData = save(mailbox, message);
        CassandraId mailboxId = (CassandraId) mailbox.getMailboxId();
        if (!message.isSeen()) {
            incrementUnseen(mailboxId);
        }
        incrementCount(mailboxId);
        return messageMetaData;
    }

    @Override
    public Iterator<UpdatedFlags> updateFlags(Mailbox mailbox, FlagsUpdateCalculator flagUpdateCalculator, MessageRange set) throws MailboxException {
        CassandraId mailboxId = (CassandraId) mailbox.getMailboxId();
        return CassandraUtils.convertToStream(session.execute(buildQuery(mailboxId, set, FetchType.Metadata)))
            .map((row) -> updateFlagsOnMessage(mailbox, flagUpdateCalculator, row))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .peek((updatedFlags) -> manageUnseenMessageCounts(mailbox, updatedFlags.getOldFlags(), updatedFlags.getNewFlags()))
            .collect(Collectors.toList()) // This collect is here as we need to consume all the stream before returning result
            .iterator();
    }

    @Override
    public <T> T execute(Transaction<T> transaction) throws MailboxException {
        return transaction.run();
    }

    @Override
    public MessageMetaData copy(Mailbox mailbox, MailboxMessage original) throws MailboxException {
        original.setFlags(new FlagsBuilder().add(original.createFlags()).add(Flag.RECENT).build());
        return add(mailbox, original);
    }

    @Override
    public long getLastUid(Mailbox mailbox) throws MailboxException {
        return uidProvider.lastUid(mailboxSession, mailbox);
    }

    private void decrementCount(CassandraId mailboxId) {
        updateMailbox(mailboxId, decr(CassandraMailboxCountersTable.COUNT));
    }

    private void incrementCount(CassandraId mailboxId) {
        updateMailbox(mailboxId, incr(CassandraMailboxCountersTable.COUNT));
    }

    private void decrementUnseen(CassandraId mailboxId) {
        updateMailbox(mailboxId, decr(CassandraMailboxCountersTable.UNSEEN));
    }

    private void incrementUnseen(CassandraId mailboxId) {
        updateMailbox(mailboxId, incr(CassandraMailboxCountersTable.UNSEEN));
    }

    private void updateMailbox(CassandraId mailboxId, Assignment operation) {
        session.execute(update(CassandraMailboxCountersTable.TABLE_NAME).with(operation).where(eq(CassandraMailboxCountersTable.MAILBOX_ID, mailboxId.asUuid())));
    }

    private MailboxMessage message(Row row, FetchType fetchType) {
        SimpleMailboxMessage message =
            new SimpleMailboxMessage(
                row.getDate(INTERNAL_DATE),
                row.getLong(FULL_CONTENT_OCTETS),
                row.getInt(BODY_START_OCTET),
                buildContent(row, fetchType),
                getFlags(row),
                getPropertyBuilder(row),
                CassandraId.of(row.getUUID(MAILBOX_ID)),
                getAttachmentsIds(row, fetchType));
        message.setUid(row.getLong(IMAP_UID));
        message.setModSeq(row.getLong(MOD_SEQ));
        return message;
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

    private List<AttachmentId> getAttachmentsIds(Row row, FetchType fetchType) {
        switch (fetchType) {
        case Full:
        case Body:
            return row.getList(ATTACHMENTS_IDS, String.class)
                    .stream()
                    .map(AttachmentId::from)
                    .collect(org.apache.james.util.streams.Collectors.toImmutableList());
        default:
            return ImmutableList.of();
        }
    }

    private MessageMetaData save(Mailbox mailbox, MailboxMessage message) throws MailboxException {
        try {
            CassandraId mailboxId = (CassandraId) mailbox.getMailboxId();
            session.execute(insertInto(TABLE_NAME)
                .value(MAILBOX_ID, mailboxId.asUuid())
                .value(IMAP_UID, message.getUid())
                .value(MOD_SEQ, message.getModSeq())
                .value(INTERNAL_DATE, message.getInternalDate())
                .value(BODY_START_OCTET, message.getFullContentOctets() - message.getBodyOctets())
                .value(FULL_CONTENT_OCTETS, message.getFullContentOctets())
                .value(BODY_OCTECTS, message.getBodyOctets())
                .value(ANSWERED, message.isAnswered())
                .value(DELETED, message.isDeleted())
                .value(DRAFT, message.isDraft())
                .value(FLAGGED, message.isFlagged())
                .value(RECENT, message.isRecent())
                .value(SEEN, message.isSeen())
                .value(USER, message.createFlags().contains(Flag.USER))
                .value(USER_FLAGS, userFlagsSet(message))
                .value(BODY_CONTENT, toByteBuffer(message.getBodyContent()))
                .value(HEADER_CONTENT, toByteBuffer(message.getHeaderContent()))
                .value(PROPERTIES, message.getProperties().stream()
                    .map(x -> typesProvider.getDefinedUserType(PROPERTIES)
                        .newValue()
                        .setString(Properties.NAMESPACE, x.getNamespace())
                        .setString(Properties.NAME, x.getLocalName())
                        .setString(Properties.VALUE, x.getValue()))
                    .collect(Collectors.toList()))
                .value(TEXTUAL_LINE_COUNT, message.getTextualLineCount())
                .value(ATTACHMENTS_IDS, message.getAttachmentsIds().stream()
                    .map(AttachmentId::getId)
                    .collect(Collectors.toList())));

            return new SimpleMessageMetaData(message);
        } catch (IOException e) {
            throw new MailboxException("Error saving mail", e);
        }
    }

    private Set<String> userFlagsSet(MailboxMessage message) {
        return Arrays.stream(message.createFlags().getUserFlags()).collect(Collectors.toSet());
    }

    private void manageUnseenMessageCounts(Mailbox mailbox, Flags oldFlags, Flags newFlags) {
        CassandraId mailboxId = (CassandraId) mailbox.getMailboxId();
        if (oldFlags.contains(Flag.SEEN) && !newFlags.contains(Flag.SEEN)) {
            incrementUnseen(mailboxId);
        }
        if (!oldFlags.contains(Flag.SEEN) && newFlags.contains(Flag.SEEN)) {
            decrementUnseen(mailboxId);
        }
    }

    private Optional<UpdatedFlags> updateFlagsOnMessage(Mailbox mailbox, FlagsUpdateCalculator flagUpdateCalculator, Row row) {
        return tryMessageFlagsUpdate(flagUpdateCalculator, mailbox, message(row, FetchType.Metadata))
            .map(Optional::of)
            .orElse(handleRetries(mailbox, flagUpdateCalculator, row.getLong(IMAP_UID)));
    }

    private Optional<UpdatedFlags> tryMessageFlagsUpdate(FlagsUpdateCalculator flagUpdateCalculator, Mailbox mailbox, MailboxMessage message) {
        try {
            long oldModSeq = message.getModSeq();
            Flags oldFlags = message.createFlags();
            Flags newFlags = flagUpdateCalculator.buildNewFlags(oldFlags);
            message.setFlags(newFlags);
            message.setModSeq(modSeqProvider.nextModSeq(mailboxSession, mailbox));
            if (conditionalSave(message, oldModSeq)) {
                return Optional.of(new UpdatedFlags(message.getUid(), message.getModSeq(), oldFlags, newFlags));
            } else {
                return Optional.empty();
            }
        } catch (MailboxException e) {
            throw Throwables.propagate(e);
        }
    }

    private Optional<UpdatedFlags> handleRetries(Mailbox mailbox, FlagsUpdateCalculator flagUpdateCalculator, long uid) {
        try {
            return Optional.of(
                new FunctionRunnerWithRetry(maxRetries)
                    .executeAndRetrieveObject(() -> retryMessageFlagsUpdate(mailbox, uid, flagUpdateCalculator)));
        } catch (MessageDeletedDuringFlagsUpdateException e) {
            mailboxSession.getLog().warn(e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    private Optional<UpdatedFlags> retryMessageFlagsUpdate(Mailbox mailbox, long uid, FlagsUpdateCalculator flagUpdateCalculator) {
        CassandraId mailboxId = (CassandraId) mailbox.getMailboxId();
        return tryMessageFlagsUpdate(flagUpdateCalculator,
            mailbox,
            message(Optional.ofNullable(session.execute(selectMessage(mailboxId, uid, FetchType.Metadata)).one())
                .orElseThrow(() -> new MessageDeletedDuringFlagsUpdateException(mailboxId, uid)),
                FetchType.Metadata));
    }

    private boolean conditionalSave(MailboxMessage message, long oldModSeq) {
        CassandraId mailboxId = (CassandraId) message.getMailboxId();
        ResultSet resultSet = session.execute(
            update(TABLE_NAME)
                .with(set(ANSWERED, message.isAnswered()))
                .and(set(DELETED, message.isDeleted()))
                .and(set(DRAFT, message.isDraft()))
                .and(set(FLAGGED, message.isFlagged()))
                .and(set(RECENT, message.isRecent()))
                .and(set(SEEN, message.isSeen()))
                .and(set(USER, message.createFlags().contains(Flag.USER)))
                .and(set(USER_FLAGS, userFlagsSet(message)))
                .and(set(MOD_SEQ, message.getModSeq()))
                .where(eq(IMAP_UID, message.getUid()))
                .and(eq(MAILBOX_ID, mailboxId.asUuid()))
                .onlyIf(eq(MOD_SEQ, oldModSeq)));
        return resultSet.one().getBool(CassandraConstants.LIGHTWEIGHT_TRANSACTION_APPLIED);
    }

    private ByteBuffer toByteBuffer(InputStream stream) throws IOException {
        return ByteBuffer.wrap(ByteStreams.toByteArray(stream));
    }

    private Where buildQuery(CassandraId mailboxId, MessageRange set, FetchType fetchType) {
        switch (set.getType()) {
        case ALL:
            return selectAll(mailboxId, fetchType);
        case FROM:
            return selectFrom(mailboxId, set.getUidFrom(), fetchType);
        case RANGE:
            return selectRange(mailboxId, set.getUidFrom(), set.getUidTo(), fetchType);
        case ONE:
            return selectMessage(mailboxId, set.getUidFrom(), fetchType);
        }
        throw new UnsupportedOperationException();
    }

    private Where selectAll(CassandraId mailboxId, FetchType fetchType) {
        return select(retrieveFields(fetchType))
            .from(TABLE_NAME)
            .where(eq(MAILBOX_ID, mailboxId.asUuid()));
    }

    private Where selectFrom(CassandraId mailboxId, long uid, FetchType fetchType) {
        return select(retrieveFields(fetchType))
            .from(TABLE_NAME)
            .where(eq(MAILBOX_ID, mailboxId.asUuid()))
            .and(gte(IMAP_UID, uid));
    }

    private Where selectRange(CassandraId mailboxId, long from, long to, FetchType fetchType) {
        return select(retrieveFields(fetchType))
            .from(TABLE_NAME)
            .where(eq(MAILBOX_ID, mailboxId.asUuid()))
            .and(gte(IMAP_UID, from))
            .and(lte(IMAP_UID, to));
    }

    private Where selectMessage(CassandraId mailboxId, long uid, FetchType fetchType) {
        return select(retrieveFields(fetchType))
            .from(TABLE_NAME)
            .where(eq(MAILBOX_ID, mailboxId.asUuid()))
            .and(eq(IMAP_UID, uid));
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
