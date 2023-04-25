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

package org.apache.james.modules.mailrepository;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.blob.api.BlobReferenceSource;
import org.apache.james.mailrepository.api.MailRepositoryFactory;
import org.apache.james.mailrepository.api.MailRepositoryUrlStore;
import org.apache.james.mailrepository.api.Protocol;
import org.apache.james.mailrepository.cassandra.CassandraMailRepository;
import org.apache.james.mailrepository.cassandra.CassandraMailRepositoryFactory;
import org.apache.james.mailrepository.cassandra.CassandraMailRepositoryKeysDAO;
import org.apache.james.mailrepository.cassandra.CassandraMailRepositoryMailDaoV2;
import org.apache.james.mailrepository.cassandra.CassandraMailRepositoryUrlModule;
import org.apache.james.mailrepository.cassandra.CassandraMailRepositoryUrlStore;
import org.apache.james.mailrepository.cassandra.MailRepositoryBlobReferenceSource;
import org.apache.james.mailrepository.memory.MailRepositoryStoreConfiguration;

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;

public class CassandraMailRepositoryModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(CassandraMailRepositoryUrlStore.class).in(Scopes.SINGLETON);
        bind(CassandraMailRepositoryKeysDAO.class).in(Scopes.SINGLETON);
        bind(CassandraMailRepositoryMailDaoV2.class).in(Scopes.SINGLETON);

        bind(MailRepositoryUrlStore.class).to(CassandraMailRepositoryUrlStore.class);

        bind(MailRepositoryStoreConfiguration.Item.class)
            .toProvider(() ->
                new MailRepositoryStoreConfiguration.Item(
                    ImmutableList.of(new Protocol("cassandra")),
                    CassandraMailRepository.class.getName(),
                    new BaseHierarchicalConfiguration()));

        Multibinder<CassandraModule> cassandraModuleBinder = Multibinder.newSetBinder(binder(), CassandraModule.class);
        cassandraModuleBinder.addBinding().toInstance(org.apache.james.mailrepository.cassandra.CassandraMailRepositoryModule.MODULE);
        cassandraModuleBinder.addBinding().toInstance(CassandraMailRepositoryUrlModule.MODULE);

        Multibinder.newSetBinder(binder(), BlobReferenceSource.class)
            .addBinding().to(MailRepositoryBlobReferenceSource.class);

        Multibinder.newSetBinder(binder(), MailRepositoryFactory.class)
                .addBinding().to(CassandraMailRepositoryFactory.class);
    }
}
