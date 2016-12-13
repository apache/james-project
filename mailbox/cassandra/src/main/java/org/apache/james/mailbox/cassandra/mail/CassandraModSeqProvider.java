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
import static org.apache.james.mailbox.cassandra.table.CassandraMessageModseqTable.MAILBOX_ID;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageModseqTable.NEXT_MODSEQ;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageModseqTable.TABLE_NAME;

import java.util.Optional;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraConstants;
import org.apache.james.backends.cassandra.utils.FunctionRunnerWithRetry;
import org.apache.james.backends.cassandra.utils.LightweightTransactionException;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.cassandra.CassandraId;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.store.mail.ModSeqProvider;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.BuiltStatement;
import com.google.common.base.Throwables;

public class CassandraModSeqProvider implements ModSeqProvider {

    private static final int DEFAULT_MAX_RETRY = 100000;
    private static final Logger LOG = LoggerFactory.getLogger(CassandraModSeqProvider.class);
    private static final ModSeq FIRST_MODSEQ = new ModSeq(0);
    
    private final Session session;
    private final FunctionRunnerWithRetry runner;

    public CassandraModSeqProvider(Session session, int maxRetry) {
        this.session = session;
        this.runner = new FunctionRunnerWithRetry(maxRetry);
    }

    @Inject
    public CassandraModSeqProvider(Session session) {
        this(session, DEFAULT_MAX_RETRY);
    }

    @Override
    public long nextModSeq(MailboxSession mailboxSession, Mailbox mailbox) throws MailboxException {
        CassandraId mailboxId = (CassandraId) mailbox.getMailboxId();
        return nextModSeq(mailboxSession, mailboxId);
    }

    @Override
    public long nextModSeq(MailboxSession session, MailboxId mailboxId) throws MailboxException {
        return nextModSeq(session, (CassandraId)mailboxId);
    }

    @Override
    public long highestModSeq(MailboxSession mailboxSession, Mailbox mailbox) throws MailboxException {
        return findHighestModSeq(mailboxSession, (CassandraId) mailbox.getMailboxId()).getValue();
    }
    
    private ModSeq findHighestModSeq(MailboxSession mailboxSession, CassandraId mailboxId) throws MailboxException {
        ResultSet result = session.execute(
                select(NEXT_MODSEQ)
                    .from(TABLE_NAME)
                    .where(eq(MAILBOX_ID, mailboxId.asUuid())));
        if (result.isExhausted()) {
            return FIRST_MODSEQ;
        } else {
            return new ModSeq(result.one().getLong(NEXT_MODSEQ));
        }
    }

    private Optional<ModSeq> tryInsertModSeq(CassandraId mailboxId, ModSeq modSeq) {
        ModSeq nextModSeq = modSeq.next();
        return transactionalStatementToOptionalModSeq(nextModSeq,
                insertInto(TABLE_NAME)
                    .value(NEXT_MODSEQ, nextModSeq.getValue())
                    .value(MAILBOX_ID, mailboxId.asUuid())
                    .ifNotExists());
    }
    
    private Optional<ModSeq> tryUpdateModSeq(CassandraId mailboxId, ModSeq modSeq) {
        ModSeq nextModSeq = modSeq.next();
        return transactionalStatementToOptionalModSeq(nextModSeq,
                update(TABLE_NAME)
                    .onlyIf(eq(NEXT_MODSEQ, modSeq.getValue()))
                    .with(set(NEXT_MODSEQ, nextModSeq.getValue()))
                    .where(eq(MAILBOX_ID, mailboxId.asUuid())));
    }

    private Optional<ModSeq> transactionalStatementToOptionalModSeq(ModSeq modSeq, BuiltStatement statement) {
        if(session.execute(statement).one().getBool(CassandraConstants.LIGHTWEIGHT_TRANSACTION_APPLIED)) {
            return Optional.of(modSeq);
        }
        return Optional.empty();
    }
    
    private long nextModSeq(MailboxSession mailboxSession, CassandraId mailboxId) throws MailboxException {
        if (findHighestModSeq(mailboxSession, mailboxId).isFirst()) {
            Optional<ModSeq> optional = tryInsertModSeq(mailboxId, FIRST_MODSEQ);
            if (optional.isPresent()) {
                return optional.get().getValue();
            }
        }

        try {
            return runner.executeAndRetrieveObject(
                        () -> {
                            try {
                                return tryUpdateModSeq(mailboxId, findHighestModSeq(mailboxSession, mailboxId))
                                        .map(ModSeq::getValue);
                            } catch (Exception exception) {
                                LOG.error("Can not retrieve next ModSeq", exception);
                                throw Throwables.propagate(exception);
                            }
                        });
        } catch (LightweightTransactionException e) {
            throw new MailboxException("Error during ModSeq update", e);
        }
    }

    private static class ModSeq {
        
        private final long value;
        
        public ModSeq(long value) {
            this.value = value;
        }
        
        public ModSeq next() {
            return new ModSeq(value + 1);
        }
        
        public long getValue() {
            return value;
        }
        
        public boolean isFirst() {
            return value == FIRST_MODSEQ.value;
        }
    }

}
