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

import org.junit.Test;

public class AttachmentIdTest {

    @Test
    public void forPayloadShouldCalculateTheUnderlyingSha1() {
        AttachmentId attachmentId = AttachmentId.forPayload("payload".getBytes());
        String expectedId = "f07e5a815613c5abeddc4b682247a4c42d8a95df";
        assertThat(attachmentId.getId()).isEqualTo(expectedId);
    }

    @Test(expected = IllegalArgumentException.class)
    public void forPayloadShouldThrowWhenPayloadIsNull() {
        AttachmentId.forPayload(null);
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
}
