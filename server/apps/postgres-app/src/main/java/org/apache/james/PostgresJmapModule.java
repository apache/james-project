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

package org.apache.james;

import org.apache.james.backends.postgres.PostgresModule;
import org.apache.james.jmap.api.change.EmailChangeRepository;
import org.apache.james.jmap.api.change.Limit;
import org.apache.james.jmap.api.change.MailboxChangeRepository;
import org.apache.james.jmap.api.change.State;
import org.apache.james.jmap.api.upload.UploadUsageRepository;
import org.apache.james.jmap.memory.change.MemoryEmailChangeRepository;
import org.apache.james.jmap.memory.change.MemoryMailboxChangeRepository;
import org.apache.james.jmap.postgres.PostgresDataJMapAggregateModule;
import org.apache.james.jmap.postgres.change.PostgresEmailChangeModule;
import org.apache.james.jmap.postgres.change.PostgresEmailChangeRepository;
import org.apache.james.jmap.postgres.upload.PostgresUploadUsageRepository;
import org.apache.james.mailbox.AttachmentManager;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.RightManager;
import org.apache.james.mailbox.store.StoreAttachmentManager;
import org.apache.james.mailbox.store.StoreMessageIdManager;
import org.apache.james.mailbox.store.StoreRightManager;
import org.apache.james.vacation.api.NotificationRegistry;
import org.apache.james.vacation.api.VacationRepository;
import org.apache.james.vacation.api.VacationService;
import org.apache.james.vacation.memory.MemoryNotificationRegistry;
import org.apache.james.vacation.memory.MemoryVacationRepository;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Names;

public class PostgresJmapModule extends AbstractModule {

    @Override
    protected void configure() {
        Multibinder.newSetBinder(binder(), PostgresModule.class).addBinding().toInstance(PostgresDataJMapAggregateModule.MODULE);

        Multibinder.newSetBinder(binder(), PostgresModule.class).addBinding().toInstance(PostgresEmailChangeModule.MODULE);

        bind(EmailChangeRepository.class).to(PostgresEmailChangeRepository.class);
        bind(PostgresEmailChangeRepository.class).in(Scopes.SINGLETON);

        bind(MailboxChangeRepository.class).to(MemoryMailboxChangeRepository.class);
        bind(MemoryMailboxChangeRepository.class).in(Scopes.SINGLETON);

        bind(Limit.class).annotatedWith(Names.named(MemoryEmailChangeRepository.LIMIT_NAME)).toInstance(Limit.of(256));
        bind(Limit.class).annotatedWith(Names.named(MemoryMailboxChangeRepository.LIMIT_NAME)).toInstance(Limit.of(256));

        bind(UploadUsageRepository.class).to(PostgresUploadUsageRepository.class);

        bind(DefaultVacationService.class).in(Scopes.SINGLETON);
        bind(VacationService.class).to(DefaultVacationService.class);

        bind(MemoryNotificationRegistry.class).in(Scopes.SINGLETON);
        bind(NotificationRegistry.class).to(MemoryNotificationRegistry.class);

        bind(MemoryVacationRepository.class).in(Scopes.SINGLETON);
        bind(VacationRepository.class).to(MemoryVacationRepository.class);

        bind(MessageIdManager.class).to(StoreMessageIdManager.class);
        bind(AttachmentManager.class).to(StoreAttachmentManager.class);
        bind(StoreMessageIdManager.class).in(Scopes.SINGLETON);
        bind(StoreAttachmentManager.class).in(Scopes.SINGLETON);
        bind(RightManager.class).to(StoreRightManager.class);
        bind(StoreRightManager.class).in(Scopes.SINGLETON);

        bind(State.Factory.class).toInstance(State.Factory.DEFAULT);
    }
}
