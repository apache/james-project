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

import java.time.Instant;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.TestBlobId;
import org.apache.james.core.Username;
import org.apache.james.mailbox.events.MailboxEvents.MessageContentDeletionEvent;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.model.TestMessageId;
import org.junit.jupiter.api.Test;

class MessageContentDeletionSerializationTest {
    private static final Username USERNAME = Username.of("user@domain.tld");
    private static final MailboxId MAILBOX_ID = TestId.of(18);
    private static final MessageId MESSAGE_ID = TestMessageId.of(42);
    private static final long SIZE = 12345L;
    private static final Instant INTERNAL_DATE = Instant.parse("2024-12-15T08:23:45Z");
    private static final boolean HAS_ATTACHMENTS = true;
    private static final BlobId HEADER_BLOB_ID = new TestBlobId("header-blob-id");
    private static final BlobId BODY_BLOB_ID =  new TestBlobId("body-blob-id");

    private static final MessageContentDeletionEvent EVENT = new MessageContentDeletionEvent(
        EVENT_ID,
        USERNAME,
        MAILBOX_ID,
        MESSAGE_ID,
        SIZE,
        INTERNAL_DATE,
        HAS_ATTACHMENTS,
        HEADER_BLOB_ID,
        BODY_BLOB_ID);

    private static final String JSON = """
        {
            "MessageContentDeletionEvent": {
                "eventId": "6e0dd59d-660e-4d9b-b22f-0354479f47b4",
                "username": "user@domain.tld",
                "size": 12345,
                "hasAttachments": true,
                "internalDate": "2024-12-15T08:23:45Z",
                "mailboxId": "18",
                "headerBlobId": "header-blob-id",
                "messageId": "42",
                "bodyBlobId": "body-blob-id"
            }
        }
        """;

    @Test
    void messageContentDeletionEventShouldBeWellSerialized() {
        assertThatJson(EVENT_SERIALIZER.toJson(EVENT))
            .isEqualTo(JSON);
    }

    @Test
    void messageContentDeletionEventShouldBeWellDeserialized() {
        assertThat(EVENT_SERIALIZER.fromJson(JSON).get())
            .isEqualTo(EVENT);
    }

}
