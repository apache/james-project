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
package org.apache.james.jmap.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;

import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.model.TestMessageId;
import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class MessageTest {

    
    @Test(expected = IllegalStateException.class)
    public void buildShouldThrowWhenIdIsNull() {
        Message.builder().build();
    }

    @Test(expected = IllegalStateException.class)
    public void buildShouldThrowWhenBlobIdIsNull() {
        Message.builder().id(TestMessageId.of(1)).build();
    }

    @Test(expected = IllegalStateException.class)
    public void buildShouldThrowWhenThreadIdIsNull() {
        Message.builder().id(TestMessageId.of(1)).blobId(BlobId.of("blobId")).build();
    }

    @Test(expected = IllegalStateException.class)
    public void buildShouldThrowWhenThreadIdIsEmpty() {
        Message.builder().id(TestMessageId.of(1)).blobId(BlobId.of("blobId")).threadId("").build();
    }

    @Test(expected = IllegalStateException.class)
    public void buildShouldThrowWhenMailboxIdsIsNull() {
        Message.builder().id(TestMessageId.of(1)).blobId(BlobId.of("blobId")).threadId("threadId").build();
    }

    @Test(expected = IllegalStateException.class)
    public void buildShouldThrowWhenHeadersIsNull() {
        Message.builder().id(TestMessageId.of(1)).blobId(BlobId.of("blobId")).threadId("threadId").fluentMailboxIds().build();
    }

    @Test(expected = IllegalStateException.class)
    public void buildShouldThrowWhenSubjectIsNull() {
        Message.builder().id(TestMessageId.of(1)).blobId(BlobId.of("blobId")).threadId("threadId").fluentMailboxIds().headers(ImmutableMap.of()).build();
    }

    @Test(expected = IllegalStateException.class)
    public void buildShouldThrowWhenSubjectIsEmpty() {
        Message.builder().id(TestMessageId.of(1)).blobId(BlobId.of("blobId")).threadId("threadId").fluentMailboxIds().headers(ImmutableMap.of())
            .subject("").build();
    }

    @Test(expected = IllegalStateException.class)
    public void buildShouldThrowWhenSizeIsNull() {
        Message.builder().id(TestMessageId.of(1)).blobId(BlobId.of("blobId")).threadId("threadId").fluentMailboxIds().headers(ImmutableMap.of())
            .subject("subject").build();
    }

    @Test(expected = IllegalStateException.class)
    public void buildShouldThrowWhenDateIsNull() {
        Message.builder().id(TestMessageId.of(1)).blobId(BlobId.of("blobId")).threadId("threadId").fluentMailboxIds().headers(ImmutableMap.of())
            .subject("subject").size(123).build();
    }

    @Test(expected = IllegalStateException.class)
    public void buildShouldThrowWhenPreviewIsNull() {
        Message.builder().id(TestMessageId.of(1)).blobId(BlobId.of("blobId")).threadId("threadId").fluentMailboxIds().headers(ImmutableMap.of())
            .subject("subject").size(123).date(Instant.now()).build();
    }

    @Test(expected = IllegalStateException.class)
    public void buildShouldThrowWhenPreviewIsEmpty() {
        Message.builder().id(TestMessageId.of(1)).blobId(BlobId.of("blobId")).threadId("threadId").fluentMailboxIds().headers(ImmutableMap.of())
            .subject("subject").size(123).date(Instant.now()).preview("").build();
    }

    @Test
    public void buildShouldWorkWhenMandatoryFieldsArePresent() {
        Instant currentDate = Instant.now();
        Number messageSize = Number.fromLong(123);
        Message expected = new Message(TestMessageId.of(1), BlobId.of("blobId"), "threadId", ImmutableList.of(InMemoryId.of(456)), Optional.empty(), false, ImmutableMap.of("key", "value"), Optional.empty(),
                ImmutableList.of(), ImmutableList.of(), ImmutableList.of(), ImmutableList.of(), "subject", currentDate, messageSize, "preview", Optional.empty(), Optional.empty(),
                ImmutableList.of(), ImmutableMap.of(), Keywords.DEFAULT_VALUE);
        Message tested = Message.builder()
                .id(TestMessageId.of(1))
                .blobId(BlobId.of("blobId"))
                .threadId("threadId")
                .mailboxId(InMemoryId.of(456))
                .headers(ImmutableMap.of("key", "value"))
                .subject("subject")
                .size(123)
                .date(currentDate)
                .preview("preview")
                .build();
        assertThat(tested).isEqualToComparingFieldByField(expected);
    }

    @Test(expected = IllegalStateException.class)
    public void buildShouldThrowWhenAttachedMessageIsNotMatchingAttachments() {
        Attachment simpleAttachment = Attachment.builder().blobId(BlobId.of("blobId")).type("type").name("name").size(123).build();
        ImmutableList<Attachment> attachments = ImmutableList.of(simpleAttachment);
        SubMessage simpleMessage = SubMessage.builder()
                .headers(ImmutableMap.of("key", "value"))
                .subject("subject")
                .date(Instant.now())
                .build();
        ImmutableMap<BlobId, SubMessage> attachedMessages = ImmutableMap.of(BlobId.of("differentBlobId"), simpleMessage);
        Message.builder()
            .id(TestMessageId.of(1))
            .blobId(BlobId.of("blobId"))
            .threadId("threadId")
            .mailboxId(InMemoryId.of(456))
            .headers(ImmutableMap.of("key", "value"))
            .subject("subject")
            .size(123)
            .date(Instant.now())
            .preview("preview")
            .attachments(attachments)
            .attachedMessages(attachedMessages)
            .build();
    }

    @Test
    public void buildShouldWorkWhenAllFieldsArePresent() {
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
        Message expected = new Message(
            TestMessageId.of(1),
            BlobId.of("blobId"),
            "threadId",
            ImmutableList.of(InMemoryId.of(456)),
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
            "preview",
            Optional.of("textBody"),
            Optional.of("htmlBody"),
            attachments,
            attachedMessages,
            keywords);
        Message tested = Message.builder()
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
            .preview("preview")
            .textBody(Optional.of("textBody"))
            .htmlBody(Optional.of("htmlBody"))
            .attachments(attachments)
            .attachedMessages(attachedMessages)
            .build();
        assertThat(tested).isEqualToComparingFieldByField(expected);
    }

    @Test(expected = IllegalStateException.class)
    public void buildShouldThrowWhenOneAttachedMessageIsNotInAttachments() throws Exception {
        Message.builder()
            .id(TestMessageId.of(1))
            .blobId(BlobId.of("blobId"))
            .threadId("threadId")
            .mailboxId(InMemoryId.of(456))
            .headers(ImmutableMap.of("key", "value"))
            .subject("subject")
            .size(1)
            .date(Instant.now())
            .preview("preview")
            .attachedMessages(ImmutableMap.of(BlobId.of("key"), SubMessage.builder()
                    .headers(ImmutableMap.of("key", "value"))
                    .subject("subject")
                    .date(Instant.now())
                    .build()))
            .build();
    }

    @Test
    public void buildShouldNotThrowWhenOneAttachedMessageIsInAttachments() throws Exception {
        Message.builder()
            .id(TestMessageId.of(1))
            .blobId(BlobId.of("blobId"))
            .threadId("threadId")
            .mailboxId(InMemoryId.of(456))
            .headers(ImmutableMap.of("key", "value"))
            .subject("subject")
            .size(1)
            .date(Instant.now())
            .preview("preview")
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
    public void hasAttachmentShouldReturnFalseWhenNoAttachment() throws Exception {
        Message message = Message.builder()
            .id(TestMessageId.of(1))
            .blobId(BlobId.of("blobId"))
            .threadId("threadId")
            .mailboxId(InMemoryId.of(456))
            .headers(ImmutableMap.of("key", "value"))
            .subject("subject")
            .size(1)
            .date(Instant.now())
            .preview("preview")
            .attachments(ImmutableList.of())
            .build();

        assertThat(message.isHasAttachment()).isFalse();
    }

    @Test
    public void hasAttachmentShouldReturnFalseWhenAllAttachmentsAreInline() throws Exception {
        Message message = Message.builder()
            .id(TestMessageId.of(1))
            .blobId(BlobId.of("blobId"))
            .threadId("threadId")
            .mailboxId(InMemoryId.of(456))
            .headers(ImmutableMap.of("key", "value"))
            .subject("subject")
            .size(1)
            .date(Instant.now())
            .preview("preview")
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
    public void hasAttachmentShouldReturnTrueWhenOneAttachmentIsNotInline() throws Exception {
        Message message = Message.builder()
            .id(TestMessageId.of(1))
            .blobId(BlobId.of("blobId"))
            .threadId("threadId")
            .mailboxId(InMemoryId.of(456))
            .headers(ImmutableMap.of("key", "value"))
            .subject("subject")
            .size(1)
            .date(Instant.now())
            .preview("preview")
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
    public void hasAttachmentShouldReturnTrueWhenAllAttachmentsAreNotInline() throws Exception {
        Message message = Message.builder()
            .id(TestMessageId.of(1))
            .blobId(BlobId.of("blobId"))
            .threadId("threadId")
            .mailboxId(InMemoryId.of(456))
            .headers(ImmutableMap.of("key", "value"))
            .subject("subject")
            .size(1)
            .date(Instant.now())
            .preview("preview")
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
