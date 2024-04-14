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

package org.apache.james.jmap.model.message.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Optional;

import org.apache.james.jmap.api.model.Preview;
import org.apache.james.jmap.model.Attachment;
import org.apache.james.jmap.model.BlobId;
import org.apache.james.jmap.model.Emailer;
import org.apache.james.jmap.model.Keyword;
import org.apache.james.jmap.model.Keywords;
import org.apache.james.jmap.model.Number;
import org.apache.james.jmap.model.PreviewDTO;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.model.TestMessageId;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

class MessageFullViewTest {

    private static final Preview PREVIEW = Preview.from("preview");
    private static final PreviewDTO PREVIEW_DTO = PreviewDTO.of(PREVIEW.getValue());

    @Test
    void buildShouldThrowWhenIdIsNull() {
        assertThatThrownBy(() -> MessageFullView.builder().build())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void buildShouldThrowWhenBlobIdIsNull() {
        assertThatThrownBy(() -> MessageFullView.builder()
                .id(TestMessageId.of(1))
                .build())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void buildShouldThrowWhenThreadIdIsNull() {
        assertThatThrownBy(() -> MessageFullView.builder()
                .id(TestMessageId.of(1))
                .blobId(BlobId.of("blobId"))
                .build())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void buildShouldThrowWhenThreadIdIsEmpty() {
        assertThatThrownBy(() -> MessageFullView.builder()
                .id(TestMessageId.of(1))
                .blobId(BlobId.of("blobId"))
                .threadId("")
                .build())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void buildShouldThrowWhenMailboxIdsIsNull() {
        assertThatThrownBy(() -> MessageFullView.builder()
                .id(TestMessageId.of(1))
                .blobId(BlobId.of("blobId"))
                .threadId("threadId")
                .build())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void buildShouldThrowWhenHeadersIsNull() {
        assertThatThrownBy(() -> MessageFullView.builder()
                .id(TestMessageId.of(1))
                .blobId(BlobId.of("blobId"))
                .threadId("threadId")
                .fluentMailboxIds()
                .build())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void buildShouldThrowWhenSubjectIsNull() {
        assertThatThrownBy(() -> MessageFullView.builder()
                .id(TestMessageId.of(1))
                .blobId(BlobId.of("blobId"))
                .threadId("threadId")
                .fluentMailboxIds()
                .headers(ImmutableMap.of())
                .build())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void buildShouldThrowWhenSubjectIsEmpty() {
        assertThatThrownBy(() -> MessageFullView.builder()
                .id(TestMessageId.of(1))
                .blobId(BlobId.of("blobId"))
                .threadId("threadId")
                .fluentMailboxIds()
                .headers(ImmutableMap.of())
                .subject("")
                .build())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void buildShouldThrowWhenSizeIsNull() {
        assertThatThrownBy(() -> MessageFullView.builder()
                .id(TestMessageId.of(1))
                .blobId(BlobId.of("blobId"))
                .threadId("threadId")
                .fluentMailboxIds()
                .headers(ImmutableMap.of())
                .subject("subject")
                .build())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void buildShouldThrowWhenDateIsNull() {
        assertThatThrownBy(() -> MessageFullView.builder()
                .id(TestMessageId.of(1))
                .blobId(BlobId.of("blobId"))
                .threadId("threadId")
                .fluentMailboxIds()
                .headers(ImmutableMap.of())
                .subject("subject")
                .size(123)
                .build())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void buildShouldNotThrowWhenPreviewIsNotPresent() {
        assertThatCode(() -> MessageFullView.builder()
                .id(TestMessageId.of(1))
                .blobId(BlobId.of("blobId"))
                .threadId("threadId")
                .fluentMailboxIds()
                    .headers(ImmutableMap.of())
                .subject("subject")
                .size(123)
                .date(Instant.now())
                .hasAttachment(false)
                .build())
            .doesNotThrowAnyException();
    }

    @Test
    void buildShouldWorkWhenMandatoryFieldsArePresent() {
        Instant currentDate = Instant.now();
        Number messageSize = Number.fromLong(123);

        MessageFullView expected = new MessageFullView(TestMessageId.of(1), BlobId.of("blobId"), "threadId",
            ImmutableSet.of(InMemoryId.of(456)), Optional.empty(), false, ImmutableMap.of("key", "value"),
            Optional.empty(), ImmutableList.of(), ImmutableList.of(), ImmutableList.of(), ImmutableList.of(),
            "subject", currentDate, messageSize, PREVIEW_DTO, Optional.empty(), Optional.empty(),
            ImmutableList.of(), ImmutableMap.of(), Keywords.DEFAULT_VALUE);

        MessageFullView tested = MessageFullView.builder()
                .id(TestMessageId.of(1))
                .blobId(BlobId.of("blobId"))
                .threadId("threadId")
                .mailboxId(InMemoryId.of(456))
                .headers(ImmutableMap.of("key", "value"))
                .subject("subject")
                .size(123)
                .date(currentDate)
                .preview(PREVIEW)
                .hasAttachment(false)
                .build();
        assertThat(tested).isEqualToComparingFieldByField(expected);
    }

    @Test
    void buildShouldThrowWhenAttachedMessageIsNotMatchingAttachments() {
        Attachment simpleAttachment = Attachment.builder().blobId(BlobId.of("blobId")).type("type").name("name").size(123).build();
        ImmutableList<Attachment> attachments = ImmutableList.of(simpleAttachment);

        SubMessage simpleMessage = SubMessage.builder()
                .headers(ImmutableMap.of("key", "value"))
                .subject("subject")
                .date(Instant.now())
                .build();

        ImmutableMap<BlobId, SubMessage> attachedMessages = ImmutableMap.of(BlobId.of("differentBlobId"), simpleMessage);

        assertThatThrownBy(() -> MessageFullView.builder()
                .id(TestMessageId.of(1))
                .blobId(BlobId.of("blobId"))
                .threadId("threadId")
                .mailboxId(InMemoryId.of(456))
                .headers(ImmutableMap.of("key", "value"))
                .subject("subject")
                .size(123)
                .date(Instant.now())
                .preview(PREVIEW)
                .attachments(attachments)
                .attachedMessages(attachedMessages)
                .build())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void buildShouldWorkWhenAllFieldsArePresent() {
        Emailer from = Emailer.builder().name("from").email("from@domain").build();
        ImmutableList<Emailer> to = ImmutableList.of(Emailer.builder().name("to").email("to@domain").build());
        ImmutableList<Emailer> cc = ImmutableList.of(Emailer.builder().name("cc").email("cc@domain").build());
        ImmutableList<Emailer> bcc = ImmutableList.of(Emailer.builder().name("bcc").email("bcc@domain").build());
        ImmutableList<Emailer> replyTo = ImmutableList.of(Emailer.builder().name("replyTo").email("replyTo@domain").build());
        Instant currentDate = Instant.now();
        Attachment simpleAttachment = Attachment.builder().blobId(BlobId.of("blobId")).type("type").name("name").size(123).build();
        ImmutableList<Attachment> attachments = ImmutableList.of(simpleAttachment);

        SubMessage simpleMessage = SubMessage.builder()
                .headers(ImmutableMap.of("key", "value"))
                .subject("subject")
                .date(currentDate)
                .build();

        ImmutableMap<BlobId, SubMessage> attachedMessages = ImmutableMap.of(BlobId.of("blobId"), simpleMessage);

        Keywords keywords = Keywords.strictFactory()
            .from(Keyword.DRAFT, Keyword.ANSWERED, Keyword.FLAGGED);

        Number messageSize = Number.fromLong(123);
        MessageFullView expected = new MessageFullView(
            TestMessageId.of(1),
            BlobId.of("blobId"),
            "threadId",
            ImmutableSet.of(InMemoryId.of(456)),
            Optional.of("inReplyToMessageId"),
            true,
            ImmutableMap.of("key", "value"),
            Optional.of(from),
            to,
            cc,
            bcc,
            replyTo,
            "subject",
            currentDate,
            messageSize,
            PREVIEW_DTO,
            Optional.of("textBody"),
            Optional.of("htmlBody"),
            attachments,
            attachedMessages,
            keywords);

        MessageFullView tested = MessageFullView.builder()
            .id(TestMessageId.of(1))
            .blobId(BlobId.of("blobId"))
            .threadId("threadId")
            .mailboxId(InMemoryId.of(456))
            .inReplyToMessageId("inReplyToMessageId")
            .keywords(keywords)
            .headers(ImmutableMap.of("key", "value"))
            .from(from)
            .to(to)
            .cc(cc)
            .bcc(bcc)
            .replyTo(replyTo)
            .subject("subject")
            .date(currentDate)
            .size(123)
            .preview(PREVIEW)
            .textBody(Optional.of("textBody"))
            .htmlBody(Optional.of("htmlBody"))
            .attachments(attachments)
            .attachedMessages(attachedMessages)
            .build();

        assertThat(tested).isEqualToComparingFieldByField(expected);
    }

    @Test
    void buildShouldThrowWhenOneAttachedMessageIsNotInAttachments() {
        assertThatThrownBy(() -> MessageFullView.builder()
                .id(TestMessageId.of(1))
                .blobId(BlobId.of("blobId"))
                .threadId("threadId")
                .mailboxId(InMemoryId.of(456))
                .headers(ImmutableMap.of("key", "value"))
                .subject("subject")
                .size(1)
                .date(Instant.now())
                .preview(PREVIEW)
                .attachedMessages(ImmutableMap.of(BlobId.of("key"), SubMessage.builder()
                        .headers(ImmutableMap.of("key", "value"))
                        .subject("subject")
                        .date(Instant.now())
                        .build()))
                .build())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void buildShouldNotThrowWhenOneAttachedMessageIsInAttachments() {
        MessageFullView.builder()
            .id(TestMessageId.of(1))
            .blobId(BlobId.of("blobId"))
            .threadId("threadId")
            .mailboxId(InMemoryId.of(456))
            .headers(ImmutableMap.of("key", "value"))
            .subject("subject")
            .size(1)
            .date(Instant.now())
            .preview(PREVIEW)
            .attachments(ImmutableList.of(Attachment.builder()
                    .blobId(BlobId.of("key"))
                    .size(1)
                    .type("type")
                    .build()))
            .attachedMessages(ImmutableMap.of(BlobId.of("key"), SubMessage.builder()
                    .headers(ImmutableMap.of("key", "value"))
                    .subject("subject")
                    .date(Instant.now())
                    .build()))
            .build();
    }

    @Test
    void hasAttachmentShouldReturnFalseWhenNoAttachment() {
        MessageFullView message = MessageFullView.builder()
            .id(TestMessageId.of(1))
            .blobId(BlobId.of("blobId"))
            .threadId("threadId")
            .mailboxId(InMemoryId.of(456))
            .headers(ImmutableMap.of("key", "value"))
            .subject("subject")
            .size(1)
            .date(Instant.now())
            .preview(PREVIEW)
            .attachments(ImmutableList.of())
            .build();

        assertThat(message.isHasAttachment()).isFalse();
    }

    @Test
    void hasAttachmentShouldReturnFalseWhenAllAttachmentsAreInline() {
        MessageFullView message = MessageFullView.builder()
            .id(TestMessageId.of(1))
            .blobId(BlobId.of("blobId"))
            .threadId("threadId")
            .mailboxId(InMemoryId.of(456))
            .headers(ImmutableMap.of("key", "value"))
            .subject("subject")
            .size(1)
            .date(Instant.now())
            .preview(PREVIEW)
            .attachments(ImmutableList.of(
                    Attachment.builder()
                        .blobId(BlobId.of("key"))
                        .size(1)
                        .type("type")
                        .cid("cid1")
                        .isInline(true)
                        .build(),
                    Attachment.builder()
                        .blobId(BlobId.of("key2"))
                        .size(2)
                        .type("type2")
                        .cid("cid2")
                        .isInline(true)
                        .build()))
            .build();

        assertThat(message.isHasAttachment()).isFalse();
    }

    @Test
    void hasAttachmentShouldReturnTrueWhenOneAttachmentIsNotInline() {
        MessageFullView message = MessageFullView.builder()
            .id(TestMessageId.of(1))
            .blobId(BlobId.of("blobId"))
            .threadId("threadId")
            .mailboxId(InMemoryId.of(456))
            .headers(ImmutableMap.of("key", "value"))
            .subject("subject")
            .size(1)
            .date(Instant.now())
            .preview(PREVIEW)
            .attachments(ImmutableList.of(
                    Attachment.builder()
                        .blobId(BlobId.of("key"))
                        .size(1)
                        .type("type")
                        .cid("cid1")
                        .isInline(true)
                        .build(),
                    Attachment.builder()
                        .blobId(BlobId.of("key2"))
                        .size(2)
                        .type("type2")
                        .isInline(false)
                        .build(),
                    Attachment.builder()
                        .blobId(BlobId.of("key3"))
                        .size(3)
                        .type("type3")
                        .cid("c")
                        .isInline(true)
                        .build()))
            .build();

        assertThat(message.isHasAttachment()).isTrue();
    }

    @Test
    void hasAttachmentShouldReturnTrueWhenAllAttachmentsAreNotInline() {
        MessageFullView message = MessageFullView.builder()
            .id(TestMessageId.of(1))
            .blobId(BlobId.of("blobId"))
            .threadId("threadId")
            .mailboxId(InMemoryId.of(456))
            .headers(ImmutableMap.of("key", "value"))
            .subject("subject")
            .size(1)
            .date(Instant.now())
            .preview(PREVIEW)
            .attachments(ImmutableList.of(
                    Attachment.builder()
                        .blobId(BlobId.of("key"))
                        .size(1)
                        .type("type")
                        .isInline(false)
                        .build(),
                    Attachment.builder()
                        .blobId(BlobId.of("key2"))
                        .size(2)
                        .type("type2")
                        .isInline(false)
                        .build(),
                    Attachment.builder()
                        .blobId(BlobId.of("key3"))
                        .size(3)
                        .type("type3")
                        .isInline(false)
                        .build()))
            .build();

        assertThat(message.isHasAttachment()).isTrue();
    }
}
