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

import org.apache.james.core.healthcheck.HealthCheck;
import org.apache.james.jmap.api.access.AccessTokenRepository;
import org.apache.james.jmap.api.filtering.FilteringManagement;
import org.apache.james.jmap.api.filtering.impl.EventSourcingFilteringManagement;
import org.apache.james.jmap.api.identity.CustomIdentityDAO;
import org.apache.james.jmap.api.projections.EmailQueryView;
import org.apache.james.jmap.api.projections.MessageFastViewProjection;
import org.apache.james.jmap.api.projections.MessageFastViewProjectionHealthCheck;
import org.apache.james.jmap.api.upload.UploadRepository;
import org.apache.james.jmap.memory.access.MemoryAccessTokenRepository;
import org.apache.james.jmap.memory.identity.MemoryCustomIdentityDAO;
import org.apache.james.jmap.memory.projections.MemoryEmailQueryView;
import org.apache.james.jmap.memory.projections.MemoryMessageFastViewProjection;
import org.apache.james.jmap.memory.upload.InMemoryUploadRepository;
import org.apache.james.mailbox.extractor.TextExtractor;
import org.apache.james.mailbox.store.extractor.DefaultTextExtractor;
import org.apache.james.mailbox.store.extractor.JsoupTextExtractor;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;

public class MemoryDataJmapModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(MemoryAccessTokenRepository.class).in(Scopes.SINGLETON);
        bind(AccessTokenRepository.class).to(MemoryAccessTokenRepository.class);

        bind(InMemoryUploadRepository.class).in(Scopes.SINGLETON);
        bind(UploadRepository.class).to(InMemoryUploadRepository.class);

        bind(MemoryCustomIdentityDAO.class).in(Scopes.SINGLETON);
        bind(CustomIdentityDAO.class).to(MemoryCustomIdentityDAO.class);

        bind(EventSourcingFilteringManagement.class).in(Scopes.SINGLETON);
        bind(FilteringManagement.class).to(EventSourcingFilteringManagement.class);

        bind(DefaultTextExtractor.class).in(Scopes.SINGLETON);
        bind(TextExtractor.class).to(JsoupTextExtractor.class);

        bind(MemoryMessageFastViewProjection.class).in(Scopes.SINGLETON);
        bind(MessageFastViewProjection.class).to(MemoryMessageFastViewProjection.class);

        bind(MemoryEmailQueryView.class).in(Scopes.SINGLETON);
        bind(EmailQueryView.class).to(MemoryEmailQueryView.class);

        bind(MessageFastViewProjectionHealthCheck.class).in(Scopes.SINGLETON);
        Multibinder.newSetBinder(binder(), HealthCheck.class)
            .addBinding()
            .to(MessageFastViewProjectionHealthCheck.class);
    }
}
