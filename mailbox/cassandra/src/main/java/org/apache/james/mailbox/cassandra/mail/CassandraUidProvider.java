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
import static org.apache.james.util.ReactorUtils.publishIfPresent;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;
import org.apache.james.backends.cassandra.init.configuration.CassandraConsistenciesConfiguration;
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
import com.github.steveash.guavate.Guavate;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

public class CassandraUidProvider implements UidProvider {
    private static final String CONDITION = "Condition";

    private final CassandraAsyncExecutor executor;
    private final long maxUidRetries;
    private final PreparedStatement insertStatement;
    private final PreparedStatement updateStatement;
    private final PreparedStatement selectStatement;
    private final ConsistencyLevel consistencyLevel;

    @Inject
    public CassandraUidProvider(Session session, CassandraConfiguration cassandraConfiguration,
                                CassandraConsistenciesConfiguration consistenciesConfiguration) {
        this.executor = new CassandraAsyncExecutor(session);
        this.consistencyLevel = consistenciesConfiguration.getLightweightTransaction();
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
            .value(NEXT_UID, bindMarker(NEXT_UID))
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
        return nextUids(cassandraId)
            .blockOptional()
            .orElseThrow(() -> new MailboxException("Error during Uid update"));
    }

    public Mono<MessageUid> nextUids(CassandraId cassandraId) {
        Mono<MessageUid> updateUid = findHighestUid(cassandraId)
            .flatMap(messageUid -> tryUpdateUid(cassandraId, messageUid));

        Duration firstBackoff = Duration.ofMillis(10);
        return updateUid
            .switchIfEmpty(tryInsert(cassandraId))
            .switchIfEmpty(updateUid)
            .single()
            .retryWhen(Retry.backoff(maxUidRetries, firstBackoff).scheduler(Schedulers.elastic()));
    }

    public Mono<List<MessageUid>> nextUids(CassandraId cassandraId, int count) {
        Mono<List<MessageUid>> updateUid = findHighestUid(cassandraId)
            .flatMap(messageUid -> tryUpdateUid(cassandraId, messageUid, count)
                .map(highest -> range(messageUid, highest)));

        Duration firstBackoff = Duration.ofMillis(10);
        return updateUid
            .switchIfEmpty(tryInsert(cassandraId, count)
                .map(highest -> range(MessageUid.MIN_VALUE, highest)))
            .switchIfEmpty(updateUid)
            .single()
            .retryWhen(Retry.backoff(maxUidRetries, firstBackoff).scheduler(Schedulers.elastic()));
    }

    private List<MessageUid> range(MessageUid lowerExclusive, MessageUid higherInclusive) {
        return LongStream.range(lowerExclusive.asLong() + 1, higherInclusive.asLong() + 1)
            .mapToObj(MessageUid::of)
            .collect(Guavate.toImmutableList());
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
                .setConsistencyLevel(consistencyLevel))
            .map(row -> MessageUid.of(row.getLong(NEXT_UID)));
    }

    private Mono<MessageUid> tryUpdateUid(CassandraId mailboxId, MessageUid uid) {
        return tryUpdateUid(mailboxId, uid, 1);
    }

    private Mono<MessageUid> tryUpdateUid(CassandraId mailboxId, MessageUid uid, int count) {
        MessageUid nextUid = uid.next(count);
        return executor.executeReturnApplied(
                updateStatement.bind()
                        .setUUID(MAILBOX_ID, mailboxId.asUuid())
                        .setLong(CONDITION, uid.asLong())
                        .setLong(NEXT_UID, nextUid.asLong()))
                .map(success -> successToUid(nextUid, success))
                .handle(publishIfPresent());
    }

    private Mono<MessageUid> tryInsert(CassandraId mailboxId) {
        return executor.executeReturnApplied(
            insertStatement.bind()
                .setLong(NEXT_UID, MessageUid.MIN_VALUE.asLong())
                .setUUID(MAILBOX_ID, mailboxId.asUuid()))
            .map(success -> successToUid(MessageUid.MIN_VALUE, success))
            .handle(publishIfPresent());
    }

    private Mono<MessageUid> tryInsert(CassandraId mailboxId, int count) {
        return executor.executeReturnApplied(
            insertStatement.bind()
                .setLong(NEXT_UID, MessageUid.MIN_VALUE.next(count).asLong())
                .setUUID(MAILBOX_ID, mailboxId.asUuid()))
            .map(success -> successToUid(MessageUid.MIN_VALUE.next(count), success))
            .handle(publishIfPresent());
    }

    private Optional<MessageUid> successToUid(MessageUid uid, Boolean success) {
        if (success) {
            return Optional.of(uid);
        }
        return Optional.empty();
    }

}
