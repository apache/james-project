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

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.james.backends.postgres.PostgresModule;
import org.apache.james.mailrepository.api.MailRepositoryFactory;
import org.apache.james.mailrepository.api.MailRepositoryUrlStore;
import org.apache.james.mailrepository.api.Protocol;
import org.apache.james.mailrepository.memory.MailRepositoryStoreConfiguration;
import org.apache.james.mailrepository.postgres.PostgresMailRepository;
import org.apache.james.mailrepository.postgres.PostgresMailRepositoryFactory;
import org.apache.james.mailrepository.postgres.PostgresMailRepositoryUrlStore;

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;

public class PostgresMailRepositoryModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(PostgresMailRepositoryUrlStore.class).in(Scopes.SINGLETON);

        bind(MailRepositoryUrlStore.class).to(PostgresMailRepositoryUrlStore.class);

        bind(MailRepositoryStoreConfiguration.Item.class)
            .toProvider(() -> new MailRepositoryStoreConfiguration.Item(
                ImmutableList.of(new Protocol("postgres")),
                PostgresMailRepository.class.getName(),
                new BaseHierarchicalConfiguration()));

        Multibinder.newSetBinder(binder(), MailRepositoryFactory.class)
            .addBinding().to(PostgresMailRepositoryFactory.class);
        Multibinder.newSetBinder(binder(), PostgresModule.class)
            .addBinding().toInstance(org.apache.james.mailrepository.postgres.PostgresMailRepositoryModule.MODULE);
    }
}
