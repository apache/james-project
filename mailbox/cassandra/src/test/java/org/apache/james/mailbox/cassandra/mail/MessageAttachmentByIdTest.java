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

import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.model.Cid;
import org.apache.james.mailbox.model.MessageAttachment;
import org.junit.Test;


public class MessageAttachmentByIdTest {

    @Test(expected=IllegalStateException.class)
    public void buildShouldThrowWhenAttachmentIsNotGiven() {
        MessageAttachment.builder()
            .build();
    }

    @Test(expected=IllegalArgumentException.class)
    public void builderShouldThrowWhenAttachmentIsNull() {
        MessageAttachment.builder()
            .attachment(null);
    }

    @Test
    public void buildShouldWorkWhenMandatoryAttributesAreGiven() {
        AttachmentId attachmentId = AttachmentId.from("1");
        MessageAttachmentById expectedMessageAttachment = new MessageAttachmentById(attachmentId, Optional.empty(), Optional.empty(), false);

        MessageAttachmentById messageAttachment = MessageAttachmentById.builder()
            .attachmentId(attachmentId)
            .build();

        assertThat(messageAttachment).isEqualTo(expectedMessageAttachment);
    }

    @Test
    public void buildShouldSetIsInlineDefaultValueWhenNotGiven() {
        AttachmentId attachmentId = AttachmentId.from("1");

        MessageAttachmentById messageAttachment = MessageAttachmentById.builder()
            .attachmentId(attachmentId)
            .build();

        assertThat(messageAttachment.isInline()).isFalse();
    }

    @Test(expected=IllegalStateException.class)
    public void buildShouldThrowWhenIsInlineAndNoCid() {
        AttachmentId attachmentId = AttachmentId.from("1");

        MessageAttachmentById.builder()
            .attachmentId(attachmentId)
            .isInline(true)
            .build();
    }

    @Test
    public void buildShouldSetAttributesWhenAllAreGiven() {
        AttachmentId attachmentId = AttachmentId.from("1");
        MessageAttachmentById expectedMessageAttachment = new MessageAttachmentById(attachmentId, Optional.of("name"), Optional.of(Cid.from("cid")), true);

        MessageAttachmentById messageAttachment = MessageAttachmentById.builder()
            .attachmentId(attachmentId)
            .name("name")
            .cid(Cid.from("cid"))
            .isInline(true)
            .build();

        assertThat(messageAttachment).isEqualTo(expectedMessageAttachment);
    }
}
