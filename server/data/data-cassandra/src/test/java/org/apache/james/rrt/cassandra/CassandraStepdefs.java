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
import org.apache.james.backends.cassandra.DockerCassandraRule;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionModule;
import org.apache.james.rrt.lib.AbstractRecipientRewriteTable;
import org.apache.james.rrt.lib.RecipientRewriteTableFixture;
import org.apache.james.rrt.lib.RewriteTablesStepdefs;
import org.apache.james.user.cassandra.CassandraUsersDAO;
import org.apache.james.user.cassandra.CassandraUsersRepositoryModule;
import org.apache.james.user.lib.UsersRepositoryImpl;
import org.junit.Rule;

import com.github.fge.lambdas.Throwing;

import io.cucumber.java.After;
import io.cucumber.java.Before;

public class CassandraStepdefs {

    @Rule
    public DockerCassandraRule cassandraServer = new DockerCassandraRule().allowRestart();

    private CassandraCluster cassandra;

    private final RewriteTablesStepdefs mainStepdefs;

    public CassandraStepdefs(RewriteTablesStepdefs mainStepdefs) {
        this.mainStepdefs = mainStepdefs;
    }

    @Before
    public void setup() {
        cassandra = CassandraCluster.create(
            CassandraModule.aggregateModules(CassandraRRTModule.MODULE, CassandraUsersRepositoryModule.MODULE, CassandraSchemaVersionModule.MODULE),
            cassandraServer.getHost());
        mainStepdefs.setUp(Throwing.supplier(this::getRecipientRewriteTable).sneakyThrow());
    }

    @After
    public void tearDown() {
        cassandra.close();
    }

    private AbstractRecipientRewriteTable getRecipientRewriteTable() throws Exception {
        CassandraRecipientRewriteTable rrt = new CassandraRecipientRewriteTable(
            new CassandraRecipientRewriteTableDAO(cassandra.getConf()),
            new CassandraMappingsSourcesDAO(cassandra.getConf()));
        rrt.setUsersRepository(new UsersRepositoryImpl<>(RecipientRewriteTableFixture.domainListForCucumberTests(),
            new CassandraUsersDAO(cassandra.getConf())));
        rrt.setDomainList(RecipientRewriteTableFixture.domainListForCucumberTests());
        return rrt;
    }
}
