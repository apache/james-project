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
package org.apache.james.rrt.jpa;

import org.apache.james.backends.jpa.JpaTestCluster;
import org.apache.james.rrt.jpa.model.JPARecipientRewrite;
import org.apache.james.rrt.lib.AbstractRecipientRewriteTable;
import org.apache.james.rrt.lib.RecipientRewriteTableFixture;
import org.apache.james.rrt.lib.RewriteTablesStepdefs;
import org.apache.james.user.jpa.JPAUsersRepository;

import com.github.fge.lambdas.Throwing;

import io.cucumber.java.After;
import io.cucumber.java.Before;

public class JPAStepdefs {

    private static final JpaTestCluster JPA_TEST_CLUSTER = JpaTestCluster.create(JPARecipientRewrite.class);

    private final RewriteTablesStepdefs mainStepdefs;

    public JPAStepdefs(RewriteTablesStepdefs mainStepdefs) {
        this.mainStepdefs = mainStepdefs;
    }

    @Before
    public void setup() throws Throwable {
        mainStepdefs.setUp(Throwing.supplier(this::getRecipientRewriteTable).sneakyThrow());
    }

    @After
    public void tearDown() {
        JPA_TEST_CLUSTER.clear(JPARecipientRewrite.JAMES_RECIPIENT_REWRITE);
    }

    private AbstractRecipientRewriteTable getRecipientRewriteTable() throws Exception {
        JPARecipientRewriteTable localVirtualUserTable = new JPARecipientRewriteTable();
        localVirtualUserTable.setEntityManagerFactory(JPA_TEST_CLUSTER.getEntityManagerFactory());
        localVirtualUserTable.setUsersRepository(new JPAUsersRepository(RecipientRewriteTableFixture.domainListForCucumberTests()));
        localVirtualUserTable.setDomainList(RecipientRewriteTableFixture.domainListForCucumberTests());
        return localVirtualUserTable;
    }
}
