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

package org.apache.james.imap.encode;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.imap.encode.base.ByteImapResponseWriter;
import org.apache.james.imap.encode.base.ImapResponseComposerImpl;
import org.apache.james.imap.message.response.MailboxStatusResponse;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.model.UidValidity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MailboxStatusResponseEncoderTest  {

    MailboxStatusResponseEncoder encoder;

    ByteImapResponseWriter writer = new ByteImapResponseWriter();
    ImapResponseComposer composer = new ImapResponseComposerImpl(writer);

    @BeforeEach
    void setUp() {
        encoder = new MailboxStatusResponseEncoder();
    }

    @Test
    void acceptableMessagesShouldReturnMailboxStatusResponseClass() {
        assertThat(encoder.acceptableMessages()).isEqualTo(MailboxStatusResponse.class);
    }

    @Test
    void testDoEncode() throws Exception {
        final Long messages = 2L;
        final Long recent = 3L;
        final MessageUid uidNext = MessageUid.of(5);
        final UidValidity uidValidity = UidValidity.of(7L);
        final Long unseen = 11L;
        final Long size = 42L;
        final Long deleted = 23L;
        final Long deletedStorage = 13L;
        final String mailbox = "A mailbox named desire";

        encoder.encode(new MailboxStatusResponse(null, null, null, deletedStorage, messages, recent, uidNext,
                null, uidValidity, unseen, mailbox, null), composer);
        composer.flush();
        assertThat(writer.getString()).isEqualTo("* STATUS \"A mailbox named desire\" (MESSAGES 2 DELETED-STORAGE 13 RECENT 3 UIDNEXT 5 UIDVALIDITY 7 UNSEEN 11)\r\n");
    }
}
