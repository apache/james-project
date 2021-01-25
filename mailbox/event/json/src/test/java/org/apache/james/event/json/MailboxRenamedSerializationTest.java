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
import static org.apache.james.event.json.SerializerFixture.EVENT_ID;
import static org.apache.james.event.json.SerializerFixture.EVENT_SERIALIZER;
import static org.apache.james.mailbox.model.MailboxConstants.USER_NAMESPACE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.NoSuchElementException;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.events.MailboxEvents.MailboxRenamed;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.TestId;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MailboxRenamedSerializationTest {

    private static final Username DEFAULT_USERNAME = Username.of("user");
    private static final String OLD_MAILBOX_NAME = "oldMailboxName";
    private static final MailboxPath DEFAULT_OLD_MAILBOX_PATH = new MailboxPath(USER_NAMESPACE, DEFAULT_USERNAME, OLD_MAILBOX_NAME);
    private static final String NEW_MAILBOX_NAME = "newMailboxName";
    private static final MailboxPath DEFAULT_NEW_MAILBOX_PATH = new MailboxPath(USER_NAMESPACE, DEFAULT_USERNAME, NEW_MAILBOX_NAME);
    private static final MailboxSession.SessionId DEFAULT_SESSION_ID = MailboxSession.SessionId.of(123456789);
    private static final MailboxId DEFAULT_MAILBOX_ID = TestId.of(123456);
    private static final MailboxRenamed DEFAULT_MAILBOX_RENAMED_EVENT = new MailboxRenamed(
        DEFAULT_SESSION_ID,
        DEFAULT_USERNAME,
        DEFAULT_OLD_MAILBOX_PATH,
        DEFAULT_MAILBOX_ID,
        DEFAULT_NEW_MAILBOX_PATH,
        EVENT_ID);

    private static final String DEFAULT_MAILBOX_RENAMED_EVENT_JSON =
            "{" +
            "  \"MailboxRenamed\":{" +
            "    \"eventId\":\"6e0dd59d-660e-4d9b-b22f-0354479f47b4\"," +
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
    class DeserializationErrors {
        @Test
        void mailboxRenamedDeSerializeShouldThrowWhenMissingUser() {
            assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                "{" +
                    "  \"MailboxRenamed\":{" +
                    "    \"eventId\":\"6e0dd59d-660e-4d9b-b22f-0354479f47b4\"," +
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
                    "    \"eventId\":\"6e0dd59d-660e-4d9b-b22f-0354479f47b4\"," +
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

        @Test
        void mailboxRenamedDeSerializeShouldThrowWhenMissingEventId() {
            String eventWithNullSessionId =
                "{" +
                    "  \"MailboxRenamed\":{" +
                    "    \"sessionId\":42," +
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

        @Test
        void mailboxRenamedDeSerializeShouldThrowWhenMissingMailboxId() {
            assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                "{" +
                    "  \"MailboxRenamed\":{" +
                    "    \"eventId\":\"6e0dd59d-660e-4d9b-b22f-0354479f47b4\"," +
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

        @Test
        void mailboxRenamedDeSerializeShouldThrowWhenMissingOldPath() {
            assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                "{" +
                    "  \"MailboxRenamed\":{" +
                    "    \"eventId\":\"6e0dd59d-660e-4d9b-b22f-0354479f47b4\"," +
                    "    \"sessionId\":123456789," +
                    "    \"user\":\"user\"," +
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
        void mailboxRenamedDeSerializeShouldThrowWhenMissingNewPath() {
            assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                "{" +
                    "  \"MailboxRenamed\":{" +
                    "    \"eventId\":\"6e0dd59d-660e-4d9b-b22f-0354479f47b4\"," +
                    "    \"sessionId\":123456789," +
                    "    \"user\":\"user\"," +
                    "    \"path\":{" +
                    "      \"namespace\":\"#private\"," +
                    "      \"user\":\"user\"," +
                    "      \"name\":\"oldMailboxName\"" +
                    "    }," +
                    "    \"mailboxId\":123456" +
                    "  }" +
                    "}").get())
                .isInstanceOf(NoSuchElementException.class);
        }
    }
}
