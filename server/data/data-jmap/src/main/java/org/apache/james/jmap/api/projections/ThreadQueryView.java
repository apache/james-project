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

import java.time.ZonedDateTime;
import java.util.Objects;

import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.util.streams.Limit;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ThreadQueryView {
    class Entry {
        private final MailboxId mailboxId;
        private final ThreadId threadId;
        private final ZonedDateTime sentAt;
        private final ZonedDateTime receivedAt;

        public Entry(MailboxId mailboxId, ThreadId threadId, ZonedDateTime sentAt, ZonedDateTime receivedAt) {
            this.mailboxId = mailboxId;
            this.threadId = threadId;
            this.sentAt = sentAt;
            this.receivedAt = receivedAt;
        }

        public MailboxId getMailboxId() {
            return mailboxId;
        }

        public ThreadId getThreadId() {
            return threadId;
        }

        public ZonedDateTime getSentAt() {
            return sentAt;
        }

        public ZonedDateTime getReceivedAt() {
            return receivedAt;
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof Entry) {
                Entry entry = (Entry) o;

                return Objects.equals(this.mailboxId, entry.mailboxId)
                    && Objects.equals(this.threadId, entry.threadId)
                    && Objects.equals(this.sentAt, entry.sentAt)
                    && Objects.equals(this.receivedAt, entry.receivedAt);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(mailboxId, threadId, sentAt, receivedAt);
        }
    }

    /**
     *
     * Sample JMAP requests:
     *
     *    - RFC-8621:
     *
     *    ["Thread/query",
     *     {
     *       "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
     *       "filter: {
     *           "inMailbox":"abcd"
     *       }
     *       "position":0,
     *       "limit":10,
     *     },
     *     "c1"]
     *
     *   - Draft
     *
     *   [["getThreadList", {"filter":{"inMailboxes": ["abcd"]},"position":0,"limit":10, , "#0"]]
     *
     * @return ThreadIds of the Threads in this mailbox, sorted by sentAt.
     */
    Flux<ThreadId> listLatestThreadIdsSortedByReceivedAt(MailboxId mailboxId, Limit limit);

  
    Mono<Void> delete(MailboxId mailboxId, ThreadId threadId);

    Mono<Void> delete(MailboxId mailboxId);

    Mono<Void> save(MailboxId mailboxId, ZonedDateTime sentAt, ZonedDateTime receivedAt, ThreadId threadId);
}
