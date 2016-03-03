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
import static org.apache.james.mailbox.cassandra.table.CassandraMessageTable.Flag.USER_FLAGS;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageTable.Flag.RECENT;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageTable.Flag.SEEN;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageTable.Flag.USER;

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

import com.datastax.driver.core.Statement;
import com.datastax.driver.core.querybuilder.Select;
import com.google.common.base.Throwables;
import org.apache.james.backends.cassandra.init.CassandraTypesProvider;
import org.apache.james.backends.cassandra.utils.CassandraConstants;
import org.apache.james.backends.cassandra.utils.CassandraUtils;
import org.apache.james.mailbox.FlagsBuilder;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.cassandra.CassandraId;
import org.apache.james.backends.cassandra.utils.FunctionRunnerWithRetry;
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
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.apache.james.mailbox.store.mail.model.impl.SimpleProperty;

import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.UDTValue;
import com.datastax.driver.core.querybuilder.Assignment;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.datastax.driver.core.querybuilder.Select.Where;
import com.google.common.io.ByteStreams;
import com.google.common.primitives.Bytes;

public class CassandraMessageMapper implements MessageMapper<CassandraId> {

    private final Session session;
    private final ModSeqProvider<CassandraId> modSeqProvider;
    private final MailboxSession mailboxSession;
    private final UidProvider<CassandraId> uidProvider;
    private final CassandraTypesProvider typesProvider;
    private final int maxRetries;

    public CassandraMessageMapper(Session session, UidProvider<CassandraId> uidProvider, ModSeqProvider<CassandraId> modSeqProvider, MailboxSession mailboxSession, int maxRetries, CassandraTypesProvider typesProvider) {
        this.session = session;
        this.uidProvider = uidProvider;
        this.modSeqProvider = modSeqProvider;
        this.mailboxSession = mailboxSession;
        this.maxRetries = maxRetries;
        this.typesProvider = typesProvider;
    }

    @Override
    public long countMessagesInMailbox(Mailbox<CassandraId> mailbox) throws MailboxException {
        ResultSet results = session.execute(
            select(CassandraMailboxCountersTable.COUNT)
                .from(CassandraMailboxCountersTable.TABLE_NAME)
                .where(eq(CassandraMailboxCountersTable.MAILBOX_ID, mailbox.getMailboxId().asUuid())));
        return results.isExhausted() ? 0 : results.one().getLong(CassandraMailboxCountersTable.COUNT);
    }

    @Override
    public long countUnseenMessagesInMailbox(Mailbox<CassandraId> mailbox) throws MailboxException {
        ResultSet results = session.execute(
            select(CassandraMailboxCountersTable.UNSEEN)
                .from(CassandraMailboxCountersTable.TABLE_NAME)
                .where(eq(CassandraMailboxCountersTable.MAILBOX_ID, mailbox.getMailboxId().asUuid())));
        if (!results.isExhausted()) {
            Row row = results.one();
            if (row.getColumnDefinitions().contains(CassandraMailboxCountersTable.UNSEEN)) {
                return row.getLong(CassandraMailboxCountersTable.UNSEEN);
            }
        }
        return 0;
    }

    @Override
    public void delete(Mailbox<CassandraId> mailbox, MailboxMessage<CassandraId> message) {
        deleteUsingMailboxId(mailbox.getMailboxId(), message);
    }

    private void deleteUsingMailboxId(CassandraId mailboxId, MailboxMessage<CassandraId> message) {
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
    public Iterator<MailboxMessage<CassandraId>> findInMailbox(Mailbox<CassandraId> mailbox, MessageRange set, FetchType ftype, int max) throws MailboxException {
        return CassandraUtils.convertToStream(session.execute(buildSelectQueryWithLimit(buildQuery(mailbox, set, ftype), max)))
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
    public List<Long> findRecentMessageUidsInMailbox(Mailbox<CassandraId> mailbox) throws MailboxException {
        return CassandraUtils.convertToStream(session.execute(selectAll(mailbox, FetchType.Metadata).and((eq(RECENT, true)))))
            .map((row) -> row.getLong(IMAP_UID))
            .sorted()
            .collect(Collectors.toList());
    }

    @Override
    public Long findFirstUnseenMessageUid(Mailbox<CassandraId> mailbox) throws MailboxException {
        return CassandraUtils.convertToStream(session.execute(selectAll(mailbox, FetchType.Metadata).and((eq(SEEN, false)))))
            .map((row) -> row.getLong(IMAP_UID))
            .sorted()
            .findFirst()
            .orElse(null);
    }

    @Override
    public Map<Long, MessageMetaData> expungeMarkedForDeletionInMailbox(Mailbox<CassandraId> mailbox, MessageRange set) throws MailboxException {
        return CassandraUtils.convertToStream(session.execute(buildQuery(mailbox, set, FetchType.Metadata).and(eq(DELETED, true))))
            .map(row -> message(row, FetchType.Metadata))
            .peek((message) -> delete(mailbox, message))
            .collect(Collectors.toMap(MailboxMessage::getUid, SimpleMessageMetaData::new));
    }

    @Override
    public MessageMetaData move(Mailbox<CassandraId> destinationMailbox, MailboxMessage<CassandraId> original) throws MailboxException {
        MessageMetaData messageMetaData = copy(destinationMailbox, original);
        deleteUsingMailboxId(original.getMailboxId(), original);
        return messageMetaData;
    }

    @Override
    public void endRequest() {
        // Do nothing
    }

    @Override
    public long getHighestModSeq(Mailbox<CassandraId> mailbox) throws MailboxException {
        return modSeqProvider.highestModSeq(mailboxSession, mailbox);
    }

    @Override
    public MessageMetaData add(Mailbox<CassandraId> mailbox, MailboxMessage<CassandraId> message) throws MailboxException {
        message.setUid(uidProvider.nextUid(mailboxSession, mailbox));
        message.setModSeq(modSeqProvider.nextModSeq(mailboxSession, mailbox));
        MessageMetaData messageMetaData = save(mailbox, message);
        if (!message.isSeen()) {
            incrementUnseen(mailbox.getMailboxId());
        }
        incrementCount(mailbox.getMailboxId());
        return messageMetaData;
    }

    @Override
    public Iterator<UpdatedFlags> updateFlags(Mailbox<CassandraId> mailbox, FlagsUpdateCalculator flagUpdateCalculator, MessageRange set) throws MailboxException {
        return CassandraUtils.convertToStream(session.execute(buildQuery(mailbox, set, FetchType.Metadata)))
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
    public MessageMetaData copy(Mailbox<CassandraId> mailbox, MailboxMessage<CassandraId> original) throws MailboxException {
        original.setFlags(new FlagsBuilder().add(original.createFlags()).add(Flag.RECENT).build());
        return add(mailbox, original);
    }

    @Override
    public long getLastUid(Mailbox<CassandraId> mailbox) throws MailboxException {
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

    private MailboxMessage<CassandraId> message(Row row, FetchType fetchType) {
        SimpleMailboxMessage<CassandraId> message =
            new SimpleMailboxMessage<>(
                row.getDate(INTERNAL_DATE),
                row.getLong(FULL_CONTENT_OCTETS),
                row.getInt(BODY_START_OCTET),
                buildContent(row, fetchType),
                getFlags(row),
                getPropertyBuilder(row),
                CassandraId.of(row.getUUID(MAILBOX_ID)));
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

    private MessageMetaData save(Mailbox<CassandraId> mailbox, MailboxMessage<CassandraId> message) throws MailboxException {
        try {
            session.execute(insertInto(TABLE_NAME)
                .value(MAILBOX_ID, mailbox.getMailboxId().asUuid())
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
                .value(TEXTUAL_LINE_COUNT, message.getTextualLineCount()));

            return new SimpleMessageMetaData(message);
        } catch (IOException e) {
            throw new MailboxException("Error saving mail", e);
        }
    }

    private Set<String> userFlagsSet(MailboxMessage<CassandraId> message) {
        return Arrays.stream(message.createFlags().getUserFlags()).collect(Collectors.toSet());
    }

    private void manageUnseenMessageCounts(Mailbox<CassandraId> mailbox, Flags oldFlags, Flags newFlags) {
        if (oldFlags.contains(Flag.SEEN) && !newFlags.contains(Flag.SEEN)) {
            incrementUnseen(mailbox.getMailboxId());
        }
        if (!oldFlags.contains(Flag.SEEN) && newFlags.contains(Flag.SEEN)) {
            decrementUnseen(mailbox.getMailboxId());
        }
    }

    private Optional<UpdatedFlags> updateFlagsOnMessage(Mailbox<CassandraId> mailbox, FlagsUpdateCalculator flagUpdateCalculator, Row row) {
        return tryMessageFlagsUpdate(flagUpdateCalculator, mailbox, message(row, FetchType.Metadata))
            .map(Optional::of)
            .orElse(handleRetries(mailbox, flagUpdateCalculator, row.getLong(IMAP_UID)));
    }

    private Optional<UpdatedFlags> tryMessageFlagsUpdate(FlagsUpdateCalculator flagUpdateCalculator, Mailbox<CassandraId> mailbox, MailboxMessage<CassandraId> message) {
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

    private Optional<UpdatedFlags> handleRetries(Mailbox<CassandraId> mailbox, FlagsUpdateCalculator flagUpdateCalculator, long uid) {
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

    private Optional<UpdatedFlags> retryMessageFlagsUpdate(Mailbox<CassandraId> mailbox, long uid, FlagsUpdateCalculator flagUpdateCalculator) {
        return tryMessageFlagsUpdate(flagUpdateCalculator,
            mailbox,
            message(Optional.ofNullable(session.execute(selectMessage(mailbox, uid, FetchType.Metadata)).one())
                .orElseThrow(() -> new MessageDeletedDuringFlagsUpdateException(mailbox.getMailboxId(), uid)),
                FetchType.Metadata));
    }

    private boolean conditionalSave(MailboxMessage<CassandraId> message, long oldModSeq) {
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
                .and(eq(MAILBOX_ID, message.getMailboxId().asUuid()))
                .onlyIf(eq(MOD_SEQ, oldModSeq)));
        return resultSet.one().getBool(CassandraConstants.LIGHTWEIGHT_TRANSACTION_APPLIED);
    }

    private ByteBuffer toByteBuffer(InputStream stream) throws IOException {
        return ByteBuffer.wrap(ByteStreams.toByteArray(stream));
    }

    private Where buildQuery(Mailbox<CassandraId> mailbox, MessageRange set, FetchType fetchType) {
        switch (set.getType()) {
        case ALL:
            return selectAll(mailbox, fetchType);
        case FROM:
            return selectFrom(mailbox, set.getUidFrom(), fetchType);
        case RANGE:
            return selectRange(mailbox, set.getUidFrom(), set.getUidTo(), fetchType);
        case ONE:
            return selectMessage(mailbox, set.getUidFrom(), fetchType);
        }
        throw new UnsupportedOperationException();
    }

    private Where selectAll(Mailbox<CassandraId> mailbox, FetchType fetchType) {
        return select(retrieveFields(fetchType))
            .from(TABLE_NAME)
            .where(eq(MAILBOX_ID, mailbox.getMailboxId().asUuid()));
    }

    private Where selectFrom(Mailbox<CassandraId> mailbox, long uid, FetchType fetchType) {
        return select(retrieveFields(fetchType))
            .from(TABLE_NAME)
            .where(eq(MAILBOX_ID, mailbox.getMailboxId().asUuid()))
            .and(gte(IMAP_UID, uid));
    }

    private Where selectRange(Mailbox<CassandraId> mailbox, long from, long to, FetchType fetchType) {
        return select(retrieveFields(fetchType))
            .from(TABLE_NAME)
            .where(eq(MAILBOX_ID, mailbox.getMailboxId().asUuid()))
            .and(gte(IMAP_UID, from))
            .and(lte(IMAP_UID, to));
    }

    private Where selectMessage(Mailbox<CassandraId> mailbox, long uid, FetchType fetchType) {
        return select(retrieveFields(fetchType))
            .from(TABLE_NAME)
            .where(eq(MAILBOX_ID, mailbox.getMailboxId().asUuid()))
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
