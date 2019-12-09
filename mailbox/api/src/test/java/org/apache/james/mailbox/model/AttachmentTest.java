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

import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

class AttachmentTest {

    private static Charset CHARSET = StandardCharsets.UTF_8;
/*
    @Test
    void streamShouldBeConsumedOneTime() throws Exception {
        String input = "mystream";
        Attachment attachment = Attachment.builder()
                .bytes(input.getBytes(CHARSET))
                .type("content")
                .build();

        InputStream stream = attachment.getStream();
        assertThat(stream).isNotNull();
        assertThat(IOUtils.toString(stream, CHARSET)).isEqualTo(input);
    }

    @Test
    void getByteShouldReturnByteArrayRepresentingTheAttachment() {
        String input = "mystream";
        Attachment attachment = Attachment.builder()
            .bytes(input.getBytes(CHARSET))
            .type("content")
            .build();

        byte[] bytes = attachment.getBytes();
        assertThat(new String(bytes, CHARSET)).isEqualTo(input);
    }

    @Test
    void streamShouldBeConsumedMoreThanOneTime() throws Exception {
        String input = "mystream";
        Attachment attachment = Attachment.builder()
                .bytes(input.getBytes(CHARSET))
                .type("content")
                .build();

        attachment.getStream();
        InputStream stream = attachment.getStream();
        assertThat(stream).isNotNull();
        assertThat(IOUtils.toString(stream, CHARSET)).isEqualTo(input);
    }

    @Test
    void builderShouldThrowWhenAttachmentIdIsNull() {
        assertThatThrownBy(() -> Attachment.builder()
                .attachmentId(null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void builderShouldThrowWhenBytesIsNull() {
        assertThatThrownBy(() -> Attachment.builder()
                .bytes(null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void builderShouldThrowWhenTypeIsNull() {
        assertThatThrownBy(() -> Attachment.builder()
                .type(null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void builderShouldThrowWhenTypeIsEmpty() {
        assertThatThrownBy(() -> Attachment.builder()
                .type(""))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildShouldThrowWhenAttachmentIdIsNotProvided() {
        assertThatThrownBy(() -> Attachment.builder()
                .build())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void buildShouldThrowWhenBytesIsNotProvided() {
        assertThatThrownBy(() -> Attachment.builder()
                .attachmentId(AttachmentId.random())
                .build())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void buildShouldThrowWhenTypeIsNotProvided() {
        assertThatThrownBy(() -> Attachment.builder()
                .attachmentId(AttachmentId.random())
                .bytes("mystream".getBytes(CHARSET))
                .build())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void buildShouldSetTheSize() {
        String input = "mystream";
        Attachment attachment = Attachment.builder()
                .bytes(input.getBytes(CHARSET))
                .type("content")
                .build();

        assertThat(attachment.getSize()).isEqualTo(input.getBytes(CHARSET).length);
    }
*/
}
