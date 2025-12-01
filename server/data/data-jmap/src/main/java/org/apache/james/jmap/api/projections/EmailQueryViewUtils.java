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
import java.util.function.Function;

import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.util.streams.Limit;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;

public class EmailQueryViewUtils {
    private static final int COLLAPSE_THREADS_LIMIT_MULTIPLIER = 4;

    public record EmailEntry(MessageId messageId, ThreadId threadId, Instant messageDate) {

    }

    public interface QueryViewExtender {
        static QueryViewExtender of(Limit initialLimit, boolean collapseThread) {
            Preconditions.checkArgument(!initialLimit.isUnlimited(), "Limit should be defined");
            if (!collapseThread) {
                return new NoThreadCollapsing(initialLimit);
            }
            Limit backendFetchLimit = Limit.limit(initialLimit.getLimit().get() * COLLAPSE_THREADS_LIMIT_MULTIPLIER);
            return new WithTreadCollapsing(initialLimit, backendFetchLimit);
        }

        Flux<MessageId> resolve(Function<Limit, Flux<EmailEntry>> fetchMoreResults);

        class NoThreadCollapsing implements QueryViewExtender {
            private final Limit limit;

            public NoThreadCollapsing(Limit limit) {
                this.limit = limit;
            }

            @Override
            public Flux<MessageId> resolve(Function<Limit, Flux<EmailEntry>> fetchMoreResults) {
                return fetchMoreResults.apply(limit).map(EmailEntry::messageId);
            }
        }

        class WithTreadCollapsing implements QueryViewExtender {
            private final Limit initialLimit;
            private final Limit backendFetchLimit;

            private WithTreadCollapsing(Limit initialLimit, Limit backendFetchLimit) {
                this.initialLimit = initialLimit;
                this.backendFetchLimit = backendFetchLimit;
            }

            @Override
            public Flux<MessageId> resolve(Function<Limit, Flux<EmailEntry>> fetchMoreResults) {
                return fetchMoreResults.apply(backendFetchLimit)
                    .collectList()
                    .flatMapMany(results -> {
                        List<EmailEntry> distinctByThreadId = distinctByThreadId(results);
                        boolean hasEnoughResults = distinctByThreadId.size() >= initialLimit.getLimit().get();
                        boolean isExhaustive = results.size() < backendFetchLimit.getLimit().get();
                        if (hasEnoughResults || isExhaustive) {
                            return Flux.fromIterable(distinctByThreadId)
                                .take(initialLimit.getLimit().get())
                                .map(EmailEntry::messageId);
                        }
                        return increaseBackendetchLimit().resolve(fetchMoreResults);
                    });
            }

            private WithTreadCollapsing increaseBackendetchLimit() {
                return new WithTreadCollapsing(initialLimit, Limit.from(backendFetchLimit.getLimit().get() * COLLAPSE_THREADS_LIMIT_MULTIPLIER));
            }

            private List<EmailEntry> distinctByThreadId(List<EmailEntry> emailEntries) {
                ImmutableList.Builder<EmailEntry> list = ImmutableList.builder();
                HashSet<ThreadId> threadIdHashSet = new HashSet<>();
                emailEntries.forEach(emailEntry -> {
                    if (threadIdHashSet.add(emailEntry.threadId())) {
                        list.add(emailEntry);
                    }
                });
                return list.build();
            }
        }
    }
}
