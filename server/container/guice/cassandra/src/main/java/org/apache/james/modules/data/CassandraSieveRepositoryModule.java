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

package org.apache.james.modules.data;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.sieve.cassandra.CassandraSieveQuotaDAO;
import org.apache.james.sieve.cassandra.CassandraSieveQuotaDAOV1;
import org.apache.james.sieve.cassandra.CassandraSieveRepository;
import org.apache.james.sieverepository.api.SieveQuotaRepository;
import org.apache.james.sieverepository.api.SieveRepository;

public class CassandraSieveRepositoryModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(CassandraSieveRepository.class).in(Scopes.SINGLETON);
        bind(CassandraSieveQuotaDAOV1.class).in(Scopes.SINGLETON);
        bind(CassandraSieveQuotaDAO.class).to(CassandraSieveQuotaDAOV1.class);
        bind(SieveRepository.class).to(CassandraSieveRepository.class);
        bind(SieveQuotaRepository.class).to(CassandraSieveRepository.class);

        Multibinder<CassandraModule> cassandraDataDefinitions = Multibinder.newSetBinder(binder(), CassandraModule.class);
        cassandraDataDefinitions.addBinding().toInstance(org.apache.james.sieve.cassandra.CassandraSieveRepositoryModule.MODULE);
    }
}
