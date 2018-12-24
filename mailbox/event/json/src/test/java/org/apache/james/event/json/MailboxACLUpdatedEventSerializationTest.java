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
import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.acl.ACLDiff;
import org.apache.james.mailbox.exception.UnsupportedRightException;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.model.TestMessageId;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonParseException;

class MailboxACLUpdatedEventSerializationTest {

    private static final User USER = User.fromUsername("user");
    private static final MailboxACL.EntryKey ENTRY_KEY = org.apache.james.mailbox.model.MailboxACL.EntryKey.createGroupEntryKey("any", false);
    private static final MailboxACL.Rfc4314Rights RIGHTS = new MailboxACL.Rfc4314Rights(MailboxACL.Right.Administer, MailboxACL.Right.Read);
    private static final EventSerializer EVENT_SERIALIZER = new EventSerializer(new TestId.Factory(), new TestMessageId.Factory());
    private static final MailboxACL MAILBOX_ACL = new MailboxACL(
        new MailboxACL.Entry(ENTRY_KEY, RIGHTS),
        new MailboxACL.Entry(MailboxACL.EntryKey.createUserEntryKey("alice", true),
            new MailboxACL.Rfc4314Rights(MailboxACL.Right.Insert)));

    private static final MailboxListener.MailboxACLUpdated MAILBOX_ACL_UPDATED = new MailboxListener.MailboxACLUpdated(
                MailboxSession.SessionId.of(6),
                USER,
                new MailboxPath(MailboxConstants.USER_NAMESPACE, "bob", "mailboxName"),
                ACLDiff.computeDiff(MailboxACL.EMPTY, MAILBOX_ACL),
                TestId.of(23));

    private static final String MAILBOX_ACL_UPDATED_JSON = "{" +
        "  \"MailboxACLUpdated\":{" +
        "    \"mailboxPath\":{" +
        "       \"namespace\":\"#private\"," +
        "       \"user\":\"bob\"," +
        "       \"name\":\"mailboxName\"" +
        "      }," +
        "    \"aclDiff\":{" +
        "       \"oldACL\":{}," +
        "       \"newACL\":{\"$any\":\"ar\", \"-alice\":\"i\"}}," +
        "    \"mailboxId\":\"23\"," +
        "    \"sessionId\":6," +
        "    \"user\":\"user\"" +
        "   }" +
        "}";

    @Test
    void mailboxACLUpdatedShouldBeSerialized() {
        assertThatJson(EVENT_SERIALIZER.toJson(MAILBOX_ACL_UPDATED))
            .isEqualTo(MAILBOX_ACL_UPDATED_JSON);
    }

    @Test
    void mailboxACLUpdatedShouldBeDeserialized() {
        assertThat(EVENT_SERIALIZER.fromJson(MAILBOX_ACL_UPDATED_JSON).get())
            .isEqualTo(MAILBOX_ACL_UPDATED);
    }

    @Nested
    class NullUserInMailboxPath {
        private final String NULL_USER = null;
        private static final String JSON_2 = "{" +
            "  \"MailboxACLUpdated\":{" +
            "    \"mailboxPath\":{" +
            "       \"namespace\":\"#private\"," +
            "       \"name\":\"mailboxName\"" +
            "      }," +
            "    \"aclDiff\":{" +
            "       \"oldACL\":{}," +
            "       \"newACL\":{\"$any\":\"ar\"}}," +
            "    \"mailboxId\":\"23\"," +
            "    \"sessionId\":6," +
            "    \"user\":\"user\"" +
            "   }" +
            "}";

        private final MailboxACL MAILBOX_ACL= new MailboxACL(
                new MailboxACL.Entry(ENTRY_KEY, RIGHTS));

        private final MailboxListener.MailboxACLUpdated UPDATED_EVENT = new MailboxListener.MailboxACLUpdated(
                MailboxSession.SessionId.of(6),
                USER,
                new MailboxPath(MailboxConstants.USER_NAMESPACE, NULL_USER, "mailboxName"),
                ACLDiff.computeDiff(MailboxACL.EMPTY, MAILBOX_ACL),
                TestId.of(23));

        @Test
        void mailboxACLUpdatedShouldBeWellSerializedWithNullUser() {
            assertThatJson(EVENT_SERIALIZER.toJson(UPDATED_EVENT))
                .isEqualTo(JSON_2);
        }

        @Test
        void mailboxACLUpdatedShouldBeWellDeSerializedWithNullUser() {
            assertThat(EVENT_SERIALIZER.fromJson(JSON_2).get())
                .isEqualTo(UPDATED_EVENT);
        }
    }

    @Nested
    class NullNameSpaceInMailboxPath {

        private final MailboxACL MAILBOX_ACL = new MailboxACL(
            new MailboxACL.Entry(ENTRY_KEY, RIGHTS));

        private final MailboxListener.MailboxACLUpdated UPDATED_EVENT = new MailboxListener.MailboxACLUpdated(
            MailboxSession.SessionId.of(6),
            USER,
            new MailboxPath(MailboxConstants.USER_NAMESPACE, "bob", "mailboxName"),
            ACLDiff.computeDiff(MailboxACL.EMPTY, MAILBOX_ACL),
            TestId.of(23));

        @Test
        void mailboxAddedShouldBeWellDeSerializedWhenMissingNameSpace() {
            assertThat(EVENT_SERIALIZER.fromJson(
                "{" +
                "  \"MailboxACLUpdated\":{" +
                "    \"mailboxPath\":{" +
                "       \"user\": \"bob\"," +
                "       \"name\":\"mailboxName\"" +
                "      }," +
                "    \"aclDiff\":{" +
                "       \"oldACL\":{}," +
                "       \"newACL\":{\"$any\":\"ar\"}}," +
                "    \"mailboxId\":\"23\"," +
                "    \"sessionId\":6," +
                "    \"user\":\"user\"" +
                "   }" +
                "}").get())
                .isEqualTo(UPDATED_EVENT);
        }

        @Test
        void mailboxAddedShouldBeWellDeSerializedWhenNullNameSpace() {
            assertThat(EVENT_SERIALIZER.fromJson(
                "{" +
                "  \"MailboxACLUpdated\":{" +
                "    \"mailboxPath\":{" +
                "       \"namespace\":null," +
                "       \"user\": \"bob\"," +
                "       \"name\":\"mailboxName\"" +
                "      }," +
                "    \"aclDiff\":{" +
                "       \"oldACL\":{}," +
                "       \"newACL\":{\"$any\":\"ar\"}}," +
                "    \"mailboxId\":\"23\"," +
                "    \"sessionId\":6," +
                "    \"user\":\"user\"" +
                "   }" +
                "}").get())
                .isEqualTo(UPDATED_EVENT);
        }
    }

    @Nested
    class EmptyRightInMailboxACL {

        private final String jsonNullRight =
            "{" +
            "  \"MailboxACLUpdated\":{" +
            "    \"mailboxPath\":{" +
            "       \"namespace\":\"#private\"," +
            "       \"user\":\"bob\"," +
            "       \"name\":\"mailboxName\"" +
            "      }," +
            "    \"aclDiff\":{" +
            "       \"oldACL\":{\"$any\":\"\"}," +
            "       \"newACL\":{}}," +
            "    \"mailboxId\":\"23\"," +
            "    \"sessionId\":6," +
            "    \"user\":\"user\"" +
            "   }" +
            "}";

        private final MailboxACL mailboxACL = new MailboxACL(
                new MailboxACL.Entry(ENTRY_KEY, new MailboxACL.Rfc4314Rights()));

        private final MailboxListener.MailboxACLUpdated mailboxACLUpdated = new MailboxListener.MailboxACLUpdated(
                MailboxSession.SessionId.of(6),
                USER,
                new MailboxPath(MailboxConstants.USER_NAMESPACE, "bob", "mailboxName"),
                ACLDiff.computeDiff(mailboxACL, MailboxACL.EMPTY),
                TestId.of(23));

        @Test
        void mailboxACLUpdatedShouldBeWellSerializedWithNullRight() {
            assertThatJson(EVENT_SERIALIZER.toJson(mailboxACLUpdated))
                .isEqualTo(jsonNullRight);
        }

        @Test
        void mailboxACLUpdatedShouldBeWellDeSerializedWithNullUser() {
            assertThat(EVENT_SERIALIZER.fromJson(jsonNullRight).get())
                .isEqualTo(mailboxACLUpdated);
        }
    }

    @Nested
    class DoubleRightInMailboxACL {

        private final String jsonDoubleRight =
            "{" +
            "  \"MailboxACLUpdated\":{" +
            "    \"mailboxPath\":{" +
            "       \"namespace\":\"#private\"," +
            "       \"user\":\"bob\"," +
            "       \"name\":\"mailboxName\"" +
            "      }," +
            "    \"aclDiff\":{" +
            "       \"oldACL\":{\"$any\":\"aa\"}," +
            "       \"newACL\":{}}," +
            "    \"mailboxId\":\"23\"," +
            "    \"sessionId\":6," +
            "    \"user\":\"user\"" +
            "   }" +
            "}";

        private final MailboxACL mailboxACL = new MailboxACL(
            new MailboxACL.Entry(ENTRY_KEY, new MailboxACL.Rfc4314Rights(MailboxACL.Right.Administer)));

        private final MailboxListener.MailboxACLUpdated mailboxACLUpdated = new MailboxListener.MailboxACLUpdated(
            MailboxSession.SessionId.of(6),
            USER,
            new MailboxPath(MailboxConstants.USER_NAMESPACE, "bob", "mailboxName"),
            ACLDiff.computeDiff(mailboxACL, MailboxACL.EMPTY),
            TestId.of(23));

        @Test
        void mailboxACLUpdatedShouldBeWellSerializedWithNullRight() {
            assertThatJson(EVENT_SERIALIZER.toJson(mailboxACLUpdated))
                .isNotEqualTo(jsonDoubleRight);
        }

        @Test
        void mailboxACLUpdatedShouldBeWellDeSerializedWithNullUser() {
            assertThat(EVENT_SERIALIZER.fromJson(jsonDoubleRight).get())
                .isEqualTo(mailboxACLUpdated);
        }
    }

    @Nested
    class DeserializationErrors {
        @Test
        void mailboxACLUpdatedShouldThrowWhenMissingSessionId() {
            assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                "{" +
                    "  \"MailboxACLUpdated\":{" +
                    "    \"mailboxPath\":{" +
                    "       \"namespace\":\"#private\"," +
                    "       \"user\":\"bob\"," +
                    "       \"name\":\"mailboxName\"" +
                    "      }," +
                    "    \"aclDiff\":{" +
                    "       \"oldACL\":{}," +
                    "       \"newACL\":{\"$any\":\"ar\"}}," +
                    "    \"mailboxId\":\"23\"," +
                    "    \"user\":\"user\"" +
                    "   }" +
                    "}").get())
                .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        void mailboxACLUpdatedShouldThrowWhenMissingUser() {
            assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                "{" +
                    "  \"MailboxACLUpdated\":{" +
                    "    \"mailboxPath\":{" +
                    "       \"namespace\":\"#private\"," +
                    "       \"name\":\"mailboxName\"" +
                    "      }," +
                    "    \"aclDiff\":{" +
                    "       \"oldACL\":{}," +
                    "       \"newACL\":{\"$any\":\"ar\"}}," +
                    "    \"mailboxId\":\"23\"," +
                    "    \"sessionId\":6" +
                    "   }" +
                    "}").get())
                .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        void mailboxACLUpdatedShouldThrowWhenMissingACLDiff() {
            assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                "{" +
                    "  \"MailboxACLUpdated\":{" +
                    "    \"mailboxPath\":{" +
                    "       \"namespace\":\"#private\"," +
                    "       \"name\":\"mailboxName\"" +
                    "      }," +
                    "    \"mailboxId\":\"23\"," +
                    "    \"sessionId\":6," +
                    "    \"user\":\"user\"" +
                    "   }" +
                    "}").get())
                .isInstanceOf(NoSuchElementException.class);
        }

        @Nested
        class DeserializationErrorOnOldACL {

            @Test
            void mailboxACLUpdatedShouldThrowWhenMissingOldACLinACLDiff() {
                assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                    "{" +
                    "  \"MailboxACLUpdated\":{" +
                    "    \"mailboxPath\":{" +
                    "       \"namespace\":\"#private\"," +
                    "       \"name\":\"mailboxName\"" +
                    "      }," +
                    "    \"aclDiff\":{" +
                    "       \"newACL\":{\"$any\":\"ar\"}}," +
                    "    \"mailboxId\":\"23\"," +
                    "    \"sessionId\":6," +
                    "    \"user\":\"user\"" +
                    "   }" +
                    "}").get())
                    .isInstanceOf(NoSuchElementException.class);
            }

            @Nested
            class DeserializationErrorOnOldACLEntryKey {

                @Test
                void mailboxACLUpdatedShouldThrowWhenNotIncludedNameInEntryKey() {
                    assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                    "{" +
                        "  \"MailboxACLUpdated\":{" +
                        "    \"mailboxPath\":{" +
                        "       \"namespace\":\"#private\"," +
                        "       \"user\":\"bob\"," +
                        "       \"name\":\"mailboxName\"" +
                        "      }," +
                        "    \"aclDiff\":{" +
                        "       \"oldACL\":{\"$\":\"ar\"}}," +
                        "       \"newACL\":{}," +
                        "    \"mailboxId\":\"23\"," +
                        "    \"sessionId\":6," +
                        "    \"user\":\"user\"" +
                        "   }" +
                        "}").get())
                        .isInstanceOf(IllegalStateException.class);
                }

                @Test
                void mailboxACLUpdatedShouldThrowWhenNameInEntryKeyIsNotString() {
                    assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                        "{" +
                        "  \"MailboxACLUpdated\":{" +
                        "    \"mailboxPath\":{" +
                        "       \"namespace\":\"#private\"," +
                        "       \"user\":\"bob\"," +
                        "       \"name\":\"mailboxName\"" +
                        "      }," +
                        "    \"aclDiff\":{" +
                        "       \"oldACL\":{1234:\"ar\"}}," +
                        "       \"newACL\":{}," +
                        "    \"mailboxId\":\"23\"," +
                        "    \"sessionId\":6," +
                        "    \"user\":\"user\"" +
                        "   }" +
                        "}").get())
                        .isInstanceOf(JsonParseException.class);
                }

                @Test
                void mailboxACLUpdatedShouldThrowWhenNameInEntryKeyIsEmpty() {
                    assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                        "{" +
                        "  \"MailboxACLUpdated\":{" +
                        "    \"mailboxPath\":{" +
                        "       \"namespace\":\"#private\"," +
                        "       \"user\":\"bob\"," +
                        "       \"name\":\"mailboxName\"" +
                        "      }," +
                        "    \"aclDiff\":{" +
                        "       \"oldACL\":{\"\":\"ar\"}}," +
                        "       \"newACL\":{}," +
                        "    \"mailboxId\":\"23\"," +
                        "    \"sessionId\":6," +
                        "    \"user\":\"user\"" +
                        "   }" +
                        "}").get())
                        .isInstanceOf(IllegalArgumentException.class);
                }

                @Test
                void mailboxACLUpdatedShouldThrowWhenNullEntryKey() {
                    assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                        "{" +
                        "  \"MailboxACLUpdated\":{" +
                        "    \"mailboxPath\":{" +
                        "       \"namespace\":\"#private\"," +
                        "       \"user\":\"bob\"," +
                        "       \"name\":\"mailboxName\"" +
                        "      }," +
                        "    \"aclDiff\":{" +
                        "       \"oldACL\":{null:\"ar\"}}," +
                        "       \"newACL\":{}," +
                        "    \"mailboxId\":\"23\"," +
                        "    \"sessionId\":6," +
                        "    \"user\":\"user\"" +
                        "   }" +
                        "}").get())
                        .isInstanceOf(JsonParseException.class);
                }

                @Test
                void mailboxACLUpdatedShouldThrowWhenEntryKeyIsNotWellFormatted() {
                    assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                        "{" +
                        "  \"MailboxACLUpdated\":{" +
                        "    \"mailboxPath\":{" +
                        "       \"namespace\":\"#private\"," +
                        "       \"user\":\"bob\"," +
                        "       \"name\":\"mailboxName\"" +
                        "      }," +
                        "    \"aclDiff\":{" +
                        "       \"oldACL\":{\"-\":\"ar\"}}," +
                        "       \"newACL\":{}," +
                        "    \"mailboxId\":\"23\"," +
                        "    \"sessionId\":6," +
                        "    \"user\":\"user\"" +
                        "   }" +
                        "}").get())
                        .isInstanceOf(StringIndexOutOfBoundsException.class);
                }
            }

            @Nested
            class DeserializationErrorOnOldACLRight {

                @Test
                void mailboxACLUpdatedShouldThrowWhenUnsupportedRightInMailboxACL() {
                    assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                        "{" +
                        "  \"MailboxACLUpdated\":{" +
                        "    \"mailboxPath\":{" +
                        "       \"namespace\":\"#private\"," +
                        "       \"user\":\"bob\"," +
                        "       \"name\":\"mailboxName\"" +
                        "      }," +
                        "    \"aclDiff\":{" +
                        "       \"oldACL\":{\"$any\":\"unsupported\"}," +
                        "       \"newACL\":{\"$any\":\"a\"}}," +
                        "    \"mailboxId\":\"23\"," +
                        "    \"sessionId\":6," +
                        "    \"user\":\"user\"" +
                        "   }" +
                        "}").get())
                        .isInstanceOf(UnsupportedRightException.class);
                }

                @Test
                void mailboxACLUpdatedShouldThrowWhenNullRightInMailboxACL() {
                    assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                        "{" +
                        "  \"MailboxACLUpdated\":{" +
                        "    \"mailboxPath\":{" +
                        "       \"namespace\":\"#private\"," +
                        "       \"user\":\"bob\"," +
                        "       \"name\":\"mailboxName\"" +
                        "      }," +
                        "    \"aclDiff\":{" +
                        "       \"oldACL\":{\"$any\":null}}," +
                        "       \"newACL\":{}," +
                        "    \"mailboxId\":\"23\"," +
                        "    \"sessionId\":6," +
                        "    \"user\":\"user\"" +
                        "   }" +
                        "}").get())
                        .isInstanceOf(NoSuchElementException.class);
                }

                @Test
                void mailboxACLUpdatedShouldThrowWhenRightIsNotStringInMailboxACL() {
                    assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                        "{" +
                        "  \"MailboxACLUpdated\":{" +
                        "    \"mailboxPath\":{" +
                        "       \"namespace\":\"#private\"," +
                        "       \"user\":\"bob\"," +
                        "       \"name\":\"mailboxName\"" +
                        "      }," +
                        "    \"aclDiff\":{" +
                        "       \"oldACL\":{\"$any\":1234}}," +
                        "       \"newACL\":{}," +
                        "    \"mailboxId\":\"23\"," +
                        "    \"sessionId\":6," +
                        "    \"user\":\"user\"" +
                        "   }" +
                        "}").get())
                        .isInstanceOf(NoSuchElementException.class);
                }

            }
        }

        @Nested
        class DeserializationErrorOnNewACL {

            @Test
            void mailboxACLUpdatedShouldThrowWhenMissingNewACLinACLDiff() {
                assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                    "{" +
                    "  \"MailboxACLUpdated\":{" +
                    "    \"mailboxPath\":{" +
                    "       \"namespace\":\"#private\"," +
                    "       \"name\":\"mailboxName\"" +
                    "      }," +
                    "    \"aclDiff\":{" +
                    "       \"oldACL\":{}}," +
                    "    \"mailboxId\":\"23\"," +
                    "    \"sessionId\":6," +
                    "    \"user\":\"user\"" +
                    "   }" +
                    "}").get())
                    .isInstanceOf(NoSuchElementException.class);
            }

            @Nested
            class DeserializationErrorOnNewACLEntryKey {

                @Test
                void mailboxACLUpdatedShouldThrowWhenNotIncludedNameInEntryKey() {
                    assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                    "{" +
                    "  \"MailboxACLUpdated\":{" +
                    "    \"mailboxPath\":{" +
                    "       \"namespace\":\"#private\"," +
                    "       \"user\":\"bob\"," +
                    "       \"name\":\"mailboxName\"" +
                    "      }," +
                    "    \"aclDiff\":{" +
                    "       \"oldACL\":{}," +
                    "       \"newACL\":{\"$\":\"ar\"}}," +
                    "    \"mailboxId\":\"23\"," +
                    "    \"sessionId\":6," +
                    "    \"user\":\"user\"" +
                    "   }" +
                    "}").get())
                        .isInstanceOf(IllegalStateException.class);
                }

                @Test
                void mailboxACLUpdatedShouldThrowWhenNameInEntryKeyIsNotString() {
                    assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                        "{" +
                        "  \"MailboxACLUpdated\":{" +
                        "    \"mailboxPath\":{" +
                        "       \"namespace\":\"#private\"," +
                        "       \"user\":\"bob\"," +
                        "       \"name\":\"mailboxName\"" +
                        "      }," +
                        "    \"aclDiff\":{" +
                        "       \"oldACL\":{}," +
                        "       \"newACL\":{1234:\"ar\"}}," +
                        "    \"mailboxId\":\"23\"," +
                        "    \"sessionId\":6," +
                        "    \"user\":\"user\"" +
                        "   }" +
                        "}").get())
                        .isInstanceOf(JsonParseException.class);
                }

                @Test
                void mailboxACLUpdatedShouldThrowWhenNameInEntryKeyIsEmpty() {
                    assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                        "{" +
                        "  \"MailboxACLUpdated\":{" +
                        "    \"mailboxPath\":{" +
                        "       \"namespace\":\"#private\"," +
                        "       \"user\":\"bob\"," +
                        "       \"name\":\"mailboxName\"" +
                        "      }," +
                        "    \"aclDiff\":{" +
                        "       \"oldACL\":{}," +
                        "       \"newACL\":{\"\":\"ar\"}}," +
                        "    \"mailboxId\":\"23\"," +
                        "    \"sessionId\":6," +
                        "    \"user\":\"user\"" +
                        "   }" +
                        "}").get())
                        .isInstanceOf(IllegalArgumentException.class);
                }

                @Test
                void mailboxACLUpdatedShouldThrowWhenNullEntryKey() {
                    assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                        "{" +
                        "  \"MailboxACLUpdated\":{" +
                        "    \"mailboxPath\":{" +
                        "       \"namespace\":\"#private\"," +
                        "       \"user\":\"bob\"," +
                        "       \"name\":\"mailboxName\"" +
                        "      }," +
                        "    \"aclDiff\":{" +
                        "       \"oldACL\":{}," +
                        "       \"newACL\":{null:\"ar\"}}," +
                        "    \"mailboxId\":\"23\"," +
                        "    \"sessionId\":6," +
                        "    \"user\":\"user\"" +
                        "   }" +
                        "}").get())
                        .isInstanceOf(JsonParseException.class);
                }

                @Test
                void mailboxACLUpdatedShouldThrowWhenEntryKeyIsNotWellFormatted() {
                    assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                        "{" +
                        "  \"MailboxACLUpdated\":{" +
                        "    \"mailboxPath\":{" +
                        "       \"namespace\":\"#private\"," +
                        "       \"user\":\"bob\"," +
                        "       \"name\":\"mailboxName\"" +
                        "      }," +
                        "    \"aclDiff\":{" +
                        "       \"oldACL\":{}," +
                        "       \"newACL\":{\"-\":\"ar\"}}," +
                        "    \"mailboxId\":\"23\"," +
                        "    \"sessionId\":6," +
                        "    \"user\":\"user\"" +
                        "   }" +
                        "}").get())
                        .isInstanceOf(StringIndexOutOfBoundsException.class);
                }
            }

            @Nested
            class DeserializationErrorOnNewACLRight {

                @Test
                void mailboxACLUpdatedShouldThrowWhenUnsupportedRightInNewACL() {
                    assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                        "{" +
                        "  \"MailboxACLUpdated\":{" +
                        "    \"mailboxPath\":{" +
                        "       \"namespace\":\"#private\"," +
                        "       \"user\":\"bob\"," +
                        "       \"name\":\"mailboxName\"" +
                        "      }," +
                        "    \"aclDiff\":{" +
                        "       \"oldACL\":{\"$any\":\"a\"}," +
                        "       \"newACL\":{\"$any\":\"unsupported\"}}," +
                        "    \"mailboxId\":\"23\"," +
                        "    \"sessionId\":6," +
                        "    \"user\":\"user\"" +
                        "   }" +
                        "}").get())
                        .isInstanceOf(UnsupportedRightException.class);
                }

                @Test
                void mailboxACLUpdatedShouldThrowWhenNullRightInMailboxACL() {
                    assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                        "{" +
                        "  \"MailboxACLUpdated\":{" +
                        "    \"mailboxPath\":{" +
                        "       \"namespace\":\"#private\"," +
                        "       \"user\":\"bob\"," +
                        "       \"name\":\"mailboxName\"" +
                        "      }," +
                        "    \"aclDiff\":{" +
                        "       \"oldACL\":{}," +
                        "       \"newACL\":{\"$any\":null}}," +
                        "    \"mailboxId\":\"23\"," +
                        "    \"sessionId\":6," +
                        "    \"user\":\"user\"" +
                        "   }" +
                        "}").get())
                        .isInstanceOf(NoSuchElementException.class);
                }

                @Test
                void mailboxACLUpdatedShouldThrowWhenRightIsNotString() {
                    assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                        "{" +
                        "  \"MailboxACLUpdated\":{" +
                        "    \"mailboxPath\":{" +
                        "       \"namespace\":\"#private\"," +
                        "       \"user\":\"bob\"," +
                        "       \"name\":\"mailboxName\"" +
                        "      }," +
                        "    \"aclDiff\":{" +
                        "       \"oldACL\":{}," +
                        "       \"newACL\":{\"$any\":1234}}," +
                        "    \"mailboxId\":\"23\"," +
                        "    \"sessionId\":6," +
                        "    \"user\":\"user\"" +
                        "   }" +
                        "}").get())
                        .isInstanceOf(NoSuchElementException.class);
                }
            }
        }

        @Test
        void mailboxACLUpdatedShouldThrowWhenMissingMailboxId() {
            assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                "{" +
                    "  \"MailboxACLUpdated\":{" +
                    "    \"mailboxPath\":{" +
                    "       \"namespace\":\"#private\"," +
                    "       \"user\":\"bob\"," +
                    "       \"name\":\"mailboxName\"" +
                    "      }," +
                    "    \"aclDiff\":{" +
                    "       \"oldACL\":{}," +
                    "       \"newACL\":{\"$any\":\"ar\"}}," +
                    "    \"sessionId\":6," +
                    "    \"user\":\"user\"" +
                    "   }" +
                    "}").get())
                .isInstanceOf(NoSuchElementException.class);
        }

        @Nested
        class DeserializationErrorOnMailboxPath {

            @Nested
            class DeserializationErrorOnNameSpace {
                @Test
                void mailboxACLUpdatedShouldThrowWhenNameSpaceIsNotAString() {
                    assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                        "{" +
                            "  \"MailboxACLUpdated\":{" +
                            "    \"mailboxPath\":{" +
                            "       \"namespace\":230192.06," +
                            "       \"user\":\"bob\"," +
                            "       \"name\":\"mailboxName\"" +
                            "      }," +
                            "    \"aclDiff\":{" +
                            "       \"oldACL\":{}," +
                            "       \"newACL\":{\"$any\":\"ar\"}}," +
                            "    \"mailboxId\":\"123\"," +
                            "    \"sessionId\":6," +
                            "    \"user\":\"user\"" +
                            "   }" +
                            "}").get())
                        .isInstanceOf(NoSuchElementException.class);
                }
            }

            @Nested
            class DeserializationErrorOnUser {
                @Test
                void mailboxACLUpdatedShouldThrowWhenUserIsNotAString() {
                    assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                        "{" +
                            "  \"MailboxACLUpdated\":{" +
                            "    \"mailboxPath\":{" +
                            "       \"namespace\":230192.06," +
                            "       \"user\":180806," +
                            "       \"name\":\"mailboxName\"" +
                            "      }," +
                            "    \"aclDiff\":{" +
                            "       \"oldACL\":{}," +
                            "       \"newACL\":{\"$any\":\"ar\"}}," +
                            "    \"mailboxId\":\"123\"," +
                            "    \"sessionId\":6," +
                            "    \"user\":\"user\"" +
                            "   }" +
                            "}").get())
                        .isInstanceOf(NoSuchElementException.class);
                }
            }

            @Nested
            class DeserializationErrorOnMailboxName {

                @Test
                void mailboxACLUpdatedShouldThrowWhenNullMailboxName() {
                    assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                        "{" +
                            "  \"MailboxACLUpdated\":{" +
                            "    \"mailboxPath\":{" +
                            "       \"namespace\":230192.06," +
                            "       \"user\":180806," +
                            "       \"name\":null" +
                            "      }," +
                            "    \"aclDiff\":{" +
                            "       \"oldACL\":{}," +
                            "       \"newACL\":{\"$any\":\"ar\"}}," +
                            "    \"mailboxId\":\"123\"," +
                            "    \"sessionId\":6," +
                            "    \"user\":\"user\"" +
                            "   }" +
                            "}").get())
                        .isInstanceOf(NoSuchElementException.class);
                }

                @Test
                void mailboxACLUpdatedShouldThrowWhenMailboxNameIdIsANumber() {
                    assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                        "{" +
                            "  \"MailboxACLUpdated\":{" +
                            "    \"mailboxPath\":{" +
                            "       \"namespace\":230192.06," +
                            "       \"user\":\"bob\"," +
                            "       \"name\":160205" +
                            "      }," +
                            "    \"aclDiff\":{" +
                            "       \"oldACL\":{}," +
                            "       \"newACL\":{\"$any\":\"ar\"}}," +
                            "    \"mailboxId\":\"123\"," +
                            "    \"sessionId\":6," +
                            "    \"user\":\"user\"" +
                            "   }" +
                            "}").get())
                        .isInstanceOf(NoSuchElementException.class);
                }
            }
        }
    }

}
