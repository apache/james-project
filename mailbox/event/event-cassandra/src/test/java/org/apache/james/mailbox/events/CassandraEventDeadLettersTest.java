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

package org.apache.james.mailbox.events;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.event.json.EventSerializer;
import org.apache.james.mailbox.events.EventDeadLettersContract.FailedEventContract;
import org.apache.james.mailbox.events.EventDeadLettersContract.FailedEventsContract;
import org.apache.james.mailbox.events.EventDeadLettersContract.GroupsWithFailedEventsContract;
import org.apache.james.mailbox.events.EventDeadLettersContract.RemoveContract;
import org.apache.james.mailbox.events.EventDeadLettersContract.StoreContract;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.model.TestMessageId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

class CassandraEventDeadLettersTest implements StoreContract, RemoveContract, FailedEventContract, FailedEventsContract,
    GroupsWithFailedEventsContract {

    @RegisterExtension
    static CassandraClusterExtension cassandraClusterExtension = new CassandraClusterExtension(CassandraEventDeadLettersModule.MODULE);

    private CassandraEventDeadLetters eventDeadLetters;

    @BeforeEach
    void setUp(CassandraCluster cassandraCluster) {
        EventSerializer eventSerializer = new EventSerializer(new TestId.Factory(), new TestMessageId.Factory());
        eventDeadLetters = new CassandraEventDeadLetters(new CassandraEventDeadLettersDAO(cassandraCluster.getConf(), eventSerializer),
                                                         new CassandraEventDeadLettersGroupDAO(cassandraCluster.getConf()));
    }

    @Override
    public EventDeadLetters eventDeadLetters() {
        return eventDeadLetters;
    }
}
