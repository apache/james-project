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

package org.apache.james.jmap.api.projections;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.util.streams.Limit;

import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;

public class EmailQueryViewUtils {
    private static final int COLLAPSE_THREADS_LIMIT_MULTIPLIER = 4;

    public static class EmailEntry {
        private final MessageId messageId;
        private final ThreadId threadId;
        private final Instant messageDate;

        public EmailEntry(MessageId messageId, ThreadId threadId, Instant messageDate) {
            this.messageId = messageId;
            this.threadId = threadId;
            this.messageDate = messageDate;
        }

        public MessageId getMessageId() {
            return messageId;
        }

        public ThreadId getThreadId() {
            return threadId;
        }

        public Instant getMessageDate() {
            return messageDate;
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof EmailEntry) {
                EmailEntry entry = (EmailEntry) o;

                return Objects.equals(this.messageId, entry.messageId)
                    && Objects.equals(this.threadId, entry.threadId)
                    && Objects.equals(this.messageDate, entry.messageDate);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(messageId, threadId, messageDate);
        }
    }

    public static Limit backendLimitFetch(Limit limit, boolean collapseThreads) {
        if (collapseThreads) {
            return Limit.limit(limit.getLimit().get() * COLLAPSE_THREADS_LIMIT_MULTIPLIER);
        }
        return limit;
    }

    public static Flux<MessageId> messagesWithCollapseThreads(Limit limit, Limit backendFetchLimit, Flux<EmailEntry> baseEntries, Function<Limit, Flux<MessageId>> listMessagesCallbackFunction) {
        return baseEntries.collectList()
            .flatMapMany(results -> {
                List<EmailEntry> distinctByThreadId = distinctByThreadId(results);
                boolean hasEnoughResults = distinctByThreadId.size() >= limit.getLimit().get();
                boolean isExhaustive = results.size() < backendFetchLimit.getLimit().get();
                if (hasEnoughResults || isExhaustive) {
                    return Flux.fromIterable(distinctByThreadId)
                        .take(limit.getLimit().get())
                        .map(EmailEntry::getMessageId);
                }
                Limit newBackendFetchLimit = Limit.from(backendFetchLimit.getLimit().get() * COLLAPSE_THREADS_LIMIT_MULTIPLIER);
                return listMessagesCallbackFunction.apply(newBackendFetchLimit);
            });
    }

    private static List<EmailEntry> distinctByThreadId(List<EmailEntry> emailEntries) {
        ImmutableList.Builder<EmailEntry> list = ImmutableList.builder();
        HashSet<ThreadId> threadIdHashSet = new HashSet<>();
        emailEntries.forEach(emailEntry -> {
            if (threadIdHashSet.add(emailEntry.getThreadId())) {
                list.add(emailEntry);
            }
        });
        return list.build();
    }
}
