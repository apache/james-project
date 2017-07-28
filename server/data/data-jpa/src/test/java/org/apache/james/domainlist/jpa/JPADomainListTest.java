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
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.jpa.model.JPADomain;
import org.apache.james.domainlist.lib.AbstractDomainListTest;
import org.apache.james.domainlist.lib.EnvDetector;
import org.slf4j.LoggerFactory;

/**
 * Test the JPA implementation of the DomainList.
 */
public class JPADomainListTest extends AbstractDomainListTest {

    private static final JpaTestCluster JPA_TEST_CLUSTER = JpaTestCluster.create(JPADomain.class);

    @Override
    protected DomainList createDomainList() {
        JPADomainList jpaDomainList = new JPADomainList(getDNSServer("localhost"),
            new EnvDetector(),
            JPA_TEST_CLUSTER.getEntityManagerFactory());
        jpaDomainList.setLog(LoggerFactory.getLogger("JPADomainListMockLog"));
        jpaDomainList.setAutoDetect(false);
        jpaDomainList.setAutoDetectIP(false);
        
        return jpaDomainList;
    }

}
