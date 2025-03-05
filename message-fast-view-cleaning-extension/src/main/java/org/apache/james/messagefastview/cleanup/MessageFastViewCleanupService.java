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

package org.apache.james.messagefastview.cleanup;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.james.core.Username;
import org.apache.james.jmap.api.projections.MessageFastViewProjection;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.SessionProvider;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.task.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import jakarta.inject.Inject;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class MessageFastViewCleanupService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MessageFastViewCleanupService.class);

    public static class RunningOptions {
        public static RunningOptions of(int messageIdsPerSecond) {
            return new RunningOptions(messageIdsPerSecond);
        }

        public static RunningOptions withMessageIdsPerSecond(int messageIdsPerSecond) {
            return new RunningOptions(messageIdsPerSecond);
        }

        public static final int DEFAULT_MESSAGE_IDS_PER_SECOND = 1000;
        public static final RunningOptions DEFAULT = of(DEFAULT_MESSAGE_IDS_PER_SECOND);

        private final int messageIdsPerSecond;

        private RunningOptions(int messageIdsPerSecond) {
            Preconditions.checkArgument(messageIdsPerSecond > 0, "'messageIdsPerSecond' needs to be strictly positive");

            this.messageIdsPerSecond = messageIdsPerSecond;
        }

        public int getMessageIdsPerSecond() {
            return messageIdsPerSecond;
        }
    }

    public static class Context {
        static class Snapshot {
            private final long deletedMessageFastViewCount;

            private Snapshot(long deletedMessageFastViewCount) {
                this.deletedMessageFastViewCount = deletedMessageFastViewCount;
            }

            long getDeletedMessageFastViewCount() {
                return deletedMessageFastViewCount;
            }

            @Override
            public final boolean equals(Object o) {
                if (o instanceof Snapshot) {
                    Snapshot snapshot = (Snapshot) o;

                    return Objects.equals(this.deletedMessageFastViewCount, snapshot.deletedMessageFastViewCount);
                }
                return false;
            }

            @Override
            public final int hashCode() {
                return Objects.hash(deletedMessageFastViewCount);
            }

            @Override
            public String toString() {
                return MoreObjects.toStringHelper(this)
                    .add("deletedMessageFastViewCount", deletedMessageFastViewCount)
                    .toString();
            }
        }

        private final AtomicLong processedMailboxCount;
        private final ConcurrentLinkedDeque<UUID> failedMailboxes;

        public Context() {
            processedMailboxCount = new AtomicLong();
            failedMailboxes = new ConcurrentLinkedDeque<>();
        }

        void incrementProcessed() {
            processedMailboxCount.incrementAndGet();
        }

        void addToFailedMailboxes(UUID cassandraId) {
            failedMailboxes.add(cassandraId);
        }

        Snapshot snapshot() {
            return new Snapshot(processedMailboxCount.get());
        }
    }

    private final MessageFastViewProjection messageFastViewProjection;
    private final MessageIdManager messageIdManager;
    private final SessionProvider sessionProvider;

    @Inject
    public MessageFastViewCleanupService(MessageFastViewProjection messageFastViewProjection, MessageIdManager messageIdManager, SessionProvider sessionProvide) {
        this.messageFastViewProjection = messageFastViewProjection;
        this.messageIdManager = messageIdManager;
        this.sessionProvider = sessionProvide;
    }

    public Mono<Task.Result> cleanup(Context context, RunningOptions runningOptions) {
        return Flux.from(messageFastViewProjection.getAllMessageIds())
            .flatMap(messageId ->
                Flux.from(messageIdManager.getMessagesReactive(ImmutableList.of(messageId), FetchGroup.MINIMAL, sessionProvider.createSystemSession(Username.of("MessageFastViewCleanup"))))
                    .singleOrEmpty()
                    .thenReturn(messageId),     // TODO: use BloomFilter
                runningOptions.messageIdsPerSecond)
            .flatMap(messageId -> Mono.from(messageFastViewProjection.delete(messageId)))
            .then()
            .thenReturn(Task.Result.COMPLETED)
            .onErrorResume(Exception.class, e -> {
                LOGGER.error("Error while accessing users from repository", e);
                return Mono.just(Task.Result.PARTIAL);
            });
    }
}
