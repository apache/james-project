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

package org.apache.james.mailbox.opensearch.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.ZoneId;

import jakarta.mail.Flags;

import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.inmemory.InMemoryMessageId;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.opensearch.IndexAttachments;
import org.apache.james.mailbox.opensearch.IndexHeaders;
import org.apache.james.mailbox.store.extractor.DefaultTextExtractor;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class IndexableMessageHasAttachmentTest {
    private static IndexableMessage index(String resource, IndexAttachments indexAttachments) throws IOException {
        MailboxMessage mailboxMessage = mock(MailboxMessage.class);
        when(mailboxMessage.getMailboxId()).thenReturn(TestId.of(1));
        when(mailboxMessage.getModSeq()).thenReturn(ModSeq.first());
        when(mailboxMessage.getMessageId()).thenReturn(InMemoryMessageId.of(42));
        when(mailboxMessage.getFullContent()).thenAnswer(any -> ClassLoader.getSystemResourceAsStream(resource));
        when(mailboxMessage.createFlags()).thenReturn(new Flags());
        when(mailboxMessage.getUid()).thenReturn(MessageUid.of(154));

        return IndexableMessage.builder()
            .message(mailboxMessage)
            .extractor(new DefaultTextExtractor())
            .zoneId(ZoneId.of("Europe/Paris"))
            .indexAttachments(indexAttachments)
            .indexHeaders(IndexHeaders.YES)
            .build()
            .block();
    }

    @ParameterizedTest
    @EnumSource(IndexAttachments.class)
    void hasAttachmentShouldNotDependOnWhetherAttachmentsAreIndexed(IndexAttachments indexAttachments) throws IOException {
        assertThat(index("eml/emailWithTextAttachment.eml", indexAttachments).getHasAttachment()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(IndexAttachments.class)
    void hasAttachmentShouldBeFalseWithoutAttachment(IndexAttachments indexAttachments) throws IOException {
        assertThat(index("eml/alternative_simple.eml", indexAttachments).getHasAttachment()).isFalse();
    }

    @Test
    void attachmentsShouldNotBeIndexedWhenDisabled() throws IOException {
        assertThat(index("eml/emailWithTextAttachment.eml", IndexAttachments.NO).getAttachments()).isEmpty();
    }
}
