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
package org.apache.james.jmap;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.james.jmap.api.AccessTokenManager;
import org.apache.james.jmap.api.SimpleTokenFactory;
import org.apache.james.jmap.api.SimpleTokenManager;
import org.apache.james.jmap.api.access.AccessTokenRepository;
import org.apache.james.jmap.crypto.AccessTokenManagerImpl;
import org.apache.james.jmap.crypto.JamesSignatureHandler;
import org.apache.james.jmap.crypto.SecurityKeyLoader;
import org.apache.james.jmap.crypto.SignatureHandler;
import org.apache.james.jmap.crypto.SignedTokenFactory;
import org.apache.james.jmap.crypto.SignedTokenManager;
import org.apache.james.jmap.model.MailboxFactory;
import org.apache.james.jmap.model.MessageFactory;
import org.apache.james.jmap.model.MessagePreviewGenerator;
import org.apache.james.jmap.send.MailSpool;
import org.apache.james.jmap.utils.HeadersAuthenticationExtractor;
import org.apache.james.util.date.DefaultZonedDateTimeProvider;
import org.apache.james.util.date.ZonedDateTimeProvider;
import org.apache.james.util.mime.MessageContentExtractor;
import org.apache.mailet.base.AutomaticallySentMailDetector;
import org.apache.mailet.base.AutomaticallySentMailDetectorImpl;

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
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
        bind(AutomaticallySentMailDetectorImpl.class).in(Scopes.SINGLETON);
        bind(MailboxFactory.class).in(Scopes.SINGLETON);
        bind(MessageFactory.class).in(Scopes.SINGLETON);
        bind(MessagePreviewGenerator.class).in(Scopes.SINGLETON);
        bind(MessageContentExtractor.class).in(Scopes.SINGLETON);
        bind(HeadersAuthenticationExtractor.class).in(Scopes.SINGLETON);
        bind(SecurityKeyLoader.class).in(Scopes.SINGLETON);

        bind(SignatureHandler.class).to(JamesSignatureHandler.class);
        bind(ZonedDateTimeProvider.class).to(DefaultZonedDateTimeProvider.class);
        bind(SimpleTokenManager.class).to(SignedTokenManager.class);
        bind(SimpleTokenFactory.class).to(SignedTokenFactory.class);
        bind(AutomaticallySentMailDetector.class).to(AutomaticallySentMailDetectorImpl.class);

        bindConstant().annotatedWith(Names.named(AccessTokenRepository.TOKEN_EXPIRATION_IN_MS)).to(DEFAULT_TOKEN_EXPIRATION_IN_MS);
        bind(AccessTokenManager.class).to(AccessTokenManagerImpl.class);
    }

    @Provides
    public List<AuthenticationStrategy> authStrategies(
            AccessTokenAuthenticationStrategy accessTokenAuthenticationStrategy,
            JWTAuthenticationStrategy jwtAuthenticationStrategy,
            QueryParameterAccessTokenAuthenticationStrategy queryParameterAuthenticationStrategy) {

        return ImmutableList.of(
                jwtAuthenticationStrategy,
                accessTokenAuthenticationStrategy,
                queryParameterAuthenticationStrategy);
    }
}
