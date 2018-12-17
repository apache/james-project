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

import java.util.NoSuchElementException;

import org.apache.james.core.User;
import org.apache.james.mailbox.Event;
import org.apache.james.mailbox.MessageMoveEvent;
import org.apache.james.mailbox.model.MessageMoves;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.model.TestMessageId;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MessageMoveEventSerializationTest {
    private static final EventSerializer EVENT_SERIALIZER = new EventSerializer(new TestId.Factory(), new TestMessageId.Factory());

    private static final Event EVENT = MessageMoveEvent.builder()
        .user(User.fromUsername("bob@domain.tld"))
        .messageId(TestMessageId.of(42))
        .messageMoves(
            MessageMoves.builder()
                .previousMailboxIds(TestId.of(18), TestId.of(24))
                .targetMailboxIds(TestId.of(36))
                .build())
        .build();
    private static final String JSON = "{" +
        "  \"MessageMoveEvent\": {" +
        "    \"user\": \"bob@domain.tld\"," +
        "    \"previousMailboxIds\": [\"18\", \"24\"]," +
        "    \"targetMailboxIds\": [\"36\"]," +
        "    \"messageIds\": [\"42\"]" +
        "  }" +
        "}";

    @Test
    void messageMoveEventShouldBeWellSerialized() {
        assertThatJson(EVENT_SERIALIZER.toJson(EVENT))
            .isEqualTo(JSON);
    }

    @Test
    void messageMoveEventShouldBeWellDeSerialized() {
        assertThat(EVENT_SERIALIZER.fromJson(JSON).get())
            .isEqualTo(EVENT);
    }

    @Nested
    class ValidPayloads {
        @Nested
        class NoVirtualHosting {
            private final Event event = MessageMoveEvent.builder()
                .user(User.fromUsername("bob"))
                .messageId(TestMessageId.of(42))
                .messageMoves(
                    MessageMoves.builder()
                        .previousMailboxIds(TestId.of(18), TestId.of(24))
                        .targetMailboxIds(TestId.of(36))
                        .build())
                .build();
            private final String json = "{" +
                "  \"MessageMoveEvent\": {" +
                "    \"user\": \"bob\"," +
                "    \"previousMailboxIds\": [\"18\", \"24\"]," +
                "    \"targetMailboxIds\": [\"36\"]," +
                "    \"messageIds\": [\"42\"]" +
                "  }" +
                "}";

            @Test
            void messageMoveEventShouldBeWellSerialized() {
                assertThatJson(EVENT_SERIALIZER.toJson(event))
                    .isEqualTo(json);
            }

            @Test
            void messageMoveEventShouldBeWellDeSerialized() {
                assertThat(EVENT_SERIALIZER.fromJson(json).get())
                    .isEqualTo(event);
            }
        }

        @Nested
        class EmptyTargetMailboxIds {
            private final Event event = MessageMoveEvent.builder()
                .user(User.fromUsername("bob"))
                .messageId(TestMessageId.of(42))
                .messageMoves(
                    MessageMoves.builder()
                        .previousMailboxIds(TestId.of(18), TestId.of(24))
                        .build())
                .build();
            private final String json = "{" +
                "  \"MessageMoveEvent\": {" +
                "    \"user\": \"bob\"," +
                "    \"previousMailboxIds\": [\"18\", \"24\"]," +
                "    \"targetMailboxIds\": []," +
                "    \"messageIds\": [\"42\"]" +
                "  }" +
                "}";

            @Test
            void messageMoveEventShouldBeWellSerialized() {
                assertThatJson(EVENT_SERIALIZER.toJson(event))
                    .isEqualTo(json);
            }

            @Test
            void messageMoveEventShouldBeWellDeSerialized() {
                assertThat(EVENT_SERIALIZER.fromJson(json).get())
                    .isEqualTo(event);
            }
        }

        @Nested
        class EmptyPreviousMailboxIds {
            private final Event event = MessageMoveEvent.builder()
                .user(User.fromUsername("bob"))
                .messageId(TestMessageId.of(42))
                .messageMoves(
                    MessageMoves.builder()
                        .targetMailboxIds(TestId.of(36))
                        .build())
                .build();
            private final String json = "{" +
                "  \"MessageMoveEvent\": {" +
                "    \"user\": \"bob\"," +
                "    \"previousMailboxIds\": []," +
                "    \"targetMailboxIds\": [\"36\"]," +
                "    \"messageIds\": [\"42\"]" +
                "  }" +
                "}";

            @Test
            void messageMoveEventShouldBeWellSerialized() {
                assertThatJson(EVENT_SERIALIZER.toJson(event))
                    .isEqualTo(json);
            }

            @Test
            void messageMoveEventShouldBeWellDeSerialized() {
                assertThat(EVENT_SERIALIZER.fromJson(json).get())
                    .isEqualTo(event);
            }
        }

        @Nested
        class EmptyMessagesIds {
            private final Event event = MessageMoveEvent.builder()
                .user(User.fromUsername("bob"))
                .messageMoves(
                    MessageMoves.builder()
                        .previousMailboxIds(TestId.of(18), TestId.of(24))
                        .targetMailboxIds(TestId.of(36))
                        .build())
                .build();
            private final String json = "{" +
                "  \"MessageMoveEvent\": {" +
                "    \"user\": \"bob\"," +
                "    \"previousMailboxIds\": [\"18\", \"24\"]," +
                "    \"targetMailboxIds\": [\"36\"]," +
                "    \"messageIds\": []" +
                "  }" +
                "}";

            @Test
            void messageMoveEventShouldBeWellSerialized() {
                assertThatJson(EVENT_SERIALIZER.toJson(event))
                    .isEqualTo(json);
            }

            @Test
            void messageMoveEventShouldBeWellDeSerialized() {
                assertThat(EVENT_SERIALIZER.fromJson(json).get())
                    .isEqualTo(event);
            }
        }
    }

    @Nested
    class InvalidPayloads {
        @Test
        void emptyUserShouldBeRejected() {
            assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson("{" +
                "  \"MessageMoveEvent\": {" +
                "    \"user\": \"\"," +
                "    \"previousMailboxIds\": [\"18\", \"24\"]," +
                "    \"targetMailboxIds\": [\"36\"]," +
                "    \"messageIds\": [\"42\"]" +
                "  }" +
                "}").get())
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void basUsersShouldBeRejected() {
            assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson("{" +
                "  \"MessageMoveEvent\": {" +
                "    \"user\": \"bob@domain.tld@bad\"," +
                "    \"previousMailboxIds\": [\"18\", \"24\"]," +
                "    \"targetMailboxIds\": [\"36\"]," +
                "    \"messageIds\": [\"42\"]" +
                "  }" +
                "}").get())
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void nonStringUserShouldBeRejected() {
            assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson("{" +
                "  \"MessageMoveEvent\": {" +
                "    \"user\": 42," +
                "    \"previousMailboxIds\": [\"18\", \"24\"]," +
                "    \"targetMailboxIds\": [\"36\"]," +
                "    \"messageIds\": [\"42\"]" +
                "  }" +
                "}").get())
                .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        void nullUserShouldBeRejected() {
            assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson("{" +
                "  \"MessageMoveEvent\": {" +
                "    \"user\": null," +
                "    \"previousMailboxIds\": [\"18\", \"24\"]," +
                "    \"targetMailboxIds\": [\"36\"]," +
                "    \"messageIds\": [\"42\"]" +
                "  }" +
                "}").get())
                .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        void nullPreviousMailboxIdsShouldBeRejected() {
            assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson("{" +
                "  \"MessageMoveEvent\": {" +
                "    \"user\": \"bob@domain.tld\"," +
                "    \"previousMailboxIds\": null," +
                "    \"targetMailboxIds\": [\"36\"]," +
                "    \"messageIds\": [\"42\"]" +
                "  }" +
                "}").get())
                .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        void nonCollectionPreviousMailboxIdsShouldBeRejected() {
            assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson("{" +
                "  \"MessageMoveEvent\": {" +
                "    \"user\": \"bob@domain.tld\"," +
                "    \"previousMailboxIds\": 42," +
                "    \"targetMailboxIds\": [\"36\"]," +
                "    \"messageIds\": [\"42\"]" +
                "  }" +
                "}").get())
                .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        void nonStringElementInPreviousMailboxIdsShouldBeRejected() {
            assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson("{" +
                "  \"MessageMoveEvent\": {" +
                "    \"user\": \"bob@domain.tld\"," +
                "    \"previousMailboxIds\": [42]," +
                "    \"targetMailboxIds\": [\"36\"]," +
                "    \"messageIds\": [\"42\"]" +
                "  }" +
                "}").get())
                .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        void nullElementInPreviousMailboxIdsShouldBeRejected() {
            assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson("{" +
                "  \"MessageMoveEvent\": {" +
                "    \"user\": \"bob@domain.tld\"," +
                "    \"previousMailboxIds\": [null]," +
                "    \"targetMailboxIds\": [\"36\"]," +
                "    \"messageIds\": [\"42\"]" +
                "  }" +
                "}").get())
                .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        void nullTargetMailboxIdsShouldBeRejected() {
            assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson("{" +
                "  \"MessageMoveEvent\": {" +
                "    \"user\": \"bob@domain.tld\"," +
                "    \"previousMailboxIds\": [\"36\"]," +
                "    \"targetMailboxIds\": null," +
                "    \"messageIds\": [\"42\"]" +
                "  }" +
                "}").get())
                .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        void nonCollectionTargetMailboxIdsShouldBeRejected() {
            assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson("{" +
                "  \"MessageMoveEvent\": {" +
                "    \"user\": \"bob@domain.tld\"," +
                "    \"previousMailboxIds\": [\"36\"]," +
                "    \"targetMailboxIds\": 42," +
                "    \"messageIds\": [\"42\"]" +
                "  }" +
                "}").get())
                .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        void nonStringElementInTargetMailboxIdsShouldBeRejected() {
            assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson("{" +
                "  \"MessageMoveEvent\": {" +
                "    \"user\": \"bob@domain.tld\"," +
                "    \"previousMailboxIds\": [\"36\"]," +
                "    \"targetMailboxIds\": [42]," +
                "    \"messageIds\": [\"42\"]" +
                "  }" +
                "}").get())
                .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        void nullElementInTargetMailboxIdsShouldBeRejected() {
            assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson("{" +
                "  \"MessageMoveEvent\": {" +
                "    \"user\": \"bob@domain.tld\"," +
                "    \"previousMailboxIds\": [\"36\"]," +
                "    \"targetMailboxIds\": [null]," +
                "    \"messageIds\": [\"42\"]" +
                "  }" +
                "}").get())
                .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        void nullMessageIdsShouldBeRejected() {
            assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson("{" +
                "  \"MessageMoveEvent\": {" +
                "    \"user\": \"bob@domain.tld\"," +
                "    \"previousMailboxIds\": [\"36\"]," +
                "    \"targetMailboxIds\": [\"42\"]," +
                "    \"messageIds\": null" +
                "  }" +
                "}").get())
                .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        void nonCollectionMessageIdsShouldBeRejected() {
            assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson("{" +
                "  \"MessageMoveEvent\": {" +
                "    \"user\": \"bob@domain.tld\"," +
                "    \"previousMailboxIds\": [\"36\"]," +
                "    \"targetMailboxIds\": [\"42\"]," +
                "    \"messageIds\": 42" +
                "  }" +
                "}").get())
                .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        void nonStringElementInMessageIdsShouldBeRejected() {
            assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson("{" +
                "  \"MessageMoveEvent\": {" +
                "    \"user\": \"bob@domain.tld\"," +
                "    \"previousMailboxIds\": [\"36\"]," +
                "    \"targetMailboxIds\": [\"42\"]," +
                "    \"messageIds\": [42]" +
                "  }" +
                "}").get())
                .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        void nullElementInMessageIdsShouldBeRejected() {
            assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson("{" +
                "  \"MessageMoveEvent\": {" +
                "    \"user\": \"bob@domain.tld\"," +
                "    \"previousMailboxIds\": [\"36\"]," +
                "    \"targetMailboxIds\": [\"42\"]," +
                "    \"messageIds\": [null]" +
                "  }" +
                "}").get())
                .isInstanceOf(NoSuchElementException.class);
        }
    }
}
