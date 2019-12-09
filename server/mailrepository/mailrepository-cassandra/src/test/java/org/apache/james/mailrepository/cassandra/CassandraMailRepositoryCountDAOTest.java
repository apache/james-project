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
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionModule;
import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class CassandraMailRepositoryCountDAOTest {
    static final MailRepositoryUrl URL = MailRepositoryUrl.from("proto://url");
    static final MailRepositoryUrl URL2 = MailRepositoryUrl.from("proto://url2");

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(
            CassandraModule.aggregateModules(CassandraSchemaVersionModule.MODULE,CassandraMailRepositoryModule.MODULE));

    CassandraMailRepositoryCountDAO testee;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        testee = new CassandraMailRepositoryCountDAO(cassandra.getConf());
    }

    @Test
    void getCountShouldReturnZeroWhenEmpty() {
        assertThat(testee.getCount(URL).block())
            .isEqualTo(0L);
    }

    @Test
    void getCountShouldReturnOneWhenIncrementedOneTime() {
        testee.increment(URL).block();

        assertThat(testee.getCount(URL).block())
            .isEqualTo(1L);
    }

    @Test
    void incrementShouldNotAffectOtherUrls() {
        testee.increment(URL).block();

        assertThat(testee.getCount(URL2).block())
            .isEqualTo(0L);
    }

    @Test
    void incrementCanBeAppliedSeveralTime() {
        testee.increment(URL).block();
        testee.increment(URL).block();

        assertThat(testee.getCount(URL).block())
            .isEqualTo(2L);
    }

    @Test
    void decrementShouldDecreaseCount() {
        testee.increment(URL).block();
        testee.increment(URL).block();
        testee.increment(URL).block();

        testee.decrement(URL).block();

        assertThat(testee.getCount(URL).block())
            .isEqualTo(2L);
    }

    @Test
    void decrementCanLeadToNegativeCount() {
        testee.decrement(URL).block();

        assertThat(testee.getCount(URL).block())
            .isEqualTo(-1L);
    }
}