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
import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(DockerCassandraExtension.class)
public class CassandraMailRepositoryCountDAOTest {
    static final MailRepositoryUrl URL = MailRepositoryUrl.from("proto://url");
    static final MailRepositoryUrl URL2 = MailRepositoryUrl.from("proto://url2");

    static CassandraCluster cassandra;
    CassandraMailRepositoryCountDAO testee;

    @BeforeAll
    static void setUpClass(DockerCassandraExtension.DockerCassandra dockerCassandra) {
        cassandra = CassandraCluster.create(new CassandraMailRepositoryModule(), dockerCassandra.getIp(), dockerCassandra.getBindingPort());
    }

    @BeforeEach
    public void setUp() {

        testee = new CassandraMailRepositoryCountDAO(cassandra.getConf());
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
    public void getCountShouldReturnZeroWhenEmpty() {
        assertThat(testee.getCount(URL).join())
            .isEqualTo(0L);
    }

    @Test
    public void getCountShouldReturnOneWhenIncrementedOneTime() {
        testee.increment(URL).join();

        assertThat(testee.getCount(URL).join())
            .isEqualTo(1L);
    }

    @Test
    public void incrementShouldNotAffectOtherUrls() {
        testee.increment(URL).join();

        assertThat(testee.getCount(URL2).join())
            .isEqualTo(0L);
    }

    @Test
    public void incrementCanBeAppliedSeveralTime() {
        testee.increment(URL).join();
        testee.increment(URL).join();

        assertThat(testee.getCount(URL).join())
            .isEqualTo(2L);
    }

    @Test
    public void decrementShouldDecreaseCount() {
        testee.increment(URL).join();
        testee.increment(URL).join();
        testee.increment(URL).join();

        testee.decrement(URL).join();

        assertThat(testee.getCount(URL).join())
            .isEqualTo(2L);
    }

    @Test
    public void decrementCanLeadToNegativeCount() {
        testee.decrement(URL).join();

        assertThat(testee.getCount(URL).join())
            .isEqualTo(-1L);
    }
}