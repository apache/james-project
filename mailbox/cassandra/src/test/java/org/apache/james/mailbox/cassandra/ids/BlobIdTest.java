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

package org.apache.james.mailbox.cassandra.ids;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.apache.james.util.ClassLoaderUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import nl.jqno.equalsverifier.EqualsVerifier;

public class BlobIdTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void shouldRespectBeanContract() {
        EqualsVerifier.forClass(BlobId.class).verify();
    }

    @Test
    public void fromShouldConstructBlobId() {
        String id = "111";
        assertThat(BlobId.from(id))
            .isEqualTo(new BlobId(id));
    }

    @Test
    public void fromShouldThrowOnNull() {
        expectedException.expect(IllegalArgumentException.class);

        BlobId.from(null);
    }

    @Test
    public void fromShouldThrowOnEmpty() {
        expectedException.expect(IllegalArgumentException.class);

        BlobId.from("");
    }

    @Test
    public void forPayloadShouldThrowOnNull() {
        expectedException.expect(IllegalArgumentException.class);

        BlobId.forPayload(null);
    }

    @Test
    public void forPayloadShouldHashEmptyArray() {
        BlobId blobId = BlobId.forPayload(new byte[0]);

        assertThat(blobId.getId()).isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    @Test
    public void forPayloadShouldHashArray() {
        BlobId blobId = BlobId.forPayload("content".getBytes(StandardCharsets.UTF_8));

        assertThat(blobId.getId()).isEqualTo("ed7002b439e9ac845f22357d822bac1444730fbdb6016d3ec9432297b9ec9f73");
    }

    @Test
    public void forPayloadShouldCalculateDifferentHashesWhenCraftedSha1Collision() throws Exception {
        byte[] payload1 = ClassLoaderUtils.getSystemResourceAsByteArray("shattered-1.pdf");
        byte[] payload2 = ClassLoaderUtils.getSystemResourceAsByteArray("shattered-2.pdf");
        BlobId blobId1 = BlobId.forPayload(payload1);
        BlobId blobId2 = BlobId.forPayload(payload2);
        assertThat(blobId1).isNotEqualTo(blobId2);
    }
}
