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

import java.util.List;
import java.util.Set;

import org.apache.james.jmap.draft.methods.GetFilterMethod;
import org.apache.james.jmap.draft.methods.GetMailboxesMethod;
import org.apache.james.jmap.draft.methods.GetMessageListMethod;
import org.apache.james.jmap.draft.methods.GetMessagesMethod;
import org.apache.james.jmap.draft.methods.GetVacationResponseMethod;
import org.apache.james.jmap.draft.methods.JmapRequestParser;
import org.apache.james.jmap.draft.methods.JmapRequestParserImpl;
import org.apache.james.jmap.draft.methods.SendMDNProcessor;
import org.apache.james.jmap.draft.methods.SetFilterMethod;
import org.apache.james.jmap.draft.methods.SetMailboxesCreationProcessor;
import org.apache.james.jmap.draft.methods.SetMailboxesDestructionProcessor;
import org.apache.james.jmap.draft.methods.SetMailboxesMethod;
import org.apache.james.jmap.draft.methods.SetMailboxesProcessor;
import org.apache.james.jmap.draft.methods.SetMailboxesUpdateProcessor;
import org.apache.james.jmap.draft.methods.SetMessagesCreationProcessor;
import org.apache.james.jmap.draft.methods.SetMessagesDestructionProcessor;
import org.apache.james.jmap.draft.methods.SetMessagesMethod;
import org.apache.james.jmap.draft.methods.SetMessagesProcessor;
import org.apache.james.jmap.draft.methods.SetMessagesUpdateProcessor;
import org.apache.james.jmap.draft.methods.SetVacationResponseMethod;
import org.apache.james.jmap.http.AccessTokenAuthenticationStrategy;
import org.apache.james.jmap.http.AuthenticationStrategy;
import org.apache.james.jmap.http.Authenticator;
import org.apache.james.jmap.http.InjectionKeys;
import org.apache.james.jmap.http.JWTAuthenticationStrategy;
import org.apache.james.jmap.http.QueryParameterAccessTokenAuthenticationStrategy;
import org.apache.james.jmap.methods.Method;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.utils.ClassName;
import org.apache.james.utils.GuiceGenericLoader;
import org.apache.james.utils.NamingScheme;
import org.apache.james.utils.PackageName;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Named;
import com.google.inject.name.Names;

public class DraftMethodsModule extends AbstractModule {
    private static PackageName IMPLICIT_AUTHENTICATION_STRATEGY_FQDN_PREFIX = PackageName.of("org.apache.james.jmap.http");
    private static List<String> DEFAULT_AUTHENTICATION_STRATEGIES = ImmutableList.of(AccessTokenAuthenticationStrategy.class.getSimpleName(),
        JWTAuthenticationStrategy.class.getSimpleName(),
        QueryParameterAccessTokenAuthenticationStrategy.class.getSimpleName());

    @Override
    protected void configure() {
        bind(JmapRequestParserImpl.class).in(Scopes.SINGLETON);
        bind(JmapRequestParser.class).to(JmapRequestParserImpl.class);

        bindConstant().annotatedWith(Names.named(GetMessageListMethod.MAXIMUM_LIMIT)).to(GetMessageListMethod.DEFAULT_MAXIMUM_LIMIT);

        Multibinder<Method> methods = Multibinder.newSetBinder(binder(), Method.class);
        methods.addBinding().to(GetMailboxesMethod.class);
        methods.addBinding().to(GetMessageListMethod.class);
        methods.addBinding().to(GetMessagesMethod.class);
        methods.addBinding().to(SetMessagesMethod.class);
        methods.addBinding().to(SetMailboxesMethod.class);
        methods.addBinding().to(GetVacationResponseMethod.class);
        methods.addBinding().to(SetVacationResponseMethod.class);
        methods.addBinding().to(GetFilterMethod.class);
        methods.addBinding().to(SetFilterMethod.class);

        Multibinder<SetMailboxesProcessor> setMailboxesProcessor =
            Multibinder.newSetBinder(binder(), SetMailboxesProcessor.class);
        setMailboxesProcessor.addBinding().to(SetMailboxesCreationProcessor.class);
        setMailboxesProcessor.addBinding().to(SetMailboxesUpdateProcessor.class);
        setMailboxesProcessor.addBinding().to(SetMailboxesDestructionProcessor.class);

        Multibinder<SetMessagesProcessor> setMessagesProcessors =
                Multibinder.newSetBinder(binder(), SetMessagesProcessor.class);
        setMessagesProcessors.addBinding().to(SetMessagesUpdateProcessor.class);
        setMessagesProcessors.addBinding().to(SetMessagesCreationProcessor.class);
        setMessagesProcessors.addBinding().to(SetMessagesDestructionProcessor.class);
        setMessagesProcessors.addBinding().to(SendMDNProcessor.class);
    }

    @Provides
    @Named(InjectionKeys.DRAFT)
    Authenticator provideAuthenticator(MetricFactory metricFactory,
                                       @Named("draftJmapAuthenticationStrategies") Set<AuthenticationStrategy> strategies) {
        return Authenticator.of(metricFactory, strategies);
    }

    @Provides
    @Singleton
    @Named("draftJmapAuthenticationStrategies")
    public Set<AuthenticationStrategy> provideAuthenticationStrategies(GuiceGenericLoader loader,
                                                                       JMAPDraftConfiguration configuration) {
        return configuration.getAuthenticationStrategies()
            .orElse(DEFAULT_AUTHENTICATION_STRATEGIES)
            .stream()
            .map(ClassName::new)
            .map(Throwing.function(loader.<AuthenticationStrategy>withNamingSheme(
                new NamingScheme.OptionalPackagePrefix(IMPLICIT_AUTHENTICATION_STRATEGY_FQDN_PREFIX))::instantiate))
            .collect(ImmutableSet.toImmutableSet());
    }
}
