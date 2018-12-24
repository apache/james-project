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

package org.apache.james.event.json;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.apache.james.mailbox.model.MailboxConstants.USER_NAMESPACE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.NoSuchElementException;
import java.util.Optional;

import org.apache.james.core.User;
import org.apache.james.core.quota.QuotaCount;
import org.apache.james.core.quota.QuotaSize;
import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.model.TestMessageId;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MailboxDeletionSerializationTest {

    private static final MailboxSession.SessionId SESSION_ID = MailboxSession.SessionId.of(3652);
    private static final User USER = User.fromUsername("user");
    private static final MailboxPath MAILBOX_PATH = new MailboxPath(USER_NAMESPACE, USER.asString(), "mailboxName");
    private static final MailboxId MAILBOX_ID = TestId.of(789);
    private static final QuotaRoot QUOTA_ROOT = QuotaRoot.quotaRoot("user@domain", Optional.empty());
    private static final QuotaCount DELETED_MESSAGE_COUNT = QuotaCount.count(60);
    private static final QuotaSize TOTAL_DELETED_SIZE = QuotaSize.size(100);
    private static final MailboxListener.MailboxDeletion DEFAULT_MAILBOX_DELETION_EVENT = new MailboxListener.MailboxDeletion(
        SESSION_ID,
        USER,
        MAILBOX_PATH,
        QUOTA_ROOT,
        DELETED_MESSAGE_COUNT,
        TOTAL_DELETED_SIZE,
        MAILBOX_ID);

    private static final String DEFAULT_EVEN_JSON =
        "{" +
        "  \"MailboxDeletion\":{" +
        "    \"sessionId\":3652," +
        "    \"user\":\"user\"," +
        "    \"path\":{" +
        "      \"namespace\":\"#private\"," +
        "      \"user\":\"user\"," +
        "      \"name\":\"mailboxName\"" +
        "    }," +
        "    \"quotaRoot\":\"user@domain\"," +
        "    \"deletedMessageCount\":60," +
        "    \"totalDeletedSize\":100," +
        "    \"mailboxId\":\"789\"" +
        "  }" +
        "}";

    private static final EventSerializer EVENT_SERIALIZER = new EventSerializer(new TestId.Factory(), new TestMessageId.Factory());

    @Test
    void mailboxAddedShouldBeWellSerialized() {
        assertThatJson(EVENT_SERIALIZER.toJson(DEFAULT_MAILBOX_DELETION_EVENT))
            .isEqualTo(DEFAULT_EVEN_JSON);
    }

    @Test
    void mailboxAddedShouldBeWellDeSerialized() {
        assertThat(EVENT_SERIALIZER.fromJson(DEFAULT_EVEN_JSON).get())
            .isEqualTo(DEFAULT_MAILBOX_DELETION_EVENT);
    }

    @Nested
    class NullOrEmptyNameSpaceInMailboxPath {

        @Test
        void mailboxAddedShouldBeWellDeSerializedWhenEmptyNameSpace() {
            assertThat(EVENT_SERIALIZER.fromJson(
                "{" +
                "  \"MailboxDeletion\":{" +
                "    \"sessionId\":3652," +
                "    \"user\":\"user\"," +
                "    \"path\":{" +
                "      \"namespace\":\"\"," +
                "      \"user\":\"user\"," +
                "      \"name\":\"mailboxName\"" +
                "    }," +
                "    \"quotaRoot\":\"user@domain\"," +
                "    \"deletedMessageCount\":60," +
                "    \"totalDeletedSize\":100," +
                "    \"mailboxId\":\"789\"" +
                "  }" +
                "}").get())
            .isEqualTo(DEFAULT_MAILBOX_DELETION_EVENT);
        }
    }

    @Nested
    class NullUserInMailboxPath {

        private final String nulUser = null;
        private final MailboxListener.MailboxDeletion nullUserInMailboxPathEvent = new MailboxListener.MailboxDeletion(
                SESSION_ID,
                USER,
                new MailboxPath(USER_NAMESPACE, nulUser, "mailboxName"),
                QUOTA_ROOT,
                DELETED_MESSAGE_COUNT,
                TOTAL_DELETED_SIZE,
                MAILBOX_ID);
        private final String nullUserMailboxEventJson =
            "{" +
            "  \"MailboxDeletion\":{" +
            "    \"sessionId\":3652," +
            "    \"user\":\"user\"," +
            "    \"path\":{" +
            "      \"namespace\":\"#private\"," +
            "      \"name\":\"mailboxName\"" +
            "    }," +
            "    \"quotaRoot\":\"user@domain\"," +
            "    \"deletedMessageCount\":60," +
            "    \"totalDeletedSize\":100," +
            "    \"mailboxId\":\"789\"" +
            "  }" +
            "}";

        @Test
        void mailboxAddedShouldBeWellSerializedWhenNullUserInMailboxPath() {
            assertThatJson(EVENT_SERIALIZER.toJson(nullUserInMailboxPathEvent))
                .isEqualTo(nullUserMailboxEventJson);
        }

        @Test
        void mailboxAddedShouldBeWellDeSerializedWhenNullUserInMailboxPath() {
            assertThat(EVENT_SERIALIZER.fromJson(nullUserMailboxEventJson).get())
                .isEqualTo(nullUserInMailboxPathEvent);
        }
    }

    @Nested
    class EmptyQuotaRoot {
        private final MailboxListener.MailboxDeletion emptyQuotaRootEvent = new MailboxListener.MailboxDeletion(
                SESSION_ID,
                USER,
                MAILBOX_PATH,
                QuotaRoot.quotaRoot("", Optional.empty()),
                DELETED_MESSAGE_COUNT,
                TOTAL_DELETED_SIZE,
                MAILBOX_ID);
        private final String nullUserMailboxEventJson =
            "{" +
            "  \"MailboxDeletion\":{" +
            "    \"sessionId\":3652," +
            "    \"user\":\"user\"," +
            "    \"path\":{" +
            "      \"namespace\":\"#private\"," +
            "      \"user\":\"user\"," +
            "      \"name\":\"mailboxName\"" +
            "    }," +
            "    \"quotaRoot\":\"\"," +
            "    \"deletedMessageCount\":60," +
            "    \"totalDeletedSize\":100," +
            "    \"mailboxId\":\"789\"" +
            "  }" +
            "}";

        @Test
        void mailboxAddedShouldBeWellSerializedWhenEmptyQuotaRoot() {
            assertThatJson(EVENT_SERIALIZER.toJson(emptyQuotaRootEvent))
                .isEqualTo(nullUserMailboxEventJson);
        }

        @Test
        void mailboxAddedShouldBeWellDeSerializedWhenEmptyQuotaRoot() {
            assertThat(EVENT_SERIALIZER.fromJson(nullUserMailboxEventJson).get())
                .isEqualTo(emptyQuotaRootEvent);
        }
    }

    @Nested
    class NullQuotaCountInDeletedMessageCount {
        private final MailboxListener.MailboxDeletion unlimitedQuotaCountDeletedMessageEvent = new MailboxListener.MailboxDeletion(
                SESSION_ID,
                USER,
                MAILBOX_PATH,
                QUOTA_ROOT,
                QuotaCount.unlimited(),
                TOTAL_DELETED_SIZE,
                MAILBOX_ID);
        private final String nullQuotaCountInDeletedMessageCountEventJson =
            "{" +
            "  \"MailboxDeletion\":{" +
            "    \"sessionId\":3652," +
            "    \"user\":\"user\"," +
            "    \"path\":{" +
            "      \"namespace\":\"#private\"," +
            "      \"user\":\"user\"," +
            "      \"name\":\"mailboxName\"" +
            "    }," +
            "    \"quotaRoot\":\"user@domain\"," +
            "    \"deletedMessageCount\":null," +
            "    \"totalDeletedSize\":100," +
            "    \"mailboxId\":\"789\"" +
            "  }" +
            "}";

        @Test
        void mailboxAddedShouldBeWellSerializedWhenNullQuotaCount() {
            assertThatJson(EVENT_SERIALIZER.toJson(unlimitedQuotaCountDeletedMessageEvent))
                .isEqualTo(nullQuotaCountInDeletedMessageCountEventJson);
        }

        @Test
        void mailboxAddedShouldBeWellDeSerializedWhenNullQuotaCount() {
            assertThat(EVENT_SERIALIZER.fromJson(nullQuotaCountInDeletedMessageCountEventJson).get())
                .isEqualTo(unlimitedQuotaCountDeletedMessageEvent);
        }
    }

    @Nested
    class NullQuotaSizeInTotalDeletedSize {
        private final MailboxListener.MailboxDeletion unlimitedQuotaSizeDeletedSizeEvent = new MailboxListener.MailboxDeletion(
                SESSION_ID,
                USER,
                MAILBOX_PATH,
                QUOTA_ROOT,
                DELETED_MESSAGE_COUNT,
                QuotaSize.unlimited(),
                MAILBOX_ID);
        private final String nullQuotaSizeInTotalDeletedMessageEventJson =
            "{" +
            "  \"MailboxDeletion\":{" +
            "    \"sessionId\":3652," +
            "    \"user\":\"user\"," +
            "    \"path\":{" +
            "      \"namespace\":\"#private\"," +
            "      \"user\":\"user\"," +
            "      \"name\":\"mailboxName\"" +
            "    }," +
            "    \"quotaRoot\":\"user@domain\"," +
            "    \"deletedMessageCount\":60," +
            "    \"totalDeletedSize\":null," +
            "    \"mailboxId\":\"789\"" +
            "  }" +
            "}";

        @Test
        void mailboxAddedShouldBeWellSerializedWhenNullQuotaSize() {
            assertThatJson(EVENT_SERIALIZER.toJson(unlimitedQuotaSizeDeletedSizeEvent))
                .isEqualTo(nullQuotaSizeInTotalDeletedMessageEventJson);
        }

        @Test
        void mailboxAddedShouldBeWellDeSerializedWhenNullQuotaSize() {
            assertThat(EVENT_SERIALIZER.fromJson(nullQuotaSizeInTotalDeletedMessageEventJson).get())
                .isEqualTo(unlimitedQuotaSizeDeletedSizeEvent);
        }
    }

    @Nested
    class DeserializationErrors {
        @Test
        void mailboxAddedShouldThrowWhenMissingSessionId() {
            assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                "{" +
                    "  \"MailboxDeletion\":{" +
                    "    \"user\":\"user\"," +
                    "    \"path\":{" +
                    "      \"namespace\":\"#private\"," +
                    "      \"user\":\"user\"," +
                    "      \"name\":\"mailboxName\"" +
                    "    }," +
                    "    \"quotaRoot\":\"user@domain\"," +
                    "    \"deletedMessageCount\":60," +
                    "    \"totalDeletedSize\":100," +
                    "    \"mailboxId\":\"789\"" +
                    "  }" +
                    "}").get())
                .isInstanceOf(NoSuchElementException.class);
        }

        @Nested
        class DeserializationErrorOnUser {
            @Test
            void mailboxAddedShouldThrowWhenMissingUser() {
                assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                    "{" +
                    "  \"MailboxDeletion\":{" +
                    "    \"sessionId\":3652," +
                    "    \"path\":{" +
                    "      \"namespace\":\"#private\"," +
                    "      \"user\":\"user\"," +
                    "      \"name\":\"mailboxName\"" +
                    "    }," +
                    "    \"quotaRoot\":\"user@domain\"," +
                    "    \"deletedMessageCount\":60," +
                    "    \"totalDeletedSize\":100," +
                    "    \"mailboxId\":\"789\"" +
                    "  }" +
                    "}").get())
                .isInstanceOf(NoSuchElementException.class);
            }

            @Test
            void mailboxAddedShouldThrowWhenUserIsNotAString() {
                assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                    "{" +
                    "  \"MailboxDeletion\":{" +
                    "    \"sessionId\":3652," +
                    "    \"user\":5489515," +
                    "    \"path\":{" +
                    "      \"namespace\":\"#private\"," +
                    "      \"user\":\"user\"," +
                    "      \"name\":\"mailboxName\"" +
                    "    }," +
                    "    \"quotaRoot\":\"user@domain\"," +
                    "    \"deletedMessageCount\":60," +
                    "    \"totalDeletedSize\":100," +
                    "    \"mailboxId\":\"789\"" +
                    "  }" +
                    "}").get())
                .isInstanceOf(NoSuchElementException.class);
            }

            @Test
            void mailboxAddedShouldThrowWhenUserIsNotWellFormatted() {
                assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                    "{" +
                    "  \"MailboxDeletion\":{" +
                    "    \"sessionId\":3652," +
                    "    \"user\":\"user@domain@secondDomain\"," +
                    "    \"path\":{" +
                    "      \"namespace\":\"#private\"," +
                    "      \"user\":\"user\"," +
                    "      \"name\":\"mailboxName\"" +
                    "    }," +
                    "    \"quotaRoot\":\"user@domain\"," +
                    "    \"deletedMessageCount\":60," +
                    "    \"totalDeletedSize\":100," +
                    "    \"mailboxId\":\"789\"" +
                    "  }" +
                    "}").get())
                .isInstanceOf(IllegalArgumentException.class);
            }
        }

        @Nested
        class DeserializationErrorOnQuotaRoot {
            @Test
            void mailboxAddedShouldThrowWhenMissingQuotaRoot() {
                assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                    "{" +
                    "  \"MailboxDeletion\":{" +
                    "    \"sessionId\":3652," +
                    "    \"user\":\"user\"," +
                    "    \"path\":{" +
                    "      \"namespace\":\"#private\"," +
                    "      \"user\":\"user\"," +
                    "      \"name\":\"mailboxName\"" +
                    "    }," +
                    "    \"deletedMessageCount\":60," +
                    "    \"totalDeletedSize\":100," +
                    "    \"mailboxId\":\"789\"" +
                    "  }" +
                    "}").get())
                .isInstanceOf(NoSuchElementException.class);
            }

            @Test
            void mailboxAddedShouldThrowWhenNullQuotaRoot() {
                assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                    "{" +
                    "  \"MailboxDeletion\":{" +
                    "    \"sessionId\":3652," +
                    "    \"user\":\"user\"," +
                    "    \"path\":{" +
                    "      \"namespace\":\"#private\"," +
                    "      \"user\":\"user\"," +
                    "      \"name\":\"mailboxName\"" +
                    "    }," +
                    "    \"quotaRoot\":null," +
                    "    \"deletedMessageCount\":60," +
                    "    \"totalDeletedSize\":100," +
                    "    \"mailboxId\":\"789\"" +
                    "  }" +
                    "}").get())
                .isInstanceOf(NoSuchElementException.class);
            }

            @Test
            void mailboxAddedShouldThrowWhenQuotaRootIsNotAString() {
                assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                    "{" +
                    "  \"MailboxDeletion\":{" +
                    "    \"sessionId\":3652," +
                    "    \"user\":\"user\"," +
                    "    \"path\":{" +
                    "      \"namespace\":\"#private\"," +
                    "      \"user\":\"user\"," +
                    "      \"name\":\"mailboxName\"" +
                    "    }," +
                    "    \"quotaRoot\":123456," +
                    "    \"deletedMessageCount\":60," +
                    "    \"totalDeletedSize\":100," +
                    "    \"mailboxId\":\"789\"" +
                    "  }" +
                    "}").get())
                .isInstanceOf(NoSuchElementException.class);
            }
        }

        @Nested
        class DeserializationErrorOnDeletedMessageCount {
            @Test
            void mailboxAddedShouldThrowWhenMissingQuotaCount() {
                assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                    "{" +
                    "  \"MailboxDeletion\":{" +
                    "    \"sessionId\":3652," +
                    "    \"user\":\"user\"," +
                    "    \"path\":{" +
                    "      \"namespace\":\"#private\"," +
                    "      \"user\":\"user\"," +
                    "      \"name\":\"mailboxName\"" +
                    "    }," +
                    "    \"quotaRoot\":\"user@domain\"," +
                    "    \"totalDeletedSize\":100," +
                    "    \"mailboxId\":\"789\"" +
                    "  }" +
                    "}").get())
                .isInstanceOf(NoSuchElementException.class);
            }

            @Test
            void mailboxAddedShouldThrowWhenQuotaCountIsNotANumber() {
                assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                    "{" +
                    "  \"MailboxDeletion\":{" +
                    "    \"sessionId\":3652," +
                    "    \"user\":\"user\"," +
                    "    \"path\":{" +
                    "      \"namespace\":\"#private\"," +
                    "      \"user\":\"user\"," +
                    "      \"name\":\"mailboxName\"" +
                    "    }," +
                    "    \"quotaRoot\":\"user@domain\"," +
                    "    \"deletedMessageCount\":\"60\"," +
                    "    \"totalDeletedSize\":100," +
                    "    \"mailboxId\":\"789\"" +
                    "  }" +
                    "}").get())
                .isInstanceOf(NoSuchElementException.class);
            }
        }

        @Nested
        class DeserializationErrorOnTotalDeletedSize {
            @Test
            void mailboxAddedShouldThrowWhenMissingQuotaSize() {
                assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                    "{" +
                    "  \"MailboxDeletion\":{" +
                    "    \"sessionId\":3652," +
                    "    \"user\":\"user\"," +
                    "    \"path\":{" +
                    "      \"namespace\":\"#private\"," +
                    "      \"user\":\"user\"," +
                    "      \"name\":\"mailboxName\"" +
                    "    }," +
                    "    \"quotaRoot\":\"user@domain\"," +
                    "    \"deletedMessageCount\":60," +
                    "    \"mailboxId\":\"789\"" +
                    "  }" +
                    "}").get())
                .isInstanceOf(NoSuchElementException.class);
            }

            @Test
            void mailboxAddedShouldThrowWhenQuotaSizeIsNotANumber() {
                assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                    "{" +
                    "  \"MailboxDeletion\":{" +
                    "    \"sessionId\":3652," +
                    "    \"user\":\"user\"," +
                    "    \"path\":{" +
                    "      \"namespace\":\"#private\"," +
                    "      \"user\":\"user\"," +
                    "      \"name\":\"mailboxName\"" +
                    "    }," +
                    "    \"quotaRoot\":\"user@domain\"," +
                    "    \"deletedMessageCount\":60," +
                    "    \"totalDeletedSize\":\"100\"," +
                    "    \"mailboxId\":\"789\"" +
                    "  }" +
                    "}").get())
                .isInstanceOf(NoSuchElementException.class);
            }
        }

        @Nested
        class DeserializationErrorOnMailboxId {
            @Test
            void mailboxAddedShouldThrowWhenMissingMailboxId() {
                assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                    "{" +
                    "  \"MailboxDeletion\":{" +
                    "    \"sessionId\":3652," +
                    "    \"user\":\"user\"," +
                    "    \"path\":{" +
                    "      \"namespace\":\"#private\"," +
                    "      \"user\":\"user\"," +
                    "      \"name\":\"mailboxName\"" +
                    "    }," +
                    "    \"quotaRoot\":\"user@domain\"," +
                    "    \"deletedMessageCount\":60," +
                    "    \"totalDeletedSize\":100" +
                    "  }" +
                    "}").get())
                .isInstanceOf(NoSuchElementException.class);
            }

            @Test
            void mailboxAddedShouldThrowWhenNullMailboxId() {
                assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                    "{" +
                    "  \"MailboxDeletion\":{" +
                    "    \"sessionId\":3652," +
                    "    \"user\":\"user\"," +
                    "    \"path\":{" +
                    "      \"namespace\":\"#private\"," +
                    "      \"user\":\"user\"," +
                    "      \"name\":\"mailboxName\"" +
                    "    }," +
                    "    \"quotaRoot\":\"user@domain\"," +
                    "    \"deletedMessageCount\":60," +
                    "    \"totalDeletedSize\":100," +
                    "    \"mailboxId\":null" +
                    "  }" +
                    "}").get())
                .isInstanceOf(NoSuchElementException.class);
            }

            @Test
            void mailboxAddedShouldThrowWhenMailboxIdIsANumber() {
                assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                    "{" +
                    "  \"MailboxDeletion\":{" +
                    "    \"sessionId\":3652," +
                    "    \"user\":\"user\"," +
                    "    \"path\":{" +
                    "      \"namespace\":\"#private\"," +
                    "      \"user\":\"user\"," +
                    "      \"name\":\"mailboxName\"" +
                    "    }," +
                    "    \"quotaRoot\":\"user@domain\"," +
                    "    \"deletedMessageCount\":60," +
                    "    \"totalDeletedSize\":100," +
                    "    \"mailboxId\":789" +
                    "  }" +
                    "}").get())
                .isInstanceOf(NoSuchElementException.class);
            }
        }

        @Nested
        class DeserializationErrorOnMailboxPath {

            @Nested
            class DeserializationErrorOnNameSpace {
                @Test
                void mailboxAddedShouldThrowWhenNameSpaceIsNotAString() {
                    assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                        "{" +
                        "  \"MailboxDeletion\":{" +
                        "    \"sessionId\":3652," +
                        "    \"user\":\"user\"," +
                        "    \"path\":{" +
                        "      \"namespace\":4268.548," +
                        "      \"user\":\"user\"," +
                        "      \"name\":\"mailBoxName\"" +
                        "    }," +
                        "    \"quotaRoot\":\"user@domain\"," +
                        "    \"deletedMessageCount\":60," +
                        "    \"totalDeletedSize\":100," +
                        "    \"mailboxId\":\"789\"" +
                        "  }" +
                        "}").get())
                    .isInstanceOf(NoSuchElementException.class);
                }
            }

            @Nested
            class DeserializationErrorOnUser {
                @Test
                void mailboxAddedShouldThrowWhenUserIsNotAString() {
                    assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                        "{" +
                        "  \"MailboxDeletion\":{" +
                        "    \"sessionId\":3652," +
                        "    \"user\":\"user\"," +
                        "    \"path\":{" +
                        "      \"namespace\":\"#private\"," +
                        "      \"user\":153274," +
                        "      \"name\":\"mailBoxName\"" +
                        "    }," +
                        "    \"quotaRoot\":\"user@domain\"," +
                        "    \"deletedMessageCount\":60," +
                        "    \"totalDeletedSize\":100," +
                        "    \"mailboxId\":\"789\"" +
                        "  }" +
                        "}").get())
                    .isInstanceOf(NoSuchElementException.class);
                }
            }

            @Nested
            class DeserializationErrorOnMailboxName {

                @Test
                void mailboxAddedShouldThrowWhenNullMailboxName() {
                    assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                        "{" +
                        "  \"MailboxDeletion\":{" +
                        "    \"sessionId\":3652," +
                        "    \"user\":\"user\"," +
                        "    \"path\":{" +
                        "      \"namespace\":\"#private\"," +
                        "      \"user\":\"user\"," +
                        "      \"name\":null" +
                        "    }," +
                        "    \"quotaRoot\":\"user@domain\"," +
                        "    \"deletedMessageCount\":60," +
                        "    \"totalDeletedSize\":100," +
                        "    \"mailboxId\":\"789\"" +
                        "  }" +
                        "}").get())
                    .isInstanceOf(NoSuchElementException.class);
                }

                @Test
                void mailboxAddedShouldThrowWhenMailboxNameIdIsANumber() {
                    assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                        "{" +
                        "  \"MailboxDeletion\":{" +
                        "    \"sessionId\":3652," +
                        "    \"user\":\"user\"," +
                        "    \"path\":{" +
                        "      \"namespace\":\"#private\"," +
                        "      \"user\":\"user\"," +
                        "      \"name\":4578" +
                        "    }," +
                        "    \"quotaRoot\":\"user@domain\"," +
                        "    \"deletedMessageCount\":60," +
                        "    \"totalDeletedSize\":100," +
                        "    \"mailboxId\":\"789\"" +
                        "  }" +
                        "}").get())
                    .isInstanceOf(NoSuchElementException.class);
                }
            }
        }
    }
}
