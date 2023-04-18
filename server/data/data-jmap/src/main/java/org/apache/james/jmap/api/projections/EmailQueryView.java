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
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.util.streams.Limit;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface EmailQueryView {
    class Entry {
        private final MailboxId mailboxId;
        private final MessageId messageId;
        private final ZonedDateTime sentAt;
        private final ZonedDateTime receivedAt;

        public Entry(MailboxId mailboxId, MessageId messageId, ZonedDateTime sentAt, ZonedDateTime receivedAt) {
            this.mailboxId = mailboxId;
            this.messageId = messageId;
            this.sentAt = sentAt;
            this.receivedAt = receivedAt;
        }

        public MailboxId getMailboxId() {
            return mailboxId;
        }

        public MessageId getMessageId() {
            return messageId;
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
                    && Objects.equals(this.messageId, entry.messageId)
                    && Objects.equals(this.sentAt, entry.sentAt)
                    && Objects.equals(this.receivedAt, entry.receivedAt);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(mailboxId, messageId, sentAt, receivedAt);
        }
    }

    /**
     *
     * Sample JMAP requests:
     *
     *    - RFC-8621:
     *
     *    ["Email/query",
     *     {
     *       "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
     *       "filter: {
     *           "inMailbox":"abcd"
     *       }
     *       "sort": [{
     *         "property":"sentAt",
     *         "isAscending": false
     *       }]
     *     },
     *     "c1"]
     *
     *   - Draft
     *
     *   [["getMessageList", {"filter":{"inMailboxes": ["abcd"]}, "sort": ["date desc"]}, "#0"]]
     *
     * @return messageIds of the messages in this mailbox, sorted by sentAt.
     */
    Flux<MessageId> listMailboxContentSortedBySentAt(MailboxId mailboxId, Limit limit);

    /**
     *
     * Sample JMAP requests:
     *
     *    - RFC-8621:
     *
     *    ["Email/query",
     *     {
     *       "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
     *       "filter: {
     *           "inMailbox":"abcd"
     *       }
     *       "sort": [{
     *         "property":"receivedAt",
     *         "isAscending": false
     *       }]
     *     },
     *     "c1"]
     *
     * @return messageIds of the messages in this mailbox, sorted by receivedAt.
     */
    Flux<MessageId> listMailboxContentSortedByReceivedAt(MailboxId mailboxId, Limit limit);

    /**
     *  Sample JMAP requests:
     *
     *      - RFC-8621:
     *
     *    ["Email/query",
     *     {
     *       "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
     *       "filter: {
     *           "inMailbox":"abcd",
     *           "after":"aDate"
     *       }
     *       "sort": [{
     *         "property":"sentAt",
     *         "isAscending": false
     *       }]
     *     },
     *     "c1"]
     *
     * @return messageIds of the messages in this mailbox, since being "after". Sorted by sentAt.
     */
    Flux<MessageId> listMailboxContentSinceAfterSortedBySentAt(MailboxId mailboxId, ZonedDateTime since, Limit limit);

    /**
     *  Sample JMAP requests:
     *
     *      - RFC-8621:
     *
     *    ["Email/query",
     *     {
     *       "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
     *       "filter: {
     *           "inMailbox":"abcd",
     *           "after":"aDate"
     *       }
     *       "sort": [{
     *         "property":"receivedAt",
     *         "isAscending": false
     *       }]
     *     },
     *     "c1"]
     *
     * @return messageIds of the messages in this mailbox, since being "after". Sorted by receivedAt.
     */
    Flux<MessageId> listMailboxContentSinceAfterSortedByReceivedAt(MailboxId mailboxId, ZonedDateTime since, Limit limit);

    /**
     *  Sample JMAP requests:
     *
     *      - RFC-8621:
     *
     *    ["Email/query",
     *     {
     *       "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
     *       "filter: {
     *           "inMailbox":"abcd",
     *           "before":"aDate"
     *       }
     *       "sort": [{
     *         "property":"receivedAt",
     *         "isAscending": false
     *       }]
     *     },
     *     "c1"]
     *
     * @return messageIds of the messages in this mailbox, since being "after". Sorted by receivedAt.
     */
    Flux<MessageId> listMailboxContentBeforeSortedByReceivedAt(MailboxId mailboxId, ZonedDateTime since, Limit limit);

    /**
     *  Sample JMAP requests:
     *
     *   - Draft
     *
     *   [["getMessageList", {"filter":{"after":"aDate", "inMailboxes": ["abcd"]}, "sort": ["date desc"]}, "#0"]]
     *
     * @return messageIds of the messages in this mailbox, sorted by sentAt, since being sentAt
     */
    Flux<MessageId> listMailboxContentSinceSentAt(MailboxId mailboxId, ZonedDateTime since, Limit limit);

    Mono<Void> delete(MailboxId mailboxId, MessageId messageId);

    Mono<Void> delete(MailboxId mailboxId);

    Mono<Void> save(MailboxId mailboxId, ZonedDateTime sentAt, ZonedDateTime receivedAt, MessageId messageId);
}
