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
import static org.apache.james.mailbox.cassandra.table.CassandraMessageUidTable.MAILBOX_ID;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageUidTable.NEXT_UID;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageUidTable.TABLE_NAME;
import static org.apache.james.util.ReactorUtils.publishIfPresent;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;

import jakarta.inject.Inject;

import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;
import org.apache.james.backends.cassandra.init.configuration.JamesExecutionProfiles;
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.store.mail.UidProvider;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.config.DriverExecutionProfile;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.type.codec.TypeCodecs;
import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

public class CassandraUidProvider implements UidProvider {
    private static final String CONDITION = "Condition";

    private final CassandraAsyncExecutor executor;
    private final PreparedStatement insertStatement;
    private final PreparedStatement updateStatement;
    private final PreparedStatement selectStatement;
    private final DriverExecutionProfile lwtProfile;
    private final RetryBackoffSpec retrySpec;
    private final CassandraConfiguration cassandraConfiguration;

    @Inject
    public CassandraUidProvider(CqlSession session, CassandraConfiguration cassandraConfiguration) {
        this.executor = new CassandraAsyncExecutor(session);
        this.lwtProfile = JamesExecutionProfiles.getLWTProfile(session);
        this.selectStatement = prepareSelect(session);
        this.updateStatement = prepareUpdate(session);
        this.insertStatement = prepareInsert(session);
        Duration firstBackoff = Duration.ofMillis(10);
        this.retrySpec = Retry.backoff(cassandraConfiguration.getUidMaxRetry(), firstBackoff)
            .scheduler(Schedulers.parallel());
        this.cassandraConfiguration = cassandraConfiguration;
    }

    private PreparedStatement prepareSelect(CqlSession session) {
        return session.prepare(selectFrom(TABLE_NAME)
            .column(NEXT_UID)
            .where(column(MAILBOX_ID).isEqualTo(bindMarker(MAILBOX_ID)))
            .build());
    }

    private PreparedStatement prepareUpdate(CqlSession session) {
        return session.prepare(update(TABLE_NAME)
            .setColumn(NEXT_UID, bindMarker(NEXT_UID))
            .where(column(MAILBOX_ID).isEqualTo(bindMarker(MAILBOX_ID)))
            .ifColumn(NEXT_UID).isEqualTo(bindMarker(CONDITION))
            .build());
    }

    private PreparedStatement prepareInsert(CqlSession session) {
        return session.prepare(insertInto(TABLE_NAME)
            .value(NEXT_UID, bindMarker(NEXT_UID))
            .value(MAILBOX_ID, bindMarker(MAILBOX_ID))
            .ifNotExists()
            .build());
    }

    @Override
    public MessageUid nextUid(Mailbox mailbox) throws MailboxException {
        return nextUid(mailbox.getMailboxId());
    }

    @Override
    public MessageUid nextUid(MailboxId mailboxId) throws MailboxException {
        CassandraId cassandraId = (CassandraId) mailboxId;
        return nextUidReactive(cassandraId)
            .blockOptional()
            .orElseThrow(() -> new MailboxException("Error during Uid update"));
    }

    @Override
    public Mono<MessageUid> nextUidReactive(MailboxId mailboxId) {
        CassandraId cassandraId = (CassandraId) mailboxId;
        Mono<MessageUid> updateUid = findHighestUid(cassandraId, Optional.of(lwtProfile))
            .flatMap(messageUid -> tryUpdateUid(cassandraId, messageUid));

        return updateUid
            .switchIfEmpty(tryInsert(cassandraId))
            .switchIfEmpty(updateUid)
            .single()
            .retryWhen(retrySpec)
            .map(uid -> uid.add(cassandraConfiguration.getUidModseqIncrement()));
    }

    @Override
    public Mono<List<MessageUid>> nextUids(MailboxId mailboxId, int count) {
        CassandraId cassandraId = (CassandraId) mailboxId;

        Mono<List<MessageUid>> updateUid = findHighestUid(cassandraId, Optional.of(lwtProfile))
            .flatMap(messageUid -> tryUpdateUid(cassandraId, messageUid, count)
                .map(highest -> range(messageUid, highest)));

        return updateUid
            .switchIfEmpty(tryInsert(cassandraId, count)
                .map(highest -> range(MessageUid.MIN_VALUE, highest)))
            .switchIfEmpty(updateUid)
            .single()
            .retryWhen(retrySpec);
    }

    private List<MessageUid> range(MessageUid lowerExclusive, MessageUid higherInclusive) {
        return LongStream.range(lowerExclusive.asLong() + 1, higherInclusive.asLong() + 1)
            .mapToObj(MessageUid::of)
            .map(uid -> uid.add(cassandraConfiguration.getUidModseqIncrement()))
            .collect(ImmutableList.toImmutableList());
    }

    @Override
    public Optional<MessageUid> lastUid(Mailbox mailbox) {
        return findHighestUid((CassandraId) mailbox.getMailboxId(), Optional.of(lwtProfile).filter(any -> cassandraConfiguration.isUidReadStrongConsistency()))
            .blockOptional()
            .map(uid -> uid.add(cassandraConfiguration.getUidModseqIncrement()));
    }

    @Override
    public Mono<Optional<MessageUid>> lastUidReactive(Mailbox mailbox) {
        return findHighestUid((CassandraId) mailbox.getMailboxId(), Optional.of(lwtProfile).filter(any -> cassandraConfiguration.isUidReadStrongConsistency()))
            .map(uid -> uid.add(cassandraConfiguration.getUidModseqIncrement()))
            .map(Optional::of)
            .switchIfEmpty(Mono.just(Optional.empty()));
    }

    private Mono<MessageUid> findHighestUid(CassandraId mailboxId, Optional<DriverExecutionProfile> executionProfile) {
        BoundStatement statement = selectStatement.bind()
            .set(MAILBOX_ID, mailboxId.asUuid(), TypeCodecs.TIMEUUID);
        return executor.executeSingleRow(
            executionProfile.map(statement::setExecutionProfile)
                .orElse(statement))
            .map(row -> MessageUid.of(row.getLong(0)));
    }

    private Mono<MessageUid> tryUpdateUid(CassandraId mailboxId, MessageUid uid) {
        return tryUpdateUid(mailboxId, uid, 1);
    }

    private Mono<MessageUid> tryUpdateUid(CassandraId mailboxId, MessageUid uid, int count) {
        MessageUid nextUid = uid.next(count);
        return executor.executeReturnApplied(
                updateStatement.bind()
                    .set(MAILBOX_ID, mailboxId.asUuid(), TypeCodecs.TIMEUUID)
                    .setLong(CONDITION, uid.asLong())
                    .setLong(NEXT_UID, nextUid.asLong()))
            .map(success -> successToUid(nextUid, success))
            .handle(publishIfPresent());
    }

    private Mono<MessageUid> tryInsert(CassandraId mailboxId) {
        return executor.executeReturnApplied(
                insertStatement.bind()
                    .setLong(NEXT_UID, MessageUid.MIN_VALUE.asLong())
                    .setUuid(MAILBOX_ID, mailboxId.asUuid()))
            .map(success -> successToUid(MessageUid.MIN_VALUE, success))
            .handle(publishIfPresent());
    }

    private Mono<MessageUid> tryInsert(CassandraId mailboxId, int count) {
        return executor.executeReturnApplied(
                insertStatement.bind()
                    .setLong(NEXT_UID, MessageUid.MIN_VALUE.next(count).asLong())
                    .setUuid(MAILBOX_ID, mailboxId.asUuid()))
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
