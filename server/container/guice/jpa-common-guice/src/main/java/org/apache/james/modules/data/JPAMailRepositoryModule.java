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

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.james.mailrepository.api.MailRepositoryUrlStore;
import org.apache.james.mailrepository.api.Protocol;
import org.apache.james.mailrepository.file.FileMailRepository;
import org.apache.james.mailrepository.jpa.JPAMailRepositoryUrlStore;
import org.apache.james.mailrepository.memory.MailRepositoryStoreConfiguration;
import org.apache.james.modules.server.MailStoreRepositoryModule;

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Scopes;

public class JPAMailRepositoryModule extends AbstractModule {
    private static final MailRepositoryStoreConfiguration.Item FILE_MAILREPOSITORY_DEFAULT_DECLARATION = new MailRepositoryStoreConfiguration.Item(
        ImmutableList.of(new Protocol("file")),
        FileMailRepository.class.getName(),
        new HierarchicalConfiguration());

    @Override
    protected void configure() {
        bind(JPAMailRepositoryUrlStore.class).in(Scopes.SINGLETON);

        bind(MailRepositoryUrlStore.class).to(JPAMailRepositoryUrlStore.class);

        bind(MailStoreRepositoryModule.DefaultItemSupplier.class).toInstance(() -> FILE_MAILREPOSITORY_DEFAULT_DECLARATION);
    }
}
