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
import java.util.concurrent.CompletableFuture;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.backends.cassandra.utils.FunctionRunnerWithRetry;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.store.mail.UidProvider;
import org.apache.james.mailbox.store.mail.model.Mailbox;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Session;
import com.google.common.annotations.VisibleForTesting;

public class CassandraUidProvider implements UidProvider {
    private static final String CONDITION = "Condition";

    private final CassandraAsyncExecutor executor;
    private final FunctionRunnerWithRetry runner;
    private final PreparedStatement insertStatement;
    private final PreparedStatement updateStatement;
    private final PreparedStatement selectStatement;

    @Inject
    public CassandraUidProvider(Session session, CassandraConfiguration cassandraConfiguration) {
        this.executor = new CassandraAsyncExecutor(session);
        this.runner = new FunctionRunnerWithRetry(cassandraConfiguration.getUidMaxRetry());
        this.selectStatement = prepareSelect(session);
        this.updateStatement = prepareUpdate(session);
        this.insertStatement = prepareInsert(session);
    }

    @VisibleForTesting
    public CassandraUidProvider(Session session) {
        this(session, CassandraConfiguration.DEFAULT_CONFIGURATION);
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
    public MessageUid nextUid(MailboxSession mailboxSession, Mailbox mailbox) throws MailboxException {
        return nextUid(mailboxSession, mailbox.getMailboxId());
    }

    @Override
    public MessageUid nextUid(MailboxSession session, MailboxId mailboxId) throws MailboxException {
        CassandraId cassandraId = (CassandraId) mailboxId;
        return nextUid(cassandraId)
        .join()
        .orElseThrow(() -> new MailboxException("Error during Uid update"));
    }

    public CompletableFuture<Optional<MessageUid>> nextUid(CassandraId cassandraId) {
        return findHighestUid(cassandraId)
            .thenCompose(optional -> {
                if (optional.isPresent()) {
                    return tryUpdateUid(cassandraId, optional);
                }
                return tryInsert(cassandraId);
            })
            .thenCompose(optional -> {
                if (optional.isPresent()) {
                    return CompletableFuture.completedFuture(optional);
                }
                return runner.executeAsyncAndRetrieveObject(
                    () -> findHighestUid(cassandraId)
                        .thenCompose(readUid -> tryUpdateUid(cassandraId, readUid)));
            });
    }

    @Override
    public Optional<MessageUid> lastUid(MailboxSession mailboxSession, Mailbox mailbox) throws MailboxException {
        return findHighestUid((CassandraId) mailbox.getMailboxId()).join();
    }

    private CompletableFuture<Optional<MessageUid>> findHighestUid(CassandraId mailboxId) {
        return executor.executeSingleRow(
            selectStatement.bind()
                .setUUID(MAILBOX_ID, mailboxId.asUuid()))
            .thenApply(optional -> optional.map(row -> MessageUid.of(row.getLong(NEXT_UID))));
    }

    private CompletableFuture<Optional<MessageUid>> tryUpdateUid(CassandraId mailboxId, Optional<MessageUid> uid) {
        if (uid.isPresent()) {
            MessageUid nextUid = uid.get().next();
            return executor.executeReturnApplied(
                updateStatement.bind()
                    .setUUID(MAILBOX_ID, mailboxId.asUuid())
                    .setLong(CONDITION, uid.get().asLong())
                    .setLong(NEXT_UID, nextUid.asLong()))
                .thenApply(success -> successToUid(nextUid, success));
        } else {
            return tryInsert(mailboxId);
        }
    }

    private CompletableFuture<Optional<MessageUid>> tryInsert(CassandraId mailboxId) {
        return executor.executeReturnApplied(
            insertStatement.bind()
                .setUUID(MAILBOX_ID, mailboxId.asUuid()))
            .thenApply(success -> successToUid(MessageUid.MIN_VALUE, success));
    }

    private Optional<MessageUid> successToUid(MessageUid uid, Boolean success) {
        if (success) {
            return Optional.of(uid);
        }
        return Optional.empty();
    }

}
