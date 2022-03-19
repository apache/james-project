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

import java.util.List;
import java.util.NoSuchElementException;

import jakarta.mail.Flags;

import org.apache.james.core.Username;
import org.apache.james.mailbox.FlagsBuilder;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.events.MailboxEvents.FlagsUpdated;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.model.TestMessageId;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

import net.javacrumbs.jsonunit.core.Option;

class FlagsUpdatedSerializationTest {

    private static final Username USERNAME = Username.of("user");
    private static final MailboxSession.SessionId SESSION_ID = MailboxSession.SessionId.of(42);
    private static final MailboxId MAILBOX_ID = TestId.of(18);
    private static final String MAILBOX_NAME = "mailboxName";
    private static final MailboxPath MAILBOX_PATH = new MailboxPath(MailboxConstants.USER_NAMESPACE, Username.of("user"), MAILBOX_NAME);
    private static final MessageUid MESSAGE_UID_1 = MessageUid.of(123456);
    private static final MessageUid MESSAGE_UID_2 = MessageUid.of(654321);

    private static final ModSeq MOD_SEQ_1 = ModSeq.of(35);
    private static final Flags OLD_FLAGS_1 = FlagsBuilder.builder()
        .add(Flags.Flag.SEEN, Flags.Flag.DELETED)
        .add("Old Flag 1")
        .build();
    private static final Flags NEW_FLAGS_1 = FlagsBuilder.builder()
        .add(Flags.Flag.ANSWERED, Flags.Flag.DRAFT)
        .add("New Flag 1")
        .build();
    private static UpdatedFlags UPDATED_FLAG_1 = UpdatedFlags.builder()
        .uid(MESSAGE_UID_1)
        .modSeq(MOD_SEQ_1)
        .oldFlags(OLD_FLAGS_1)
        .newFlags(NEW_FLAGS_1)
        .build();

    private static final ModSeq MOD_SEQ_2 = ModSeq.of(36);
    private static final Flags OLD_FLAGS_2 = FlagsBuilder.builder()
        .add(Flags.Flag.RECENT, Flags.Flag.FLAGGED)
        .add("Old Flag 2")
        .build();
    private static final Flags NEW_FLAGS_2 = FlagsBuilder.builder()
        .add(Flags.Flag.SEEN, Flags.Flag.ANSWERED)
        .add("New Flag 2")
        .build();
    private static UpdatedFlags UPDATED_FLAG_2 = UpdatedFlags.builder()
        .uid(MESSAGE_UID_2)
        .modSeq(MOD_SEQ_2)
        .oldFlags(OLD_FLAGS_2)
        .newFlags(NEW_FLAGS_2)
        .build();

    private static List<UpdatedFlags> UPDATED_FLAGS_LIST = ImmutableList.of(UPDATED_FLAG_1, UPDATED_FLAG_2);

    private static final FlagsUpdated DEFAULT_EVENT = new FlagsUpdated(SESSION_ID, USERNAME,
        MAILBOX_PATH, MAILBOX_ID, UPDATED_FLAGS_LIST, EVENT_ID);
    private static final String DEFAULT_EVENT_JSON =
        "{" +
        "  \"FlagsUpdated\": {" +
        "    \"eventId\":\"6e0dd59d-660e-4d9b-b22f-0354479f47b4\"," +
        "    \"path\": {" +
        "      \"namespace\": \"#private\"," +
        "      \"user\": \"user\"," +
        "      \"name\": \"mailboxName\"" +
        "    }," +
        "    \"mailboxId\": \"18\"," +
        "    \"sessionId\": 42," +
        "    \"updatedFlags\": [" +
        "      {" +
        "        \"uid\": 123456," +
        "        \"modSeq\": 35," +
        "        \"oldFlags\": {\"systemFlags\":[\"Deleted\",\"Seen\"],\"userFlags\":[\"Old Flag 1\"]}," +
        "        \"newFlags\": {\"systemFlags\":[\"Answered\",\"Draft\"],\"userFlags\":[\"New Flag 1\"]}" +
        "      }," +
        "      {" +
        "        \"uid\": 654321," +
        "        \"modSeq\": 36," +
        "        \"oldFlags\": {\"systemFlags\":[\"Flagged\",\"Recent\"],\"userFlags\":[\"Old Flag 2\"]}," +
        "        \"newFlags\": {\"systemFlags\":[\"Answered\",\"Seen\"],\"userFlags\":[\"New Flag 2\"]}" +
        "      }" +
        "    ]," +
        "    \"user\": \"user\"" +
        "  }" +
        "}";

    @Test
    void flagsUpdatedShouldBeWellSerialized() {
        assertThatJson(EVENT_SERIALIZER.toJson(DEFAULT_EVENT))
            .when(Option.IGNORING_ARRAY_ORDER)
            .isEqualTo(DEFAULT_EVENT_JSON);
    }

    @Test
    void flagsUpdatedShouldBeWellDeSerialized() {
        assertThat(EVENT_SERIALIZER.fromJson(DEFAULT_EVENT_JSON).get())
            .isEqualTo(DEFAULT_EVENT);
    }

    @Nested
    class WithEmptyUpdatedFlags {
        private final List<UpdatedFlags> emptyUpdatedFlags = ImmutableList.of();
        private final FlagsUpdated emptyUpdatedFlagsEvent = new FlagsUpdated(SESSION_ID, USERNAME, MAILBOX_PATH,
            MAILBOX_ID, emptyUpdatedFlags, EVENT_ID);

        private static final String EVENT_JSON_WITH_EMPTY_UPDATED_FLAGS =
            "{" +
                "  \"FlagsUpdated\": {" +
                "    \"eventId\":\"6e0dd59d-660e-4d9b-b22f-0354479f47b4\"," +
                "    \"path\": {" +
                "      \"namespace\": \"#private\"," +
                "      \"user\": \"user\"," +
                "      \"name\": \"mailboxName\"" +
                "    }," +
                "    \"mailboxId\": \"18\"," +
                "    \"sessionId\": 42," +
                "    \"updatedFlags\": []," +
                "    \"user\": \"user\"" +
                "  }" +
                "}";

        @Test
        void flagsUpdatedShouldBeWellSerialized() {
            assertThatJson(EVENT_SERIALIZER.toJson(emptyUpdatedFlagsEvent))
                .when(Option.IGNORING_ARRAY_ORDER)
                .isEqualTo(EVENT_JSON_WITH_EMPTY_UPDATED_FLAGS);
        }

        @Test
        void flagsUpdatedShouldBeWellDeSerialized() {
            assertThat(EVENT_SERIALIZER.fromJson(EVENT_JSON_WITH_EMPTY_UPDATED_FLAGS).get())
                .isEqualTo(emptyUpdatedFlagsEvent);
        }
    }

    @Nested
    class WithMessageId {
        private final MessageId messageId1 = TestMessageId.of(23456);
        private final MessageId messageId2 = TestMessageId.of(78901);

        private final UpdatedFlags updatedFlagsWithMessageId1 = UpdatedFlags.builder()
            .uid(MESSAGE_UID_1)
            .messageId(messageId1)
            .modSeq(MOD_SEQ_1)
            .oldFlags(OLD_FLAGS_1)
            .newFlags(NEW_FLAGS_1)
            .build();
        private final UpdatedFlags updatedFlagsWithMessageId2 = UpdatedFlags.builder()
            .uid(MESSAGE_UID_2)
            .messageId(messageId2)
            .modSeq(MOD_SEQ_2)
            .oldFlags(OLD_FLAGS_2)
            .newFlags(NEW_FLAGS_2)
            .build();

        private final List<UpdatedFlags> updatedFlagsListWithMessageIds = ImmutableList.of(updatedFlagsWithMessageId1, updatedFlagsWithMessageId2);

        private final FlagsUpdated eventWithMessageIds = new FlagsUpdated(SESSION_ID, USERNAME,
            MAILBOX_PATH, MAILBOX_ID, updatedFlagsListWithMessageIds, EVENT_ID);

        private static final String EVENT_WITH_MESSAGE_IDS_JSON =
            "{" +
                "  \"FlagsUpdated\": {" +
                "    \"eventId\":\"6e0dd59d-660e-4d9b-b22f-0354479f47b4\"," +
                "    \"path\": {" +
                "      \"namespace\": \"#private\"," +
                "      \"user\": \"user\"," +
                "      \"name\": \"mailboxName\"" +
                "    }," +
                "    \"mailboxId\": \"18\"," +
                "    \"sessionId\": 42," +
                "    \"updatedFlags\": [" +
                "      {" +
                "        \"uid\": 123456," +
                "        \"messageId\": \"23456\"," +
                "        \"modSeq\": 35," +
                "        \"oldFlags\": {\"systemFlags\":[\"Deleted\",\"Seen\"],\"userFlags\":[\"Old Flag 1\"]}," +
                "        \"newFlags\": {\"systemFlags\":[\"Answered\",\"Draft\"],\"userFlags\":[\"New Flag 1\"]}" +
                "      }," +
                "      {" +
                "        \"uid\": 654321," +
                "        \"messageId\": \"78901\"," +
                "        \"modSeq\": 36," +
                "        \"oldFlags\": {\"systemFlags\":[\"Flagged\",\"Recent\"],\"userFlags\":[\"Old Flag 2\"]}," +
                "        \"newFlags\": {\"systemFlags\":[\"Answered\",\"Seen\"],\"userFlags\":[\"New Flag 2\"]}" +
                "      }" +
                "    ]," +
                "    \"user\": \"user\"" +
                "  }" +
                "}";

        @Test
        void flagsUpdatedShouldBeWellSerialized() {
            assertThatJson(EVENT_SERIALIZER.toJson(eventWithMessageIds))
                .when(Option.IGNORING_ARRAY_ORDER)
                .isEqualTo(EVENT_WITH_MESSAGE_IDS_JSON);
        }

        @Test
        void flagsUpdatedShouldBeWellDeSerialized() {
            assertThat(EVENT_SERIALIZER.fromJson(EVENT_WITH_MESSAGE_IDS_JSON).get())
                .isEqualTo(eventWithMessageIds);
        }
    }

    @Nested
    class DeserializationError {
        @Test
        void flagsUpdatedShouldThrowWhenMissingSessionId() {
            assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                "{" +
                    "  \"FlagsUpdated\": {" +
                    "    \"eventId\":\"6e0dd59d-660e-4d9b-b22f-0354479f47b4\"," +
                    "    \"path\": {" +
                    "      \"namespace\": \"#private\"," +
                    "      \"user\": \"user\"," +
                    "      \"name\": \"mailboxName\"" +
                    "    }," +
                    "    \"mailboxId\": \"18\"," +
                    "    \"updatedFlags\": [" +
                    "      {" +
                    "        \"uid\": 123456," +
                    "        \"modSeq\": 35," +
                    "        \"oldFlags\": {\"systemFlags\":[\"Deleted\",\"Seen\"],\"userFlags\":[\"Old Flag 1\"]}," +
                    "        \"newFlags\": {\"systemFlags\":[\"Answered\",\"Draft\"],\"userFlags\":[\"New Flag 1\"]}" +
                    "      }," +
                    "      {" +
                    "        \"uid\": 654321," +
                    "        \"modSeq\": 36," +
                    "        \"oldFlags\": {\"systemFlags\":[\"Flagged\",\"Recent\"],\"userFlags\":[\"Old Flag 2\"]}," +
                    "        \"newFlags\": {\"systemFlags\":[\"Answered\",\"Seen\"],\"userFlags\":[\"New Flag 2\"]}" +
                    "      }" +
                    "    ]," +
                    "    \"user\": \"user\"" +
                    "  }" +
                    "}").get())
                .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        void flagsUpdatedShouldThrowWhenMissingEventId() {
            assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                "{" +
                    "  \"FlagsUpdated\": {" +
                    "    \"sessionId\":42," +
                    "    \"path\": {" +
                    "      \"namespace\": \"#private\"," +
                    "      \"user\": \"user\"," +
                    "      \"name\": \"mailboxName\"" +
                    "    }," +
                    "    \"mailboxId\": \"18\"," +
                    "    \"updatedFlags\": []," +
                    "    \"user\": \"user\"" +
                    "  }" +
                    "}").get())
                .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        void flagsUpdatedShouldThrowWhenMissingUser() {
            assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                "{" +
                    "  \"FlagsUpdated\": {" +
                    "    \"eventId\":\"6e0dd59d-660e-4d9b-b22f-0354479f47b4\"," +
                    "    \"path\": {" +
                    "      \"namespace\": \"#private\"," +
                    "      \"user\": \"user\"," +
                    "      \"name\": \"mailboxName\"" +
                    "    }," +
                    "    \"mailboxId\": \"18\"," +
                    "    \"sessionId\": 42," +
                    "    \"updatedFlags\": [" +
                    "      {" +
                    "        \"uid\": 123456," +
                    "        \"modSeq\": 35," +
                    "        \"oldFlags\": {\"systemFlags\":[\"Deleted\",\"Seen\"],\"userFlags\":[\"Old Flag 1\"]}," +
                    "        \"newFlags\": {\"systemFlags\":[\"Answered\",\"Draft\"],\"userFlags\":[\"New Flag 1\"]}" +
                    "      }," +
                    "      {" +
                    "        \"uid\": 654321," +
                    "        \"modSeq\": 36," +
                    "        \"oldFlags\": {\"systemFlags\":[\"Flagged\",\"Recent\"],\"userFlags\":[\"Old Flag 2\"]}," +
                    "        \"newFlags\": {\"systemFlags\":[\"Answered\",\"Seen\"],\"userFlags\":[\"New Flag 2\"]}" +
                    "      }" +
                    "    ]" +
                    "  }" +
                    "}").get())
                .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        void flagsUpdatedShouldThrowWhenMissingMailboxId() {
            assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                "{" +
                    "  \"FlagsUpdated\": {" +
                    "    \"eventId\":\"6e0dd59d-660e-4d9b-b22f-0354479f47b4\"," +
                    "    \"path\": {" +
                    "      \"namespace\": \"#private\"," +
                    "      \"user\": \"user\"," +
                    "      \"name\": \"mailboxName\"" +
                    "    }," +
                    "    \"sessionId\": 42," +
                    "    \"updatedFlags\": [" +
                    "      {" +
                    "        \"uid\": 123456," +
                    "        \"modSeq\": 35," +
                    "        \"oldFlags\": {\"systemFlags\":[\"Deleted\",\"Seen\"],\"userFlags\":[\"Old Flag 1\"]}," +
                    "        \"newFlags\": {\"systemFlags\":[\"Answered\",\"Draft\"],\"userFlags\":[\"New Flag 1\"]}" +
                    "      }," +
                    "      {" +
                    "        \"uid\": 654321," +
                    "        \"modSeq\": 36," +
                    "        \"oldFlags\": {\"systemFlags\":[\"Flagged\",\"Recent\"],\"userFlags\":[\"Old Flag 2\"]}," +
                    "        \"newFlags\": {\"systemFlags\":[\"Answered\",\"Seen\"],\"userFlags\":[\"New Flag 2\"]}" +
                    "      }" +
                    "    ]," +
                    "    \"user\": \"user\"" +
                    "  }" +
                    "}").get())
                .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        void flagsUpdatedShouldThrowWhenMissingMailboxPath() {
            assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                "{" +
                    "  \"FlagsUpdated\": {" +
                    "    \"eventId\":\"6e0dd59d-660e-4d9b-b22f-0354479f47b4\"," +
                    "    \"path\": {" +
                    "      \"namespace\": 482," +
                    "      \"user\": \"user\"," +
                    "      \"name\": \"mailboxName\"" +
                    "    }," +
                    "    \"mailboxId\": \"18\"," +
                    "    \"sessionId\": 42," +
                    "    \"updatedFlags\": [" +
                    "      {" +
                    "        \"uid\": 123456," +
                    "        \"modSeq\": 35," +
                    "        \"oldFlags\": {\"systemFlags\":[\"Deleted\",\"Seen\"],\"userFlags\":[\"Old Flag 1\"]}," +
                    "        \"newFlags\": {\"systemFlags\":[\"Answered\",\"Draft\"],\"userFlags\":[\"New Flag 1\"]}" +
                    "      }," +
                    "      {" +
                    "        \"uid\": 654321," +
                    "        \"modSeq\": 36," +
                    "        \"oldFlags\": {\"systemFlags\":[\"Flagged\",\"Recent\"],\"userFlags\":[\"Old Flag 2\"]}," +
                    "        \"newFlags\": {\"systemFlags\":[\"Answered\",\"Seen\"],\"userFlags\":[\"New Flag 2\"]}" +
                    "      }" +
                    "    ]," +
                    "    \"user\": \"user\"" +
                    "  }" +
                    "}").get())
                .isInstanceOf(NoSuchElementException.class);
        }
    }
}
