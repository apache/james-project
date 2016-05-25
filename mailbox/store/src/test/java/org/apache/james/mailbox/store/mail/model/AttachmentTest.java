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


package org.apache.james.mailbox.store.mail.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;

import org.apache.commons.io.IOUtils;
import org.junit.Test;

public class AttachmentTest {

    @Test
    public void streamShouldBeConsumedOneTime() throws Exception {
        String input = "mystream";
        Attachment attachment = Attachment.from(input.getBytes(), "content");

        InputStream stream = attachment.getStream();
        assertThat(stream).isNotNull();
        assertThat(IOUtils.toString(stream)).isEqualTo(input);
    }

    @Test
    public void streamShouldBeConsumedMoreThanOneTime() throws Exception {
        String input = "mystream";
        Attachment attachment = Attachment.from(input.getBytes(), "content");

        attachment.getStream();
        InputStream stream = attachment.getStream();
        assertThat(stream).isNotNull();
        assertThat(IOUtils.toString(stream)).isEqualTo(input);
    }

    @Test (expected = IllegalArgumentException.class)
    public void builderShouldThrowWhenAttachmentIdIsNull() {
        Attachment.builder()
            .attachmentId(null);
    }

    @Test (expected = IllegalArgumentException.class)
    public void builderShouldThrowWhenBytesIsNull() {
        Attachment.builder()
            .bytes(null);
    }

    @Test (expected = IllegalArgumentException.class)
    public void builderShouldThrowWhenTypeIsNull() {
        Attachment.builder()
            .type(null);
    }

    @Test (expected = IllegalArgumentException.class)
    public void builderShouldThrowWhenTypeIsEmpty() {
        Attachment.builder()
            .type("");
    }

    @Test (expected = IllegalStateException.class)
    public void buildShouldThrowWhenAttachmentIdIsNotProvided() {
        Attachment.builder().build();
    }

    @Test (expected = IllegalStateException.class)
    public void buildShouldThrowWhenBytesIsNotProvided() {
        Attachment.builder()
            .attachmentId(AttachmentId.forPayload("mystream".getBytes()))
            .build();
    }

    @Test (expected = IllegalStateException.class)
    public void buildShouldThrowWhenTypeIsNotProvided() {
        Attachment.builder()
            .attachmentId(AttachmentId.forPayload("mystream".getBytes()))
            .bytes("mystream".getBytes())
            .build();
    }

    @Test (expected = IllegalStateException.class)
    public void buildShouldThrowWhenSizeIsNotProvided() {
        Attachment.builder()
            .attachmentId(AttachmentId.forPayload("mystream".getBytes()))
            .bytes("mystream".getBytes())
            .type("content")
            .build();
    }

    @Test
    public void fromShouldSetTheAttachmentId() throws Exception {
        byte[] bytes = "mystream".getBytes();
        Attachment attachment = Attachment.from(bytes, "content");
        AttachmentId expected = AttachmentId.forPayload(bytes);

        assertThat(attachment.getAttachmentId()).isEqualTo(expected);
    }

    @Test
    public void fromShouldSetTheSize() throws Exception {
        String input = "mystream";
        Attachment attachment = Attachment.from(input.getBytes(), "content");

        assertThat(attachment.getSize()).isEqualTo(input.getBytes().length);
    }
}
