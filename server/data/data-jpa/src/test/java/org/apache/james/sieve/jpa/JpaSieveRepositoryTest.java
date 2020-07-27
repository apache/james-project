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

package org.apache.james.sieve.jpa;

import org.apache.james.backends.jpa.JpaTestCluster;
import org.apache.james.sieve.jpa.model.JPASieveQuota;
import org.apache.james.sieve.jpa.model.JPASieveScript;
import org.apache.james.sieverepository.api.SieveRepository;
import org.apache.james.sieverepository.lib.AbstractSieveRepositoryTest;
import org.junit.After;
import org.junit.Before;

public class JpaSieveRepositoryTest extends AbstractSieveRepositoryTest {

    private static final JpaTestCluster JPA_TEST_CLUSTER = JpaTestCluster.create(JPASieveScript.class, JPASieveQuota.class);

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
    }

    @After
    public void tearDown() throws Exception {
        JPA_TEST_CLUSTER.clear("JAMES_SIEVE_SCRIPT", "JAMES_SIEVE_QUOTA");
    }

    @Override
    protected SieveRepository createSieveRepository() throws Exception {
        return new JPASieveRepository(JPA_TEST_CLUSTER.getEntityManagerFactory());
    }
}
