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

import java.util.UUID;

import org.junit.Test;

public class AttachmentIdTest {

    @Test
    public void forPayloadAndTypeShouldCalculateTheUnderlyingSha1() {
        AttachmentId attachmentId = AttachmentId.forPayloadAndType("payload".getBytes(), "text/plain");
        String expectedId = "826b0786f04e07525a36be70f84c647af7b73059";
        assertThat(attachmentId.getId()).isEqualTo(expectedId);
    }

    @Test
    public void forPayloadAndTypeShouldCalculateDifferentSha1WhenContentTypeIsDifferent() {
        AttachmentId attachmentId = AttachmentId.forPayloadAndType("payload".getBytes(), "text/plain");
        AttachmentId attachmentId2 = AttachmentId.forPayloadAndType("payload".getBytes(), "text/html");
        assertThat(attachmentId.getId()).isNotEqualTo(attachmentId2.getId());
    }

    @Test
    public void forPayloadAndTypeShouldCalculateSameSha1WhenMimeTypeIsSameButNotParameters() {
        AttachmentId attachmentId = AttachmentId.forPayloadAndType("payload".getBytes(), "text/html; charset=UTF-8");
        AttachmentId attachmentId2 = AttachmentId.forPayloadAndType("payload".getBytes(), "text/html; charset=UTF-16");
        assertThat(attachmentId.getId()).isEqualTo(attachmentId2.getId());
    }

    @Test(expected = IllegalArgumentException.class)
    public void forPayloadAndTypeShouldThrowWhenPayloadIsNull() {
        AttachmentId.forPayloadAndType(null, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void forPayloadAndTypeShouldThrowWhenTypeIsNull() {
        AttachmentId.forPayloadAndType("payload".getBytes(), null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void forPayloadAndTypeShouldThrowWhenTypeIsEmpty() {
        AttachmentId.forPayloadAndType("payload".getBytes(), "");
    }

    @Test(expected = IllegalArgumentException.class)
    public void fromShouldThrowWhenIdIsNull() {
        AttachmentId.from(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void fromShouldThrowWhenIdIsEmpty() {
        AttachmentId.from("");
    }

    @Test
    public void fromShouldWork() {
        String expectedId = "f07e5a815613c5abeddc4b682247a4c42d8a95df";
        AttachmentId attachmentId = AttachmentId.from(expectedId);
        assertThat(attachmentId.getId()).isEqualTo(expectedId);
    }

    @Test
    public void asUUIDShouldReturnAValidUUID() {
        AttachmentId attachmentId = AttachmentId.from("magic");

        assertThat(attachmentId.asUUID())
            .isEqualTo(UUID.fromString("2f3a4fcc-ca64-36e3-9bcf-33e92dd93135"));
    }

    @Test
    public void asMimeTypeShouldReturnOnlyMimeTypeFromContentTypeWhenContainingParameters() {
        String mimeType = AttachmentId.asMimeType("text/html; charset=UTF-8");
        
        assertThat(mimeType).isEqualTo("text/html");
    }

    @Test
    public void asMimeTypeShouldReturnOnlyMimeTypeFromContentTypeWhenNoParameters() {
        String mimeType = AttachmentId.asMimeType("text/html");
        
        assertThat(mimeType).isEqualTo("text/html");
    }

    @Test
    public void asMimeTypeShouldReturnDefaultMimeTypeWhenContentTypeIsUnparsable() {
        String mimeType = AttachmentId.asMimeType("text");
        
        assertThat(mimeType).isEqualTo("application/octet-stream");
    }
}
