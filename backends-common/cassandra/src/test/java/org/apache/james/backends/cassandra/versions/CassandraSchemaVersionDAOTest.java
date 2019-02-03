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

package org.apache.james.backends.cassandra.versions;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.utils.CassandraUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class CassandraSchemaVersionDAOTest {
    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(CassandraSchemaVersionModule.MODULE);

    private CassandraSchemaVersionDAO testee;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        testee = new CassandraSchemaVersionDAO(cassandra.getConf(), CassandraUtils.WITH_DEFAULT_CONFIGURATION);
    }

    @Test
    void getCurrentSchemaVersionShouldReturnEmptyWhenTableIsEmpty() {
        assertThat(testee.getCurrentSchemaVersion().join())
            .isEqualTo(Optional.empty());
    }

    @Test
    void getCurrentSchemaVersionShouldReturnVersionPresentInTheTable() {
        SchemaVersion version = new SchemaVersion(42);

        testee.updateVersion(version).join();

        assertThat(testee.getCurrentSchemaVersion().join()).contains(version);
    }

    @Test
    void getCurrentSchemaVersionShouldBeIdempotent() {
        SchemaVersion version = new SchemaVersion(42);

        testee.updateVersion(version.next()).join();
        testee.updateVersion(version).join();

        assertThat(testee.getCurrentSchemaVersion().join()).contains(version.next());
    }
}