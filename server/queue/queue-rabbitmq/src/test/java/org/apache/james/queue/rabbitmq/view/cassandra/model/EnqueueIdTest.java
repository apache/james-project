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

package org.apache.james.queue.rabbitmq.view.cassandra.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.apache.james.queue.rabbitmq.EnqueueId;
import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class EnqueueIdTest {
    private static final UUID UUID_1 = UUID.fromString("110e8400-e29b-11d4-a716-446655440000");

    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(EnqueueId.class)
            .verify();
    }

    @Test
    void ofSerializedShouldDeserializeTheGivenEnqueueId() {
        assertThat(EnqueueId.ofSerialized(UUID_1.toString()))
            .isEqualTo(EnqueueId.of(UUID_1));
    }

    @Test
    void serializeShouldReturnAStringRepresentation() {
        EnqueueId enqueueId = EnqueueId.of(UUID_1);

        assertThat(enqueueId.serialize())
            .isEqualTo(UUID_1.toString());
    }

    @Test
    void ofSerializedShouldRevertSerialize() {
        EnqueueId enqueueId = EnqueueId.of(UUID_1);

        assertThat(EnqueueId.ofSerialized(enqueueId.serialize()))
            .isEqualTo(enqueueId);
    }
}