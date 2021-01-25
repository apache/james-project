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

package org.apache.james.events;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class CassandraEventDeadLettersGroupDAOTest {

    @RegisterExtension
    static CassandraClusterExtension cassandraClusterExtension = new CassandraClusterExtension(CassandraEventDeadLettersModule.MODULE);

    private static CassandraEventDeadLettersGroupDAO GROUP_DAO;

    @BeforeAll
    static void setUp(CassandraCluster cassandraCluster) {
        GROUP_DAO = new CassandraEventDeadLettersGroupDAO(cassandraCluster.getConf());
    }

    @Test
    void retrieveAllGroupsShouldReturnEmptyWhenDefault() {
        assertThat(GROUP_DAO.retrieveAllGroups()
                .collectList().block())
            .isEmpty();
    }

    @Test
    void retrieveAllGroupsShouldReturnStoredGroups() {
        GROUP_DAO.storeGroup(EventDeadLettersContract.GROUP_A).block();
        GROUP_DAO.storeGroup(EventDeadLettersContract.GROUP_B).block();

        assertThat(GROUP_DAO.retrieveAllGroups()
                .collectList().block())
            .containsOnly(EventDeadLettersContract.GROUP_A, EventDeadLettersContract.GROUP_B);
    }
}
