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

import nl.jqno.equalsverifier.EqualsVerifier;

public class PartIdTest {
    private static final BlobId BLOB_ID = BlobId.from("abc");

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void shouldRespectBeanContract() {
        EqualsVerifier.forClass(PartId.class).verify();
    }

    @Test
    public void test () {
        String id = "111";
        assertThat(PartId.from(id))
            .isEqualTo(new PartId(id));
    }

    @Test
    public void fromShouldThrowOnNull() {
        expectedException.expect(IllegalArgumentException.class);

        PartId.from(null);
    }

    @Test
    public void fromShouldThrowOnEmpty() {
        expectedException.expect(IllegalArgumentException.class);

        PartId.from("");
    }

    @Test
    public void createShouldThrowOnNullBlobId() {
        expectedException.expect(NullPointerException.class);

        PartId.create(null, 1);
    }

    @Test
    public void createShouldThrowOnNegativePosition() {
        expectedException.expect(IllegalArgumentException.class);

        PartId.create(BLOB_ID, -1);
    }

    @Test
    public void createShouldAcceptPositionZero() {
        assertThat(PartId.create(BLOB_ID, 0).getId())
            .isEqualTo(BLOB_ID.getId() + "-0");
    }

    @Test
    public void createShouldConcatenateBlobIdAndPosition() {
        assertThat(PartId.create(BLOB_ID, 36).getId())
            .isEqualTo(BLOB_ID.getId() + "-36");
    }
}
