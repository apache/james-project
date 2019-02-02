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

import org.junit.Test;

public class AttachmentIdTest {

    @Test
    public void randomShouldGenerateDifferentIds() {
        AttachmentId attachmentId = AttachmentId.random();
        AttachmentId attachmentId2 = AttachmentId.random();
        assertThat(attachmentId.getId()).isNotEqualTo(attachmentId2.getId());
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
}
