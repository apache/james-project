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

package org.apache.james.sieve.cassandra;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraMutualizedQuotaModule;
import org.apache.james.backends.cassandra.components.CassandraQuotaCurrentValueDao;
import org.apache.james.backends.cassandra.components.CassandraQuotaLimitDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

class CassandraSieveQuotaDAOV2Test implements CassandraSieveQuotaDAOContract {
    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(CassandraMutualizedQuotaModule.MODULE);

    private CassandraSieveQuotaDAOV2 sieveQuotaDAO;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        sieveQuotaDAO = new CassandraSieveQuotaDAOV2(
            new CassandraQuotaCurrentValueDao(cassandra.getConf()),
            new CassandraQuotaLimitDao(cassandra.getConf()));
    }

    @Override
    public CassandraSieveQuotaDAO testee() {
        return sieveQuotaDAO;
    }
}
