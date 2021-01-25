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
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.NoSuchElementException;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.events.MailboxEvents.MailboxAdded;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.TestId;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MailboxAddedSerializationTest {
    private static final Username USERNAME = Username.of("user");
    private static final MailboxAdded EVENT_1 = new MailboxAdded(
        MailboxSession.SessionId.of(42),
        USERNAME,
        new MailboxPath(MailboxConstants.USER_NAMESPACE, Username.of("bob"), "mailboxName"),
        TestId.of(18),
        EVENT_ID);
    private static final String JSON_1 = "{" +
        "  \"MailboxAdded\":{" +
        "    \"eventId\":\"6e0dd59d-660e-4d9b-b22f-0354479f47b4\"," +
        "    \"mailboxPath\":{" +
        "      \"namespace\":\"#private\"," +
        "      \"user\":\"bob\"," +
        "      \"name\":\"mailboxName\"" +
        "     }," +
        "     \"mailboxId\":\"18\"," +
        "     \"user\":\"user\"," +
        "     \"sessionId\":42" +
        "  }" +
        "}";

    @Test
    void mailboxAddedShouldBeWellSerialized() {
        assertThatJson(EVENT_SERIALIZER.toJson(EVENT_1))
            .isEqualTo(JSON_1);
    }

    @Test
    void mailboxAddedShouldBeWellDeSerialized() {
        assertThat(EVENT_SERIALIZER.fromJson(JSON_1).get())
            .isEqualTo(EVENT_1);
    }

    @Nested
    class DeserializationErrors {

        @Test
        void fromJsonShouldRejectMissingSessionId() {
            assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson("{" +
                "  \"MailboxAdded\":{" +
                "    \"eventId\":\"6e0dd59d-660e-4d9b-b22f-0354479f47b4\"," +
                "    \"mailboxPath\":{" +
                "      \"namespace\":\"#private\"," +
                "      \"user\":\"bob\"" +
                "     }," +
                "     \"mailboxId\":\"18\"," +
                "     \"user\":\"user\"," +
                "     \"sessionId\":18" +
                "  }" +
                "}").get())
                .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        void fromJsonShouldRejectMissingEventId() {
            assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson("{" +
                "  \"MailboxAdded\":{" +
                "    \"sessionId\":42," +
                "    \"mailboxPath\":{" +
                "      \"namespace\":\"#private\"," +
                "      \"user\":\"bob\"" +
                "     }," +
                "     \"mailboxId\":\"18\"," +
                "     \"user\":\"user\"," +
                "     \"sessionId\":18" +
                "  }" +
                "}").get())
                .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        void fromJsonShouldRejectMissingMailboxId() {
            assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson("{" +
                "  \"MailboxAdded\":{" +
                "    \"eventId\":\"6e0dd59d-660e-4d9b-b22f-0354479f47b4\"," +
                "    \"mailboxPath\":{" +
                "      \"namespace\":\"#private\"," +
                "      \"user\":\"bob\"," +
                "      \"name\":\"mailboxName\"" +
                "     }," +
                "     \"user\":\"user\"," +
                "     \"sessionId\":18" +
                "  }" +
                "}").get())
                .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        void fromJsonShouldRejectMissingUser() {
            assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson("{" +
                "  \"MailboxAdded\":{" +
                "    \"eventId\":\"6e0dd59d-660e-4d9b-b22f-0354479f47b4\"," +
                "    \"mailboxPath\":{" +
                "      \"namespace\":\"#private\"," +
                "      \"user\":\"bob\"," +
                "      \"name\":\"mailboxName\"" +
                "     }," +
                "     \"mailboxId\":\"18\"," +
                "     \"sessionId\":18" +
                "  }" +
                "}").get())
                .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        void fromJsonShouldRejectMissingMailboxPath() {
            assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson("{" +
                "  \"MailboxAdded\":{" +
                "    \"eventId\":\"6e0dd59d-660e-4d9b-b22f-0354479f47b4\"," +
                "     \"mailboxId\":\"18\"," +
                "     \"user\":\"user\"," +
                "     \"sessionId\":18" +
                "  }" +
                "}").get())
                .isInstanceOf(NoSuchElementException.class);
        }
    }
}
