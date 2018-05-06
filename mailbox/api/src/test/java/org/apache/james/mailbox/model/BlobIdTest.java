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

import java.nio.charset.StandardCharsets;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public class BlobIdTest {

    @Test
    public void shouldMatchBeanContact() {
        EqualsVerifier.forClass(BlobId.class)
            .allFieldsShouldBeUsed()
            .verify();
    }

    @Test
    public void fromStringShouldThrowOnNull() {
        assertThatThrownBy(() -> BlobId.fromString(null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void fromStringShouldThrowOnEmpty() {
        assertThatThrownBy(() -> BlobId.fromString(""))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void asStringShouldReturnUnderlyingId() {
        assertThat(BlobId.fromString("abc").asString())
            .isEqualTo("abc");
    }

    @Test
    public void fromBytesShouldProduceASHA256() {
        assertThat(BlobId.fromBytes("abc".getBytes(StandardCharsets.UTF_8)).asString())
            .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    public void fromBytesShouldCalculateSameSha256() {
        byte[] bytes = "abc".getBytes(StandardCharsets.UTF_8);

        assertThat(BlobId.fromBytes(bytes))
            .isEqualTo(BlobId.fromBytes(bytes));
    }
}
