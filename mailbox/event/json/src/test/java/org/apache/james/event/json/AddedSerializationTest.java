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
import static org.apache.james.mailbox.events.MailboxEvents.Added.IS_APPENDED;
import static org.apache.james.mailbox.events.MailboxEvents.Added.IS_DELIVERY;
import static org.apache.james.mailbox.store.mail.model.MailboxMessage.EMPTY_SAVE_DATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Date;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.SortedMap;

import javax.mail.Flags;

import org.apache.james.core.Username;
import org.apache.james.mailbox.FlagsBuilder;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.events.MailboxEvents.Added;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.model.TestMessageId;
import org.apache.james.mailbox.model.ThreadId;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableSortedMap;

class AddedSerializationTest {

    private static final Username USERNAME = Username.of("user");
    private static final MailboxSession.SessionId SESSION_ID = MailboxSession.SessionId.of(42);
    private static final MailboxId MAILBOX_ID = TestId.of(18);
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
    private static final SortedMap<MessageUid, MessageMetaData> ADDED = ImmutableSortedMap.of(
        MESSAGE_UID, new MessageMetaData(MESSAGE_UID, MOD_SEQ, FLAGS, SIZE, Date.from(INSTANT), Optional.of(Date.from(INSTANT)), MESSAGE_ID, ThreadId.fromBaseMessageId(MESSAGE_ID)));
    private static final SortedMap<MessageUid, MessageMetaData> BACKWARD_ADDED = ImmutableSortedMap.of(
        MESSAGE_UID, new MessageMetaData(MESSAGE_UID, MOD_SEQ, FLAGS, SIZE, Date.from(INSTANT), EMPTY_SAVE_DATE, MESSAGE_ID, ThreadId.fromBaseMessageId(MESSAGE_ID)));
    private static final SortedMap<MessageUid, MessageMetaData> ADDED_WITH_DISTINCT_MESSAGE_ID_AND_THREAD_ID = ImmutableSortedMap.of(
        MESSAGE_UID, new MessageMetaData(MESSAGE_UID, MOD_SEQ, FLAGS, SIZE, Date.from(INSTANT), Optional.of(Date.from(INSTANT)), MESSAGE_ID, ThreadId.fromBaseMessageId(TestMessageId.of(100))));

    private static final Added DEFAULT_ADDED_EVENT = new Added(SESSION_ID, USERNAME, MAILBOX_PATH, MAILBOX_ID, ADDED, EVENT_ID, !IS_DELIVERY, IS_APPENDED);
    private static final Added BACKWARD_ADDED_EVENT = new Added(SESSION_ID, USERNAME, MAILBOX_PATH, MAILBOX_ID, BACKWARD_ADDED, EVENT_ID, !IS_DELIVERY, IS_APPENDED);
    private static final Added ADDED_WITH_DISTINCT_MESSAGE_ID_AND_THREAD_ID_EVENT = new Added(
        SESSION_ID, USERNAME, MAILBOX_PATH, MAILBOX_ID, ADDED_WITH_DISTINCT_MESSAGE_ID_AND_THREAD_ID, EVENT_ID, !IS_DELIVERY, IS_APPENDED);
    private static final String DEFAULT_ADDED_EVENT_JSON = 
        "{" +
        "  \"Added\": {" +
        "    \"eventId\":\"6e0dd59d-660e-4d9b-b22f-0354479f47b4\"," +
        "    \"path\": {" +
        "      \"namespace\": \"#private\"," +
        "      \"user\": \"user\"," +
        "      \"name\": \"mailboxName\"" +
        "    }," +
        "    \"mailboxId\": \"18\"," +
        "    \"added\": {" +
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
        "        \"threadId\":\"42\""   +
        "      }" +
        "    }," +
        "    \"sessionId\": 42," +
        "    \"isDelivery\": false," +
        "    \"isAppended\": true," +
        "    \"user\": \"user\"" +
        "  }" +
        "}";
    private static final String ADDED_WITH_DISTINCT_MESSAGE_ID_AND_THREAD_ID_EVENT_JSON =
        "{" +
            "  \"Added\": {" +
            "    \"eventId\":\"6e0dd59d-660e-4d9b-b22f-0354479f47b4\"," +
            "    \"path\": {" +
            "      \"namespace\": \"#private\"," +
            "      \"user\": \"user\"," +
            "      \"name\": \"mailboxName\"" +
            "    }," +
            "    \"mailboxId\": \"18\"," +
            "    \"added\": {" +
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
            "        \"threadId\":\"100\""   +
            "      }" +
            "    }," +
            "    \"sessionId\": 42," +
            "    \"isDelivery\": false," +
            "    \"isAppended\": true," +
            "    \"user\": \"user\"" +
            "  }" +
            "}";
    private static final String DEFAULT_BACKWARD_ADDED_EVENT_JSON =
        "{" +
            "  \"Added\": {" +
            "    \"eventId\":\"6e0dd59d-660e-4d9b-b22f-0354479f47b4\"," +
            "    \"path\": {" +
            "      \"namespace\": \"#private\"," +
            "      \"user\": \"user\"," +
            "      \"name\": \"mailboxName\"" +
            "    }," +
            "    \"mailboxId\": \"18\"," +
            "    \"added\": {" +
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
    void addedShouldBeWellSerialized() {
        assertThatJson(EVENT_SERIALIZER.toJson(DEFAULT_ADDED_EVENT))
            .isEqualTo(DEFAULT_ADDED_EVENT_JSON);
    }

    @Test
    void addedWithDistinctMessageIdAndThreadIdShouldBeWellSerialized() {
        assertThatJson(EVENT_SERIALIZER.toJson(ADDED_WITH_DISTINCT_MESSAGE_ID_AND_THREAD_ID_EVENT))
            .isEqualTo(ADDED_WITH_DISTINCT_MESSAGE_ID_AND_THREAD_ID_EVENT_JSON);
    }

    @Test
    void addedShouldBeWellDeSerialized() {
        assertThat(EVENT_SERIALIZER.fromJson(DEFAULT_ADDED_EVENT_JSON).get())
            .isEqualTo(DEFAULT_ADDED_EVENT);
    }

    @Test
    void previousAddedFormatShouldBeWellDeserialized() {
        assertThat(EVENT_SERIALIZER.fromJson(DEFAULT_BACKWARD_ADDED_EVENT_JSON).get())
            .isEqualTo(BACKWARD_ADDED_EVENT);
    }

    @Nested
    class WithEmptyAddedMap {

        private final Added emptyAddedEvent = new Added(SESSION_ID, USERNAME, MAILBOX_PATH,
            MAILBOX_ID, ImmutableSortedMap.of(), EVENT_ID, !IS_DELIVERY, IS_APPENDED);
        private final String emptyAddedEventJson =
            "{" +
            "  \"Added\": {" +
            "    \"eventId\":\"6e0dd59d-660e-4d9b-b22f-0354479f47b4\"," +
            "    \"path\": {" +
            "      \"namespace\": \"#private\"," +
            "      \"user\": \"user\"," +
            "      \"name\": \"mailboxName\"" +
            "    }," +
            "    \"mailboxId\": \"18\"," +
            "    \"added\": {}," +
            "    \"sessionId\": 42," +
            "    \"isDelivery\": false," +
            "    \"isAppended\": true," +
            "    \"user\": \"user\"" +
            "  }" +
            "}";

        @Test
        void addedShouldBeWellSerializedWhenMapKeyIsEmpty() {
            assertThatJson(EVENT_SERIALIZER.toJson(emptyAddedEvent))
                .isEqualTo(emptyAddedEventJson);
        }

        @Test
        void addedShouldBeWellDeSerializedWhenMapKeyIsEmpty() {
            assertThat(EVENT_SERIALIZER.fromJson(emptyAddedEventJson).get())
                .isEqualTo(emptyAddedEvent);
        }
    }

    @Nested
    class DeserializationErrors {
        @Test
        void addedShouldThrowWhenMissingEventId() {
            assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                "{" +
                    "  \"Added\": {" +
                    "    \"sessionId\":42," +
                    "    \"path\": {" +
                    "      \"namespace\": \"#private\"," +
                    "      \"user\": \"user\"," +
                    "      \"name\": \"mailboxName\"" +
                    "    }," +
                    "    \"mailboxId\": \"18\"," +
                    "    \"added\": {" +
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
        void addedShouldThrowWhenMissingSessionId() {
            assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                "{" +
                    "  \"Added\": {" +
                    "    \"eventId\":\"6e0dd59d-660e-4d9b-b22f-0354479f47b4\"," +
                    "    \"path\": {" +
                    "      \"namespace\": \"#private\"," +
                    "      \"user\": \"user\"," +
                    "      \"name\": \"mailboxName\"" +
                    "    }," +
                    "    \"mailboxId\": \"18\"," +
                    "    \"added\": {" +
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
        void addedShouldThrowWhenMissingUser() {
            assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                "{" +
                    "  \"Added\": {" +
                    "    \"eventId\":\"6e0dd59d-660e-4d9b-b22f-0354479f47b4\"," +
                    "    \"path\": {" +
                    "      \"namespace\": \"#private\"," +
                    "      \"user\": \"user\"," +
                    "      \"name\": \"mailboxName\"" +
                    "    }," +
                    "    \"mailboxId\": \"18\"," +
                    "    \"added\": {" +
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
        void addedShouldThrowWhenMissingMailboxId() {
            assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                "{" +
                    "  \"Added\": {" +
                    "    \"eventId\":\"6e0dd59d-660e-4d9b-b22f-0354479f47b4\"," +
                    "    \"path\": {" +
                    "      \"namespace\": \"#private\"," +
                    "      \"user\": \"user\"," +
                    "      \"name\": \"mailboxName\"" +
                    "    }," +
                    "    \"added\": {" +
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
        void addedShouldThrowWhenMissingPath() {
            assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                "{" +
                    "  \"Added\": {" +
                    "    \"eventId\":\"6e0dd59d-660e-4d9b-b22f-0354479f47b4\"," +
                    "    \"mailboxId\": \"18\"," +
                    "    \"added\": {" +
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
        void addedShouldThrowWhenMapKeyIsNull() {
            assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                "{" +
                    "  \"Added\": {" +
                    "    \"eventId\":\"6e0dd59d-660e-4d9b-b22f-0354479f47b4\"," +
                    "    \"path\": {" +
                    "      \"namespace\": \"#private\"," +
                    "      \"user\": \"user\"," +
                    "      \"name\": \"mailboxName\"" +
                    "    }," +
                    "    \"mailboxId\": \"18\"," +
                    "    \"added\": null," +
                    "    \"sessionId\": 42," +
                    "    \"user\": \"user\"" +
                    "  }" +
                    "}").get())
                .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        void addedShouldThrowWhenMissingAdded() {
            assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                "{" +
                    "  \"Added\": {" +
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
