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

package org.apache.james.mailbox.cassandra.mail.task;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

import jakarta.inject.Inject;
import jakarta.mail.Flags;

import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.mail.CassandraDeletedMessageDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxRecentsDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageIdDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageMetadata;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.task.Task;
import org.apache.james.util.streams.Limit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Mono;

public class SolveMailboxFlagInconsistenciesService {

    public enum TargetFlag {
        RECENT,
        DELETED;

        public static TargetFlag from(String value) {
            return TargetFlag.valueOf(value);
        }
    }

    public record Context(AtomicLong processedMailboxEntries,
                          ConcurrentLinkedDeque<CassandraId> errors) {

        record Snapshot(long processedMailboxEntries, ImmutableList<CassandraId> errors) {
        }

        public Context() {
            this(new AtomicLong(0), new ConcurrentLinkedDeque<>());
        }

        void incrementProcessedMailboxEntries() {
            processedMailboxEntries.incrementAndGet();
        }

        void addError(CassandraId cassandraId) {
            errors.add(cassandraId);
        }

        Snapshot snapshot() {
            return new Snapshot(processedMailboxEntries.get(), ImmutableList.copyOf(errors));
        }
    }

    public interface SolveMessageFlagInconsistencyStrategy {
        Predicate<CassandraMessageMetadata> filterOutFlagInconsistencies();

        Mono<Void> removeAllByMailboxId(CassandraId cassandraId);

        Mono<Void> addEntry(CassandraId cassandraId, List<MessageUid> uids);

        TargetFlag targetFlag();
    }

    public record SolveMailboxDeletedFlagInconsistenciesStrategy(CassandraDeletedMessageDAO deletedMessageDAO) implements SolveMessageFlagInconsistencyStrategy {
        @Override
        public Predicate<CassandraMessageMetadata> filterOutFlagInconsistencies() {
            return metaData -> metaData.getComposedMessageId().getFlags().contains(Flags.Flag.DELETED);
        }

        @Override
        public Mono<Void> removeAllByMailboxId(CassandraId cassandraId) {
            return deletedMessageDAO.removeAll(cassandraId);
        }

        @Override
        public Mono<Void> addEntry(CassandraId cassandraId, List<MessageUid> uids) {
            return deletedMessageDAO.addDeleted(cassandraId, uids);
        }

        @Override
        public TargetFlag targetFlag() {
            return TargetFlag.DELETED;
        }
    }

    public record SoleMailboxRecentFlagInconsistenciesStrategy(CassandraMailboxRecentsDAO mailboxRecentDAO) implements SolveMessageFlagInconsistencyStrategy {
        @Override
        public Predicate<CassandraMessageMetadata> filterOutFlagInconsistencies() {
            return metaData -> metaData.getComposedMessageId().getFlags().contains(Flags.Flag.RECENT);
        }

        @Override
        public Mono<Void> removeAllByMailboxId(CassandraId cassandraId) {
            return mailboxRecentDAO.delete(cassandraId);
        }

        @Override
        public Mono<Void> addEntry(CassandraId cassandraId, List<MessageUid> uids) {
            return mailboxRecentDAO.addToRecent(cassandraId, uids);
        }

        @Override
        public TargetFlag targetFlag() {
            return TargetFlag.RECENT;
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(SolveMailboxFlagInconsistenciesService.class);

    private final Set<SolveMessageFlagInconsistencyStrategy> fixInconsistenciesStrategies;
    private final CassandraMessageIdDAO messageIdDAO;
    private final CassandraMailboxDAO mailboxDAO;

    @Inject
    public SolveMailboxFlagInconsistenciesService(Set<SolveMessageFlagInconsistencyStrategy> fixInconsistenciesStrategies,
                                                  CassandraMessageIdDAO messageIdDAO,
                                                  CassandraMailboxDAO mailboxDAO) {
        this.messageIdDAO = messageIdDAO;
        this.fixInconsistenciesStrategies = fixInconsistenciesStrategies;
        this.mailboxDAO = mailboxDAO;
    }

    public Mono<Task.Result> fixInconsistencies(Context context, TargetFlag targetFlag) {
        SolveMessageFlagInconsistencyStrategy fixInconsistencyStrategy = getFixInconsistencyStrategy(targetFlag);
        return mailboxDAO.retrieveAllMailboxes()
            .concatMap(mailbox -> fixDeletedMessagesInconsistencyPerMailbox(fixInconsistencyStrategy, (CassandraId) mailbox.getMailboxId(), context)
                .doOnNext(any -> context.incrementProcessedMailboxEntries()))
            .reduce(Task.Result.COMPLETED, Task::combine);
    }

    private Mono<Task.Result> fixDeletedMessagesInconsistencyPerMailbox(SolveMessageFlagInconsistencyStrategy fixInconsistenciesStrategy, CassandraId cassandraId, Context context) {
        return fixInconsistenciesStrategy.removeAllByMailboxId(cassandraId)
            .then(messageIdDAO.retrieveMessages(cassandraId, MessageRange.all(), Limit.unlimited())
                .filter(fixInconsistenciesStrategy.filterOutFlagInconsistencies())
                .map(metadata -> metadata.getComposedMessageId().getComposedMessageId().getUid())
                .collectList()
                .filter(uids -> !uids.isEmpty())
                .flatMap(uids -> fixInconsistenciesStrategy.addEntry(cassandraId, uids)))
            .thenReturn(Task.Result.COMPLETED)
            .onErrorResume(e -> {
                LOGGER.error("Error while fixing inconsistencies for mailbox {}", cassandraId, e);
                context.addError(cassandraId);
                return Mono.just(Task.Result.PARTIAL);
            });
    }

    private SolveMessageFlagInconsistencyStrategy getFixInconsistencyStrategy(TargetFlag targetFlag) {
        return fixInconsistenciesStrategies.stream()
            .filter(strategy -> strategy.targetFlag() == targetFlag)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("No strategy found for " + targetFlag));
    }
}
