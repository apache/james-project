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
import static org.apache.james.mailbox.store.mail.model.MailboxMessage.EMPTY_SAVE_DATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

import javax.mail.Flags;

import org.apache.james.core.Username;
import org.apache.james.mailbox.FlagsBuilder;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.events.MailboxEvents.Expunged;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.model.TestMessageId;
import org.apache.james.mailbox.model.ThreadId;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableMap;

class ExpungedSerializationTest {

    private static final Username USERNAME = Username.of("user");
    private static final MailboxSession.SessionId SESSION_ID = MailboxSession.SessionId.of(42);
    private static final MailboxId MAILBOX_ID = TestId.of(18);
    private static final MailboxId MOVED_MAILBOX_ID = TestId.of(28);
    private static final String MAILBOX_NAME = "mailboxName";
    private static final MailboxPath MAILBOX_PATH = new MailboxPath(MailboxConstants.USER_NAMESPACE, Username.of("user"), MAILBOX_NAME);
    private static final MessageUid MESSAGE_UID = MessageUid.of(123456);
    private static final Instant INSTANT = Instant.parse("2018-12-14T09:41:51.541Z");
    private static final TestMessageId MESSAGE_ID = TestMessageId.of(42);
    private static final ModSeq MOD_SEQ = ModSeq.of(35);
    private static final int SIZE = 45;
    private static final Flags FLAGS = FlagsBuilder.builder()
        .add(Flags.Flag.ANSWERED, Flags.Flag.DRAFT)
        .add("User Custom Flag")
        .build();
    private static final Map<MessageUid, MessageMetaData> EXPUNGED = ImmutableMap.of(
        MESSAGE_UID, new MessageMetaData(MESSAGE_UID, MOD_SEQ, FLAGS, SIZE, Date.from(INSTANT), Optional.of(Date.from(INSTANT)), MESSAGE_ID, ThreadId.fromBaseMessageId(MESSAGE_ID)));
    private static final Map<MessageUid, MessageMetaData> BACKWARD_EXPUNGED = ImmutableMap.of(
        MESSAGE_UID, new MessageMetaData(MESSAGE_UID, MOD_SEQ, FLAGS, SIZE, Date.from(INSTANT), EMPTY_SAVE_DATE, MESSAGE_ID, ThreadId.fromBaseMessageId(MESSAGE_ID)));
    private static final Map<MessageUid, MessageMetaData> EXPUNGED_WITH_DISTINCT_MESSAGE_ID_AND_THREAD_ID = ImmutableMap.of(
        MESSAGE_UID, new MessageMetaData(MESSAGE_UID, MOD_SEQ, FLAGS, SIZE, Date.from(INSTANT), Optional.of(Date.from(INSTANT)), MESSAGE_ID, ThreadId.fromBaseMessageId(TestMessageId.of(100))));

    private static final Expunged DEFAULT_EXPUNGED_EVENT = new Expunged(SESSION_ID, USERNAME, MAILBOX_PATH, MAILBOX_ID, EXPUNGED, EVENT_ID, Optional.empty());
    private static final Expunged BACKWARD_EXPUNGED_EVENT = new Expunged(SESSION_ID, USERNAME, MAILBOX_PATH, MAILBOX_ID, BACKWARD_EXPUNGED, EVENT_ID, Optional.empty());
    private static final Expunged EXPUNGED_WITH_DISTINCT_MESSAGE_ID_AND_THREAD_ID_EVENT = new Expunged(
        SESSION_ID, USERNAME, MAILBOX_PATH, MAILBOX_ID, EXPUNGED_WITH_DISTINCT_MESSAGE_ID_AND_THREAD_ID, EVENT_ID, Optional.of(MOVED_MAILBOX_ID));
    private static final String DEFAULT_EXPUNGED_EVENT_JSON =
        "{" +
        "  \"Expunged\": {" +
        "    \"eventId\":\"6e0dd59d-660e-4d9b-b22f-0354479f47b4\"," +
        "    \"path\": {" +
        "      \"namespace\": \"#private\"," +
        "      \"user\": \"user\"," +
        "      \"name\": \"mailboxName\"" +
        "    }," +
        "    \"mailboxId\": \"18\"," +
        "    \"expunged\": {" +
        "      \"123456\": {" +
        "        \"uid\": 123456," +
        "        \"modSeq\": 35," +
        "        \"flags\": {" +
        "          \"systemFlags\":[\"Answered\",\"Draft\"], " +
        "          \"userFlags\":[\"User Custom Flag\"]}," +
        "        \"size\": 45,  " +
        "        \"internalDate\": \"2018-12-14T09:41:51.541Z\"," +
        "        \"saveDate\": \"2018-12-14T09:41:51.541Z\"," +
        "        \"messageId\": \"42\"," +
        "        \"threadId\": \"42\"" +
        "      }" +
        "    }," +
        "    \"sessionId\": 42," +
        "    \"user\": \"user\"" +
        "  }" +
        "}";
    private static final String EXPUNGED_WITH_DISTINCT_MESSAGE_ID_AND_THREAD_ID_EVENT_JSON =
        "{" +
            "  \"Expunged\": {" +
            "    \"eventId\":\"6e0dd59d-660e-4d9b-b22f-0354479f47b4\"," +
            "    \"path\": {" +
            "      \"namespace\": \"#private\"," +
            "      \"user\": \"user\"," +
            "      \"name\": \"mailboxName\"" +
            "    }," +
            "    \"mailboxId\": \"18\"," +
            "    \"expunged\": {" +
            "      \"123456\": {" +
            "        \"uid\": 123456," +
            "        \"modSeq\": 35," +
            "        \"flags\": {" +
            "          \"systemFlags\":[\"Answered\",\"Draft\"], " +
            "          \"userFlags\":[\"User Custom Flag\"]}," +
            "        \"size\": 45,  " +
            "        \"internalDate\": \"2018-12-14T09:41:51.541Z\"," +
            "        \"saveDate\": \"2018-12-14T09:41:51.541Z\"," +
            "        \"messageId\": \"42\"," +
            "        \"threadId\": \"100\"" +
            "      }" +
            "    }," +
            "    \"sessionId\": 42," +
            "    \"user\": \"user\"," +
            "    \"movedToMailboxId\":  \"28\"" +
            "  }" +
            "}";
    private static final String DEFAULT_BACKWARD_EXPUNGED_EVENT_JSON =
        "{" +
            "  \"Expunged\": {" +
            "    \"eventId\":\"6e0dd59d-660e-4d9b-b22f-0354479f47b4\"," +
            "    \"path\": {" +
            "      \"namespace\": \"#private\"," +
            "      \"user\": \"user\"," +
            "      \"name\": \"mailboxName\"" +
            "    }," +
            "    \"mailboxId\": \"18\"," +
            "    \"expunged\": {" +
            "      \"123456\": {" +
            "        \"uid\": 123456," +
            "        \"modSeq\": 35," +
            "        \"flags\": {" +
            "          \"systemFlags\":[\"Answered\",\"Draft\"], " +
            "          \"userFlags\":[\"User Custom Flag\"]}," +
            "        \"size\": 45,  " +
            "        \"internalDate\": \"2018-12-14T09:41:51.541Z\"," +
            "        \"messageId\": \"42\"" +
            "      }" +
            "    }," +
            "    \"sessionId\": 42," +
            "    \"user\": \"user\"" +
            "  }" +
            "}";

    @Test
    void expungedShouldBeWellSerialized() {
        assertThatJson(EVENT_SERIALIZER.toJson(DEFAULT_EXPUNGED_EVENT))
            .isEqualTo(DEFAULT_EXPUNGED_EVENT_JSON);
    }

    @Test
    void expungedWithDistinctMessageIdAndThreadIdShouldBeWellSerialized() {
        assertThatJson(EVENT_SERIALIZER.toJson(EXPUNGED_WITH_DISTINCT_MESSAGE_ID_AND_THREAD_ID_EVENT))
            .isEqualTo(EXPUNGED_WITH_DISTINCT_MESSAGE_ID_AND_THREAD_ID_EVENT_JSON);
    }

    @Test
    void expungedShouldBeWellDeSerialized() {
        assertThat(EVENT_SERIALIZER.fromJson(DEFAULT_EXPUNGED_EVENT_JSON).get())
            .isEqualTo(DEFAULT_EXPUNGED_EVENT);
    }

    @Test
    void previousExpungedFormatShouldBeWellDeserialized() {
        assertThat(EVENT_SERIALIZER.fromJson(DEFAULT_BACKWARD_EXPUNGED_EVENT_JSON).get())
            .isEqualTo(BACKWARD_EXPUNGED_EVENT);
    }

    @Test
    void expungedWithMovedToMailboxIdShouldBeWellDeserialized() {
        assertThat(EVENT_SERIALIZER.fromJson("{" +
            "  \"Expunged\": {" +
            "    \"eventId\":\"6e0dd59d-660e-4d9b-b22f-0354479f47b4\"," +
            "    \"path\": {" +
            "      \"namespace\": \"#private\"," +
            "      \"user\": \"user\"," +
            "      \"name\": \"mailboxName\"" +
            "    }," +
            "    \"mailboxId\": \"18\"," +
            "    \"expunged\": {" +
            "      \"123456\": {" +
            "        \"uid\": 123456," +
            "        \"modSeq\": 35," +
            "        \"flags\": {" +
            "          \"systemFlags\":[\"Answered\",\"Draft\"], " +
            "          \"userFlags\":[\"User Custom Flag\"]}," +
            "        \"size\": 45,  " +
            "        \"internalDate\": \"2018-12-14T09:41:51.541Z\"," +
            "        \"messageId\": \"42\"" +
            "      }" +
            "    }," +
            "    \"sessionId\": 42," +
            "    \"user\": \"user\"," +
            "    \"movedToMailboxId\": \"28\"" +
            "  }" +
            "}").get())
            .isEqualTo(new Expunged(SESSION_ID, USERNAME, MAILBOX_PATH, MAILBOX_ID, BACKWARD_EXPUNGED, EVENT_ID, Optional.of(MOVED_MAILBOX_ID)));
    }

    @Nested
    class WithEmptyExpungedMap {

        private final Expunged emptyExpungedEvent = new Expunged(SESSION_ID, USERNAME,
            MAILBOX_PATH, MAILBOX_ID, ImmutableMap.of(), EVENT_ID, Optional.empty());
        private final String emptyExpungedEventJson =
            "{" +
            "  \"Expunged\": {" +
            "    \"eventId\":\"6e0dd59d-660e-4d9b-b22f-0354479f47b4\"," +
            "    \"path\": {" +
            "      \"namespace\": \"#private\"," +
            "      \"user\": \"user\"," +
            "      \"name\": \"mailboxName\"" +
            "    }," +
            "    \"mailboxId\": \"18\"," +
            "    \"expunged\": {}," +
            "    \"sessionId\": 42," +
            "    \"user\": \"user\"" +
            "  }" +
            "}";

        @Test
        void expungedShouldBeWellSerializedWhenMapKeyIsEmpty() {
            assertThatJson(EVENT_SERIALIZER.toJson(emptyExpungedEvent))
                .isEqualTo(emptyExpungedEventJson);
        }

        @Test
        void expungedShouldBeWellDeSerializedWhenMapKeyIsEmpty() {
            assertThat(EVENT_SERIALIZER.fromJson(emptyExpungedEventJson).get())
                .isEqualTo(emptyExpungedEvent);
        }
    }

    @Nested
    class DeserializationErrors {
        @Test
        void expungedShouldThrowWhenMissingSessionId() {
            assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                "{" +
                    "  \"Expunged\": {" +
                    "    \"eventId\":\"6e0dd59d-660e-4d9b-b22f-0354479f47b4\"," +
                    "    \"path\": {" +
                    "      \"namespace\": \"#private\"," +
                    "      \"user\": \"user\"," +
                    "      \"name\": \"mailboxName\"" +
                    "    }," +
                    "    \"mailboxId\": \"18\"," +
                    "    \"expunged\": {" +
                    "      \"123456\": {" +
                    "        \"uid\": 123456," +
                    "        \"modSeq\": 35," +
                    "        \"flags\": {" +
                    "          \"systemFlags\":[\"Answered\",\"Draft\"], " +
                    "          \"userFlags\":[\"User Custom Flag\"]}," +
                    "        \"size\": 45,  " +
                    "        \"internalDate\": \"2018-12-14T09:41:51.541Z\"," +
                    "        \"messageId\": \"42\"" +
                    "      }" +
                    "    }," +
                    "    \"user\": \"user\"" +
                    "  }" +
                    "}").get())
                .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        void expungedShouldThrowWhenMissingEventId() {
            assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                "{" +
                    "  \"Expunged\": {" +
                    "    \"sessionId\":42," +
                    "    \"path\": {" +
                    "      \"namespace\": \"#private\"," +
                    "      \"user\": \"user\"," +
                    "      \"name\": \"mailboxName\"" +
                    "    }," +
                    "    \"mailboxId\": \"18\"," +
                    "    \"expunged\": {}," +
                    "    \"user\": \"user\"" +
                    "  }" +
                    "}").get())
                .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        void expungedShouldThrowWhenMissingUser() {
            assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                "{" +
                    "  \"Expunged\": {" +
                    "    \"eventId\":\"6e0dd59d-660e-4d9b-b22f-0354479f47b4\"," +
                    "    \"path\": {" +
                    "      \"namespace\": \"#private\"," +
                    "      \"user\": \"user\"," +
                    "      \"name\": \"mailboxName\"" +
                    "    }," +
                    "    \"mailboxId\": \"18\"," +
                    "    \"expunged\": {" +
                    "      \"123456\": {" +
                    "        \"uid\": 123456," +
                    "        \"modSeq\": 35," +
                    "        \"flags\": {" +
                    "          \"systemFlags\":[\"Answered\",\"Draft\"], " +
                    "          \"userFlags\":[\"User Custom Flag\"]}," +
                    "        \"size\": 45,  " +
                    "        \"internalDate\": \"2018-12-14T09:41:51.541Z\"," +
                    "        \"messageId\": \"42\"" +
                    "      }" +
                    "    }," +
                    "    \"sessionId\": 42" +
                    "  }" +
                    "}").get())
                .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        void expungedShouldThrowWhenMissingMailboxId() {
            assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                "{" +
                    "  \"Expunged\": {" +
                    "    \"eventId\":\"6e0dd59d-660e-4d9b-b22f-0354479f47b4\"," +
                    "    \"path\": {" +
                    "      \"namespace\": \"#private\"," +
                    "      \"user\": \"user\"," +
                    "      \"name\": \"mailboxName\"" +
                    "    }," +
                    "    \"expunged\": {" +
                    "      \"123456\": {" +
                    "        \"uid\": 123456," +
                    "        \"modSeq\": 35," +
                    "        \"flags\": {" +
                    "          \"systemFlags\":[\"Answered\",\"Draft\"], " +
                    "          \"userFlags\":[\"User Custom Flag\"]}," +
                    "        \"size\": 45,  " +
                    "        \"internalDate\": \"2018-12-14T09:41:51.541Z\"," +
                    "        \"messageId\": \"42\"" +
                    "      }" +
                    "    }," +
                    "    \"sessionId\": 42," +
                    "    \"user\": \"user\"" +
                    "  }" +
                    "}").get())
                .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        void expungedShouldThrowWhenMissingPath() {
            assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                "{" +
                    "  \"Expunged\": {" +
                    "    \"eventId\":\"6e0dd59d-660e-4d9b-b22f-0354479f47b4\"," +
                    "    \"mailboxId\": \"18\"," +
                    "    \"expunged\": {" +
                    "      \"123456\": {" +
                    "        \"uid\": 123456," +
                    "        \"modSeq\": 35," +
                    "        \"flags\": {" +
                    "          \"systemFlags\":[\"Answered\",\"Draft\"], " +
                    "          \"userFlags\":[\"User Custom Flag\"]}," +
                    "        \"size\": 45,  " +
                    "        \"internalDate\": \"2018-12-14T09:41:51.541Z\"," +
                    "        \"messageId\": \"42\"" +
                    "      }" +
                    "    }," +
                    "    \"sessionId\": 42," +
                    "    \"user\": \"user\"" +
                    "  }" +
                    "}").get())
                .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        void expungedShouldThrowWhenMapKeyIsNull() {
            assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                "{" +
                    "  \"Expunged\": {" +
                    "    \"eventId\":\"6e0dd59d-660e-4d9b-b22f-0354479f47b4\"," +
                    "    \"path\": {" +
                    "      \"namespace\": \"#private\"," +
                    "      \"user\": \"user\"," +
                    "      \"name\": \"mailboxName\"" +
                    "    }," +
                    "    \"mailboxId\": \"18\"," +
                    "    \"Expunged\": null," +
                    "    \"sessionId\": 42," +
                    "    \"user\": \"user\"" +
                    "  }" +
                    "}").get())
                .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        void expungedShouldThrowWhenMissingExpunged() {
            assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                "{" +
                    "  \"Expunged\": {" +
                    "    \"eventId\":\"6e0dd59d-660e-4d9b-b22f-0354479f47b4\"," +
                    "    \"path\": {" +
                    "      \"namespace\": \"#private\"," +
                    "      \"user\": \"user\"," +
                    "      \"name\": \"mailboxName\"" +
                    "    }," +
                    "    \"mailboxId\": \"18\"," +
                    "    \"sessionId\": 42," +
                    "    \"user\": \"user\"" +
                    "  }" +
                    "}").get())
                .isInstanceOf(NoSuchElementException.class);
        }
    }
}
