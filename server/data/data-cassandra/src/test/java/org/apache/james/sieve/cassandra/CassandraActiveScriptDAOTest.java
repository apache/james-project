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

package org.apache.james.sieve.cassandra;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.core.User;
import org.apache.james.sieve.cassandra.model.ActiveScriptInfo;
import org.apache.james.sieverepository.api.ScriptName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class CassandraActiveScriptDAOTest {
    private static final User USER = User.fromUsername("user");
    private static final ScriptName SCRIPT_NAME = new ScriptName("sciptName");
    private static final ScriptName NEW_SCRIPT_NAME = new ScriptName("newScriptName");

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(CassandraSieveRepositoryModule.MODULE);

    private CassandraActiveScriptDAO activeScriptDAO;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        activeScriptDAO = new CassandraActiveScriptDAO(cassandra.getConf());
    }

    @Test
    void getActiveSctiptInfoShouldReturnEmptyByDefault() {
        assertThat(activeScriptDAO.getActiveSctiptInfo(USER).blockOptional().isPresent())
            .isFalse();
    }

    @Test
    void getActiveSctiptInfoShouldReturnStoredName() {
        activeScriptDAO.activate(USER, SCRIPT_NAME).block();

        Optional<ActiveScriptInfo> actual = activeScriptDAO.getActiveSctiptInfo(USER).blockOptional();

        assertThat(actual.isPresent()).isTrue();
        assertThat(actual.get().getName()).isEqualTo(SCRIPT_NAME);
    }

    @Test
    void activateShouldAllowRename() {
        activeScriptDAO.activate(USER, SCRIPT_NAME).block();

        activeScriptDAO.activate(USER, NEW_SCRIPT_NAME).block();

        Optional<ActiveScriptInfo> actual = activeScriptDAO.getActiveSctiptInfo(USER).blockOptional();
        assertThat(actual.isPresent()).isTrue();
        assertThat(actual.get().getName()).isEqualTo(NEW_SCRIPT_NAME);
    }

    @Test
    void unactivateShouldAllowRemovingActiveScript() {
        activeScriptDAO.activate(USER, SCRIPT_NAME).block();

        activeScriptDAO.unactivate(USER).block();

        Optional<ActiveScriptInfo> actual = activeScriptDAO.getActiveSctiptInfo(USER).blockOptional();
        assertThat(actual.isPresent()).isFalse();
    }

    @Test
    void unactivateShouldWorkWhenNoneStore() {
        activeScriptDAO.unactivate(USER).block();

        Optional<ActiveScriptInfo> actual = activeScriptDAO.getActiveSctiptInfo(USER).blockOptional();
        assertThat(actual.isPresent()).isFalse();
    }
}
