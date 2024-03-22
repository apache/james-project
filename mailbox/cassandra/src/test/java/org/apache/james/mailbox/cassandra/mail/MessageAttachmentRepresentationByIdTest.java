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

package org.apache.james.mailbox.cassandra.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.apache.james.mailbox.model.Cid;
import org.apache.james.mailbox.model.MessageAttachmentMetadata;
import org.apache.james.mailbox.model.StringBackedAttachmentId;
import org.junit.jupiter.api.Test;


class MessageAttachmentRepresentationByIdTest {

    @Test
    void buildShouldThrowWhenAttachmentIsNotGiven() {
        assertThatThrownBy(() -> MessageAttachmentMetadata.builder().build())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void builderShouldThrowWhenAttachmentIsNull() {
        assertThatThrownBy(() -> MessageAttachmentMetadata.builder().attachment(null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildShouldWorkWhenMandatoryAttributesAreGiven() {
        StringBackedAttachmentId attachmentId = StringBackedAttachmentId.from("1");
        MessageAttachmentRepresentation expectedMessageAttachmentRepresentation = new MessageAttachmentRepresentation(attachmentId, Optional.empty(), Optional.empty(), false);

        MessageAttachmentRepresentation messageAttachmentRepresentation = MessageAttachmentRepresentation.builder()
            .attachmentId(attachmentId)
            .build();

        assertThat(messageAttachmentRepresentation).isEqualTo(expectedMessageAttachmentRepresentation);
    }

    @Test
    void buildShouldSetIsInlineDefaultValueWhenNotGiven() {
        StringBackedAttachmentId attachmentId = StringBackedAttachmentId.from("1");

        MessageAttachmentRepresentation messageAttachmentRepresentation = MessageAttachmentRepresentation.builder()
            .attachmentId(attachmentId)
            .build();

        assertThat(messageAttachmentRepresentation.isInline()).isFalse();
    }

    @Test
    void buildShouldAcceptInlineAndWithoutCid() {
        StringBackedAttachmentId attachmentId = StringBackedAttachmentId.from("1");
        MessageAttachmentRepresentation expectedMessageAttachmentRepresentation = new MessageAttachmentRepresentation(attachmentId, Optional.empty(), Optional.empty(), true);

        MessageAttachmentRepresentation messageAttachmentRepresentation = MessageAttachmentRepresentation.builder()
            .attachmentId(attachmentId)
            .isInline(true)
            .build();

        assertThat(messageAttachmentRepresentation).isEqualTo(expectedMessageAttachmentRepresentation);
    }

    @Test
    void buildShouldSetAttributesWhenAllAreGiven() {
        StringBackedAttachmentId attachmentId = StringBackedAttachmentId.from("1");
        MessageAttachmentRepresentation expectedMessageAttachmentRepresentation = new MessageAttachmentRepresentation(attachmentId, Optional.of("name"), Optional.of(Cid.from("cid")), true);

        MessageAttachmentRepresentation messageAttachmentRepresentation = MessageAttachmentRepresentation.builder()
            .attachmentId(attachmentId)
            .name("name")
            .cid(Cid.from("cid"))
            .isInline(true)
            .build();

        assertThat(messageAttachmentRepresentation).isEqualTo(expectedMessageAttachmentRepresentation);
    }
}
