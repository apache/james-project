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

package org.apache.james.droplists.jpa;

import org.apache.james.backends.jpa.JpaTestCluster;
import org.apache.james.droplists.api.DropList;
import org.apache.james.droplists.api.DropListContract;
import org.apache.james.droplists.jpa.model.JPADropListEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

class JPADropListTest implements DropListContract {

    static final JpaTestCluster jpaTestCluster = JpaTestCluster.create(JPADropListEntry.class);

    JPADropList jpaDropList;

    @BeforeEach
    public void setUp() throws Exception {
        jpaDropList = new JPADropList(jpaTestCluster.getEntityManagerFactory());
    }

    @AfterEach
    void tearDown() {
        jpaTestCluster.clear("JAMES_DROP_LIST");
    }

    @Override
    public DropList dropList() {
        return jpaDropList;
    }
}