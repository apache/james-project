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

import java.util.Optional;

import org.apache.james.mailbox.cassandra.mail.MessageAttachmentRepresentation;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.model.Cid;
import org.junit.Test;


public class MessageAttachmentRepresentationByIdTest {

    @Test(expected = IllegalStateException.class)
    public void buildShouldThrowWhenAttachmentIsNotGiven() {
        org.apache.james.mailbox.model.MessageAttachment.builder()
            .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void builderShouldThrowWhenAttachmentIsNull() {
        org.apache.james.mailbox.model.MessageAttachment.builder()
            .attachment(null);
    }

    @Test
    public void buildShouldWorkWhenMandatoryAttributesAreGiven() {
        AttachmentId attachmentId = AttachmentId.from("1");
        MessageAttachmentRepresentation expectedMessageAttachmentRepresentation = new MessageAttachmentRepresentation(attachmentId, Optional.empty(), Optional.empty(), false);

        MessageAttachmentRepresentation messageAttachmentRepresentation = MessageAttachmentRepresentation.builder()
            .attachmentId(attachmentId)
            .build();

        assertThat(messageAttachmentRepresentation).isEqualTo(expectedMessageAttachmentRepresentation);
    }

    @Test
    public void buildShouldSetIsInlineDefaultValueWhenNotGiven() {
        AttachmentId attachmentId = AttachmentId.from("1");

        MessageAttachmentRepresentation messageAttachmentRepresentation = MessageAttachmentRepresentation.builder()
            .attachmentId(attachmentId)
            .build();

        assertThat(messageAttachmentRepresentation.isInline()).isFalse();
    }

    @Test
    public void buildShouldAcceptInlineAndWithoutCid() {
        AttachmentId attachmentId = AttachmentId.from("1");
        MessageAttachmentRepresentation expectedMessageAttachmentRepresentation = new MessageAttachmentRepresentation(attachmentId, Optional.empty(), Optional.empty(), true);

        MessageAttachmentRepresentation messageAttachmentRepresentation = MessageAttachmentRepresentation.builder()
            .attachmentId(attachmentId)
            .isInline(true)
            .build();

        assertThat(messageAttachmentRepresentation).isEqualTo(expectedMessageAttachmentRepresentation);
    }

    @Test
    public void buildShouldSetAttributesWhenAllAreGiven() {
        AttachmentId attachmentId = AttachmentId.from("1");
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
