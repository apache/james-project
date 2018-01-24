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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(DockerCassandraExtension.class)
public class CassandraMailRepositoryKeysDAOTest {


    static final String URL = "url";
    static final String URL2 = "url2";
    static final String KEY_1 = "key1";
    static final String KEY_2 = "key2";
    static final String KEY_3 = "key3";

    CassandraCluster cassandra;
    CassandraMailRepositoryKeysDAO testee;

    @BeforeEach
    public void setUp(DockerCassandraExtension.DockerCassandra dockerCassandra) {
        cassandra = CassandraCluster.create(
            new CassandraMailRepositoryModule(), dockerCassandra.getIp(), dockerCassandra.getBindingPort());

        testee = new CassandraMailRepositoryKeysDAO(cassandra.getConf(), CassandraUtils.WITH_DEFAULT_CONFIGURATION);
    }

    @AfterEach
    public void tearDown() {
        cassandra.close();
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