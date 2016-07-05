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
package org.apache.james.jmap.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.Test;

public class AttachmentTest {

    @Test(expected=IllegalStateException.class)
    public void buildShouldThrowWhenBlobIdIsNull() {
        Attachment.builder().build();
    }

    @Test(expected=IllegalStateException.class)
    public void buildShouldThrowWhenTypeIsNull() {
        Attachment.builder().blobId(BlobId.of("blobId")).build();
    }

    @Test(expected=IllegalStateException.class)
    public void buildShouldThrowWhenNameIsNull() {
        Attachment.builder().blobId(BlobId.of("blobId")).type("type").build();
    }

    @Test(expected=IllegalStateException.class)
    public void buildShouldThrowWhenSizeIsNull() {
        Attachment.builder().blobId(BlobId.of("blobId")).type("type").name("name").build();
    }
    
    @Test(expected=IllegalStateException.class)
    public void buildShouldThrowWhenTypeIsEmpty() {
        Attachment.builder().blobId(BlobId.of("blobId")).type("").name("name").size(123).build();
    }
    
    @Test
    public void buildShouldWorkWhenMandatoryFieldsArePresent() {
        Attachment expected = new Attachment(BlobId.of("blobId"), "type", Optional.empty(), 123, Optional.empty(), false, Optional.empty(), Optional.empty());
        Attachment tested = Attachment.builder()
            .blobId(BlobId.of("blobId"))
            .type("type")
            .size(123)
            .build();
        assertThat(tested).isEqualToComparingFieldByField(expected);
    }

    @Test
    public void buildShouldWorkWithAllFieldsSet() {
        Attachment expected = new Attachment(BlobId.of("blobId"), "type", Optional.of("name"), 123, Optional.of("cid"), true, Optional.of(456L), Optional.of(789L));
        Attachment tested = Attachment.builder()
            .blobId(BlobId.of("blobId"))
            .type("type")
            .name("name")
            .size(123)
            .cid("cid")
            .isInline(true)
            .width(456)
            .height(789)
            .build();
        assertThat(tested).isEqualToComparingFieldByField(expected);
    }

}
