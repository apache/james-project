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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AttachmentTest {
    @Test
    void builderShouldThrowWhenAttachmentIdIsNull() {
        assertThatThrownBy(() -> Attachment.builder()
                .attachmentId(null))
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
    void buildShouldThrowWhenSizeIsNotProvided() {
        assertThatThrownBy(() -> Attachment.builder()
                .attachmentId(AttachmentId.random())
                .type("TYPE")
                .build())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void buildShouldThrowWhenTypeIsNotProvided() {
        assertThatThrownBy(() -> Attachment.builder()
                .attachmentId(AttachmentId.random())
                .size(36)
                .build())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void sizeShouldThrowOnNegativeValue() {
        assertThatThrownBy(() -> Attachment.builder()
                .attachmentId(AttachmentId.random())
                .size(-3))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
