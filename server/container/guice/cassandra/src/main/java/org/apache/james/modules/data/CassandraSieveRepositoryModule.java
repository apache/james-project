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

import org.apache.james.backends.cassandra.components.CassandraDataDefinition;
import org.apache.james.sieve.cassandra.CassandraSieveRepository;
import org.apache.james.sieve.cassandra.CassandraSieveRepositoryDataDefinition;
import org.apache.james.sieverepository.api.SieveQuotaRepository;
import org.apache.james.sieverepository.api.SieveRepository;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;

public class CassandraSieveRepositoryModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(CassandraSieveRepository.class).in(Scopes.SINGLETON);
        bind(SieveRepository.class).to(CassandraSieveRepository.class);
        bind(SieveQuotaRepository.class).to(CassandraSieveRepository.class);

        Multibinder<CassandraDataDefinition> cassandraDataDefinitions = Multibinder.newSetBinder(binder(), CassandraDataDefinition.class);
        cassandraDataDefinitions.addBinding().toInstance(CassandraSieveRepositoryDataDefinition.MODULE);
    }
}
