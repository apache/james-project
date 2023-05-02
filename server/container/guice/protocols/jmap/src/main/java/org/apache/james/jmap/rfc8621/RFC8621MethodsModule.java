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

package org.apache.james.jmap.rfc8621;

import static org.apache.james.jmap.core.JmapRfc8621Configuration.LOCALHOST_CONFIGURATION;

import java.io.FileNotFoundException;
import java.util.List;
import java.util.Set;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.jmap.JMAPRoutes;
import org.apache.james.jmap.JMAPRoutesHandler;
import org.apache.james.jmap.Version;
import org.apache.james.jmap.api.model.TypeName;
import org.apache.james.jmap.change.EmailDeliveryTypeName$;
import org.apache.james.jmap.change.EmailSubmissionTypeName$;
import org.apache.james.jmap.change.EmailTypeName$;
import org.apache.james.jmap.change.IdentityTypeName$;
import org.apache.james.jmap.change.MailboxTypeName$;
import org.apache.james.jmap.change.ThreadTypeName$;
import org.apache.james.jmap.change.VacationResponseTypeName$;
import org.apache.james.jmap.core.JmapRfc8621Configuration;
import org.apache.james.jmap.http.AuthenticationStrategy;
import org.apache.james.jmap.http.Authenticator;
import org.apache.james.jmap.http.BasicAuthenticationStrategy;
import org.apache.james.jmap.http.JWTAuthenticationStrategy;
import org.apache.james.jmap.http.rfc8621.InjectionKeys;
import org.apache.james.jmap.mail.DefaultNamespaceFactory;
import org.apache.james.jmap.mail.NamespaceFactory;
import org.apache.james.jmap.method.CoreEchoMethod;
import org.apache.james.jmap.method.DelegateGetMethod;
import org.apache.james.jmap.method.DelegateSetMethod;
import org.apache.james.jmap.method.DelegatedAccountGetMethod;
import org.apache.james.jmap.method.DelegatedAccountSetMethod;
import org.apache.james.jmap.method.EmailChangesMethod;
import org.apache.james.jmap.method.EmailGetMethod;
import org.apache.james.jmap.method.EmailImportMethod;
import org.apache.james.jmap.method.EmailParseMethod;
import org.apache.james.jmap.method.EmailQueryMethod;
import org.apache.james.jmap.method.EmailSetMethod;
import org.apache.james.jmap.method.EmailSubmissionSetMethod;
import org.apache.james.jmap.method.IdentityGetMethod;
import org.apache.james.jmap.method.IdentitySetMethod;
import org.apache.james.jmap.method.MDNParseMethod;
import org.apache.james.jmap.method.MDNSendMethod;
import org.apache.james.jmap.method.MailboxChangesMethod;
import org.apache.james.jmap.method.MailboxGetMethod;
import org.apache.james.jmap.method.MailboxQueryMethod;
import org.apache.james.jmap.method.MailboxSetMethod;
import org.apache.james.jmap.method.Method;
import org.apache.james.jmap.method.PushSubscriptionGetMethod;
import org.apache.james.jmap.method.PushSubscriptionSetMethod;
import org.apache.james.jmap.method.QuotaChangesMethod;
import org.apache.james.jmap.method.QuotaGetMethod;
import org.apache.james.jmap.method.QuotaQueryMethod;
import org.apache.james.jmap.method.SystemZoneIdProvider;
import org.apache.james.jmap.method.ThreadChangesMethod;
import org.apache.james.jmap.method.ThreadGetMethod;
import org.apache.james.jmap.method.VacationResponseGetMethod;
import org.apache.james.jmap.method.VacationResponseSetMethod;
import org.apache.james.jmap.method.ZoneIdProvider;
import org.apache.james.jmap.pushsubscription.DefaultWebPushClient;
import org.apache.james.jmap.pushsubscription.PushClientConfiguration;
import org.apache.james.jmap.pushsubscription.WebPushClient;
import org.apache.james.jmap.routes.AttachmentBlobResolver;
import org.apache.james.jmap.routes.BlobResolver;
import org.apache.james.jmap.routes.DownloadRoutes;
import org.apache.james.jmap.routes.EventSourceRoutes;
import org.apache.james.jmap.routes.JMAPApiRoutes;
import org.apache.james.jmap.routes.MessageBlobResolver;
import org.apache.james.jmap.routes.MessagePartBlobResolver;
import org.apache.james.jmap.routes.SessionRoutes;
import org.apache.james.jmap.routes.UploadResolver;
import org.apache.james.jmap.routes.UploadRoutes;
import org.apache.james.jmap.routes.WebSocketRoutes;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.utils.ClassName;
import org.apache.james.utils.GuiceGenericLoader;
import org.apache.james.utils.InitializationOperation;
import org.apache.james.utils.InitilizationOperationBuilder;
import org.apache.james.utils.NamingScheme;
import org.apache.james.utils.PackageName;
import org.apache.james.utils.PropertiesProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.google.inject.name.Named;

public class RFC8621MethodsModule extends AbstractModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(RFC8621MethodsModule.class);
    private static PackageName IMPLICIT_AUTHENTICATION_STRATEGY_FQDN_PREFIX = PackageName.of("org.apache.james.jmap.http");
    private static List<String> DEFAULT_AUTHENTICATION_STRATEGIES = ImmutableList.of(
        BasicAuthenticationStrategy.class.getSimpleName(),
        JWTAuthenticationStrategy.class.getSimpleName());

    @Override
    protected void configure() {
        bind(NamespaceFactory.class).to(DefaultNamespaceFactory.class);
        bind(ZoneIdProvider.class).to(SystemZoneIdProvider.class);

        bind(EmailSubmissionSetMethod.class).in(Scopes.SINGLETON);
        bind(MDNSendMethod.class).in(Scopes.SINGLETON);

        bind(DefaultWebPushClient.class).in(Scopes.SINGLETON);
        bind(WebPushClient.class).to(DefaultWebPushClient.class);

        Multibinder<Method> methods = Multibinder.newSetBinder(binder(), Method.class);
        methods.addBinding().to(CoreEchoMethod.class);
        methods.addBinding().to(EmailChangesMethod.class);
        methods.addBinding().to(EmailImportMethod.class);
        methods.addBinding().to(EmailGetMethod.class);
        methods.addBinding().to(EmailQueryMethod.class);
        methods.addBinding().to(EmailParseMethod.class);
        methods.addBinding().to(EmailSetMethod.class);
        methods.addBinding().to(EmailSubmissionSetMethod.class);
        methods.addBinding().to(IdentityGetMethod.class);
        methods.addBinding().to(IdentitySetMethod.class);
        methods.addBinding().to(MailboxChangesMethod.class);
        methods.addBinding().to(MailboxGetMethod.class);
        methods.addBinding().to(MailboxQueryMethod.class);
        methods.addBinding().to(MailboxSetMethod.class);
        methods.addBinding().to(MDNParseMethod.class);
        methods.addBinding().to(MDNSendMethod.class);
        methods.addBinding().to(PushSubscriptionGetMethod.class);
        methods.addBinding().to(PushSubscriptionSetMethod.class);
        methods.addBinding().to(QuotaChangesMethod.class);
        methods.addBinding().to(QuotaGetMethod.class);
        methods.addBinding().to(QuotaQueryMethod.class);
        methods.addBinding().to(ThreadChangesMethod.class);
        methods.addBinding().to(ThreadGetMethod.class);
        methods.addBinding().to(VacationResponseGetMethod.class);
        methods.addBinding().to(VacationResponseSetMethod.class);
        methods.addBinding().to(DelegatedAccountGetMethod.class);
        methods.addBinding().to(DelegateGetMethod.class);
        methods.addBinding().to(DelegateSetMethod.class);
        methods.addBinding().to(DelegatedAccountSetMethod.class);

        Multibinder<JMAPRoutes> routes = Multibinder.newSetBinder(binder(), JMAPRoutes.class);
        routes.addBinding().to(SessionRoutes.class);
        routes.addBinding().to(JMAPApiRoutes.class);
        routes.addBinding().to(DownloadRoutes.class);
        routes.addBinding().to(UploadRoutes.class);
        routes.addBinding().to(WebSocketRoutes.class);
        routes.addBinding().to(EventSourceRoutes.class);

        Multibinder<AuthenticationStrategy> authenticationStrategies = Multibinder.newSetBinder(binder(), AuthenticationStrategy.class);
        authenticationStrategies.addBinding().to(BasicAuthenticationStrategy.class);
        authenticationStrategies.addBinding().to(JWTAuthenticationStrategy.class);

        Multibinder<TypeName> typeNameMultibinder = Multibinder.newSetBinder(binder(), TypeName.class);
        typeNameMultibinder.addBinding().toInstance(MailboxTypeName$.MODULE$);
        typeNameMultibinder.addBinding().toInstance(EmailTypeName$.MODULE$);
        typeNameMultibinder.addBinding().toInstance(ThreadTypeName$.MODULE$);
        typeNameMultibinder.addBinding().toInstance(IdentityTypeName$.MODULE$);
        typeNameMultibinder.addBinding().toInstance(EmailSubmissionTypeName$.MODULE$);
        typeNameMultibinder.addBinding().toInstance(EmailDeliveryTypeName$.MODULE$);
        typeNameMultibinder.addBinding().toInstance(VacationResponseTypeName$.MODULE$);

        Multibinder<BlobResolver> blobResolverMultibinder = Multibinder.newSetBinder(binder(), BlobResolver.class);
        blobResolverMultibinder.addBinding().to(MessageBlobResolver.class);
        blobResolverMultibinder.addBinding().to(UploadResolver.class);
        blobResolverMultibinder.addBinding().to(MessagePartBlobResolver.class);
        blobResolverMultibinder.addBinding().to(AttachmentBlobResolver.class);
    }

    @ProvidesIntoSet
    JMAPRoutesHandler routesHandler(Set<JMAPRoutes> routes) {
        return new JMAPRoutesHandler(Version.RFC8621, routes);
    }

    @Provides
    @Singleton
    @Named(InjectionKeys.RFC_8621)
    Authenticator provideAuthenticator(MetricFactory metricFactory,
                                       @Named("jmapRFC8621AuthenticationStrategies") Set<AuthenticationStrategy> authenticationStrategies) {

        return Authenticator.of(
            metricFactory,
            authenticationStrategies);
    }

    @Provides
    @Singleton
    @Named("jmapRFC8621AuthenticationStrategies")
    public Set<AuthenticationStrategy> provideAuthenticationStrategies(GuiceGenericLoader loader,
                                                                       JmapRfc8621Configuration configuration) {
        return configuration.getAuthenticationStrategiesAsJava()
            .orElse(DEFAULT_AUTHENTICATION_STRATEGIES)
            .stream()
            .map(ClassName::new)
            .map(Throwing.function(loader.<AuthenticationStrategy>withNamingSheme(
                new NamingScheme.OptionalPackagePrefix(IMPLICIT_AUTHENTICATION_STRATEGY_FQDN_PREFIX))::instantiate))
            .collect(ImmutableSet.toImmutableSet());
    }

    @Provides
    @Singleton
    PushClientConfiguration providePushClientConfiguration(JmapRfc8621Configuration configuration) {
        return configuration.webPushConfiguration();
    }

    @Provides
    @Singleton
    JmapRfc8621Configuration provideConfiguration(PropertiesProvider propertiesProvider) throws ConfigurationException {
        try {
            Configuration configuration = propertiesProvider.getConfiguration("jmap");
            return JmapRfc8621Configuration.from(configuration);
        } catch (FileNotFoundException e) {
            LOGGER.warn("Could not find JMAP configuration file [jmap.properties]. JMAP server will be enabled with default value.");
            return LOCALHOST_CONFIGURATION();
        }
    }

    @ProvidesIntoSet
    InitializationOperation initSubmissions(EmailSubmissionSetMethod instance) {
        return InitilizationOperationBuilder
                .forClass(EmailSubmissionSetMethod.class)
                .init(instance::init);
    }

    @ProvidesIntoSet
    InitializationOperation initMDNSends(MDNSendMethod instance) {
        return InitilizationOperationBuilder
            .forClass(MDNSendMethod.class)
            .init(instance::init);
    }
}
