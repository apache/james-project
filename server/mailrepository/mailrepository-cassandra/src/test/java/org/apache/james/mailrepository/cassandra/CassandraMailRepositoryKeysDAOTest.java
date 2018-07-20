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

package org.apache.james.mailrepository.cassandra;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.DockerCassandraExtension;
import org.apache.james.backends.cassandra.utils.CassandraUtils;
import org.apache.james.mailrepository.api.MailKey;
import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(DockerCassandraExtension.class)
public class CassandraMailRepositoryKeysDAOTest {

    static final MailRepositoryUrl URL = MailRepositoryUrl.from("proto://url");
    static final MailRepositoryUrl URL2 = MailRepositoryUrl.from("proto://url2");
    static final MailKey KEY_1 = new MailKey("key1");
    static final MailKey KEY_2 = new MailKey("key2");
    static final MailKey KEY_3 = new MailKey("key3");

    static CassandraCluster cassandra;
    CassandraMailRepositoryKeysDAO testee;

    @BeforeAll
    static void setUpClass(DockerCassandraExtension.DockerCassandra dockerCassandra) {
        cassandra = CassandraCluster.create(new CassandraMailRepositoryModule(), dockerCassandra.getIp(), dockerCassandra.getBindingPort());
    }

    @BeforeEach
    public void setUp() {
        testee = new CassandraMailRepositoryKeysDAO(cassandra.getConf(), CassandraUtils.WITH_DEFAULT_CONFIGURATION);
    }

    @AfterEach
    void tearDown() {
        cassandra.clearTables();
    }

    @AfterAll
    static void tearDownClass() {
        cassandra.closeCluster();
    }

    @Test
    public void test() {
        assertThat(testee.list(URL).join())
            .isEmpty();
    }

    @Test
    public void listShouldReturnEmptyByDefault() {
        testee.store(URL, KEY_1).join();

        assertThat(testee.list(URL).join())
            .containsOnly(KEY_1);
    }

    @Test
    public void listShouldNotReturnElementsOfOtherRepositories() {
        testee.store(URL, KEY_1).join();

        assertThat(testee.list(URL2).join())
            .isEmpty();
    }

    @Test
    public void listShouldReturnSeveralElements() {
        testee.store(URL, KEY_1).join();
        testee.store(URL, KEY_2).join();
        testee.store(URL, KEY_3).join();

        assertThat(testee.list(URL).join())
            .containsOnly(KEY_1, KEY_2, KEY_3);
    }

    @Test
    public void listShouldNotReturnRemovedElements() {
        testee.store(URL, KEY_1).join();
        testee.store(URL, KEY_2).join();
        testee.store(URL, KEY_3).join();

        testee.remove(URL, KEY_2).join();

        assertThat(testee.list(URL).join())
            .containsOnly(KEY_1, KEY_3);
    }

    @Test
    public void removeShouldBeIdempotent() {
        testee.remove(URL, KEY_2).join();
    }

    @Test
    public void removeShouldNotAffectOtherRepositories() {
        testee.store(URL, KEY_1).join();

        testee.remove(URL2, KEY_2).join();

        assertThat(testee.list(URL).join())
            .containsOnly(KEY_1);
    }

}