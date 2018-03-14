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

import org.apache.commons.configuration.DefaultConfigurationBuilder;
import org.apache.james.backends.jpa.JpaTestCluster;
import org.apache.james.rrt.api.RecipientRewriteTableException;
import org.apache.james.rrt.jpa.model.JPARecipientRewrite;
import org.apache.james.rrt.lib.AbstractRecipientRewriteTable;
import org.apache.james.rrt.lib.AbstractRecipientRewriteTableTest;
import org.junit.After;
import org.junit.Before;

/**
 * Test the JPA Virtual User Table implementation.
 */
public class JPARecipientRewriteTableTest extends AbstractRecipientRewriteTableTest {

    private static final JpaTestCluster JPA_TEST_CLUSTER = JpaTestCluster.create(JPARecipientRewrite.class);

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Override
    protected AbstractRecipientRewriteTable getRecipientRewriteTable() throws Exception {
        JPARecipientRewriteTable localVirtualUserTable = new JPARecipientRewriteTable();
        localVirtualUserTable.setEntityManagerFactory(JPA_TEST_CLUSTER.getEntityManagerFactory());
        DefaultConfigurationBuilder defaultConfiguration = new DefaultConfigurationBuilder();
        localVirtualUserTable.configure(defaultConfiguration);
        return localVirtualUserTable;
    }

    @Override
    protected void addMapping(String user, String domain, String mapping, int type) throws RecipientRewriteTableException {
        switch (type) {
        case ERROR_TYPE:
            virtualUserTable.addErrorMapping(user, domain, mapping);
            break;
        case REGEX_TYPE:
            virtualUserTable.addRegexMapping(user, domain, mapping);
            break;
        case ADDRESS_TYPE:
            virtualUserTable.addAddressMapping(user, domain, mapping);
            break;
        case ALIASDOMAIN_TYPE:
            virtualUserTable.addAliasDomainMapping(domain, mapping);
            break;
        default:
            throw new RuntimeException("Invalid mapping type: " + type);
        }
    }

    @Override
    protected void removeMapping(String user, String domain, String mapping, int type) throws RecipientRewriteTableException {
        switch (type) {
        case ERROR_TYPE:
            virtualUserTable.removeErrorMapping(user, domain, mapping);
            break;
        case REGEX_TYPE:
            virtualUserTable.removeRegexMapping(user, domain, mapping);
            break;
        case ADDRESS_TYPE:
            virtualUserTable.removeAddressMapping(user, domain, mapping);
            break;
        case ALIASDOMAIN_TYPE:
            virtualUserTable.removeAliasDomainMapping(domain, mapping);
            break;
        default:
            throw new RuntimeException("Invalid mapping type: " + type);
        }
    }
}
