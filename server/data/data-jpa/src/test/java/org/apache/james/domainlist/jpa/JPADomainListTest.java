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
package org.apache.james.domainlist.jpa;

import org.apache.james.backends.jpa.JpaTestCluster;
import org.apache.james.core.Domain;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.jpa.model.JPADomain;
import org.apache.james.domainlist.lib.AbstractDomainListTest;
import org.junit.After;
import org.junit.Before;

/**
 * Test the JPA implementation of the DomainList.
 */
public class JPADomainListTest extends AbstractDomainListTest {

    private static final JpaTestCluster JPA_TEST_CLUSTER = JpaTestCluster.create(JPADomain.class);

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
    }

    @After
    public void tearDown() throws Exception {
        DomainList domainList = createDomainList();
        for (Domain domain: domainList.getDomains()) {
            domainList.removeDomain(domain);
        }
    }

    @Override
    protected DomainList createDomainList() throws Exception {
        JPADomainList jpaDomainList = new JPADomainList(getDNSServer("localhost"),
            JPA_TEST_CLUSTER.getEntityManagerFactory());
        jpaDomainList.setAutoDetect(false);
        jpaDomainList.setAutoDetectIP(false);
        
        return jpaDomainList;
    }

}
