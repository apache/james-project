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

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.google.common.base.Charsets;

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

        assertThat(blobId.getId()).isEqualTo("da39a3ee5e6b4b0d3255bfef95601890afd80709");
    }

    @Test
    public void forPayloadShouldHashArray() {
        BlobId blobId = BlobId.forPayload("content".getBytes(Charsets.UTF_8));

        assertThat(blobId.getId()).isEqualTo("040f06fd774092478d450774f5ba30c5da78acc8");
    }
}
