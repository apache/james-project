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

import java.util.List;
import java.util.Optional;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.DockerCassandraRule;
import org.apache.james.core.User;
import org.apache.james.sieve.cassandra.model.Script;
import org.apache.james.sieverepository.api.ScriptName;
import org.apache.james.sieverepository.api.ScriptSummary;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

public class CassandraSieveDAOTest {

    public static final User USER = User.fromUsername("user");
    public static final ScriptName SCRIPT_NAME = new ScriptName("scriptName");
    public static final ScriptName SCRIPT_NAME2 = new ScriptName("scriptName2");
    public static final Script SCRIPT = Script.builder()
        .name(SCRIPT_NAME)
        .content("content")
        .isActive(false)
        .build();
    public static final Script SCRIPT2 = Script.builder()
        .name(SCRIPT_NAME2)
        .content("alternative content")
        .isActive(true)
        .build();
    public static final Script ACTIVE_SCRIPT = Script.builder()
        .copyOf(SCRIPT)
        .isActive(true)
        .build();
    public static final Script SCRIPT_NEW_CONTENT = Script.builder()
        .copyOf(SCRIPT)
        .content("newContent")
        .build();

    @ClassRule public static DockerCassandraRule cassandraServer = new DockerCassandraRule();
    
    private CassandraCluster cassandra;
    private CassandraSieveDAO sieveDAO;

    @Before
    public void setUp() throws Exception {
        cassandra = CassandraCluster.create(new CassandraSieveRepositoryModule(), cassandraServer.getIp(), cassandraServer.getBindingPort());
        sieveDAO = new CassandraSieveDAO(cassandra.getConf());
    }

    @After
    public void tearDown() {
        cassandra.close();
    }
    
     @Test
    public void getScriptShouldReturnEmptyByDefault() {
        assertThat(sieveDAO.getScript(USER, SCRIPT_NAME).join().isPresent())
            .isFalse();
    }

    @Test
    public void getScriptShouldReturnStoredScript() {
        sieveDAO.insertScript(USER, SCRIPT).join();

        Optional<Script> actual = sieveDAO.getScript(USER, SCRIPT_NAME).join();

        assertThat(actual.isPresent()).isTrue();
        assertThat(actual.get()).isEqualTo(SCRIPT);
    }

    @Test
    public void insertScriptShouldUpdateContent() {
        sieveDAO.insertScript(USER, SCRIPT).join();

        sieveDAO.insertScript(USER, SCRIPT_NEW_CONTENT).join();

        Optional<Script> actual = sieveDAO.getScript(USER, SCRIPT_NAME).join();
        assertThat(actual.isPresent()).isTrue();
        assertThat(actual.get()).isEqualTo(SCRIPT_NEW_CONTENT);
    }

    @Test
    public void insertScriptShouldUpdateActivate() {
        sieveDAO.insertScript(USER, SCRIPT).join();

        sieveDAO.insertScript(USER, ACTIVE_SCRIPT).join();

        Optional<Script> actual = sieveDAO.getScript(USER, SCRIPT_NAME).join();
        assertThat(actual.isPresent()).isTrue();
        assertThat(actual.get()).isEqualTo(ACTIVE_SCRIPT);
    }

    @Test
    public void deleteScriptInCassandraShouldWork() {
        sieveDAO.insertScript(USER, SCRIPT).join();

        sieveDAO.deleteScriptInCassandra(USER, SCRIPT_NAME).join();

        Optional<Script> actual = sieveDAO.getScript(USER, SCRIPT_NAME).join();
        assertThat(actual.isPresent()).isFalse();
    }

    @Test
    public void deleteScriptInCassandraShouldWorkWhenNoneStore() {
        sieveDAO.deleteScriptInCassandra(USER, SCRIPT_NAME).join();

        Optional<Script> actual = sieveDAO.getScript(USER, SCRIPT_NAME).join();
        assertThat(actual.isPresent()).isFalse();
    }

    @Test
    public void listScriptsShouldReturnEmpty() {
        List<ScriptSummary> scriptSummaryList = sieveDAO.listScripts(USER).join();

        assertThat(scriptSummaryList).isEmpty();
    }

    @Test
    public void listScriptsShouldReturnSingleStoredValue() {
        sieveDAO.insertScript(USER, SCRIPT).join();
        sieveDAO.insertScript(USER, SCRIPT2).join();

        List<ScriptSummary> scriptSummaryList = sieveDAO.listScripts(USER).join();

        assertThat(scriptSummaryList).containsOnly(SCRIPT.toSummary(), SCRIPT2.toSummary());
    }
}
