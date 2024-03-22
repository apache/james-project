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

package org.apache.james.mailbox.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class MessageAttachmentMetadataTest {

    @Test
    void buildShouldThrowWhenAttachmentIsNotGiven() {
        assertThatThrownBy(() -> MessageAttachmentMetadata.builder()
                .build())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void builderShouldThrowWhenAttachmentIsNull() {
        assertThatThrownBy(() -> MessageAttachmentMetadata.builder()
                .attachment(null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildShouldWorkWhenMandatoryAttributesAreGiven() {
        AttachmentMetadata attachment = AttachmentMetadata.builder()
            .attachmentId(StringBackedAttachmentId.from("1"))
            .size(36)
            .type("type")
            .messageId(TestMessageId.of(23))
            .build();
        MessageAttachmentMetadata expectedMessageAttachment = new MessageAttachmentMetadata(attachment, Optional.empty(), Optional.empty(), false);

        MessageAttachmentMetadata messageAttachment = MessageAttachmentMetadata.builder()
            .attachment(attachment)
            .build();

        assertThat(messageAttachment).isEqualTo(expectedMessageAttachment);
    }

    @Test
    void buildShouldAcceptIsInlineAndNoCid() {
        AttachmentMetadata attachment = AttachmentMetadata.builder()
            .attachmentId(StringBackedAttachmentId.from("1"))
            .size(36)
            .type("type")
            .messageId(TestMessageId.of(23))
            .build();

        MessageAttachmentMetadata messageAttachment = MessageAttachmentMetadata.builder()
            .attachment(attachment)
            .isInline(true)
            .build();

        assertThat(messageAttachment.isInline()).isTrue();
    }

    @Test
    void buildShouldSetAttributesWhenAllAreGiven() {
        AttachmentMetadata attachment = AttachmentMetadata.builder()
            .attachmentId(StringBackedAttachmentId.from("1"))
            .size(36)
            .type("type")
            .messageId(TestMessageId.of(23))
            .build();
        MessageAttachmentMetadata expectedMessageAttachment = new MessageAttachmentMetadata(attachment, Optional.of("name"), Optional.of(Cid.from("cid")), true);

        MessageAttachmentMetadata messageAttachment = MessageAttachmentMetadata.builder()
            .attachment(attachment)
            .name("name")
            .cid(Cid.from("cid"))
            .isInline(true)
            .build();

        assertThat(messageAttachment).isEqualTo(expectedMessageAttachment);
    }

    @Test
    void isInlinedWithCidShouldReturnTrueWhenIsInlineAndHasCid() throws Exception {
        AttachmentMetadata attachment = AttachmentMetadata.builder()
            .attachmentId(StringBackedAttachmentId.from("1"))
            .size(36)
            .type("type")
            .messageId(TestMessageId.of(23))
            .build();

        MessageAttachmentMetadata messageAttachment = MessageAttachmentMetadata.builder()
            .attachment(attachment)
            .name("name")
            .cid(Cid.from("cid"))
            .isInline(true)
            .build();

        assertThat(messageAttachment.isInlinedWithCid()).isTrue();
    }

    @Test
    void isInlinedWithCidShouldReturnFalseWhenIsNotInline() throws Exception {
        AttachmentMetadata attachment = AttachmentMetadata.builder()
            .attachmentId(StringBackedAttachmentId.from("1"))
            .size(36)
            .type("type")
            .messageId(TestMessageId.of(23))
            .build();

        MessageAttachmentMetadata messageAttachment = MessageAttachmentMetadata.builder()
            .attachment(attachment)
            .name("name")
            .cid(Cid.from("cid"))
            .isInline(false)
            .build();

        assertThat(messageAttachment.isInlinedWithCid()).isFalse();
    }

    @Test
    void isInlinedWithCidShouldReturnFalseWhenIsInlineButNoCid() throws Exception {
        AttachmentMetadata attachment = AttachmentMetadata.builder()
            .attachmentId(StringBackedAttachmentId.from("1"))
            .size(36)
            .type("type")
            .messageId(TestMessageId.of(23))
            .build();

        MessageAttachmentMetadata messageAttachment = MessageAttachmentMetadata.builder()
            .attachment(attachment)
            .name("name")
            .isInline(true)
            .build();

        assertThat(messageAttachment.isInlinedWithCid()).isFalse();
    }
}
