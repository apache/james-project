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
import org.apache.james.backends.cassandra.components.CassandraDataDefinition;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionDAO;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionDataDefinition;
import org.apache.james.domainlist.api.mock.SimpleDomainList;
import org.apache.james.rrt.lib.AbstractRecipientRewriteTable;
import org.apache.james.rrt.lib.RecipientRewriteTableContract;
import org.apache.james.user.cassandra.CassandraUsersDAO;
import org.apache.james.user.cassandra.CassandraUsersRepositoryDataDefinition;
import org.apache.james.user.lib.UsersRepositoryImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

class CassandraRecipientRewriteTableTest implements RecipientRewriteTableContract {
    static final CassandraDataDefinition MODULE = CassandraDataDefinition.aggregateModules(
        CassandraRRTDataDefinition.MODULE,
        CassandraUsersRepositoryDataDefinition.MODULE,
        CassandraSchemaVersionDataDefinition.MODULE);

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(MODULE);

    AbstractRecipientRewriteTable recipientRewriteTable;
    CassandraRecipientRewriteTableDAO recipientRewriteTableDAO;
    CassandraMappingsSourcesDAO mappingsSourcesDAO;
    CassandraSchemaVersionDAO cassandraSchemaVersionDAO;

    @BeforeEach
    void setup(CassandraCluster cassandra) throws Exception {
        cassandraSchemaVersionDAO = new CassandraSchemaVersionDAO(cassandra.getConf());
        recipientRewriteTableDAO = new CassandraRecipientRewriteTableDAO(cassandra.getConf());
        mappingsSourcesDAO = new CassandraMappingsSourcesDAO(cassandra.getConf());

        setUp();
    }

    @AfterEach
    void teardown() throws Exception {
        tearDown();
    }

    @Override
    public void createRecipientRewriteTable() {
        recipientRewriteTable = new CassandraRecipientRewriteTable(recipientRewriteTableDAO, mappingsSourcesDAO);
        recipientRewriteTable.setUsersRepository(new UsersRepositoryImpl<>(new SimpleDomainList(),
            new CassandraUsersDAO(cassandraCluster.getCassandraCluster().getConf())));
    }

    @Override
    public AbstractRecipientRewriteTable virtualUserTable() {
        return recipientRewriteTable;
    }
}
