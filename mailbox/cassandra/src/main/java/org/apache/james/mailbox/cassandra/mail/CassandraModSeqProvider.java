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
import static org.apache.james.mailbox.cassandra.table.CassandraMessageModseqTable.MAILBOX_ID;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageModseqTable.NEXT_MODSEQ;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageModseqTable.TABLE_NAME;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.init.CassandraConfiguration;
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.backends.cassandra.utils.FunctionRunnerWithRetry;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.store.mail.ModSeqProvider;
import org.apache.james.mailbox.store.mail.model.Mailbox;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Session;
import com.google.common.annotations.VisibleForTesting;

public class CassandraModSeqProvider implements ModSeqProvider {

    public static final String MOD_SEQ_CONDITION = "modSeqCondition";

    public static class ExceptionRelay extends RuntimeException {
        private final MailboxException underlying;

        public ExceptionRelay(MailboxException underlying) {
            super(underlying);
            this.underlying = underlying;
        }

        public MailboxException getUnderlying() {
            return underlying;
        }
    }

    private static <T> T unbox(Supplier<T> supplier) throws MailboxException {
        try {
            return supplier.get();
        } catch (CompletionException e) {
            if (e.getCause() instanceof ExceptionRelay) {
                throw ((ExceptionRelay) e.getCause()).getUnderlying();
            }
            throw e;
        }
    }

    private static final ModSeq FIRST_MODSEQ = new ModSeq(0);

    private final CassandraAsyncExecutor cassandraAsyncExecutor;
    private final FunctionRunnerWithRetry runner;
    private final PreparedStatement select;
    private final PreparedStatement update;
    private final PreparedStatement insert;

    @Inject
    public CassandraModSeqProvider(Session session, CassandraConfiguration cassandraConfiguration) {
        this.cassandraAsyncExecutor = new CassandraAsyncExecutor(session);
        this.runner = new FunctionRunnerWithRetry(cassandraConfiguration.getModSeqMaxRetry());
        this.insert = prepareInsert(session);
        this.update = prepareUpdate(session);
        this.select = prepareSelect(session);
    }

    @VisibleForTesting
    public CassandraModSeqProvider(Session session) {
        this(session, CassandraConfiguration.DEFAULT_CONFIGURATION);
    }

    private PreparedStatement prepareInsert(Session session) {
        return session.prepare(insertInto(TABLE_NAME)
            .value(NEXT_MODSEQ, bindMarker(NEXT_MODSEQ))
            .value(MAILBOX_ID, bindMarker(MAILBOX_ID))
            .ifNotExists());
    }

    private PreparedStatement prepareUpdate(Session session) {
        return session.prepare(update(TABLE_NAME)
            .onlyIf(eq(NEXT_MODSEQ, bindMarker(MOD_SEQ_CONDITION)))
            .with(set(NEXT_MODSEQ, bindMarker(NEXT_MODSEQ)))
            .where(eq(MAILBOX_ID, bindMarker(MAILBOX_ID))));
    }

    private PreparedStatement prepareSelect(Session session) {
        return session.prepare(select(NEXT_MODSEQ)
            .from(TABLE_NAME)
            .where(eq(MAILBOX_ID, bindMarker(MAILBOX_ID))));
    }



    @Override
    public long nextModSeq(MailboxSession mailboxSession, Mailbox mailbox) throws MailboxException {
        CassandraId mailboxId = (CassandraId) mailbox.getMailboxId();
        return nextModSeq(mailboxId).join()
            .orElseThrow(() -> new MailboxException("Can not retrieve modseq for " + mailboxId));
    }

    @Override
    public long nextModSeq(MailboxSession session, MailboxId mailboxId) throws MailboxException {
        return nextModSeq((CassandraId) mailboxId)
            .join()
            .orElseThrow(() -> new MailboxException("Can not retrieve modseq for " + mailboxId));
    }

    @Override
    public long highestModSeq(MailboxSession mailboxSession, Mailbox mailbox) throws MailboxException {
        return unbox(() -> findHighestModSeq((CassandraId) mailbox.getMailboxId()).join().getValue());
    }

    @Override
    public long highestModSeq(MailboxSession mailboxSession, MailboxId mailboxId) throws MailboxException {
        return unbox(() -> findHighestModSeq((CassandraId) mailboxId).join().getValue());
    }

    private CompletableFuture<ModSeq> findHighestModSeq(CassandraId mailboxId) {
        return cassandraAsyncExecutor.executeSingleRow(
            select.bind()
                .setUUID(MAILBOX_ID, mailboxId.asUuid()))
            .thenApply(optional -> optional.map(row -> new ModSeq(row.getLong(NEXT_MODSEQ)))
                .orElse(FIRST_MODSEQ));
    }

    private CompletableFuture<Optional<ModSeq>> tryInsertModSeq(CassandraId mailboxId, ModSeq modSeq) {
        ModSeq nextModSeq = modSeq.next();
        return cassandraAsyncExecutor.executeReturnApplied(
            insert.bind()
                .setUUID(MAILBOX_ID, mailboxId.asUuid())
                .setLong(NEXT_MODSEQ, nextModSeq.getValue()))
            .thenApply(success -> successToModSeq(nextModSeq, success));
    }

    private CompletableFuture<Optional<ModSeq>> tryUpdateModSeq(CassandraId mailboxId, ModSeq modSeq) {
        ModSeq nextModSeq = modSeq.next();
        return cassandraAsyncExecutor.executeReturnApplied(
            update.bind()
                .setUUID(MAILBOX_ID, mailboxId.asUuid())
                .setLong(NEXT_MODSEQ, nextModSeq.getValue())
                .setLong(MOD_SEQ_CONDITION, modSeq.getValue()))
            .thenApply(success -> successToModSeq(nextModSeq, success));
    }

    private Optional<ModSeq> successToModSeq(ModSeq modSeq, Boolean success) {
        if (success) {
            return Optional.of(modSeq);
        }
        return Optional.empty();
    }
    
    public CompletableFuture<Optional<Long>> nextModSeq(CassandraId mailboxId) {
        return findHighestModSeq(mailboxId)
            .thenCompose(modSeq -> {
                if (modSeq.isFirst()) {
                    return tryInsertModSeq(mailboxId, FIRST_MODSEQ);
                }
                return tryUpdateModSeq(mailboxId, modSeq);
            }).thenCompose(firstInsert -> {
                    if (firstInsert.isPresent()) {
                        return CompletableFuture.completedFuture(firstInsert);
                    }
                    return handleRetries(mailboxId);
                })
            .thenApply(optional -> optional.map(ModSeq::getValue));
    }

    private CompletableFuture<Optional<ModSeq>> handleRetries(CassandraId mailboxId) {
        return runner.executeAsyncAndRetrieveObject(
            () -> findHighestModSeq(mailboxId)
                .thenCompose(newModSeq -> tryUpdateModSeq(mailboxId, newModSeq)));
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
