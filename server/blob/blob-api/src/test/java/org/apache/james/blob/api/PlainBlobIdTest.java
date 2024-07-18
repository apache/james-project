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

import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class PlainBlobIdTest {

    private static final PlainBlobId.Factory BLOB_ID_FACTORY = new PlainBlobId.Factory();

    @Test
    void shouldRespectBeanContract() {
        EqualsVerifier.forClass(PlainBlobId.class).verify();
    }

    @Test
    void fromShouldConstructBlobId() {
        String id = "111";
        assertThat(BLOB_ID_FACTORY.parse(id))
            .isEqualTo(new PlainBlobId(id));
    }

    @Test
    void fromShouldThrowOnNull() {
        assertThatThrownBy(() -> BLOB_ID_FACTORY.parse(null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromShouldThrowOnEmpty() {
        assertThatThrownBy(() -> BLOB_ID_FACTORY.parse(""))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
