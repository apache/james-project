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

package org.apache.james.modules.mailbox;

import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.mailbox.cassandra.quota.CassandraCurrentQuotaManagerV1;
import org.apache.james.mailbox.cassandra.quota.CassandraPerUserMaxQuotaManagerV1;
import org.apache.james.mailbox.quota.CurrentQuotaManager;
import org.apache.james.mailbox.quota.MaxQuotaManager;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Names;

@Deprecated() // To be removed after release 3.9.0
public class CassandraMailboxQuotaLegacyModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(CassandraCurrentQuotaManagerV1.class).in(Scopes.SINGLETON);
        bind(CurrentQuotaManager.class).to(CassandraCurrentQuotaManagerV1.class);
        bind(CurrentQuotaManager.class).annotatedWith(Names.named("old")).to(CassandraCurrentQuotaManagerV1.class);

        bind(CassandraPerUserMaxQuotaManagerV1.class).in(Scopes.SINGLETON);
        bind(MaxQuotaManager.class).to(CassandraPerUserMaxQuotaManagerV1.class);
        bind(MaxQuotaManager.class).annotatedWith(Names.named("old")).to(CassandraPerUserMaxQuotaManagerV1.class);

        Multibinder<CassandraModule> cassandraDataDefinitions = Multibinder.newSetBinder(binder(), CassandraModule.class);
        cassandraDataDefinitions.addBinding().toInstance(org.apache.james.mailbox.cassandra.modules.CassandraMailboxQuotaModule.MODULE);
    }
}
