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

import java.util.UUID;

import org.apache.james.util.ClassLoaderUtils;
import org.junit.Test;

public class AttachmentIdTest {

    @Test
    public void forPayloadAndTypeShouldCalculateTheUnderlyingSha256() {
        AttachmentId attachmentId = AttachmentId.forPayloadAndType("payload".getBytes(), "text/plain");
        String expectedId = "d3a2642ee092a1b32c0a83cf94fc2499f7495b7b91b1bd434302a0a4c2aa4278";
        assertThat(attachmentId.getId()).isEqualTo(expectedId);
    }

    @Test
    public void forPayloadAndTypeShouldCalculateDifferentSha256WhenContentTypeIsDifferent() {
        AttachmentId attachmentId = AttachmentId.forPayloadAndType("payload".getBytes(), "text/plain");
        AttachmentId attachmentId2 = AttachmentId.forPayloadAndType("payload".getBytes(), "text/html");
        assertThat(attachmentId.getId()).isNotEqualTo(attachmentId2.getId());
    }

    @Test
    public void forPayloadAndTypeShouldCalculateSameSha256WhenMimeTypeIsSameButNotParameters() {
        AttachmentId attachmentId = AttachmentId.forPayloadAndType("payload".getBytes(), "text/html; charset=UTF-8");
        AttachmentId attachmentId2 = AttachmentId.forPayloadAndType("payload".getBytes(), "text/html; charset=UTF-16");
        assertThat(attachmentId.getId()).isEqualTo(attachmentId2.getId());
    }
    
    @Test
    public void forPayloadAndTypeShouldThrowWhenPayloadIsNull() {
        assertThatThrownBy(() -> AttachmentId.forPayloadAndType(null, "text/plain")).isInstanceOf(NullPointerException.class);
    }

    @Test
    public void forPayloadAndTypeShouldThrowWhenTypeIsNull() {
        assertThatThrownBy(() -> AttachmentId.forPayloadAndType("payload".getBytes(), null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void forPayloadAndTypeShouldThrowWhenTypeIsEmpty() {
        assertThatThrownBy(() -> AttachmentId.forPayloadAndType("payload".getBytes(), "")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void fromShouldThrowWhenIdIsNull() {
        String value = null;
        assertThatThrownBy(() -> AttachmentId.from(value)).isInstanceOf(NullPointerException.class);
    }

    @Test
    public void fromShouldThrowWhenBlobIdIsNull() {
        BlobId value = null;
        assertThatThrownBy(() -> AttachmentId.from(value)).isInstanceOf(NullPointerException.class);
    }

    @Test
    public void fromShouldThrowWhenIdIsEmpty() {
        assertThatThrownBy(() -> AttachmentId.from("")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void fromStringShouldWork() {
        String expectedId = "f07e5a815613c5abeddc4b682247a4c42d8a95df";
        AttachmentId attachmentId = AttachmentId.from(expectedId);
        assertThat(attachmentId.getId()).isEqualTo(expectedId);
    }

    @Test
    public void fromBlobIdShouldWork() {
        String expectedId = "f07e5a815613c5abeddc4b682247a4c42d8a95df";
        AttachmentId attachmentId = AttachmentId.from(BlobId.fromString(expectedId));
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

    @Test
    public void forPayloadAndTypeShouldCalculateDifferentHashesWhenCraftedSha1Collision() throws Exception {
        byte[] payload1 = ClassLoaderUtils.getSystemResourceAsByteArray("shattered-1.pdf");
        byte[] payload2 = ClassLoaderUtils.getSystemResourceAsByteArray("shattered-2.pdf");
        AttachmentId attachmentId1 = AttachmentId.forPayloadAndType(payload1, "application/pdf");
        AttachmentId attachmentId2 = AttachmentId.forPayloadAndType(payload2, "application/pdf");
        assertThat(attachmentId1).isNotEqualTo(attachmentId2);
    }
}
