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

package org.apache.james.jmap.cassandra.projections;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.jmap.api.projections.MessageFastViewProjection;
import org.apache.james.jmap.api.projections.MessageFastViewProjectionContract;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.TestMessageId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class CassandraMessageFastViewProjectionTest implements MessageFastViewProjectionContract {

    @RegisterExtension
    static CassandraClusterExtension cassandra = new CassandraClusterExtension(CassandraMessageFastViewProjectionModule.MODULE);

    private CassandraMessageFastViewProjection testee;
    private CassandraMessageId.Factory cassandraMessageIdFactory;

    @BeforeEach
    void setUp() {
        cassandraMessageIdFactory = new CassandraMessageId.Factory();
        testee = new CassandraMessageFastViewProjection(cassandra.getCassandraCluster().getConf());
    }

    @Override
    public MessageFastViewProjection testee() {
        return testee;
    }

    @Override
    public MessageId newMessageId() {
        return cassandraMessageIdFactory.generate();
    }

    @Test
    void storeShouldThrowWhenMessageIdIsNotCassandraType() {
        assertThatThrownBy(() -> testee.store(TestMessageId.of(1), PREVIEW_1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("MessageId type is required to be CassandraMessageId");
    }

    @Test
    void retrieveShouldThrowWhenMessageIdIsNotCassandraType() {
        assertThatThrownBy(() -> testee.retrieve(TestMessageId.of(1)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("MessageId type is required to be CassandraMessageId");
    }

    @Test
    void deleteShouldThrowWhenMessageIdIsNotCassandraType() {
        assertThatThrownBy(() -> testee.retrieve(TestMessageId.of(1)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("MessageId type is required to be CassandraMessageId");
    }
}