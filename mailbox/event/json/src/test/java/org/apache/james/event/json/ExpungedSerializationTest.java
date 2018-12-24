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
        void expungedShouldThrowWhenMissingPath() {
            assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                "{" +
                    "  \"Expunged\": {" +
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
        }
    }
}
