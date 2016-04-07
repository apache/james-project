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
import org.apache.james.jmap.api.ContinuationTokenManager;
import org.apache.james.jmap.api.access.AccessTokenRepository;
import org.apache.james.jmap.crypto.AccessTokenManagerImpl;
import org.apache.james.jmap.crypto.JamesSignatureHandler;
import org.apache.james.jmap.crypto.SignatureHandler;
import org.apache.james.jmap.crypto.SignedContinuationTokenManager;
import org.apache.james.jmap.send.MailFactory;
import org.apache.james.jmap.send.MailSpool;
import org.apache.james.jmap.utils.DefaultZonedDateTimeProvider;
import org.apache.james.jmap.utils.ZonedDateTimeProvider;

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Names;

public class JMAPCommonModule extends AbstractModule {
    
    private static final long DEFAULT_TOKEN_EXPIRATION_IN_MS = TimeUnit.MILLISECONDS.convert(15, TimeUnit.MINUTES);

    @Override
    protected void configure() {
        bind(SignatureHandler.class).to(JamesSignatureHandler.class);
        bind(ZonedDateTimeProvider.class).to(DefaultZonedDateTimeProvider.class);
        bind(ContinuationTokenManager.class).to(SignedContinuationTokenManager.class);

        bindConstant().annotatedWith(Names.named(AccessTokenRepository.TOKEN_EXPIRATION_IN_MS)).to(DEFAULT_TOKEN_EXPIRATION_IN_MS);
        bind(AccessTokenManager.class).to(AccessTokenManagerImpl.class);

        bind(MailSpool.class).in(Singleton.class);
        bind(MailFactory.class).in(Singleton.class);
    }

    @Provides
    public List<AuthenticationStrategy> authStrategies(
            AccessTokenAuthenticationStrategy accessTokenAuthenticationStrategy,
            JWTAuthenticationStrategy jwtAuthenticationStrategy) {

        return ImmutableList.of(
                jwtAuthenticationStrategy,
                accessTokenAuthenticationStrategy);
    }
}
