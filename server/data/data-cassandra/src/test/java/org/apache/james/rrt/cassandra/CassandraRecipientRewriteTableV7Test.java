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

package org.apache.james.rrt.cassandra;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.DockerCassandraRule;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.utils.CassandraUtils;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionDAO;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionModule;
import org.apache.james.backends.cassandra.versions.SchemaVersion;
import org.apache.james.rrt.lib.AbstractRecipientRewriteTable;
import org.apache.james.rrt.lib.AbstractRecipientRewriteTableTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;

public class CassandraRecipientRewriteTableV7Test extends AbstractRecipientRewriteTableTest {
    private static final SchemaVersion SCHEMA_VERSION_V7 = new SchemaVersion(7);

    private static final CassandraModule MODULE = CassandraModule.aggregateModules(
        CassandraRRTModule.MODULE,
        CassandraSchemaVersionModule.MODULE);

    @Rule
    public DockerCassandraRule cassandraServer = new DockerCassandraRule().allowRestart();

    protected CassandraCluster cassandra;

    @Override
    @Before
    public void setUp() throws Exception {
        cassandra = CassandraCluster.create(MODULE, cassandraServer.getHost());
        super.setUp();
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
        cassandra.close();
    }

    @Override
    protected AbstractRecipientRewriteTable getRecipientRewriteTable() throws Exception {
        CassandraSchemaVersionDAO cassandraSchemaVersionDAO = new CassandraSchemaVersionDAO(
            cassandra.getConf()
        );

        CassandraRecipientRewriteTable rrt = new CassandraRecipientRewriteTable(
            new CassandraRecipientRewriteTableDAO(cassandra.getConf(), CassandraUtils.WITH_DEFAULT_CONFIGURATION),
            new CassandraMappingsSourcesDAO(cassandra.getConf()),
            cassandraSchemaVersionDAO);

        cassandraSchemaVersionDAO.updateVersion(SCHEMA_VERSION_V7);

        return rrt;
    }
}
