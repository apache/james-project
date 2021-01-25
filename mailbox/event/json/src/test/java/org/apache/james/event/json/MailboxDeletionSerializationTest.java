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
import static org.apache.james.mailbox.model.MailboxConstants.USER_NAMESPACE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.NoSuchElementException;
import java.util.Optional;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaCountUsage;
import org.apache.james.core.quota.QuotaSizeUsage;
import org.apache.james.events.MailboxEvents.MailboxDeletion;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.model.TestId;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MailboxDeletionSerializationTest {

    private static final MailboxSession.SessionId SESSION_ID = MailboxSession.SessionId.of(3652);
    private static final Username USERNAME = Username.of("user");
    private static final MailboxPath MAILBOX_PATH = new MailboxPath(USER_NAMESPACE, USERNAME, "mailboxName");
    private static final MailboxId MAILBOX_ID = TestId.of(789);
    private static final QuotaRoot QUOTA_ROOT = QuotaRoot.quotaRoot("#private&user@domain", Optional.of(Domain.of("domain")));
    private static final QuotaCountUsage DELETED_MESSAGE_COUNT = QuotaCountUsage.count(60);
    private static final QuotaSizeUsage TOTAL_DELETED_SIZE = QuotaSizeUsage.size(100);
    private static final MailboxDeletion DEFAULT_MAILBOX_DELETION_EVENT = new MailboxDeletion(
        SESSION_ID,
        USERNAME,
        MAILBOX_PATH,
        new MailboxACL(Pair.of(MailboxACL.EntryKey.createUserEntryKey(USERNAME), new MailboxACL.Rfc4314Rights(MailboxACL.Right.allRights))),
        QUOTA_ROOT,
        DELETED_MESSAGE_COUNT,
        TOTAL_DELETED_SIZE,
        MAILBOX_ID,
        EVENT_ID);

    private static final MailboxDeletion EMPTY_ACL_MAILBOX_DELETION_EVENT = new MailboxDeletion(
        SESSION_ID,
        USERNAME,
        MAILBOX_PATH,
        new MailboxACL(),
        QUOTA_ROOT,
        DELETED_MESSAGE_COUNT,
        TOTAL_DELETED_SIZE,
        MAILBOX_ID,
        EVENT_ID);

    private static final String DEFAULT_EVENT_JSON =
        "{" +
            "  \"MailboxDeletion\":{" +
            "    \"eventId\":\"6e0dd59d-660e-4d9b-b22f-0354479f47b4\"," +
            "    \"mailboxACL\":{" +
            "      \"entries\": {" +
            "        \"user\":\"aeiklprstwx\"" +
            "       }" +
            "    }," +
            "    \"sessionId\":3652," +
            "    \"user\":\"user\"," +
            "    \"path\":{" +
            "      \"namespace\":\"#private\"," +
            "      \"user\":\"user\"," +
            "      \"name\":\"mailboxName\"" +
            "    }," +
            "    \"quotaRoot\":\"#private&user@domain\"," +
            "    \"deletedMessageCount\":60," +
            "    \"totalDeletedSize\":100," +
            "    \"mailboxId\":\"789\"" +
            "  }" +
            "}";

    private static final String EVENT_WITH_EMPTY_ACL_JSON =
        "{" +
            "  \"MailboxDeletion\":{" +
            "    \"eventId\":\"6e0dd59d-660e-4d9b-b22f-0354479f47b4\"," +
            "    \"mailboxACL\":{" +
            "      \"entries\": {}" +
            "    }," +
            "    \"sessionId\":3652," +
            "    \"user\":\"user\"," +
            "    \"path\":{" +
            "      \"namespace\":\"#private\"," +
            "      \"user\":\"user\"," +
            "      \"name\":\"mailboxName\"" +
            "    }," +
            "    \"quotaRoot\":\"#private&user@domain\"," +
            "    \"deletedMessageCount\":60," +
            "    \"totalDeletedSize\":100," +
            "    \"mailboxId\":\"789\"" +
            "  }" +
            "}";

    private static final String LEGACY_EVENT_JSON =
        "{" +
            "  \"MailboxDeletion\":{" +
            "    \"eventId\":\"6e0dd59d-660e-4d9b-b22f-0354479f47b4\"," +
            "    \"sessionId\":3652," +
            "    \"user\":\"user\"," +
            "    \"path\":{" +
            "      \"namespace\":\"#private\"," +
            "      \"user\":\"user\"," +
            "      \"name\":\"mailboxName\"" +
            "    }," +
            "    \"quotaRoot\":\"#private&user@domain\"," +
            "    \"deletedMessageCount\":60," +
            "    \"totalDeletedSize\":100," +
            "    \"mailboxId\":\"789\"" +
            "  }" +
            "}";

    @Test
    void mailboxDeletionShouldBeWellSerialized() {
        assertThatJson(EVENT_SERIALIZER.toJson(DEFAULT_MAILBOX_DELETION_EVENT))
            .isEqualTo(DEFAULT_EVENT_JSON);
    }

    @Test
    void mailboxDeletionShouldBeWellDeSerialized() {
        assertThat(EVENT_SERIALIZER.fromJson(DEFAULT_EVENT_JSON).get())
            .isEqualTo(DEFAULT_MAILBOX_DELETION_EVENT);
    }

    @Test
    void mailboxDeletionWithEmptyACLShouldBeWellSerialized() {
        assertThatJson(EVENT_SERIALIZER.toJson(EMPTY_ACL_MAILBOX_DELETION_EVENT))
            .isEqualTo(EVENT_WITH_EMPTY_ACL_JSON);
    }

    @Test
    void legacyMailboxDeletionShouldBeWellDeSerialized() {
        assertThat(EVENT_SERIALIZER.fromJson(LEGACY_EVENT_JSON).get())
            .isEqualTo(EMPTY_ACL_MAILBOX_DELETION_EVENT);
    }

    @Nested
    class DeserializationErrors {
        @Test
        void mailboxDeletionShouldThrowWhenMissingSessionId() {
            assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                "{" +
                    "  \"MailboxDeletion\":{" +
                    "    \"eventId\":\"6e0dd59d-660e-4d9b-b22f-0354479f47b4\"," +
                    "    \"user\":\"user\"," +
                    "    \"path\":{" +
                    "      \"namespace\":\"#private\"," +
                    "      \"user\":\"user\"," +
                    "      \"name\":\"mailboxName\"" +
                    "    }," +
                    "    \"quotaRoot\":\"#private&user@domain\"," +
                    "    \"deletedMessageCount\":60," +
                    "    \"totalDeletedSize\":100," +
                    "    \"mailboxId\":\"789\"" +
                    "  }" +
                    "}").get())
                .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        void mailboxDeletionShouldThrowWhenMissingEventId() {
            assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                "{" +
                    "  \"MailboxDeletion\":{" +
                    "    \"sessionId\":42," +
                    "    \"user\":\"user\"," +
                    "    \"path\":{" +
                    "      \"namespace\":\"#private\"," +
                    "      \"user\":\"user\"," +
                    "      \"name\":\"mailboxName\"" +
                    "    }," +
                    "    \"quotaRoot\":\"#private&user@domain\"," +
                    "    \"deletedMessageCount\":60," +
                    "    \"totalDeletedSize\":100," +
                    "    \"mailboxId\":\"789\"" +
                    "  }" +
                    "}").get())
                .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        void mailboxDeletionShouldThrowWhenMissingUser() {
            assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                "{" +
                    "  \"MailboxDeletion\":{" +
                    "    \"eventId\":\"6e0dd59d-660e-4d9b-b22f-0354479f47b4\"," +
                    "    \"sessionId\":3652," +
                    "    \"path\":{" +
                    "      \"namespace\":\"#private\"," +
                    "      \"user\":\"user\"," +
                    "      \"name\":\"mailboxName\"" +
                    "    }," +
                    "    \"quotaRoot\":\"#private&user@domain\"," +
                    "    \"deletedMessageCount\":60," +
                    "    \"totalDeletedSize\":100," +
                    "    \"mailboxId\":\"789\"" +
                    "  }" +
                    "}").get())
                .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        void mailboxDeletionShouldThrowWhenMissingQuotaRoot() {
            assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                "{" +
                    "  \"MailboxDeletion\":{" +
                    "    \"eventId\":\"6e0dd59d-660e-4d9b-b22f-0354479f47b4\"," +
                    "    \"sessionId\":3652," +
                    "    \"user\":\"user\"," +
                    "    \"path\":{" +
                    "      \"namespace\":\"#private\"," +
                    "      \"user\":\"user\"," +
                    "      \"name\":\"mailboxName\"" +
                    "    }," +
                    "    \"deletedMessageCount\":60," +
                    "    \"totalDeletedSize\":100," +
                    "    \"mailboxId\":\"789\"" +
                    "  }" +
                    "}").get())
                .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        void mailboxDeletionShouldThrowWhenMissingQuotaCount() {
            assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                "{" +
                    "  \"MailboxDeletion\":{" +
                    "    \"eventId\":\"6e0dd59d-660e-4d9b-b22f-0354479f47b4\"," +
                    "    \"sessionId\":3652," +
                    "    \"user\":\"user\"," +
                    "    \"path\":{" +
                    "      \"namespace\":\"#private\"," +
                    "      \"user\":\"user\"," +
                    "      \"name\":\"mailboxName\"" +
                    "    }," +
                    "    \"quotaRoot\":\"#private&user@domain\"," +
                    "    \"totalDeletedSize\":100," +
                    "    \"mailboxId\":\"789\"" +
                    "  }" +
                    "}").get())
                .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        void mailboxDeletionShouldThrowWhenMissingQuotaSize() {
            assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                "{" +
                    "  \"MailboxDeletion\":{" +
                    "    \"eventId\":\"6e0dd59d-660e-4d9b-b22f-0354479f47b4\"," +
                    "    \"sessionId\":3652," +
                    "    \"user\":\"user\"," +
                    "    \"path\":{" +
                    "      \"namespace\":\"#private\"," +
                    "      \"user\":\"user\"," +
                    "      \"name\":\"mailboxName\"" +
                    "    }," +
                    "    \"quotaRoot\":\"#private&user@domain\"," +
                    "    \"deletedMessageCount\":60," +
                    "    \"mailboxId\":\"789\"" +
                    "  }" +
                    "}").get())
                .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        void mailboxDeletionShouldThrowWhenMissingMailboxId() {
            assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                "{" +
                    "  \"MailboxDeletion\":{" +
                    "    \"eventId\":\"6e0dd59d-660e-4d9b-b22f-0354479f47b4\"," +
                    "    \"sessionId\":3652," +
                    "    \"user\":\"user\"," +
                    "    \"path\":{" +
                    "      \"namespace\":\"#private\"," +
                    "      \"user\":\"user\"," +
                    "      \"name\":\"mailboxName\"" +
                    "    }," +
                    "    \"quotaRoot\":\"#private&user@domain\"," +
                    "    \"deletedMessageCount\":60," +
                    "    \"totalDeletedSize\":100" +
                    "  }" +
                    "}").get())
                .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        void mailboxDeletionShouldThrowWhenMissingPath() {
            assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                "{" +
                    "  \"MailboxDeletion\":{" +
                    "    \"eventId\":\"6e0dd59d-660e-4d9b-b22f-0354479f47b4\"," +
                    "    \"sessionId\":3652," +
                    "    \"user\":\"user\"," +
                    "    \"quotaRoot\":\"#private&user@domain\"," +
                    "    \"deletedMessageCount\":60," +
                    "    \"totalDeletedSize\":100," +
                    "    \"mailboxId\":\"789\"" +
                    "  }" +
                    "}").get())
                .isInstanceOf(NoSuchElementException.class);
        }
    }
}
