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

import java.util.List;
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
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.model.TestMessageId;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

import net.javacrumbs.jsonunit.core.Option;

class FlagsUpdatedSerializationTest {

    private static final User USER = User.fromUsername("user");
    private static final MailboxSession.SessionId SESSION_ID = MailboxSession.SessionId.of(42);
    private static final MailboxId MAILBOX_ID = TestId.of(18);
    private static final String MAILBOX_NAME = "mailboxName";
    private static final MailboxPath MAILBOX_PATH = new MailboxPath(MailboxConstants.USER_NAMESPACE, "user", MAILBOX_NAME);
    private static final MessageUid MESSAGE_UID_1 = MessageUid.of(123456);
    private static final MessageUid MESSAGE_UID_2 = MessageUid.of(654321);

    private static final int MOD_SEQ_1 = 35;
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

    private static final int MOD_SEQ_2 = 36;
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

    private static final MailboxListener.FlagsUpdated DEFAULT_EVENT = new MailboxListener.FlagsUpdated(SESSION_ID, USER,
        MAILBOX_PATH, MAILBOX_ID, UPDATED_FLAGS_LIST);
    private static final String DEFAULT_EVENT_JSON =
        "{" +
        "  \"FlagsUpdated\": {" +
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

    private static final EventSerializer EVENT_SERIALIZER = new EventSerializer(new TestId.Factory(), new TestMessageId.Factory());

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
        private final MailboxListener.FlagsUpdated emptyUpdatedFlagsEvent = new MailboxListener.FlagsUpdated(SESSION_ID, USER, MAILBOX_PATH,
            MAILBOX_ID, emptyUpdatedFlags);

        private static final String EVENT_JSON_WITH_EMPTY_UPDATED_FLAGS =
            "{" +
                "  \"FlagsUpdated\": {" +
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
    class DeserializationError {
        @Test
        void flagsUpdatedShouldThrowWhenMissingSessionId() {
            assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                "{" +
                    "  \"FlagsUpdated\": {" +
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
        void flagsUpdatedShouldThrowWhenMissingUser() {
            assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                "{" +
                    "  \"FlagsUpdated\": {" +
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

        @Nested
        class DeserializationErrorOnUpdatedFlags {
            @Nested
            class DeserializationErrorOnMoqSeq {

                @Test
                void flagsUpdatedShouldThrowWhenMoqSeqIsAString() {
                    assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                        "{" +
                        "  \"FlagsUpdated\": {" +
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
                        "        \"modSeq\": \"35\"," +
                        "        \"oldFlags\": {\"systemFlags\":[\"Deleted\",\"Seen\"],\"userFlags\":[\"Old Flag 1\"]}," +
                        "        \"newFlags\": {\"systemFlags\":[\"Answered\",\"Draft\"],\"userFlags\":[\"New Flag 1\"]}" +
                        "      }" +
                        "    ]," +
                        "    \"user\": \"user\"" +
                        "  }" +
                        "}").get())
                    .isInstanceOf(NoSuchElementException.class);
                }

                @Test
                void flagsUpdatedShouldThrowWhenMoqSeqIsNull() {
                    assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                        "{" +
                        "  \"FlagsUpdated\": {" +
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
                        "        \"modSeq\": null," +
                        "        \"oldFlags\": {\"systemFlags\":[\"Deleted\",\"Seen\"],\"userFlags\":[\"Old Flag 1\"]}," +
                        "        \"newFlags\": {\"systemFlags\":[\"Answered\",\"Draft\"],\"userFlags\":[\"New Flag 1\"]}" +
                        "      }" +
                        "    ]," +
                        "    \"user\": \"user\"" +
                        "  }" +
                        "}").get())
                    .isInstanceOf(NoSuchElementException.class);
                }

                @Test
                void flagsUpdatedShouldThrowWhenMoqSeqIsNotALongNumber() {
                    assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                        "{" +
                        "  \"FlagsUpdated\": {" +
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
                        "        \"modSeq\": 35.2567454," +
                        "        \"oldFlags\": {\"systemFlags\":[\"Deleted\",\"Seen\"],\"userFlags\":[\"Old Flag 1\"]}," +
                        "        \"newFlags\": {\"systemFlags\":[\"Answered\",\"Draft\"],\"userFlags\":[\"New Flag 1\"]}" +
                        "      }" +
                        "    ]," +
                        "    \"user\": \"user\"" +
                        "  }" +
                        "}").get())
                    .isInstanceOf(NoSuchElementException.class);
                }
            }

            @Nested
            class DeserializationErrorOnOldFlags {
                @Test
                void flagsUpdatedShouldThrowWhenOldFlagsIsNull() {
                    assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                        "{" +
                        "  \"FlagsUpdated\": {" +
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
                        "        \"modSeq\": \"35\"," +
                        "        \"oldFlags\": null," +
                        "        \"newFlags\": {\"systemFlags\":[\"Answered\",\"Draft\"],\"userFlags\":[\"New Flag 1\"]}" +
                        "      }" +
                        "    ]," +
                        "    \"user\": \"user\"" +
                        "  }" +
                        "}").get())
                    .isInstanceOf(NoSuchElementException.class);
                }
            }

            @Nested
            class DeserializationErrorOnNewFlags {
                @Test
                void flagsUpdatedShouldThrowWhenNewFlagsIsNull() {
                    assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                        "{" +
                        "  \"FlagsUpdated\": {" +
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
                        "        \"modSeq\": \"35\"," +
                        "        \"newFlags\": null," +
                        "        \"oldFlags\": {\"systemFlags\":[\"Answered\",\"Draft\"],\"userFlags\":[\"New Flag 1\"]}" +
                        "      }" +
                        "    ]," +
                        "    \"user\": \"user\"" +
                        "  }" +
                        "}").get())
                    .isInstanceOf(NoSuchElementException.class);
                }
            }
        }
    }
}
