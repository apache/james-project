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
import org.apache.james.mailbox.acl.ACLDiff;
import org.apache.james.mailbox.events.MailboxEvents.MailboxACLUpdated;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.TestId;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MailboxACLUpdatedEventSerializationTest {

    private static final Username USERNAME = Username.of("user");
    private static final MailboxACL.EntryKey ENTRY_KEY = org.apache.james.mailbox.model.MailboxACL.EntryKey.createGroupEntryKey("any", false);
    private static final MailboxACL.Rfc4314Rights RIGHTS = new MailboxACL.Rfc4314Rights(MailboxACL.Right.Administer, MailboxACL.Right.Read);
    private static final MailboxACL MAILBOX_ACL = new MailboxACL(
        new MailboxACL.Entry(ENTRY_KEY, RIGHTS),
        new MailboxACL.Entry(MailboxACL.EntryKey.createUserEntryKey(Username.of("alice"), true),
            new MailboxACL.Rfc4314Rights(MailboxACL.Right.Insert)));

    private static final MailboxACLUpdated MAILBOX_ACL_UPDATED = new MailboxACLUpdated(
                MailboxSession.SessionId.of(6),
        USERNAME,
                new MailboxPath(MailboxConstants.USER_NAMESPACE, Username.of("bob"), "mailboxName"),
                ACLDiff.computeDiff(MailboxACL.EMPTY, MAILBOX_ACL),
                TestId.of(23),
                EVENT_ID);

    private static final String MAILBOX_ACL_UPDATED_JSON = "{" +
        "  \"MailboxACLUpdated\":{" +
        "    \"eventId\":\"6e0dd59d-660e-4d9b-b22f-0354479f47b4\"," +
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
    class DeserializationErrors {
        @Test
        void mailboxACLUpdatedShouldThrowWhenMissingSessionId() {
            assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                "{" +
                    "  \"MailboxACLUpdated\":{" +
                    "    \"eventId\":\"6e0dd59d-660e-4d9b-b22f-0354479f47b4\"," +
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
        void mailboxACLUpdatedShouldThrowWhenMissingEventId() {
            assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                "{" +
                    "  \"MailboxACLUpdated\":{" +
                    "    \"sessionId\":42," +
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
                    "    \"eventId\":\"6e0dd59d-660e-4d9b-b22f-0354479f47b4\"," +
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
                    "    \"eventId\":\"6e0dd59d-660e-4d9b-b22f-0354479f47b4\"," +
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

        @Test
        void mailboxACLUpdatedShouldThrowWhenMissingMailboxId() {
            assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                "{" +
                    "  \"MailboxACLUpdated\":{" +
                    "    \"eventId\":\"6e0dd59d-660e-4d9b-b22f-0354479f47b4\"," +
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

        @Test
        void mailboxACLUpdatedShouldThrowWhenMissingMailboxPath() {
            assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                "{" +
                    "  \"MailboxACLUpdated\":{" +
                    "    \"eventId\":\"6e0dd59d-660e-4d9b-b22f-0354479f47b4\"," +
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

}
