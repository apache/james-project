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
import java.util.NoSuchElementException;
import java.util.SortedMap;

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

import com.google.common.collect.ImmutableSortedMap;

class AddedSerializationTest {

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
    private static final SortedMap<MessageUid, MessageMetaData> ADDED = ImmutableSortedMap.of(
        MESSAGE_UID, new MessageMetaData(MESSAGE_UID, MOD_SEQ, FLAGS, SIZE, Date.from(INSTANT), MESSAGE_ID));

    private static final MailboxListener.Added DEFAULT_ADDED_EVENT = new MailboxListener.Added(SESSION_ID, USER, MAILBOX_PATH, MAILBOX_ID, ADDED);
    private static final String DEFAULT_ADDED_EVENT_JSON = 
        "{" +
        "  \"Added\": {" +
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

    private static final EventSerializer EVENT_SERIALIZER = new EventSerializer(new TestId.Factory(), new TestMessageId.Factory());

    @Test
    void addedShouldBeWellSerialized() {
        assertThatJson(EVENT_SERIALIZER.toJson(DEFAULT_ADDED_EVENT))
            .isEqualTo(DEFAULT_ADDED_EVENT_JSON);
    }

    @Test
    void addedShouldBeWellDeSerialized() {
        assertThat(EVENT_SERIALIZER.fromJson(DEFAULT_ADDED_EVENT_JSON).get())
            .isEqualTo(DEFAULT_ADDED_EVENT);
    }

    @Nested
    class WithEmptyAddedMap {

        private final MailboxListener.Added emptyAddedEvent = new MailboxListener.Added(SESSION_ID, USER, MAILBOX_PATH, MAILBOX_ID, ImmutableSortedMap.of());
        private final String emptyAddedEventJson =
            "{" +
            "  \"Added\": {" +
            "    \"path\": {" +
            "      \"namespace\": \"#private\"," +
            "      \"user\": \"user\"," +
            "      \"name\": \"mailboxName\"" +
            "    }," +
            "    \"mailboxId\": \"18\"," +
            "    \"added\": {}," +
            "    \"sessionId\": 42," +
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
    class WithFlags {

        @Nested
        class WithEmptyFlags {
            private final Flags emptyFlags = new FlagsBuilder().build();
            private final MailboxListener.Added emptyFlagsAddedEvent = new MailboxListener.Added(SESSION_ID, USER, MAILBOX_PATH, MAILBOX_ID,
                ImmutableSortedMap.of(
                    MESSAGE_UID,
                    new MessageMetaData(MESSAGE_UID, MOD_SEQ, emptyFlags, SIZE, Date.from(INSTANT), MESSAGE_ID)));

            private final String emptyFlagsAddedEventJson =
                "{" +
                "  \"Added\": {" +
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
            void addedShouldBeWellSerializedWhenEmptyFlags() {
                assertThatJson(EVENT_SERIALIZER.toJson(emptyFlagsAddedEvent))
                    .isEqualTo(emptyFlagsAddedEventJson);
            }

            @Test
            void addedShouldBeWellDeSerializedWhenEmptyFlags() {
                assertThat(EVENT_SERIALIZER.fromJson(emptyFlagsAddedEventJson).get())
                    .isEqualTo(emptyFlagsAddedEvent);
            }
        }

        @Nested
        class WithOnlyUserFlags {
            private final Flags onlyUserFlags = new FlagsBuilder()
                .add("Custom 1", "Custom 2", "")
                .build();
            private final MailboxListener.Added onlyUserFlagsAddedEvent = new MailboxListener.Added(SESSION_ID, USER, MAILBOX_PATH, MAILBOX_ID,
                ImmutableSortedMap.of(
                    MESSAGE_UID,
                    new MessageMetaData(MESSAGE_UID, MOD_SEQ, onlyUserFlags, SIZE, Date.from(INSTANT), MESSAGE_ID)));

            private final String userOnlyFlagsAddedEventJson =
                "{" +
                "  \"Added\": {" +
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
            void addedShouldBeWellSerializedWhenOnlyUserFlags() {
                assertThatJson(EVENT_SERIALIZER.toJson(onlyUserFlagsAddedEvent))
                    .when(IGNORING_ARRAY_ORDER)
                    .isEqualTo(userOnlyFlagsAddedEventJson);
            }

            @Test
            void addedShouldBeWellDeSerializedWhenOnlyUserFlags() {
                assertThat(EVENT_SERIALIZER.fromJson(userOnlyFlagsAddedEventJson).get())
                    .isEqualTo(onlyUserFlagsAddedEvent);
            }
        }

        @Nested
        class WithOnlySystemFlags {
            private final Flags onlySystemFlags = new FlagsBuilder()
                .add(Flags.Flag.SEEN, Flags.Flag.ANSWERED, Flags.Flag.DELETED)
                .build();
            private final MailboxListener.Added onlySystemFlagsAddedEvent = new MailboxListener.Added(SESSION_ID, USER, MAILBOX_PATH, MAILBOX_ID,
                ImmutableSortedMap.of(
                    MESSAGE_UID,
                    new MessageMetaData(MESSAGE_UID, MOD_SEQ, onlySystemFlags, SIZE, Date.from(INSTANT), MESSAGE_ID)));

            private final String systemOnlyFlagsAddedEventJson =
                "{" +
                "  \"Added\": {" +
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
            void addedShouldBeWellSerializedWhenOnlySystemFlags() {
                assertThatJson(EVENT_SERIALIZER.toJson(onlySystemFlagsAddedEvent))
                    .when(IGNORING_ARRAY_ORDER)
                    .isEqualTo(systemOnlyFlagsAddedEventJson);
            }

            @Test
            void addedShouldBeWellDeSerializedWhenOnlySystemFlags() {
                assertThat(EVENT_SERIALIZER.fromJson(systemOnlyFlagsAddedEventJson).get())
                    .isEqualTo(onlySystemFlagsAddedEvent);
            }
        }
    }

    @Nested
    class WithInternalDate {

        @Test
        void addedShouldDeserializeWhenInternalDateIsInGoodISOFormat() {
            SortedMap<MessageUid, MessageMetaData> added = ImmutableSortedMap.of(
                MESSAGE_UID, new MessageMetaData(MESSAGE_UID, MOD_SEQ, FLAGS, SIZE, Date.from(Instant.parse("2018-12-14T09:41:51Z")), MESSAGE_ID));
            MailboxListener.Added eventRoundToMillis = new MailboxListener.Added(SESSION_ID, USER, MAILBOX_PATH, MAILBOX_ID, added);

            assertThat(EVENT_SERIALIZER.fromJson(
                "{" +
                "  \"Added\": {" +
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
        void addedShouldDeserializeWhenInternalDateIsMissingMilliSeconds() {
            SortedMap<MessageUid, MessageMetaData> added = ImmutableSortedMap.of(
                MESSAGE_UID, new MessageMetaData(MESSAGE_UID, MOD_SEQ, FLAGS, SIZE, Date.from(Instant.parse("2018-12-14T09:41:51Z")), MESSAGE_ID));
            MailboxListener.Added eventRoundToMillis = new MailboxListener.Added(SESSION_ID, USER, MAILBOX_PATH, MAILBOX_ID, added);

            assertThat(EVENT_SERIALIZER.fromJson(
                "{" +
                "  \"Added\": {" +
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
        void addedShouldDeserializeWhenInternalDateIsMissingSeconds() {
            SortedMap<MessageUid, MessageMetaData> added = ImmutableSortedMap.of(
                MESSAGE_UID, new MessageMetaData(MESSAGE_UID, MOD_SEQ, FLAGS, SIZE, Date.from(Instant.parse("2018-12-14T09:41:00Z")), MESSAGE_ID));
            MailboxListener.Added eventRoundToMinute = new MailboxListener.Added(SESSION_ID, USER, MAILBOX_PATH, MAILBOX_ID, added);

            assertThat(EVENT_SERIALIZER.fromJson(
                "{" +
                "  \"Added\": {" +
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
        void addedShouldBeWellDeSerializedWhenNullNameSpace() {
            assertThat(EVENT_SERIALIZER.fromJson(
                "{" +
                "  \"Added\": {" +
                "    \"path\": {" +
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
                "}").get())
            .isEqualTo(DEFAULT_ADDED_EVENT);
        }

        @Test
        void addedShouldBeWellDeSerializedWhenEmptyNameSpace() {
            assertThat(EVENT_SERIALIZER.fromJson(
                "{" +
                "  \"Added\": {" +
                "    \"path\": {" +
                "      \"namespace\": \"\"," +
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
                "}").get())
            .isEqualTo(DEFAULT_ADDED_EVENT);
        }
    }

    @Nested
    class NullUserInMailboxPath {
        private final String nullUser = null;
        private final MailboxListener.Added eventWithNullUserInPath = new MailboxListener.Added(
            SESSION_ID,
            USER,
            new MailboxPath(USER_NAMESPACE, nullUser, MAILBOX_NAME),
            MAILBOX_ID,
            ADDED);

        private static final String EVENT_JSON_WITH_NULL_USER_IN_PATH =
            "{" +
            "  \"Added\": {" +
            "    \"path\": {" +
            "      \"namespace\": \"#private\"," +
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
            assertThatJson(EVENT_SERIALIZER.toJson(eventWithNullUserInPath))
                .isEqualTo(EVENT_JSON_WITH_NULL_USER_IN_PATH);
        }

        @Test
        void addedShouldBeWellDeSerialized() {
            assertThat(EVENT_SERIALIZER.fromJson(EVENT_JSON_WITH_NULL_USER_IN_PATH).get())
                .isEqualTo(eventWithNullUserInPath);
        }
    }

    @Nested
    class DeserializationErrors {
        @Test
        void addedShouldThrowWhenMissingSessionId() {
            assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                "{" +
                    "  \"Added\": {" +
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

        @Nested
        class DeserializationErrorOnUser {
            @Test
            void addedShouldThrowWhenMissingUser() {
                assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                    "{" +
                    "  \"Added\": {" +
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
            void addedShouldThrowWhenUserIsNotAString() {
                assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                    "{" +
                    "  \"Added\": {" +
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
                    "    \"user\": 596" +
                    "  }" +
                    "}").get())
                .isInstanceOf(NoSuchElementException.class);
            }

            @Test
            void addedShouldThrowWhenUserIsNotWellFormatted() {
                assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                    "{" +
                    "  \"Added\": {" +
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
                    "    \"user\": \"user@user@anotherUser\"" +
                    "  }" +
                    "}").get())
                .isInstanceOf(IllegalArgumentException.class);
            }
        }

        @Nested
        class DeserializationErrorOnMailboxId {
            @Test
            void addedShouldThrowWhenMissingMailboxId() {
                assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                    "{" +
                    "  \"Added\": {" +
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
            void addedShouldThrowWhenNullMailboxId() {
                assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                    "{" +
                    "  \"Added\": {" +
                    "    \"path\": {" +
                    "      \"namespace\": \"#private\"," +
                    "      \"user\": \"user\"," +
                    "      \"name\": \"mailboxName\"" +
                    "    }," +
                    "    \"mailboxId\": null," +
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
            void addedShouldThrowWhenMailboxIdIsANumber() {
                assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                    "{" +
                    "  \"Added\": {" +
                    "    \"path\": {" +
                    "      \"namespace\": \"#private\"," +
                    "      \"user\": \"user\"," +
                    "      \"name\": \"mailboxName\"" +
                    "    }," +
                    "    \"mailboxId\": 18," +
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
        }

        @Nested
        class DeserializationErrorOnMailboxPath {

            @Nested
            class DeserializationErrorOnNameSpace {
                @Test
                void addedShouldThrowWhenNameSpaceIsNotAString() {
                    assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                        "{" +
                        "  \"Added\": {" +
                        "    \"path\": {" +
                        "      \"namespace\": 48246," +
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
                        "}").get())
                    .isInstanceOf(NoSuchElementException.class);
                }
            }

            @Nested
            class DeserializationErrorOnUser {
                @Test
                void addedShouldThrowWhenUserIsNotAString() {
                    assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                        "{" +
                        "  \"Added\": {" +
                        "    \"path\": {" +
                        "      \"namespace\": \"#private\"," +
                        "      \"user\": 265412.64," +
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
                        "}").get())
                    .isInstanceOf(NoSuchElementException.class);
                }
            }

            @Nested
            class DeserializationErrorOnMailboxName {

                @Test
                void addedShouldThrowWhenNullMailboxName() {
                    assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                        "{" +
                        "  \"Added\": {" +
                        "    \"path\": {" +
                        "      \"namespace\": \"#private\"," +
                        "      \"user\": \"user\"," +
                        "      \"name\": null" +
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
                        "}").get())
                    .isInstanceOf(NoSuchElementException.class);
                }

                @Test
                void addedShouldThrowWhenMailboxNameIdIsANumber() {
                    assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                        "{" +
                        "  \"Added\": {" +
                        "    \"path\": {" +
                        "      \"namespace\": \"#private\"," +
                        "      \"user\": \"user\"," +
                        "      \"name\": 11861" +
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
                        "}").get())
                    .isInstanceOf(NoSuchElementException.class);
                }
            }
        }

        @Nested
        class DeserializationErrorOnAddedMap {
            @Test
            void addedShouldThrowWhenMapKeyIsNull() {
                assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                    "{" +
                    "  \"Added\": {" +
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

            @Nested
            class DeserializationErrorOnMessageUid {

                @Test
                void addedShouldThrowWhenMessageUidIsAString() {
                    assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                        "{" +
                        "  \"Added\": {" +
                        "    \"path\": {" +
                        "      \"namespace\": \"#private\"," +
                        "      \"user\": \"user\"," +
                        "      \"name\": null" +
                        "    }," +
                        "    \"mailboxId\": \"18\"," +
                        "    \"added\": {" +
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
                void addedShouldThrowWhenMessageUidIsNull() {
                    assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                        "{" +
                        "  \"Added\": {" +
                        "    \"path\": {" +
                        "      \"namespace\": \"#private\"," +
                        "      \"user\": \"user\"," +
                        "      \"name\": null" +
                        "    }," +
                        "    \"mailboxId\": \"18\"," +
                        "    \"added\": {" +
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
                void addedShouldThrowWhenModSeqIsAString() {
                    assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                        "{" +
                        "  \"Added\": {" +
                        "    \"path\": {" +
                        "      \"namespace\": \"#private\"," +
                        "      \"user\": \"user\"," +
                        "      \"name\": \"mailboxName\"" +
                        "    }," +
                        "    \"mailboxId\": \"18\"," +
                        "    \"added\": {" +
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
                void addedShouldThrowWhenModSeqIsNull() {
                    assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                        "{" +
                        "  \"Added\": {" +
                        "    \"path\": {" +
                        "      \"namespace\": \"#private\"," +
                        "      \"user\": \"user\"," +
                        "      \"name\": null" +
                        "    }," +
                        "    \"mailboxId\": \"18\"," +
                        "    \"added\": {" +
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
                void addedShouldThrowWhenSizeIsAString() {
                    assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                        "{" +
                        "  \"Added\": {" +
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
                void addedShouldThrowWhenSizeIsNull() {
                    assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                        "{" +
                        "  \"Added\": {" +
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
                void addedShouldThrowWhenMessageIdIsANumber() {
                    assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                        "{" +
                        "  \"Added\": {" +
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
                void addedShouldThrowWhenMessageIdIsNull() {
                    assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                        "{" +
                        "  \"Added\": {" +
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
                void addedShouldThrowWhenInternalDateIsNotInISOFormatBecauseOfMissingTWord() {
                    assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                        "{" +
                        "  \"Added\": {" +
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
                void addedShouldThrowWhenInternalDateContainsOnlyDate() {
                    assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                        "{" +
                        "  \"Added\": {" +
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
                void addedShouldThrowWhenInternalDateIsMissingHourPart() {
                    assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                        "{" +
                        "  \"Added\": {" +
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
                void addedShouldThrowWhenInternalDateIsMissingTimeZone() {
                    assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                        "{" +
                        "  \"Added\": {" +
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
                void addedShouldThrowWhenInternalDateIsMissingHours() {
                    assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                        "{" +
                        "  \"Added\": {" +
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
                void addedShouldThrowWhenInternalDateIsEmpty() {
                    assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                        "{" +
                        "  \"Added\": {" +
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
                void addedShouldThrowWhenInternalDateIsNull() {
                    assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                        "{" +
                        "  \"Added\": {" +
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
                        "  \"Added\": {" +
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
                        "  \"Added\": {" +
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
                        "  \"Added\": {" +
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
                        "  \"Added\": {" +
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
                        "  \"Added\": {" +
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
                        "  \"Added\": {" +
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
                        "  \"Added\": {" +
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
                            "  \"Added\": {" +
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
