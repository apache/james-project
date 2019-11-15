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

import java.util.UUID;

import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class CassandraMessageIdTest {

    @Test
    void beanShouldRespectBeanContract() {
        EqualsVerifier.forClass(CassandraMessageId.class)
            .verify();
    }

    @Test
    void generateShouldReturnAValidCassandraMesssageId() {
        CassandraMessageId.Factory testee = new CassandraMessageId.Factory();

        CassandraMessageId cassandraMessageId = testee.generate();
        assertThat(cassandraMessageId.serialize()).isNotNull();
    }

    @Test
    void ofShouldReturnAValidCassandraMesssageId() {
        CassandraMessageId.Factory testee = new CassandraMessageId.Factory();

        UUID expectedUuid = UUID.randomUUID();
        CassandraMessageId cassandraMessageId = testee.of(expectedUuid);

        assertThat(cassandraMessageId.get()).isEqualTo(expectedUuid);
    }

    @Test
    void serializeShouldReturnTheUuidAsString() {
        CassandraMessageId.Factory testee = new CassandraMessageId.Factory();

        UUID uuid = UUID.randomUUID();
        CassandraMessageId cassandraMessageId = testee.of(uuid);

        String expected = uuid.toString();
        assertThat(cassandraMessageId.serialize()).isEqualTo(expected);
    }

    @Test
    void shouldBeSerializable() {
        assertThat(new CassandraMessageId.Factory().generate().isSerializable()).isTrue();
    }
}
