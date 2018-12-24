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

import org.apache.james.core.User;
import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.model.TestMessageId;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MailboxRenamedSerializationTest {

    private static final User DEFAULT_USER = User.fromUsername("user");
    private static final String OLD_MAILBOX_NAME = "oldMailboxName";
    private static final MailboxPath DEFAULT_OLD_MAILBOX_PATH = new MailboxPath(USER_NAMESPACE, DEFAULT_USER.asString(), OLD_MAILBOX_NAME);
    private static final String NEW_MAILBOX_NAME = "newMailboxName";
    private static final MailboxPath DEFAULT_NEW_MAILBOX_PATH = new MailboxPath(USER_NAMESPACE, DEFAULT_USER.asString(), NEW_MAILBOX_NAME);
    private static final MailboxSession.SessionId DEFAULT_SESSION_ID = MailboxSession.SessionId.of(123456789);
    private static final MailboxId DEFAULT_MAILBOX_ID = TestId.of(123456);
    private static final MailboxListener.MailboxRenamed DEFAULT_MAILBOX_RENAMED_EVENT = new MailboxListener.MailboxRenamed(
        DEFAULT_SESSION_ID,
        DEFAULT_USER,
        DEFAULT_OLD_MAILBOX_PATH,
        DEFAULT_MAILBOX_ID,
        DEFAULT_NEW_MAILBOX_PATH);

    private static final String DEFAULT_MAILBOX_RENAMED_EVENT_JSON =
            "{" +
            "  \"MailboxRenamed\":{" +
            "    \"sessionId\":123456789," +
            "    \"user\":\"user\"," +
            "    \"path\":{" +
            "      \"namespace\":\"#private\"," +
            "      \"user\":\"user\"," +
            "      \"name\":\"oldMailboxName\"" +
            "     }," +
            "    \"mailboxId\":\"123456\"," +
            "    \"newPath\":{" +
            "      \"namespace\":\"#private\"," +
            "      \"user\":\"user\"," +
            "      \"name\":\"newMailboxName\"" +
            "     }" +
            "  }" +
            "}";

    private static final EventSerializer EVENT_SERIALIZER = new EventSerializer(new TestId.Factory(), new TestMessageId.Factory());

    @Test
    void mailboxRenamedShouldBeWellSerialized() {
        assertThatJson(EVENT_SERIALIZER.toJson(DEFAULT_MAILBOX_RENAMED_EVENT))
            .isEqualTo(DEFAULT_MAILBOX_RENAMED_EVENT_JSON);
    }

    @Test
    void mailboxRenamedShouldBeWellDeSerialized() {
        assertThat(EVENT_SERIALIZER.fromJson(DEFAULT_MAILBOX_RENAMED_EVENT_JSON).get())
            .isEqualTo(DEFAULT_MAILBOX_RENAMED_EVENT);
    }

    @Nested
    class NullUserInMailboxPath {

        @Nested
        class NullUserInOldPath {
            private final String nullUser = null;
            private final MailboxListener.MailboxRenamed eventWithNullUserOldPath = new MailboxListener.MailboxRenamed(
                DEFAULT_SESSION_ID,
                DEFAULT_USER,
                new MailboxPath(USER_NAMESPACE, nullUser, OLD_MAILBOX_NAME),
                DEFAULT_MAILBOX_ID,
                DEFAULT_NEW_MAILBOX_PATH);

            private static final String EVENT_JSON_WITH_NULL_USER_OLD_PATH = "{" +
                    "  \"MailboxRenamed\":{" +
                    "    \"sessionId\":123456789," +
                    "    \"user\":\"user\"," +
                    "    \"path\":{" +
                    "      \"namespace\":\"#private\"," +
                    "      \"name\":\"oldMailboxName\"" +
                    "     }," +
                    "    \"mailboxId\":\"123456\"," +
                    "    \"newPath\":{" +
                    "      \"namespace\":\"#private\"," +
                    "      \"user\":\"user\"," +
                    "      \"name\":\"newMailboxName\"" +
                    "     }" +
                    "  }" +
                    "}";

            @Test
            void mailboxRenamedShouldBeWellSerialized() {
                assertThatJson(EVENT_SERIALIZER.toJson(eventWithNullUserOldPath))
                    .isEqualTo(EVENT_JSON_WITH_NULL_USER_OLD_PATH);
            }

            @Test
            void mailboxRenamedShouldBeWellDeSerialized() {
                assertThat(EVENT_SERIALIZER.fromJson(EVENT_JSON_WITH_NULL_USER_OLD_PATH).get())
                    .isEqualTo(eventWithNullUserOldPath);
            }
        }

        @Nested
        class NullUserInNewPath {
            private final String nullUser = null;
            private final MailboxListener.MailboxRenamed eventWithNullUserNewPath = new MailboxListener.MailboxRenamed(
                DEFAULT_SESSION_ID,
                DEFAULT_USER,
                DEFAULT_OLD_MAILBOX_PATH,
                DEFAULT_MAILBOX_ID,
                new MailboxPath(USER_NAMESPACE, nullUser, NEW_MAILBOX_NAME));

            private static final String EVENT_JSON_WITH_NULL_USER_NEW_PATH = "{" +
                    "  \"MailboxRenamed\":{" +
                    "    \"sessionId\":123456789," +
                    "    \"user\":\"user\"," +
                    "    \"path\":{" +
                    "      \"namespace\":\"#private\"," +
                    "      \"user\":\"user\"," +
                    "      \"name\":\"oldMailboxName\"" +
                    "     }," +
                    "    \"mailboxId\":\"123456\"," +
                    "    \"newPath\":{" +
                    "      \"namespace\":\"#private\"," +
                    "      \"name\":\"newMailboxName\"" +
                    "     }" +
                    "  }" +
                    "}";

            @Test
            void mailboxRenamedShouldBeWellSerialized() {
                assertThatJson(EVENT_SERIALIZER.toJson(eventWithNullUserNewPath))
                    .isEqualTo(EVENT_JSON_WITH_NULL_USER_NEW_PATH);
            }

            @Test
            void mailboxRenamedShouldBeWellDeSerialized() {
                assertThat(EVENT_SERIALIZER.fromJson(EVENT_JSON_WITH_NULL_USER_NEW_PATH).get())
                    .isEqualTo(eventWithNullUserNewPath);
            }
        }

        @Nested
        class NullUserInOldPathAndNewPath {
            private final String nullUser = null;
            private final MailboxListener.MailboxRenamed eventWithNullUserBothPath = new MailboxListener.MailboxRenamed(
                DEFAULT_SESSION_ID,
                DEFAULT_USER,
                new MailboxPath(USER_NAMESPACE, nullUser, OLD_MAILBOX_NAME),
                DEFAULT_MAILBOX_ID,
                new MailboxPath(USER_NAMESPACE, nullUser, NEW_MAILBOX_NAME));

            private static final String EVENT_JSON_WITH_NULL_USER_BOTH_PATH =
                    "{" +
                    "  \"MailboxRenamed\":{" +
                    "    \"sessionId\":123456789," +
                    "    \"user\":\"user\"," +
                    "    \"path\":{" +
                    "      \"namespace\":\"#private\"," +
                    "      \"name\":\"oldMailboxName\"" +
                    "     }," +
                    "    \"mailboxId\":\"123456\"," +
                    "    \"newPath\":{" +
                    "      \"namespace\":\"#private\"," +
                    "      \"name\":\"newMailboxName\"" +
                    "     }" +
                    "  }" +
                    "}";

            @Test
            void mailboxRenamedShouldBeWellSerialized() {
                assertThatJson(EVENT_SERIALIZER.toJson(eventWithNullUserBothPath))
                    .isEqualTo(EVENT_JSON_WITH_NULL_USER_BOTH_PATH);
            }

            @Test
            void mailboxRenamedShouldBeWellDeSerialized() {
                assertThat(EVENT_SERIALIZER.fromJson(EVENT_JSON_WITH_NULL_USER_BOTH_PATH).get())
                    .isEqualTo(eventWithNullUserBothPath);
            }
        }
    }

    @Nested
    class EmptyNameSpaceInMailboxPath {

        @Test
        void mailboxRenamedShouldBeWellDeSerializedWhenEmptyNameSpaceOldPath() {
            assertThat(EVENT_SERIALIZER.fromJson(
                "{" +
                "  \"MailboxRenamed\":{" +
                "    \"sessionId\":123456789," +
                "    \"user\":\"user\"," +
                "    \"path\":{" +
                "      \"user\":\"user\"," +
                "      \"namespace\":\"\"," +
                "      \"name\":\"oldMailboxName\"" +
                "     }," +
                "    \"mailboxId\":\"123456\"," +
                "    \"newPath\":{" +
                "      \"namespace\":\"#private\"," +
                "      \"user\":\"user\"," +
                "      \"name\":\"newMailboxName\"" +
                "     }" +
                "  }" +
                "}").get())
            .isEqualTo(DEFAULT_MAILBOX_RENAMED_EVENT);
        }

        @Test
        void mailboxRenamedShouldBeWellDeSerializedWhenEmptyNameSpaceNewPath() {
            assertThat(EVENT_SERIALIZER.fromJson(
                "{" +
                "  \"MailboxRenamed\":{" +
                "    \"sessionId\":123456789," +
                "    \"user\":\"user\"," +
                "    \"path\":{" +
                "      \"user\":\"user\"," +
                "      \"namespace\":\"#private\"," +
                "      \"name\":\"oldMailboxName\"" +
                "     }," +
                "    \"mailboxId\":\"123456\"," +
                "    \"newPath\":{" +
                "      \"namespace\":\"\"," +
                "      \"user\":\"user\"," +
                "      \"name\":\"newMailboxName\"" +
                "     }" +
                "  }" +
                "}").get())
            .isEqualTo(DEFAULT_MAILBOX_RENAMED_EVENT);
        }

        @Test
        void mailboxRenamedShouldBeWellDeSerializedWhenEmptyNameSpaceBothPath() {
            assertThat(EVENT_SERIALIZER.fromJson(
                "{" +
                "  \"MailboxRenamed\":{" +
                "    \"sessionId\":123456789," +
                "    \"user\":\"user\"," +
                "    \"path\":{" +
                "      \"user\":\"user\"," +
                "      \"namespace\":\"\"," +
                "      \"name\":\"oldMailboxName\"" +
                "     }," +
                "    \"mailboxId\":\"123456\"," +
                "    \"newPath\":{" +
                "      \"namespace\":\"\"," +
                "      \"user\":\"user\"," +
                "      \"name\":\"newMailboxName\"" +
                "     }" +
                "  }" +
                "}").get())
            .isEqualTo(DEFAULT_MAILBOX_RENAMED_EVENT);
        }
    }

    @Nested
    class DeserializationErrors {
        @Test
        void mailboxRenamedDeSerializeShouldThrowWhenMissingUser() {
            assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                "{" +
                    "  \"MailboxRenamed\":{" +
                    "    \"sessionId\":123456789," +
                    "    \"path\":{" +
                    "      \"namespace\":\"#private\"," +
                    "      \"user\":\"user\"," +
                    "      \"name\":\"oldMailboxName\"" +
                    "     }," +
                    "    \"mailboxId\":\"123456\"," +
                    "    \"newPath\":{" +
                    "      \"namespace\":\"#private\"," +
                    "      \"user\":\"user\"," +
                    "      \"name\":\"newMailboxName\"" +
                    "     }" +
                    "  }" +
                    "}").get())
                .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        void mailboxRenamedDeSerializeShouldThrowWhenMissingSessionId() {
            String eventWithNullSessionId =
                "{" +
                    "  \"MailboxRenamed\":{" +
                    "    \"user\":\"user\"," +
                    "    \"path\":{" +
                    "      \"namespace\":\"#private\"," +
                    "      \"user\":\"user\"," +
                    "      \"name\":\"oldMailboxName\"" +
                    "     }," +
                    "    \"mailboxId\":\"123456\"," +
                    "    \"newPath\":{" +
                    "      \"namespace\":\"#private\"," +
                    "      \"user\":\"user\"," +
                    "      \"name\":\"newMailboxName\"" +
                    "     }" +
                    "  }" +
                    "}";
            assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(eventWithNullSessionId).get())
                .isInstanceOf(NoSuchElementException.class);
        }

        @Nested
        class DeserializationErrorOnMailBoxId {
            @Test
            void mailboxRenamedDeSerializeShouldThrowWhenNumberMailboxId() {
                assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                    "{" +
                    "  \"MailboxRenamed\":{" +
                    "    \"sessionId\":123456789," +
                    "    \"user\":\"user\"," +
                    "    \"path\":{" +
                    "      \"namespace\":\"#private\"," +
                    "      \"user\":\"user\"," +
                    "      \"name\":\"oldMailboxName\"" +
                    "    }," +
                    "    \"mailboxId\":123456," +
                    "    \"newPath\":{" +
                    "      \"namespace\":\"#private\"," +
                    "      \"user\":\"user\"," +
                    "      \"name\":\"newMailboxName\"" +
                    "     }" +
                    "  }" +
                    "}").get())
                .isInstanceOf(NoSuchElementException.class);
            }

            @Test
            void mailboxRenamedDeSerializeShouldThrowWhenNullMailboxId() {
                assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                    "{" +
                    "  \"MailboxRenamed\":{" +
                    "    \"sessionId\":123456789," +
                    "    \"user\":\"user\"," +
                    "    \"path\":{" +
                    "      \"namespace\":\"#private\"," +
                    "      \"user\":\"user\"," +
                    "      \"name\":\"oldMailboxName\"" +
                    "    }," +
                    "    \"newPath\":{" +
                    "      \"namespace\":\"#private\"," +
                    "      \"user\":\"user\"," +
                    "      \"name\":\"newMailboxName\"" +
                    "     }" +
                    "  }" +
                    "}").get())
                .isInstanceOf(NoSuchElementException.class);
            }
        }

        @Nested
        class DeserializationErrorOnOldMailboxPath {

            @Test
            void mailboxRenamedDeSerializeShouldThrowWhenMissingOldPathNameSpace() {
                assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                    "{" +
                    "  \"MailboxRenamed\":{" +
                    "    \"sessionId\":123456789," +
                    "    \"user\":\"user\"," +
                    "    \"path\":{" +
                    "      \"user\":\"user\"," +
                    "      \"name\":\"oldMailboxName\"" +
                    "    }," +
                    "    \"mailboxId\":123456," +
                    "    \"newPath\":{" +
                    "      \"namespace\":\"#private\"," +
                    "      \"user\":\"user\"," +
                    "      \"name\":\"newMailboxName\"" +
                    "     }" +
                    "  }" +
                    "}").get())
                .isInstanceOf(NoSuchElementException.class);
            }

            @Test
            void mailboxRenamedDeSerializeShouldThrowWhenOldPathNameSpaceNotString() {
                assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                    "{" +
                    "  \"MailboxRenamed\":{" +
                    "    \"sessionId\":123456789," +
                    "    \"user\":\"user\"," +
                    "    \"path\":{" +
                    "      \"namespace\":999," +
                    "      \"user\":\"user\"," +
                    "      \"name\":\"oldMailboxName\"" +
                    "    }," +
                    "    \"mailboxId\":123456," +
                    "    \"newPath\":{" +
                    "      \"namespace\":\"#private\"," +
                    "      \"user\":\"user\"," +
                    "      \"name\":\"newMailboxName\"" +
                    "     }" +
                    "  }" +
                    "}").get())
                .isInstanceOf(NoSuchElementException.class);
            }

            @Test
            void mailboxRenamedDeSerializeShouldThrowWhenMissingOldPathUser() {
                assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                    "{" +
                    "  \"MailboxRenamed\":{" +
                    "    \"sessionId\":123456789," +
                    "    \"user\":\"user\"," +
                    "    \"path\":{" +
                    "      \"namespace\":\"#private\"," +
                    "      \"name\":\"oldMailboxName\"" +
                    "    }," +
                    "    \"mailboxId\":123456," +
                    "    \"newPath\":{" +
                    "      \"namespace\":\"#private\"," +
                    "      \"user\":\"user\"," +
                    "      \"name\":\"newMailboxName\"" +
                    "     }" +
                    "  }" +
                    "}").get())
                .isInstanceOf(NoSuchElementException.class);
            }

            @Test
            void mailboxRenamedDeSerializeShouldThrowWhenNotStringOldPathUser() {
                assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                    "{" +
                    "  \"MailboxRenamed\":{" +
                    "    \"sessionId\":123456789," +
                    "    \"user\":\"user\"," +
                    "    \"path\":{" +
                    "      \"namespace\":\"#private\"," +
                    "      \"user\":666," +
                    "      \"name\":\"oldMailboxName\"" +
                    "    }," +
                    "    \"mailboxId\":123456," +
                    "    \"newPath\":{" +
                    "      \"namespace\":\"#private\"," +
                    "      \"user\":\"user\"," +
                    "      \"name\":\"newMailboxName\"" +
                    "     }" +
                    "  }" +
                    "}").get())
                .isInstanceOf(NoSuchElementException.class);
            }

            @Test
            void mailboxRenamedDeSerializeShouldThrowWhenMissingOldPathName() {
                assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                    "{" +
                    "  \"MailboxRenamed\":{" +
                    "    \"sessionId\":123456789," +
                    "    \"user\":\"user\"," +
                    "    \"path\":{" +
                    "      \"namespace\":\"#private\"," +
                    "      \"user\":\"user\"" +
                    "    }," +
                    "    \"mailboxId\":123456," +
                    "    \"newPath\":{" +
                    "      \"namespace\":\"#private\"," +
                    "      \"user\":\"user\"," +
                    "      \"name\":\"newMailboxName\"" +
                    "     }" +
                    "  }" +
                    "}").get())
                .isInstanceOf(NoSuchElementException.class);
            }

            @Test
            void mailboxRenamedDeSerializeShouldThrowWhenNotStringOldPathName() {
                assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                    "{" +
                    "  \"MailboxRenamed\":{" +
                    "    \"sessionId\":123456789," +
                    "    \"user\":\"user\"," +
                    "    \"path\":{" +
                    "      \"namespace\":\"#private\"," +
                    "      \"user\":\"user\"," +
                    "      \"name\":1456" +
                    "    }," +
                    "    \"mailboxId\":123456," +
                    "    \"newPath\":{" +
                    "      \"namespace\":\"#private\"," +
                    "      \"user\":\"user\"," +
                    "      \"name\":\"newMailboxName\"" +
                    "     }" +
                    "  }" +
                    "}").get())
                .isInstanceOf(NoSuchElementException.class);
            }

            @Test
            void mailboxRenamedDeSerializeShouldThrowWhenMissingNewPathNameSpace() {
                assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                    "{" +
                    "  \"MailboxRenamed\":{" +
                    "    \"sessionId\":123456789," +
                    "    \"user\":\"user\"," +
                    "    \"path\":{" +
                    "      \"namespace\":\"#private\"," +
                    "      \"user\":\"user\"," +
                    "      \"name\":\"oldMailboxName\"" +
                    "    }," +
                    "    \"mailboxId\":123456," +
                    "    \"newPath\":{" +
                    "      \"user\":\"user\"," +
                    "      \"name\":\"newMailboxName\"" +
                    "     }" +
                    "  }" +
                    "}").get())
                .isInstanceOf(NoSuchElementException.class);
            }

            @Test
            void mailboxRenamedDeSerializeShouldThrowWhenNewPathNameSpaceNotString() {
                String eventWithNumberMailboxId =
                    "{" +
                    "  \"MailboxRenamed\":{" +
                    "    \"sessionId\":123456789," +
                    "    \"user\":\"user\"," +
                    "    \"path\":{" +
                    "      \"namespace\":\"#private\"," +
                    "      \"user\":\"user\"," +
                    "      \"name\":\"oldMailboxName\"" +
                    "    }," +
                    "    \"mailboxId\":123456," +
                    "    \"newPath\":{" +
                    "      \"namespace\":999," +
                    "      \"user\":\"user\"," +
                    "      \"name\":\"newMailboxName\"" +
                    "     }" +
                    "  }" +
                    "}";
                assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(eventWithNumberMailboxId).get())
                    .isInstanceOf(NoSuchElementException.class);
            }

            @Test
            void mailboxRenamedDeSerializeShouldThrowWhenMissingNewPathUser() {
                assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                    "{" +
                    "  \"MailboxRenamed\":{" +
                    "    \"sessionId\":123456789," +
                    "    \"user\":\"user\"," +
                    "    \"path\":{" +
                    "      \"namespace\":\"#private\"," +
                    "      \"user\":\"user\"," +
                    "      \"name\":\"oldMailboxName\"" +
                    "    }," +
                    "    \"mailboxId\":123456," +
                    "    \"newPath\":{" +
                    "      \"namespace\":\"#private\"," +
                    "      \"name\":\"newMailboxName\"" +
                    "     }" +
                    "  }" +
                    "}").get())
                .isInstanceOf(NoSuchElementException.class);
            }

            @Test
            void mailboxRenamedDeSerializeShouldThrowWhenNotStringNewPathUser() {
                assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                    "{" +
                    "  \"MailboxRenamed\":{" +
                    "    \"sessionId\":123456789," +
                    "    \"user\":\"user\"," +
                    "    \"path\":{" +
                    "      \"namespace\":\"#private\"," +
                    "      \"user\":\"user\"," +
                    "      \"name\":\"oldMailboxName\"" +
                    "    }," +
                    "    \"mailboxId\":123456," +
                    "    \"newPath\":{" +
                    "      \"namespace\":\"#private\"," +
                    "      \"user\":4569," +
                    "      \"name\":\"newMailboxName\"" +
                    "     }" +
                    "  }" +
                    "}").get())
                .isInstanceOf(NoSuchElementException.class);
            }

            @Test
            void mailboxRenamedDeSerializeShouldThrowWhenMissingNewPathName() {
                assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                    "{" +
                    "  \"MailboxRenamed\":{" +
                    "    \"sessionId\":123456789," +
                    "    \"user\":\"user\"," +
                    "    \"path\":{" +
                    "      \"namespace\":\"#private\"," +
                    "      \"user\":\"user\"," +
                    "      \"name\":\"newMailboxName\"" +
                    "    }," +
                    "    \"mailboxId\":123456," +
                    "    \"newPath\":{" +
                    "      \"namespace\":\"#private\"," +
                    "      \"user\":\"user\"" +
                    "     }" +
                    "  }" +
                    "}").get())
                .isInstanceOf(NoSuchElementException.class);
            }

            @Test
            void mailboxRenamedDeSerializeShouldThrowWhenNotStringNewPathName() {
                assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                    "{" +
                    "  \"MailboxRenamed\":{" +
                    "    \"sessionId\":123456789," +
                    "    \"user\":\"user\"," +
                    "    \"path\":{" +
                    "      \"namespace\":\"#private\"," +
                    "      \"user\":\"user\"," +
                    "      \"name\":\"newMailboxName\"" +
                    "    }," +
                    "    \"mailboxId\":123456," +
                    "    \"newPath\":{" +
                    "      \"namespace\":\"#private\"," +
                    "      \"user\":\"user\"," +
                    "      \"name\":7529" +
                    "     }" +
                    "  }" +
                    "}").get())
                .isInstanceOf(NoSuchElementException.class);
            }
        }
    }
}
