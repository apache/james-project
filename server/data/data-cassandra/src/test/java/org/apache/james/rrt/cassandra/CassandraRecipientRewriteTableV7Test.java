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

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.utils.CassandraUtils;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionDAO;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionManager;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionModule;
import org.apache.james.backends.cassandra.versions.SchemaVersion;
import org.apache.james.rrt.lib.AbstractRecipientRewriteTable;
import org.apache.james.rrt.lib.RecipientRewriteTableContract;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

class CassandraRecipientRewriteTableV7Test implements RecipientRewriteTableContract {
    static final SchemaVersion SCHEMA_VERSION_V7 = new SchemaVersion(7);

    static final CassandraModule MODULE = CassandraModule.aggregateModules(
        CassandraRRTModule.MODULE,
        CassandraSchemaVersionModule.MODULE);

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(MODULE);

    AbstractRecipientRewriteTable recipientRewriteTable;
    CassandraRecipientRewriteTableDAO recipientRewriteTableDAO;
    CassandraMappingsSourcesDAO mappingsSourcesDAO;
    CassandraSchemaVersionManager schemaVersionManager;
    CassandraSchemaVersionDAO cassandraSchemaVersionDAO;

    @BeforeEach
    void setup(CassandraCluster cassandra) throws Exception {
        cassandraSchemaVersionDAO = new CassandraSchemaVersionDAO(cassandra.getConf());
        recipientRewriteTableDAO = new CassandraRecipientRewriteTableDAO(cassandra.getConf(), CassandraUtils.WITH_DEFAULT_CONFIGURATION);
        mappingsSourcesDAO = new CassandraMappingsSourcesDAO(cassandra.getConf());
        schemaVersionManager = new CassandraSchemaVersionManager(cassandraSchemaVersionDAO);

        setUp();
    }

    @AfterEach
    void teardown() throws Exception {
        tearDown();
    }

    @Override
    public void createRecipientRewriteTable() {
        CassandraRecipientRewriteTable rrt = new CassandraRecipientRewriteTable(recipientRewriteTableDAO, mappingsSourcesDAO, schemaVersionManager);

        cassandraSchemaVersionDAO.updateVersion(SCHEMA_VERSION_V7).block();

        recipientRewriteTable = rrt;
    }

    @Override
    public AbstractRecipientRewriteTable virtualUserTable() {
        return recipientRewriteTable;
    }
}
