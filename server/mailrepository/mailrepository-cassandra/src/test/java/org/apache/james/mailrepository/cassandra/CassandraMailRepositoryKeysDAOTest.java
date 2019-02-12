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
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.utils.CassandraUtils;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionModule;
import org.apache.james.mailrepository.api.MailKey;
import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class CassandraMailRepositoryKeysDAOTest {
    static final MailRepositoryUrl URL = MailRepositoryUrl.from("proto://url");
    static final MailRepositoryUrl URL2 = MailRepositoryUrl.from("proto://url2");
    static final MailKey KEY_1 = new MailKey("key1");
    static final MailKey KEY_2 = new MailKey("key2");
    static final MailKey KEY_3 = new MailKey("key3");
    static final CassandraModule MODULE = CassandraModule.aggregateModules(CassandraMailRepositoryModule.MODULE,
            CassandraSchemaVersionModule.MODULE);

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(MODULE);

    CassandraMailRepositoryKeysDAO testee;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        testee = new CassandraMailRepositoryKeysDAO(cassandra.getConf(), CassandraUtils.WITH_DEFAULT_CONFIGURATION);
    }

    @Test
    void listShouldBeEmptyByDefault() {
        assertThat(testee.list(URL).collectList().block())
            .isEmpty();
    }

    @Test
    void listShouldReturnEmptyByDefault() {
        testee.store(URL, KEY_1).block();

        assertThat(testee.list(URL).collectList().block())
            .containsOnly(KEY_1);
    }

    @Test
    void listShouldNotReturnElementsOfOtherRepositories() {
        testee.store(URL, KEY_1).block();

        assertThat(testee.list(URL2).collectList().block())
            .isEmpty();
    }

    @Test
    void listShouldReturnSeveralElements() {
        testee.store(URL, KEY_1).block();
        testee.store(URL, KEY_2).block();
        testee.store(URL, KEY_3).block();

        assertThat(testee.list(URL).collectList().block())
            .containsOnly(KEY_1, KEY_2, KEY_3);
    }

    @Test
    void listShouldNotReturnRemovedElements() {
        testee.store(URL, KEY_1).block();
        testee.store(URL, KEY_2).block();
        testee.store(URL, KEY_3).block();

        testee.remove(URL, KEY_2).block();

        assertThat(testee.list(URL).collectList().block())
            .containsOnly(KEY_1, KEY_3);
    }

    @Test
    void removeShouldBeIdempotent() {
        testee.remove(URL, KEY_2).block();
    }

    @Test
    void removeShouldNotAffectOtherRepositories() {
        testee.store(URL, KEY_1).block();

        testee.remove(URL2, KEY_2).block();

        assertThat(testee.list(URL).collectList().block())
            .containsOnly(KEY_1);
    }

    @Test
    void removeShouldReturnTrueWhenKeyDeleted() {
        testee.store(URL, KEY_1).block();

        boolean isDeleted = testee.remove(URL, KEY_1).block();

        assertThat(isDeleted).isTrue();
    }

    @Test
    void removeShouldReturnFalseWhenKeyNotDeleted() {
        boolean isDeleted = testee.remove(URL2, KEY_2).block();

        assertThat(isDeleted).isFalse();
    }


    @Test
    void storeShouldReturnTrueWhenNotPreviouslyStored() {
        boolean isStored = testee.store(URL, KEY_1).block();

        assertThat(isStored).isTrue();
    }

    @Test
    void storeShouldReturnFalseWhenPreviouslyStored() {
        testee.store(URL, KEY_1).block();

        boolean isStored = testee.store(URL, KEY_1).block();

        assertThat(isStored).isFalse();
    }

}