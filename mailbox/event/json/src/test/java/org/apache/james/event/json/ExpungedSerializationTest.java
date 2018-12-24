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
import static net.javacrumbs.jsonunit.core.Option.IGNORING_ARRAY_ORDER;
import static org.apache.james.mailbox.model.MailboxConstants.USER_NAMESPACE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.NoSuchElementException;

import javax.mail.Flags;

import org.apache.james.core.User;
import org.apache.james.mailbox.FlagsBuilder;
import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.model.TestMessageId;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableMap;

class ExpungedSerializationTest {

    private static final User USER = User.fromUsername("user");
    private static final MailboxSession.SessionId SESSION_ID = MailboxSession.SessionId.of(42);
    private static final MailboxId MAILBOX_ID = TestId.of(18);
    private static final String MAILBOX_NAME = "mailboxName";
    private static final MailboxPath MAILBOX_PATH = new MailboxPath(MailboxConstants.USER_NAMESPACE, "user", MAILBOX_NAME);
    private static final MessageUid MESSAGE_UID = MessageUid.of(123456);
    private static final Instant INSTANT = Instant.parse("2018-12-14T09:41:51.541Z");
    private static final TestMessageId MESSAGE_ID = TestMessageId.of(42);
    private static final int MOD_SEQ = 35;
    private static final int SIZE = 45;
    private static final Flags FLAGS = FlagsBuilder.builder()
        .add(Flags.Flag.ANSWERED, Flags.Flag.DRAFT)
        .add("User Custom Flag")
        .build();
    private static final Map<MessageUid, MessageMetaData> EXPUNGED = ImmutableMap.of(
        MESSAGE_UID, new MessageMetaData(MESSAGE_UID, MOD_SEQ, FLAGS, SIZE, Date.from(INSTANT), MESSAGE_ID));

    private static final MailboxListener.Expunged DEFAULT_EXPUNGED_EVENT = new MailboxListener.Expunged(SESSION_ID, USER, MAILBOX_PATH, MAILBOX_ID, EXPUNGED);
    private static final String DEFAULT_EXPUNGED_EVENT_JSON =
        "{" +
        "  \"Expunged\": {" +
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

    private static final EventSerializer EVENT_SERIALIZER = new EventSerializer(new TestId.Factory(), new TestMessageId.Factory());

    @Test
    void expungedShouldBeWellSerialized() {
        assertThatJson(EVENT_SERIALIZER.toJson(DEFAULT_EXPUNGED_EVENT))
            .isEqualTo(DEFAULT_EXPUNGED_EVENT_JSON);
    }

    @Test
    void expungedShouldBeWellDeSerialized() {
        assertThat(EVENT_SERIALIZER.fromJson(DEFAULT_EXPUNGED_EVENT_JSON).get())
            .isEqualTo(DEFAULT_EXPUNGED_EVENT);
    }

    @Nested
    class WithEmptyExpungedMap {

        private final MailboxListener.Expunged emptyExpungedEvent = new MailboxListener.Expunged(SESSION_ID, USER, MAILBOX_PATH, MAILBOX_ID, ImmutableMap.of());
        private final String emptyExpungedEventJson =
            "{" +
            "  \"Expunged\": {" +
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
    class WithFlags {

        @Nested
        class WithEmptyFlags {
            private final Flags emptyFlags = new FlagsBuilder().build();
            private final MailboxListener.Expunged emptyFlagsExpungedEvent = new MailboxListener.Expunged(SESSION_ID, USER, MAILBOX_PATH, MAILBOX_ID,
                ImmutableMap.of(
                    MESSAGE_UID,
                    new MessageMetaData(MESSAGE_UID, MOD_SEQ, emptyFlags, SIZE, Date.from(INSTANT), MESSAGE_ID)));

            private final String emptyFlagsExpungedEventJson =
                "{" +
                "  \"Expunged\": {" +
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
                "          \"systemFlags\":[], " +
                "          \"userFlags\":[]}," +
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
            void expungedShouldBeWellSerializedWhenEmptyFlags() {
                assertThatJson(EVENT_SERIALIZER.toJson(emptyFlagsExpungedEvent))
                    .isEqualTo(emptyFlagsExpungedEventJson);
            }

            @Test
            void expungedShouldBeWellDeSerializedWhenEmptyFlags() {
                assertThat(EVENT_SERIALIZER.fromJson(emptyFlagsExpungedEventJson).get())
                    .isEqualTo(emptyFlagsExpungedEvent);
            }
        }

        @Nested
        class WithOnlyUserFlags {
            private final Flags onlyUserFlags = new FlagsBuilder()
                .add("Custom 1", "Custom 2", "")
                .build();
            private final MailboxListener.Expunged onlyUserFlagsExpungedEvent = new MailboxListener.Expunged(SESSION_ID, USER, MAILBOX_PATH, MAILBOX_ID,
                ImmutableMap.of(
                    MESSAGE_UID,
                    new MessageMetaData(MESSAGE_UID, MOD_SEQ, onlyUserFlags, SIZE, Date.from(INSTANT), MESSAGE_ID)));

            private final String userOnlyFlagsExpungedEventJson =
                "{" +
                "  \"Expunged\": {" +
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
                "          \"systemFlags\":[], " +
                "          \"userFlags\":[\"Custom 1\", \"Custom 2\", \"\"]}," +
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
            void expungedShouldBeWellSerializedWhenOnlyUserFlags() {
                assertThatJson(EVENT_SERIALIZER.toJson(onlyUserFlagsExpungedEvent))
                    .when(IGNORING_ARRAY_ORDER)
                    .isEqualTo(userOnlyFlagsExpungedEventJson);
            }

            @Test
            void expungedShouldBeWellDeSerializedWhenOnlyUserFlags() {
                assertThat(EVENT_SERIALIZER.fromJson(userOnlyFlagsExpungedEventJson).get())
                    .isEqualTo(onlyUserFlagsExpungedEvent);
            }
        }

        @Nested
        class WithOnlySystemFlags {
            private final Flags onlySystemFlags = new FlagsBuilder()
                .add(Flags.Flag.SEEN, Flags.Flag.ANSWERED, Flags.Flag.DELETED)
                .build();
            private final MailboxListener.Expunged onlySystemFlagsExpungedEvent = new MailboxListener.Expunged(SESSION_ID, USER, MAILBOX_PATH, MAILBOX_ID,
                ImmutableMap.of(
                    MESSAGE_UID,
                    new MessageMetaData(MESSAGE_UID, MOD_SEQ, onlySystemFlags, SIZE, Date.from(INSTANT), MESSAGE_ID)));

            private final String systemOnlyFlagsExpungedEventJson =
                "{" +
                "  \"Expunged\": {" +
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
                "          \"systemFlags\":[\"Seen\",\"Answered\",\"Deleted\"], " +
                "          \"userFlags\":[]}," +
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
            void expungedShouldBeWellSerializedWhenOnlySystemFlags() {
                assertThatJson(EVENT_SERIALIZER.toJson(onlySystemFlagsExpungedEvent))
                    .when(IGNORING_ARRAY_ORDER)
                    .isEqualTo(systemOnlyFlagsExpungedEventJson);
            }

            @Test
            void expungedShouldBeWellDeSerializedWhenOnlySystemFlags() {
                assertThat(EVENT_SERIALIZER.fromJson(systemOnlyFlagsExpungedEventJson).get())
                    .isEqualTo(onlySystemFlagsExpungedEvent);
            }
        }
    }

    @Nested
    class WithInternalDate {

        @Test
        void expungedShouldDeserializeWhenInternalDateIsInGoodISOFormat() {
            Map<MessageUid, MessageMetaData> Expunged = ImmutableMap.of(
                MESSAGE_UID, new MessageMetaData(MESSAGE_UID, MOD_SEQ, FLAGS, SIZE, Date.from(Instant.parse("2018-12-14T09:41:51Z")), MESSAGE_ID));
            MailboxListener.Expunged eventRoundToMillis = new MailboxListener.Expunged(SESSION_ID, USER, MAILBOX_PATH, MAILBOX_ID, Expunged);

            assertThat(EVENT_SERIALIZER.fromJson(
                "{" +
                "  \"Expunged\": {" +
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
                "        \"internalDate\": \"2018-12-14T09:41:51+00:00\"," +
                "        \"messageId\": \"42\"" +
                "      }" +
                "    }," +
                "    \"sessionId\": 42," +
                "    \"user\": \"user\"" +
                "  }" +
                "}").get())
            .isEqualTo(eventRoundToMillis);
        }

        @Test
        void expungedShouldDeserializeWhenInternalDateIsMissingMilliSeconds() {
            Map<MessageUid, MessageMetaData> Expunged = ImmutableMap.of(
                MESSAGE_UID, new MessageMetaData(MESSAGE_UID, MOD_SEQ, FLAGS, SIZE, Date.from(Instant.parse("2018-12-14T09:41:51Z")), MESSAGE_ID));
            MailboxListener.Expunged eventRoundToMillis = new MailboxListener.Expunged(SESSION_ID, USER, MAILBOX_PATH, MAILBOX_ID, Expunged);

            assertThat(EVENT_SERIALIZER.fromJson(
                "{" +
                "  \"Expunged\": {" +
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
                "        \"internalDate\": \"2018-12-14T09:41:51Z\"," +
                "        \"messageId\": \"42\"" +
                "      }" +
                "    }," +
                "    \"sessionId\": 42," +
                "    \"user\": \"user\"" +
                "  }" +
                "}").get())
            .isEqualTo(eventRoundToMillis);
        }

        @Test
        void expungedShouldDeserializeWhenInternalDateIsMissingSeconds() {
            Map<MessageUid, MessageMetaData> Expunged = ImmutableMap.of(
                MESSAGE_UID, new MessageMetaData(MESSAGE_UID, MOD_SEQ, FLAGS, SIZE, Date.from(Instant.parse("2018-12-14T09:41:00Z")), MESSAGE_ID));
            MailboxListener.Expunged eventRoundToMinute = new MailboxListener.Expunged(SESSION_ID, USER, MAILBOX_PATH, MAILBOX_ID, Expunged);

            assertThat(EVENT_SERIALIZER.fromJson(
                "{" +
                "  \"Expunged\": {" +
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
                "        \"internalDate\": \"2018-12-14T09:41Z\"," +
                "        \"messageId\": \"42\"" +
                "      }" +
                "    }," +
                "    \"sessionId\": 42," +
                "    \"user\": \"user\"" +
                "  }" +
                "}").get())
            .isEqualTo(eventRoundToMinute);
        }
    }
    @Nested
    class NullOrEmptyNameSpaceInMailboxPath {

        @Test
        void expungedShouldBeWellDeSerializedWhenNullNameSpace() {
            assertThat(EVENT_SERIALIZER.fromJson(
                "{" +
                "  \"Expunged\": {" +
                "    \"path\": {" +
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
                "}").get())
            .isEqualTo(DEFAULT_EXPUNGED_EVENT);
        }

        @Test
        void expungedShouldBeWellDeSerializedWhenEmptyNameSpace() {
            assertThat(EVENT_SERIALIZER.fromJson(
                "{" +
                "  \"Expunged\": {" +
                "    \"path\": {" +
                "      \"namespace\": \"\"," +
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
                "}").get())
            .isEqualTo(DEFAULT_EXPUNGED_EVENT);
        }
    }

    @Nested
    class NullUserInMailboxPath {
        private final String nullUser = null;
        private final MailboxListener.Expunged eventWithNullUserInPath = new MailboxListener.Expunged(
            SESSION_ID,
            USER,
            new MailboxPath(USER_NAMESPACE, nullUser, MAILBOX_NAME),
            MAILBOX_ID,
            EXPUNGED);

        private static final String EVENT_JSON_WITH_NULL_USER_IN_PATH =
            "{" +
            "  \"Expunged\": {" +
            "    \"path\": {" +
            "      \"namespace\": \"#private\"," +
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
            assertThatJson(EVENT_SERIALIZER.toJson(eventWithNullUserInPath))
                .isEqualTo(EVENT_JSON_WITH_NULL_USER_IN_PATH);
        }

        @Test
        void expungedShouldBeWellDeSerialized() {
            assertThat(EVENT_SERIALIZER.fromJson(EVENT_JSON_WITH_NULL_USER_IN_PATH).get())
                .isEqualTo(eventWithNullUserInPath);
        }
    }

    @Nested
    class DeserializationErrors {
        @Test
        void expungedShouldThrowWhenMissingSessionId() {
            assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                "{" +
                    "  \"Expunged\": {" +
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

        @Nested
        class DeserializationErrorOnUser {
            @Test
            void expungedShouldThrowWhenMissingUser() {
                assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                    "{" +
                    "  \"Expunged\": {" +
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
            void expungedShouldThrowWhenUserIsNotAString() {
                assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                    "{" +
                    "  \"Expunged\": {" +
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
                    "    \"user\": 596" +
                    "  }" +
                    "}").get())
                .isInstanceOf(NoSuchElementException.class);
            }

            @Test
            void expungedShouldThrowWhenUserIsNotWellFormatted() {
                assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                    "{" +
                    "  \"Expunged\": {" +
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
                    "    \"user\": \"user@user@anotherUser\"" +
                    "  }" +
                    "}").get())
                .isInstanceOf(IllegalArgumentException.class);
            }
        }

        @Nested
        class DeserializationErrorOnMailboxId {
            @Test
            void expungedShouldThrowWhenMissingMailboxId() {
                assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                    "{" +
                    "  \"Expunged\": {" +
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
            void expungedShouldThrowWhenNullMailboxId() {
                assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                    "{" +
                    "  \"Expunged\": {" +
                    "    \"path\": {" +
                    "      \"namespace\": \"#private\"," +
                    "      \"user\": \"user\"," +
                    "      \"name\": \"mailboxName\"" +
                    "    }," +
                    "    \"mailboxId\": null," +
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
            void expungedShouldThrowWhenMailboxIdIsANumber() {
                assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                    "{" +
                    "  \"Expunged\": {" +
                    "    \"path\": {" +
                    "      \"namespace\": \"#private\"," +
                    "      \"user\": \"user\"," +
                    "      \"name\": \"mailboxName\"" +
                    "    }," +
                    "    \"mailboxId\": 18," +
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
        }

        @Nested
        class DeserializationErrorOnMailboxPath {

            @Nested
            class DeserializationErrorOnNameSpace {
                @Test
                void expungedShouldThrowWhenNameSpaceIsNotAString() {
                    assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                        "{" +
                        "  \"Expunged\": {" +
                        "    \"path\": {" +
                        "      \"namespace\": 48246," +
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
                        "}").get())
                    .isInstanceOf(NoSuchElementException.class);
                }
            }

            @Nested
            class DeserializationErrorOnUser {
                @Test
                void expungedShouldThrowWhenUserIsNotAString() {
                    assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                        "{" +
                        "  \"Expunged\": {" +
                        "    \"path\": {" +
                        "      \"namespace\": \"#private\"," +
                        "      \"user\": 265412.64," +
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
                        "}").get())
                    .isInstanceOf(NoSuchElementException.class);
                }
            }

            @Nested
            class DeserializationErrorOnMailboxName {

                @Test
                void expungedShouldThrowWhenNullMailboxName() {
                    assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                        "{" +
                        "  \"Expunged\": {" +
                        "    \"path\": {" +
                        "      \"namespace\": \"#private\"," +
                        "      \"user\": \"user\"," +
                        "      \"name\": null" +
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
                        "}").get())
                    .isInstanceOf(NoSuchElementException.class);
                }

                @Test
                void expungedShouldThrowWhenMailboxNameIdIsANumber() {
                    assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                        "{" +
                        "  \"Expunged\": {" +
                        "    \"path\": {" +
                        "      \"namespace\": \"#private\"," +
                        "      \"user\": \"user\"," +
                        "      \"name\": 11861" +
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
                        "}").get())
                    .isInstanceOf(NoSuchElementException.class);
                }
            }
        }

        @Nested
        class DeserializationErrorOnExpungedMap {
            @Test
            void expungedShouldThrowWhenMapKeyIsNull() {
                assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                    "{" +
                    "  \"Expunged\": {" +
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

            @Nested
            class DeserializationErrorOnMessageUid {

                @Test
                void expungedShouldThrowWhenMessageUidIsAString() {
                    assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                        "{" +
                        "  \"Expunged\": {" +
                        "    \"path\": {" +
                        "      \"namespace\": \"#private\"," +
                        "      \"user\": \"user\"," +
                        "      \"name\": null" +
                        "    }," +
                        "    \"mailboxId\": \"18\"," +
                        "    \"expunged\": {" +
                        "      \"123456\": {" +
                        "        \"uid\": \"123456\"," +
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
                void expungedShouldThrowWhenMessageUidIsNull() {
                    assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                        "{" +
                        "  \"Expunged\": {" +
                        "    \"path\": {" +
                        "      \"namespace\": \"#private\"," +
                        "      \"user\": \"user\"," +
                        "      \"name\": null" +
                        "    }," +
                        "    \"mailboxId\": \"18\"," +
                        "    \"expunged\": {" +
                        "      \"123456\": {" +
                        "        \"uid\": null," +
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
            }

            @Nested
            class DeserializationErrorOnModSeq {

                @Test
                void expungedShouldThrowWhenModSeqIsAString() {
                    assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                        "{" +
                        "  \"Expunged\": {" +
                        "    \"path\": {" +
                        "      \"namespace\": \"#private\"," +
                        "      \"user\": \"user\"," +
                        "      \"name\": \"mailboxName\"" +
                        "    }," +
                        "    \"mailboxId\": \"18\"," +
                        "    \"expunged\": {" +
                        "      \"123456\": {" +
                        "        \"uid\": 123456," +
                        "        \"modSeq\": \"35\"," +
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
                void expungedShouldThrowWhenModSeqIsNull() {
                    assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                        "{" +
                        "  \"Expunged\": {" +
                        "    \"path\": {" +
                        "      \"namespace\": \"#private\"," +
                        "      \"user\": \"user\"," +
                        "      \"name\": null" +
                        "    }," +
                        "    \"mailboxId\": \"18\"," +
                        "    \"expunged\": {" +
                        "      \"123456\": {" +
                        "        \"uid\": 123456," +
                        "        \"modSeq\": null," +
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
            }


            @Nested
            class DeserializationErrorOnSize {

                @Test
                void expungedShouldThrowWhenSizeIsAString() {
                    assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                        "{" +
                        "  \"Expunged\": {" +
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
                        "        \"size\": \"45\",  " +
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
                void expungedShouldThrowWhenSizeIsNull() {
                    assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                        "{" +
                        "  \"Expunged\": {" +
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
                        "        \"size\": null,  " +
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
            }

            @Nested
            class DeserializationErrorOnMessageId {

                @Test
                void expungedShouldThrowWhenMessageIdIsANumber() {
                    assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                        "{" +
                        "  \"Expunged\": {" +
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
                        "        \"messageId\": 42" +
                        "      }" +
                        "    }," +
                        "    \"sessionId\": 42," +
                        "    \"user\": \"user\"" +
                        "  }" +
                        "}").get())
                    .isInstanceOf(NoSuchElementException.class);
                }

                @Test
                void expungedShouldThrowWhenMessageIdIsNull() {
                    assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                        "{" +
                        "  \"Expunged\": {" +
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
                        "        \"messageId\": null" +
                        "      }" +
                        "    }," +
                        "    \"sessionId\": 42," +
                        "    \"user\": \"user\"" +
                        "  }" +
                        "}").get())
                    .isInstanceOf(NoSuchElementException.class);
                }
            }

            @Nested
            class DeserializationErrorOnInternalDate {
                @Test
                void expungedShouldThrowWhenInternalDateIsNotInISOFormatBecauseOfMissingTWord() {
                    assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                        "{" +
                        "  \"Expunged\": {" +
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
                        "        \"internalDate\": \"2018-12-14 12:52:36+07:00\"," +
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
                void expungedShouldThrowWhenInternalDateContainsOnlyDate() {
                    assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                        "{" +
                        "  \"Expunged\": {" +
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
                        "        \"internalDate\": \"2018-12-14\"," +
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
                void expungedShouldThrowWhenInternalDateIsMissingHourPart() {
                    assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                        "{" +
                        "  \"Expunged\": {" +
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
                        "        \"internalDate\": \"2018-12-14TZ\"," +
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
                void expungedShouldThrowWhenInternalDateIsMissingTimeZone() {
                    assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                        "{" +
                        "  \"Expunged\": {" +
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
                        "        \"internalDate\": \"2018-12-14T09:41:51.541\"," +
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
                void expungedShouldThrowWhenInternalDateIsMissingHours() {
                    assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                        "{" +
                        "  \"Expunged\": {" +
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
                        "        \"internalDate\": \"2018-12-14Z\"," +
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
                void expungedShouldThrowWhenInternalDateIsEmpty() {
                    assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                        "{" +
                        "  \"Expunged\": {" +
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
                        "        \"internalDate\": \"\"," +
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
                void expungedShouldThrowWhenInternalDateIsNull() {
                    assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                        "{" +
                        "  \"Expunged\": {" +
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
                        "        \"internalDate\": null," +
                        "        \"messageId\": \"42\"" +
                        "      }" +
                        "    }," +
                        "    \"sessionId\": 42," +
                        "    \"user\": \"user\"" +
                        "  }" +
                        "}").get())
                    .isInstanceOf(NoSuchElementException.class);
                }
            }

            @Nested
            class DeserializationErrorOnFlags {

                @Test
                void addedShouldThrowWhenFlagsIsNull() {
                    assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                        "{" +
                        "  \"Expunged\": {" +
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
                        "        \"flags\": null," +
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
                void addedShouldThrowWhenSystemFlagsContainsNullElements() {
                    assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                        "{" +
                        "  \"Expunged\": {" +
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
                        "          \"systemFlags\":[null, \"Draft\"], " +
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
                void addedShouldThrowWhenUserFlagsContainsNullElements() {
                    assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                        "{" +
                        "  \"Expunged\": {" +
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
                        "          \"systemFlags\":[\"Draft\"], " +
                        "          \"userFlags\":[\"User Custom Flag\", null]}," +
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
                void addedShouldThrowWhenSystemFlagsContainsNumberElements() {
                    assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                        "{" +
                        "  \"Expunged\": {" +
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
                        "          \"systemFlags\":[42, \"Draft\"], " +
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
                void addedShouldThrowWhenUserFlagsContainsNumberElements() {
                    assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                        "{" +
                        "  \"Expunged\": {" +
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
                        "          \"systemFlags\":[\"Draft\"], " +
                        "          \"userFlags\":[\"User Custom Flag\", 42]}," +
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
                void addedShouldThrowWhenSystemFlagsDoNotHaveTheRightCase() {
                    assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                        "{" +
                        "  \"Expunged\": {" +
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
                        "          \"systemFlags\":[\"draft\"], " +
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
                void addedShouldThrowWhenNoSystemFlags() {
                    assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                        "{" +
                        "  \"Expunged\": {" +
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
                void addedShouldThrowWhenNoUserFlags() {
                    assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                        "{" +
                        "  \"Expunged\": {" +
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
                        "          \"systemFlags\":[\"Draft\"]}," +
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
            }
        }
    }
}
