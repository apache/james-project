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
package org.apache.james.jmap.draft;

import java.util.concurrent.TimeUnit;

import org.apache.james.events.EventListener;
import org.apache.james.jmap.api.access.AccessTokenRepository;
import org.apache.james.jmap.draft.api.AccessTokenManager;
import org.apache.james.jmap.draft.api.SimpleTokenFactory;
import org.apache.james.jmap.draft.api.SimpleTokenManager;
import org.apache.james.jmap.draft.crypto.AccessTokenManagerImpl;
import org.apache.james.jmap.draft.crypto.JamesSignatureHandler;
import org.apache.james.jmap.draft.crypto.SecurityKeyLoader;
import org.apache.james.jmap.draft.crypto.SignatureHandler;
import org.apache.james.jmap.draft.crypto.SignedTokenFactory;
import org.apache.james.jmap.draft.crypto.SignedTokenManager;
import org.apache.james.jmap.draft.model.MailboxFactory;
import org.apache.james.jmap.draft.model.message.view.MessageFastViewFactory;
import org.apache.james.jmap.draft.model.message.view.MessageFullViewFactory;
import org.apache.james.jmap.draft.model.message.view.MessageHeaderViewFactory;
import org.apache.james.jmap.draft.model.message.view.MessageMetadataViewFactory;
import org.apache.james.jmap.draft.send.MailSpool;
import org.apache.james.jmap.event.ComputeMessageFastViewProjectionListener;
import org.apache.james.lifecycle.api.ConfigurationSanitizer;
import org.apache.james.lifecycle.api.StartUpCheck;
import org.apache.james.util.date.DefaultZonedDateTimeProvider;
import org.apache.james.util.date.ZonedDateTimeProvider;
import org.apache.james.util.mime.MessageContentExtractor;
import org.apache.james.utils.InitializationOperation;
import org.apache.james.utils.InitilizationOperationBuilder;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.google.inject.name.Names;

public class JMAPCommonModule extends AbstractModule {
    
    private static final long DEFAULT_TOKEN_EXPIRATION_IN_MS = TimeUnit.MILLISECONDS.convert(15, TimeUnit.MINUTES);

    @Override
    protected void configure() {
        bind(JamesSignatureHandler.class).in(Scopes.SINGLETON);
        bind(DefaultZonedDateTimeProvider.class).in(Scopes.SINGLETON);
        bind(SignedTokenManager.class).in(Scopes.SINGLETON);
        bind(AccessTokenManagerImpl.class).in(Scopes.SINGLETON);
        bind(MailSpool.class).in(Scopes.SINGLETON);
        bind(MailboxFactory.class).in(Scopes.SINGLETON);

        bind(MessageFullViewFactory.class).in(Scopes.SINGLETON);
        bind(MessageMetadataViewFactory.class).in(Scopes.SINGLETON);
        bind(MessageHeaderViewFactory.class).in(Scopes.SINGLETON);
        bind(MessageFastViewFactory.class).in(Scopes.SINGLETON);

        bind(MessageContentExtractor.class).in(Scopes.SINGLETON);
        bind(SecurityKeyLoader.class).in(Scopes.SINGLETON);

        bind(SignatureHandler.class).to(JamesSignatureHandler.class);
        bind(ZonedDateTimeProvider.class).to(DefaultZonedDateTimeProvider.class);
        bind(SimpleTokenManager.class).to(SignedTokenManager.class);
        bind(SimpleTokenFactory.class).to(SignedTokenFactory.class);

        bindConstant().annotatedWith(Names.named(AccessTokenRepository.TOKEN_EXPIRATION_IN_MS)).to(DEFAULT_TOKEN_EXPIRATION_IN_MS);
        bind(AccessTokenManager.class).to(AccessTokenManagerImpl.class);

        Multibinder.newSetBinder(binder(), EventListener.ReactiveGroupEventListener.class)
            .addBinding()
            .to(ComputeMessageFastViewProjectionListener.class);

        Multibinder.newSetBinder(binder(), StartUpCheck.class)
            .addBinding().to(JMAPConfigurationStartUpCheck.class);

        Multibinder.newSetBinder(binder(), ConfigurationSanitizer.class)
            .addBinding().to(JmapConfigurationSanitizer.class);
    }

    @ProvidesIntoSet
    InitializationOperation workQueue(MailSpool instance) {
        return InitilizationOperationBuilder
            .forClass(MailSpool.class)
            .init(instance::start);
    }
}
