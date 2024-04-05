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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.apache.james.jmap.model.Blob.InputStreamSupplier;
import org.apache.james.mailbox.model.ContentType;
import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class BlobTest {
    static final BlobId ID = BlobId.of("123");
    static final ContentType CONTENT_TYPE = ContentType.of("text/plain");
    static final InputStreamSupplier PAYLOAD = () -> new ByteArrayInputStream("abc".getBytes(StandardCharsets.UTF_8));
    static final int LENGTH = 3;

    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(Blob.class)
            .withIgnoredFields("payload", "size")
            .verify();
    }

    @Test
    void buildShouldConstructValidBlob() {
        assertThat(
            Blob.builder()
                .id(ID)
                .contentType(CONTENT_TYPE)
                .payload(PAYLOAD)
                .size(LENGTH)
                .build())
            .isEqualTo(
                new Blob(ID, PAYLOAD, CONTENT_TYPE, LENGTH));
    }

    @Test
    void buildShouldThrowOnMissingBlobId() {
        assertThatThrownBy(() ->
            Blob.builder()
                .contentType(CONTENT_TYPE)
                .payload(PAYLOAD)
                .build())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void buildShouldThrowOnMissingContentType() {
        assertThatThrownBy(() ->
            Blob.builder()
                .id(ID)
                .payload(PAYLOAD)
                .build())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void buildShouldThrowOnMissingPayload() {
        assertThatThrownBy(() ->
            Blob.builder()
                .id(ID)
                .contentType(CONTENT_TYPE)
                .build())
            .isInstanceOf(IllegalStateException.class);
    }
}
