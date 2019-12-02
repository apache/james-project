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

import org.apache.james.jmap.api.access.AccessTokenRepository;
import org.apache.james.jmap.api.filtering.FilteringManagement;
import org.apache.james.jmap.api.filtering.impl.EventSourcingFilteringManagement;
import org.apache.james.jmap.api.projections.MessageFastViewProjection;
import org.apache.james.jmap.api.vacation.NotificationRegistry;
import org.apache.james.jmap.api.vacation.VacationRepository;
import org.apache.james.jmap.memory.access.MemoryAccessTokenRepository;
import org.apache.james.jmap.memory.projections.MemoryMessageFastViewProjection;
import org.apache.james.jmap.memory.vacation.MemoryNotificationRegistry;
import org.apache.james.jmap.memory.vacation.MemoryVacationRepository;
import org.apache.james.mailbox.extractor.TextExtractor;
import org.apache.james.mailbox.store.extractor.DefaultTextExtractor;
import org.apache.james.mailbox.store.extractor.JsoupTextExtractor;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;

public class MemoryDataJmapModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(MemoryAccessTokenRepository.class).in(Scopes.SINGLETON);
        bind(AccessTokenRepository.class).to(MemoryAccessTokenRepository.class);

        bind(MemoryVacationRepository.class).in(Scopes.SINGLETON);
        bind(VacationRepository.class).to(MemoryVacationRepository.class);

        bind(MemoryNotificationRegistry.class).in(Scopes.SINGLETON);
        bind(NotificationRegistry.class).to(MemoryNotificationRegistry.class);

        bind(EventSourcingFilteringManagement.class).in(Scopes.SINGLETON);
        bind(FilteringManagement.class).to(EventSourcingFilteringManagement.class);

        bind(DefaultTextExtractor.class).in(Scopes.SINGLETON);
        bind(TextExtractor.class).to(JsoupTextExtractor.class);

        bind(MemoryMessageFastViewProjection.class).in(Scopes.SINGLETON);
        bind(MessageFastViewProjection.class).to(MemoryMessageFastViewProjection.class);
    }
}
