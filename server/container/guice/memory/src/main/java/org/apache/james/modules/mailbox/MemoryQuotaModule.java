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

import org.apache.james.events.EventListener;
import org.apache.james.jmap.api.upload.UploadUsageRepository;
import org.apache.james.jmap.memory.upload.InMemoryUploadUsageRepository;
import org.apache.james.mailbox.inmemory.quota.InMemoryCurrentQuotaManager;
import org.apache.james.mailbox.inmemory.quota.InMemoryPerUserMaxQuotaManager;
import org.apache.james.mailbox.quota.CurrentQuotaManager;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.QuotaRootDeserializer;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.mailbox.quota.UserQuotaRootResolver;
import org.apache.james.mailbox.store.quota.DefaultUserQuotaRootResolver;
import org.apache.james.mailbox.store.quota.ListeningCurrentQuotaUpdater;
import org.apache.james.mailbox.store.quota.QuotaUpdater;
import org.apache.james.mailbox.store.quota.StoreQuotaManager;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;

public class MemoryQuotaModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(DefaultUserQuotaRootResolver.class).in(Scopes.SINGLETON);
        bind(InMemoryPerUserMaxQuotaManager.class).in(Scopes.SINGLETON);
        bind(StoreQuotaManager.class).in(Scopes.SINGLETON);
        bind(InMemoryCurrentQuotaManager.class).in(Scopes.SINGLETON);

        bind(UserQuotaRootResolver.class).to(DefaultUserQuotaRootResolver.class);
        bind(QuotaRootResolver.class).to(DefaultUserQuotaRootResolver.class);
        bind(QuotaRootDeserializer.class).to(DefaultUserQuotaRootResolver.class);
        bind(MaxQuotaManager.class).to(InMemoryPerUserMaxQuotaManager.class);
        bind(QuotaManager.class).to(StoreQuotaManager.class);
        bind(CurrentQuotaManager.class).to(InMemoryCurrentQuotaManager.class);
        bind(UploadUsageRepository.class).to(InMemoryUploadUsageRepository.class);

        bind(ListeningCurrentQuotaUpdater.class).in(Scopes.SINGLETON);
        bind(QuotaUpdater.class).to(ListeningCurrentQuotaUpdater.class);
        Multibinder.newSetBinder(binder(), EventListener.ReactiveGroupEventListener.class)
            .addBinding()
            .to(ListeningCurrentQuotaUpdater.class);
    }
}
