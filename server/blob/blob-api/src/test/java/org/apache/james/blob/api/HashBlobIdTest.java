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

package org.apache.james.blob.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.util.ClassLoaderUtils;
import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public class HashBlobIdTest {

    private static final HashBlobId.Factory BLOB_ID_FACTORY = new HashBlobId.Factory();

    @Test
    public void shouldRespectBeanContract() {
        EqualsVerifier.forClass(HashBlobId.class).verify();
    }

    @Test
    public void fromShouldConstructBlobId() {
        String id = "111";
        assertThat(BLOB_ID_FACTORY.from(id))
            .isEqualTo(new HashBlobId(id));
    }

    @Test
    public void fromShouldThrowOnNull() {
        assertThatThrownBy(() -> BLOB_ID_FACTORY.from(null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void fromShouldThrowOnEmpty() {
        assertThatThrownBy(() -> BLOB_ID_FACTORY.from(""))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void forPayloadShouldThrowOnNull() {
        assertThatThrownBy(() -> BLOB_ID_FACTORY.forPayload((byte[]) null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void forPayloadShouldHashEmptyArray() {
        BlobId blobId = BLOB_ID_FACTORY.forPayload(new byte[0]);

        assertThat(blobId.asString()).isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    @Test
    public void forPayloadShouldHashArray() {
        BlobId blobId = BLOB_ID_FACTORY.forPayload("content".getBytes(StandardCharsets.UTF_8));

        assertThat(blobId.asString()).isEqualTo("ed7002b439e9ac845f22357d822bac1444730fbdb6016d3ec9432297b9ec9f73");
    }

    @Test
    public void forPayloadShouldCalculateDifferentHashesWhenCraftedSha1Collision() throws Exception {
        byte[] payload1 = ClassLoaderUtils.getSystemResourceAsByteArray("shattered-1.pdf");
        byte[] payload2 = ClassLoaderUtils.getSystemResourceAsByteArray("shattered-2.pdf");
        BlobId blobId1 = BLOB_ID_FACTORY.forPayload(payload1);
        BlobId blobId2 = BLOB_ID_FACTORY.forPayload(payload2);
        assertThat(blobId1).isNotEqualTo(blobId2);
    }
}
