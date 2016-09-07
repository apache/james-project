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

import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static com.datastax.driver.core.querybuilder.QueryBuilder.insertInto;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;
import static com.datastax.driver.core.querybuilder.QueryBuilder.set;
import static com.datastax.driver.core.querybuilder.QueryBuilder.update;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageUidTable.NEXT_UID;

import java.util.Optional;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraConstants;
import org.apache.james.backends.cassandra.utils.FunctionRunnerWithRetry;
import org.apache.james.backends.cassandra.utils.LightweightTransactionException;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.cassandra.CassandraId;
import org.apache.james.mailbox.cassandra.table.CassandraMessageUidTable;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.store.mail.UidProvider;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.BuiltStatement;
import com.google.common.base.Throwables;

public class CassandraUidProvider implements UidProvider {
    public final static int DEFAULT_MAX_RETRY = 100000;
    private static final Logger LOG = LoggerFactory.getLogger(CassandraUidProvider.class);

    private final Session session;
    private final FunctionRunnerWithRetry runner;

    public CassandraUidProvider(Session session, int maxRetry) {
        this.session = session;
        this.runner = new FunctionRunnerWithRetry(maxRetry);
    }

    @Inject
    public CassandraUidProvider(Session session) {
        this(session, DEFAULT_MAX_RETRY);
    }

    @Override
    public MessageUid nextUid(MailboxSession mailboxSession, Mailbox mailbox) throws MailboxException {
        CassandraId mailboxId = (CassandraId) mailbox.getMailboxId();
        if (! findHighestUid(mailboxId).isPresent()) {
            Optional<MessageUid> optional = tryInsertUid(mailboxId, Optional.empty());
            if (optional.isPresent()) {
                return optional.get();
            }
        }

        try {
            return runner.executeAndRetrieveObject(
                () -> {
                    try {
                        return tryUpdateUid(mailboxId, findHighestUid(mailboxId));
                    } catch (Exception exception) {
                        LOG.error("Can not retrieve next Uid", exception);
                        throw Throwables.propagate(exception);
                    }
                });
        } catch (LightweightTransactionException e) {
            throw new MailboxException("Error during Uid update", e);
        }
    }

    @Override
    public com.google.common.base.Optional<MessageUid> lastUid(MailboxSession mailboxSession, Mailbox mailbox) throws MailboxException {
        return findHighestUid((CassandraId) mailbox.getMailboxId());
    }

    private com.google.common.base.Optional<MessageUid> findHighestUid(CassandraId mailboxId) throws MailboxException {
        ResultSet result = session.execute(
            select(NEXT_UID)
                .from(CassandraMessageUidTable.TABLE_NAME)
                .where(eq(CassandraMessageUidTable.MAILBOX_ID, mailboxId.asUuid())));
        if (result.isExhausted()) {
            return com.google.common.base.Optional.absent();
        } else {
            return com.google.common.base.Optional.of(MessageUid.of(result.one().getLong(NEXT_UID)));
        }
    }

    private Optional<MessageUid> tryInsertUid(CassandraId mailboxId, Optional<MessageUid> uid) {
        MessageUid nextUid = uid.map(MessageUid::next).orElse(MessageUid.MIN_VALUE);
        return transactionalStatementToOptionalUid(nextUid,
            insertInto(CassandraMessageUidTable.TABLE_NAME)
                .value(NEXT_UID, nextUid.asLong())
                .value(CassandraMessageUidTable.MAILBOX_ID, mailboxId.asUuid())
                .ifNotExists());
    }

    private Optional<MessageUid> tryUpdateUid(CassandraId mailboxId, com.google.common.base.Optional<MessageUid> uid) {
        if (uid.isPresent()) {
            MessageUid nextUid = uid.get().next();
            return transactionalStatementToOptionalUid(nextUid,
                    update(CassandraMessageUidTable.TABLE_NAME)
                        .onlyIf(eq(NEXT_UID, uid.get().asLong()))
                        .with(set(NEXT_UID, nextUid.asLong()))
                        .where(eq(CassandraMessageUidTable.MAILBOX_ID, mailboxId.asUuid())));
        } else {
            return transactionalStatementToOptionalUid(MessageUid.MIN_VALUE,
                    update(CassandraMessageUidTable.TABLE_NAME)
                    .onlyIf(eq(NEXT_UID, null))
                    .with(set(NEXT_UID, MessageUid.MIN_VALUE.asLong()))
                    .where(eq(CassandraMessageUidTable.MAILBOX_ID, mailboxId.asUuid())));
        }
    }

    private Optional<MessageUid> transactionalStatementToOptionalUid(MessageUid uid, BuiltStatement statement) {
        if(session.execute(statement).one().getBool(CassandraConstants.LIGHTWEIGHT_TRANSACTION_APPLIED)) {
            return Optional.of(uid);
        }
        return Optional.empty();
    }

}
