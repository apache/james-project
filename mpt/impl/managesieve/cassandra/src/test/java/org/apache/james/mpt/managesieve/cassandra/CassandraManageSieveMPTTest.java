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

package org.apache.james.mpt.managesieve.cassandra;

import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.domainlist.cassandra.CassandraDomainListModule;
import org.apache.james.mpt.ManageSieveMPTContract;
import org.apache.james.mpt.host.ManageSieveHostSystem;
import org.apache.james.mpt.managesieve.cassandra.host.CassandraHostSystem;
import org.apache.james.sieve.cassandra.CassandraSieveQuotaModule;
import org.apache.james.sieve.cassandra.CassandraSieveRepositoryModule;
import org.apache.james.user.cassandra.CassandraUsersRepositoryModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

class CassandraManageSieveMPTTest implements ManageSieveMPTContract {
    @RegisterExtension
    static CassandraClusterExtension cassandra = new CassandraClusterExtension(CassandraModule.aggregateModules(
        CassandraDomainListModule.MODULE,
        CassandraSieveRepositoryModule.MODULE,
        CassandraSieveQuotaModule.MODULE,
        CassandraUsersRepositoryModule.MODULE));

    private ManageSieveHostSystem system;

    @BeforeEach
    void setUp() throws Exception {
        system = new CassandraHostSystem(cassandra.getCassandraCluster());
        system.beforeTest();
    }

    @Override
    public ManageSieveHostSystem hostSystem() {
        return system;
    }
}
