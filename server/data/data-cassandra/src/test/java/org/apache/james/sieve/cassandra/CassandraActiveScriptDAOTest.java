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
import org.apache.james.backends.cassandra.DockerCassandraRule;
import org.apache.james.core.User;
import org.apache.james.sieve.cassandra.model.ActiveScriptInfo;
import org.apache.james.sieverepository.api.ScriptName;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

public class CassandraActiveScriptDAOTest {

    public static final User USER = User.fromUsername("user");
    public static final ScriptName SCRIPT_NAME = new ScriptName("sciptName");
    public static final ScriptName NEW_SCRIPT_NAME = new ScriptName("newScriptName");

    @ClassRule public static DockerCassandraRule cassandraServer = new DockerCassandraRule();
    private static CassandraCluster cassandra;

    private CassandraActiveScriptDAO activeScriptDAO;

    @BeforeClass
    public static void setUpClass() {
        cassandra = CassandraCluster.create(new CassandraSieveRepositoryModule(), cassandraServer.getHost());
    }

    @Before
    public void setUp() throws Exception {
        activeScriptDAO = new CassandraActiveScriptDAO(cassandra.getConf());
    }

    @After
    public void tearDown() {
        cassandra.clearTables();
    }

    @AfterClass
    public static void tearDownClass() {
        cassandra.closeCluster();
    }

    @Test
    public void getActiveSctiptInfoShouldReturnEmptyByDefault() {
        assertThat(activeScriptDAO.getActiveSctiptInfo(USER).join().isPresent())
            .isFalse();
    }

    @Test
    public void getActiveSctiptInfoShouldReturnStoredName() {
        activeScriptDAO.activate(USER, SCRIPT_NAME).join();

        Optional<ActiveScriptInfo> actual = activeScriptDAO.getActiveSctiptInfo(USER).join();

        assertThat(actual.isPresent()).isTrue();
        assertThat(actual.get().getName()).isEqualTo(SCRIPT_NAME);
    }

    @Test
    public void activateShouldAllowRename() {
        activeScriptDAO.activate(USER, SCRIPT_NAME).join();

        activeScriptDAO.activate(USER, NEW_SCRIPT_NAME).join();

        Optional<ActiveScriptInfo> actual = activeScriptDAO.getActiveSctiptInfo(USER).join();
        assertThat(actual.isPresent()).isTrue();
        assertThat(actual.get().getName()).isEqualTo(NEW_SCRIPT_NAME);
    }

    @Test
    public void unactivateShouldAllowRemovingActiveScript() {
        activeScriptDAO.activate(USER, SCRIPT_NAME).join();

        activeScriptDAO.unactivate(USER).join();

        Optional<ActiveScriptInfo> actual = activeScriptDAO.getActiveSctiptInfo(USER).join();
        assertThat(actual.isPresent()).isFalse();
    }


    @Test
    public void unactivateShouldWorkWhenNoneStore() {
        activeScriptDAO.unactivate(USER).join();

        Optional<ActiveScriptInfo> actual = activeScriptDAO.getActiveSctiptInfo(USER).join();
        assertThat(actual.isPresent()).isFalse();
    }

}
