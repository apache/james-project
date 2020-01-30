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
import static com.datastax.driver.core.querybuilder.QueryBuilder.set;
import static com.datastax.driver.core.querybuilder.QueryBuilder.update;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageUidTable.MAILBOX_ID;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageUidTable.NEXT_UID;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageUidTable.TABLE_NAME;

import java.util.Optional;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.store.mail.UidProvider;

import com.datastax.driver.core.ConsistencyLevel;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Session;

import reactor.core.publisher.Mono;

public class CassandraUidProvider implements UidProvider {
    private static final String CONDITION = "Condition";

    private final CassandraAsyncExecutor executor;
    private final long maxUidRetries;
    private final PreparedStatement insertStatement;
    private final PreparedStatement updateStatement;
    private final PreparedStatement selectStatement;

    @Inject
    public CassandraUidProvider(Session session, CassandraConfiguration cassandraConfiguration) {
        this.executor = new CassandraAsyncExecutor(session);
        this.maxUidRetries = cassandraConfiguration.getUidMaxRetry();
        this.selectStatement = prepareSelect(session);
        this.updateStatement = prepareUpdate(session);
        this.insertStatement = prepareInsert(session);
    }

    private PreparedStatement prepareSelect(Session session) {
        return session.prepare(select(NEXT_UID)
            .from(TABLE_NAME)
            .where(eq(MAILBOX_ID, bindMarker(MAILBOX_ID))));
    }

    private PreparedStatement prepareUpdate(Session session) {
        return session.prepare(update(TABLE_NAME)
            .onlyIf(eq(NEXT_UID, bindMarker(CONDITION)))
            .with(set(NEXT_UID, bindMarker(NEXT_UID)))
            .where(eq(MAILBOX_ID, bindMarker(MAILBOX_ID))));
    }

    private PreparedStatement prepareInsert(Session session) {
        return session.prepare(insertInto(TABLE_NAME)
            .value(NEXT_UID, MessageUid.MIN_VALUE.asLong())
            .value(MAILBOX_ID, bindMarker(MAILBOX_ID))
            .ifNotExists());
    }

    @Override
    public MessageUid nextUid(Mailbox mailbox) throws MailboxException {
        return nextUid(mailbox.getMailboxId());
    }

    @Override
    public MessageUid nextUid(MailboxId mailboxId) throws MailboxException {
        CassandraId cassandraId = (CassandraId) mailboxId;
        return nextUid(cassandraId)
            .blockOptional()
            .orElseThrow(() -> new MailboxException("Error during Uid update"));
    }

    public Mono<MessageUid> nextUid(CassandraId cassandraId) {
        Mono<MessageUid> updateUid = findHighestUid(cassandraId)
            .flatMap(messageUid -> tryUpdateUid(cassandraId, messageUid));

        return updateUid
            .switchIfEmpty(tryInsert(cassandraId))
            .switchIfEmpty(updateUid)
            .single()
            .retry(maxUidRetries);
    }

    @Override
    public Optional<MessageUid> lastUid(Mailbox mailbox) {
        return findHighestUid((CassandraId) mailbox.getMailboxId())
                .blockOptional();
    }

    private Mono<MessageUid> findHighestUid(CassandraId mailboxId) {
        return executor.executeSingleRow(
            selectStatement.bind()
                .setUUID(MAILBOX_ID, mailboxId.asUuid())
                .setConsistencyLevel(ConsistencyLevel.SERIAL))
            .map(row -> MessageUid.of(row.getLong(NEXT_UID)));
    }

    private Mono<MessageUid> tryUpdateUid(CassandraId mailboxId, MessageUid uid) {
        MessageUid nextUid = uid.next();
        return executor.executeReturnApplied(
                updateStatement.bind()
                        .setUUID(MAILBOX_ID, mailboxId.asUuid())
                        .setLong(CONDITION, uid.asLong())
                        .setLong(NEXT_UID, nextUid.asLong()))
                .handle((success, sink) -> successToUid(nextUid, success).ifPresent(sink::next));
    }

    private Mono<MessageUid> tryInsert(CassandraId mailboxId) {
        return executor.executeReturnApplied(
            insertStatement.bind()
                .setUUID(MAILBOX_ID, mailboxId.asUuid()))
            .handle((success, sink) -> successToUid(MessageUid.MIN_VALUE, success).ifPresent(sink::next));
    }

    private Optional<MessageUid> successToUid(MessageUid uid, Boolean success) {
        if (success) {
            return Optional.of(uid);
        }
        return Optional.empty();
    }

}
