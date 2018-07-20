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
import org.apache.james.backends.cassandra.DockerCassandraRule;
import org.apache.james.backends.cassandra.utils.CassandraUtils;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

public class CassandraSchemaVersionDAOTest {

    @ClassRule public static DockerCassandraRule cassandraServer = new DockerCassandraRule();

    private static CassandraCluster cassandra;

    private CassandraSchemaVersionDAO testee;

    @BeforeClass
    public static void setUpClass() {
        cassandra = CassandraCluster.create(new CassandraSchemaVersionModule(), cassandraServer.getHost());
    }

    @Before
    public void setUp() {
        testee = new CassandraSchemaVersionDAO(cassandra.getConf(), CassandraUtils.WITH_DEFAULT_CONFIGURATION);
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
    public void getCurrentSchemaVersionShouldReturnEmptyWhenTableIsEmpty() {
        assertThat(testee.getCurrentSchemaVersion().join())
            .isEqualTo(Optional.empty());
    }

    @Test
    public void getCurrentSchemaVersionShouldReturnVersionPresentInTheTable() {
        SchemaVersion version = new SchemaVersion(42);

        testee.updateVersion(version).join();

        assertThat(testee.getCurrentSchemaVersion().join()).contains(version);
    }

    @Test
    public void getCurrentSchemaVersionShouldBeIdempotent() {
        SchemaVersion version = new SchemaVersion(42);

        testee.updateVersion(version.next()).join();
        testee.updateVersion(version).join();

        assertThat(testee.getCurrentSchemaVersion().join()).contains(version.next());
    }
}