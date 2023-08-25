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

package org.apache.james.mpt.managesieve.cassandra.host;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.components.CassandraQuotaCurrentValueDao;
import org.apache.james.backends.cassandra.components.CassandraQuotaLimitDao;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.mpt.host.JamesManageSieveHostSystem;
import org.apache.james.sieve.cassandra.CassandraActiveScriptDAO;
import org.apache.james.sieve.cassandra.CassandraSieveDAO;
import org.apache.james.sieve.cassandra.CassandraSieveQuotaDAOV2;
import org.apache.james.sieve.cassandra.CassandraSieveRepository;
import org.apache.james.sieverepository.api.SieveRepository;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.cassandra.CassandraUsersDAO;
import org.apache.james.user.lib.UsersRepositoryImpl;

public class CassandraHostSystem extends JamesManageSieveHostSystem {
    private static final DomainList NO_DOMAIN_LIST = null;
    private final CassandraCluster cassandra;

    public CassandraHostSystem(CassandraCluster cassandra) {
        this.cassandra = cassandra;
    }

    @Override
    protected SieveRepository createSieveRepository() {
        return new CassandraSieveRepository(
            new CassandraSieveDAO(cassandra.getConf()),
            new CassandraSieveQuotaDAOV2(new CassandraQuotaCurrentValueDao(cassandra.getConf()), new CassandraQuotaLimitDao(cassandra.getConf())),
            new CassandraActiveScriptDAO(cassandra.getConf()));
    }

    @Override
    protected UsersRepository createUsersRepository() {
        CassandraUsersDAO usersDAO = new CassandraUsersDAO(cassandra.getConf());
        UsersRepositoryImpl usersRepository = new UsersRepositoryImpl(NO_DOMAIN_LIST, usersDAO);
        usersRepository.setEnableVirtualHosting(false);
        return usersRepository;
    }
}
