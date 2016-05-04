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
    private static final Uid FIRST_UID = new Uid(0);

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
    public long nextUid(MailboxSession mailboxSession, Mailbox mailbox) throws MailboxException {
        CassandraId mailboxId = (CassandraId) mailbox.getMailboxId();
        if (findHighestUid(mailboxId).isFirst()) {
            Optional<Uid> optional = tryInsertUid(mailboxId, FIRST_UID);
            if (optional.isPresent()) {
                return optional.get().getValue();
            }
        }

        try {
            return runner.executeAndRetrieveObject(
                () -> {
                    try {
                        return tryUpdateUid(mailboxId, findHighestUid(mailboxId))
                            .map(Uid::getValue);
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
    public long lastUid(MailboxSession mailboxSession, Mailbox mailbox) throws MailboxException {
        return findHighestUid((CassandraId) mailbox.getMailboxId()).getValue();
    }

    private Uid findHighestUid(CassandraId mailboxId) throws MailboxException {
        ResultSet result = session.execute(
            select(NEXT_UID)
                .from(CassandraMessageUidTable.TABLE_NAME)
                .where(eq(CassandraMessageUidTable.MAILBOX_ID, mailboxId.asUuid())));
        if (result.isExhausted()) {
            return FIRST_UID;
        } else {
            return new Uid(result.one().getLong(NEXT_UID));
        }
    }

    private Optional<Uid> tryInsertUid(CassandraId mailboxId, Uid uid) {
        Uid nextUid = uid.next();
        return transactionalStatementToOptionalUid(nextUid,
            insertInto(CassandraMessageUidTable.TABLE_NAME)
                .value(NEXT_UID, nextUid.getValue())
                .value(CassandraMessageUidTable.MAILBOX_ID, mailboxId.asUuid())
                .ifNotExists());
    }

    private Optional<Uid> tryUpdateUid(CassandraId mailboxId, Uid uid) {
        Uid nextUid = uid.next();
        return transactionalStatementToOptionalUid(nextUid,
            update(CassandraMessageUidTable.TABLE_NAME)
                .onlyIf(eq(NEXT_UID, uid.getValue()))
                .with(set(NEXT_UID, nextUid.getValue()))
                .where(eq(CassandraMessageUidTable.MAILBOX_ID, mailboxId.asUuid())));
    }

    private Optional<Uid> transactionalStatementToOptionalUid(Uid uid, BuiltStatement statement) {
        if(session.execute(statement).one().getBool(CassandraConstants.LIGHTWEIGHT_TRANSACTION_APPLIED)) {
            return Optional.of(uid);
        }
        return Optional.empty();
    }

    private static class Uid {

        private final long value;

        public Uid(long value) {
            this.value = value;
        }

        public Uid next() {
            return new Uid(value + 1);
        }

        public long getValue() {
            return value;
        }

        public boolean isFirst() {
            return value == FIRST_UID.value;
        }
    }

}
