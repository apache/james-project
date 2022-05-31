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
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;

import java.util.Optional;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.core.Username;
import org.apache.james.sieve.cassandra.model.Script;
import org.apache.james.sieverepository.api.ScriptName;
import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class CassandraSieveDAOTest {

    private static final Username USERNAME = Username.of("user");
    private static final ScriptName SCRIPT_NAME = new ScriptName("scriptName");
    private static final ScriptName SCRIPT_NAME2 = new ScriptName("scriptName2");
    private static final Script SCRIPT = Script.builder()
        .name(SCRIPT_NAME)
        .content("content")
        .isActive(false)
        .build();
    private static final Script SCRIPT2 = Script.builder()
        .name(SCRIPT_NAME2)
        .content("alternative content")
        .isActive(true)
        .build();
    private static final Script ACTIVE_SCRIPT = Script.builder()
        .copyOf(SCRIPT)
        .isActive(true)
        .build();
    private static final Script SCRIPT_NEW_CONTENT = Script.builder()
        .copyOf(SCRIPT)
        .content("newContent")
        .build();
    private static final ConditionFactory CALMLY_AWAIT = Awaitility
        .with().pollInterval(ONE_HUNDRED_MILLISECONDS)
        .and().pollDelay(ONE_HUNDRED_MILLISECONDS)
        .await();

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(CassandraSieveRepositoryModule.MODULE);

    private CassandraSieveDAO sieveDAO;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        sieveDAO = new CassandraSieveDAO(cassandra.getConf());
    }
    
     @Test
    void getScriptShouldReturnEmptyByDefault() {
        assertThat(sieveDAO.getScript(USERNAME, SCRIPT_NAME).blockOptional())
            .isEmpty();
    }

    @Test
    void getScriptShouldReturnStoredScript() {
        sieveDAO.insertScript(USERNAME, SCRIPT).block();

        Optional<Script> actual = sieveDAO.getScript(USERNAME, SCRIPT_NAME).blockOptional();

        assertThat(actual).contains(SCRIPT);
    }

    @Test
    void insertScriptShouldUpdateContent() {
        sieveDAO.insertScript(USERNAME, SCRIPT).block();

        sieveDAO.insertScript(USERNAME, SCRIPT_NEW_CONTENT).block();

        Optional<Script> actual = sieveDAO.getScript(USERNAME, SCRIPT_NAME).blockOptional();
        assertThat(actual).contains(SCRIPT_NEW_CONTENT);
    }

    @Test
    void insertScriptShouldUpdateActivate() {
        sieveDAO.insertScript(USERNAME, SCRIPT).block();

        sieveDAO.insertScript(USERNAME, ACTIVE_SCRIPT).block();

        Optional<Script> actual = sieveDAO.getScript(USERNAME, SCRIPT_NAME).blockOptional();
        assertThat(actual).contains(ACTIVE_SCRIPT);
    }

    @Test
    void deleteScriptInCassandraShouldWork() {
        sieveDAO.insertScript(USERNAME, SCRIPT).block();

        CALMLY_AWAIT.atMost(Durations.TEN_SECONDS)
            .untilAsserted(() -> {
                sieveDAO.deleteScriptInCassandra(USERNAME, SCRIPT_NAME).block();
                assertThat(sieveDAO.getScript(USERNAME, SCRIPT_NAME).blockOptional()).isEmpty();
            });
    }

    @Test
    void deleteScriptInCassandraShouldWorkWhenNoneStore() {
        sieveDAO.deleteScriptInCassandra(USERNAME, SCRIPT_NAME).block();

        Optional<Script> actual = sieveDAO.getScript(USERNAME, SCRIPT_NAME).blockOptional();
        assertThat(actual).isEmpty();
    }

    @Test
    void listScriptsShouldReturnEmpty() {
        assertThat(sieveDAO.listScripts(USERNAME).toIterable()).isEmpty();
    }

    @Test
    void listScriptsShouldReturnSingleStoredValue() {
        sieveDAO.insertScript(USERNAME, SCRIPT).block();
        sieveDAO.insertScript(USERNAME, SCRIPT2).block();

        assertThat(sieveDAO.listScripts(USERNAME).toIterable())
            .containsOnly(SCRIPT.toSummary(), SCRIPT2.toSummary());
    }
}
