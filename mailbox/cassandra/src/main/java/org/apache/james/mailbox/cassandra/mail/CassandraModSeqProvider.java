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

import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.insertInto;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.update;
import static com.datastax.oss.driver.api.querybuilder.relation.Relation.column;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageModseqTable.MAILBOX_ID;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageModseqTable.NEXT_MODSEQ;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageModseqTable.TABLE_NAME;
import static org.apache.james.util.ReactorUtils.publishIfPresent;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;
import org.apache.james.backends.cassandra.init.configuration.JamesExecutionProfiles;
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.store.mail.ModSeqProvider;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.config.DriverExecutionProfile;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.type.codec.TypeCodecs;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

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

    private final CassandraAsyncExecutor cassandraAsyncExecutor;
    private final PreparedStatement select;
    private final PreparedStatement update;
    private final PreparedStatement insert;
    private final RetryBackoffSpec retrySpec;
    private final DriverExecutionProfile lwtProfile;
    private final CassandraConfiguration cassandraConfiguration;

    @Inject
    public CassandraModSeqProvider(CqlSession session, CassandraConfiguration cassandraConfiguration) {
        this.cassandraAsyncExecutor = new CassandraAsyncExecutor(session);
        this.lwtProfile = JamesExecutionProfiles.getLWTProfile(session);
        this.insert = prepareInsert(session);
        this.update = prepareUpdate(session);
        this.select = prepareSelect(session);
        Duration firstBackoff = Duration.ofMillis(10);
        this.retrySpec = Retry.backoff(cassandraConfiguration.getModSeqMaxRetry(), firstBackoff)
            .scheduler(Schedulers.parallel());
        this.cassandraConfiguration = cassandraConfiguration;
    }

    private PreparedStatement prepareInsert(CqlSession session) {
        return session.prepare(insertInto(TABLE_NAME)
            .value(NEXT_MODSEQ, bindMarker(NEXT_MODSEQ))
            .value(MAILBOX_ID, bindMarker(MAILBOX_ID))
            .ifNotExists()
            .build());
    }

    private PreparedStatement prepareUpdate(CqlSession session) {
        return session.prepare(update(TABLE_NAME)
            .setColumn(NEXT_MODSEQ, bindMarker(NEXT_MODSEQ))
            .where(column(MAILBOX_ID).isEqualTo(bindMarker(MAILBOX_ID)))
            .ifColumn(NEXT_MODSEQ).isEqualTo(bindMarker(MOD_SEQ_CONDITION))
            .build());
    }

    private PreparedStatement prepareSelect(CqlSession session) {
        return session.prepare(selectFrom(TABLE_NAME)
            .column(NEXT_MODSEQ)
            .where(column(MAILBOX_ID).isEqualTo(bindMarker(MAILBOX_ID)))
            .build());
    }

    @Override
    public ModSeq nextModSeq(Mailbox mailbox) throws MailboxException {
        return nextModSeqReactive(mailbox.getMailboxId())
            .blockOptional()
            .orElseThrow(() -> new MailboxException("Can not retrieve modseq for " + mailbox.getMailboxId()));
    }

    @Override
    public ModSeq nextModSeq(MailboxId mailboxId) throws MailboxException {
        return nextModSeqReactive(mailboxId)
            .blockOptional()
            .orElseThrow(() -> new MailboxException("Can not retrieve modseq for " + mailboxId));
    }

    @Override
    public ModSeq highestModSeq(Mailbox mailbox) throws MailboxException {
        return highestModSeq(mailbox.getMailboxId());
    }

    @Override
    public ModSeq highestModSeq(MailboxId mailboxId) throws MailboxException {
        return unbox(() -> findHighestModSeq((CassandraId) mailboxId,
            Optional.of(lwtProfile).filter(any -> cassandraConfiguration.isUidReadStrongConsistency()))
            .block().orElse(ModSeq.first()))
            .add(cassandraConfiguration.getUidModseqIncrement());
    }

    private Mono<Optional<ModSeq>> findHighestModSeq(CassandraId mailboxId, Optional<DriverExecutionProfile> executionProfile) {
        BoundStatement statement = select.bind()
            .set(MAILBOX_ID, mailboxId.asUuid(), TypeCodecs.TIMEUUID);
        return cassandraAsyncExecutor.executeSingleRowOptional(
            executionProfile.map(statement::setExecutionProfile)
                .orElse(statement))
            .map(maybeRow -> maybeRow.map(row -> ModSeq.of(row.getLong(0))));
    }

    private Mono<ModSeq> tryInsertModSeq(CassandraId mailboxId, ModSeq modSeq) {
        ModSeq nextModSeq = modSeq.next();
        return cassandraAsyncExecutor.executeReturnApplied(
                insert.bind()
                    .set(MAILBOX_ID, mailboxId.asUuid(), TypeCodecs.TIMEUUID)
                    .setLong(NEXT_MODSEQ, nextModSeq.asLong()))
            .map(success -> successToModSeq(nextModSeq, success))
            .handle(publishIfPresent());
    }

    private Mono<ModSeq> tryUpdateModSeq(CassandraId mailboxId, ModSeq modSeq) {
        ModSeq nextModSeq = modSeq.next();
        return cassandraAsyncExecutor.executeReturnApplied(
                update.bind()
                    .set(MAILBOX_ID, mailboxId.asUuid(), TypeCodecs.TIMEUUID)
                    .setLong(NEXT_MODSEQ, nextModSeq.asLong())
                    .setLong(MOD_SEQ_CONDITION, modSeq.asLong()))
            .map(success -> successToModSeq(nextModSeq, success))
            .handle(publishIfPresent());
    }

    private Optional<ModSeq> successToModSeq(ModSeq modSeq, Boolean success) {
        if (success) {
            return Optional.of(modSeq);
        }
        return Optional.empty();
    }

    @Override
    public Mono<ModSeq> nextModSeqReactive(MailboxId mailboxId) {
        CassandraId cassandraId = (CassandraId) mailboxId;
        return findHighestModSeq(cassandraId, Optional.of(lwtProfile))
            .flatMap(maybeHighestModSeq -> maybeHighestModSeq
                .map(highestModSeq -> tryUpdateModSeq(cassandraId, highestModSeq))
                .orElseGet(() -> tryInsertModSeq(cassandraId, ModSeq.first())))
            .single()
            .retryWhen(retrySpec)
            .map(modSeq -> modSeq.add(cassandraConfiguration.getUidModseqIncrement()));
    }

    @Override
    public Mono<ModSeq> highestModSeqReactive(Mailbox mailbox) {
        return findHighestModSeq((CassandraId) mailbox.getMailboxId(), Optional.empty())
            .map(optional -> optional.orElse(ModSeq.first()))
            .map(modSeq -> modSeq.add(cassandraConfiguration.getUidModseqIncrement()));
    }
}
