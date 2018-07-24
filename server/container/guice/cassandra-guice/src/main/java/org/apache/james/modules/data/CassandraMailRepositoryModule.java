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

import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.blob.api.ObjectStore;
import org.apache.james.mailrepository.api.MailRepository;
import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.apache.james.mailrepository.api.MailRepositoryUrlStore;
import org.apache.james.mailrepository.cassandra.CassandraMailRepository;
import org.apache.james.mailrepository.cassandra.CassandraMailRepositoryCountDAO;
import org.apache.james.mailrepository.cassandra.CassandraMailRepositoryKeysDAO;
import org.apache.james.mailrepository.cassandra.CassandraMailRepositoryMailDAO;
import org.apache.james.mailrepository.cassandra.CassandraMailRepositoryUrlModule;
import org.apache.james.mailrepository.cassandra.CassandraMailRepositoryUrlStore;
import org.apache.james.utils.MailRepositoryProvider;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;

public class CassandraMailRepositoryModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(CassandraMailRepositoryUrlStore.class).in(Scopes.SINGLETON);
        bind(CassandraMailRepositoryKeysDAO.class).in(Scopes.SINGLETON);
        bind(CassandraMailRepositoryCountDAO.class).in(Scopes.SINGLETON);
        bind(CassandraMailRepositoryMailDAO.class).in(Scopes.SINGLETON);

        bind(MailRepositoryUrlStore.class).to(CassandraMailRepositoryUrlStore.class);

        Multibinder<MailRepositoryProvider> multibinder = Multibinder.newSetBinder(binder(), MailRepositoryProvider.class);
        multibinder.addBinding().to(CassandraMailRepositoryProvider.class);

        Multibinder<CassandraModule> cassandraModuleBinder = Multibinder.newSetBinder(binder(), CassandraModule.class);
        cassandraModuleBinder.addBinding().toInstance(org.apache.james.mailrepository.cassandra.CassandraMailRepositoryModule.MODULE);
        cassandraModuleBinder.addBinding().toInstance(CassandraMailRepositoryUrlModule.MODULE);
    }

    public static class CassandraMailRepositoryProvider implements MailRepositoryProvider {
        private final CassandraMailRepositoryKeysDAO keysDAO;
        private final CassandraMailRepositoryCountDAO countDAO;
        private final CassandraMailRepositoryMailDAO mailDAO;
        private final ObjectStore objectStore;

        @Inject
        public CassandraMailRepositoryProvider(CassandraMailRepositoryKeysDAO keysDAO, CassandraMailRepositoryCountDAO countDAO, CassandraMailRepositoryMailDAO mailDAO, ObjectStore objectStore) {
            this.keysDAO = keysDAO;
            this.countDAO = countDAO;
            this.mailDAO = mailDAO;
            this.objectStore = objectStore;
        }

        @Override
        public String canonicalName() {
            return CassandraMailRepository.class.getCanonicalName();
        }

        @Override
        public MailRepository provide(MailRepositoryUrl url) {
            return new CassandraMailRepository(url, keysDAO, countDAO, mailDAO, objectStore);
        }
    }
}
