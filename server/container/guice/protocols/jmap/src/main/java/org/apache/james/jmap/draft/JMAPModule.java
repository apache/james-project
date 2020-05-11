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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.io.FileUtils;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.jmap.JMAPConfiguration;
import org.apache.james.jmap.JMAPServer;
import org.apache.james.jmap.Version;
import org.apache.james.jmap.draft.methods.RequestHandler;
import org.apache.james.jmap.draft.send.PostDequeueDecoratorFactory;
import org.apache.james.jmap.draft.utils.JsoupHtmlTextExtractor;
import org.apache.james.jmap.event.PropagateLookupRightListener;
import org.apache.james.jmap.mailet.VacationMailet;
import org.apache.james.jmap.mailet.filter.JMAPFiltering;
import org.apache.james.jmap.rfc8621.RFC8621MethodsModule;
import org.apache.james.jwt.JwtConfiguration;
import org.apache.james.lifecycle.api.StartUpCheck;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxManager.SearchCapabilities;
import org.apache.james.mailbox.events.MailboxListener;
import org.apache.james.modules.server.CamelMailetContainerModule;
import org.apache.james.queue.api.MailQueueItemDecoratorFactory;
import org.apache.james.server.core.configuration.FileConfigurationProvider;
import org.apache.james.transport.matchers.RecipientIsLocal;
import org.apache.james.util.Port;
import org.apache.james.util.html.HtmlTextExtractor;
import org.apache.james.utils.PropertiesProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.github.steveash.guavate.Guavate;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;

public class JMAPModule extends AbstractModule {
    private static final int DEFAULT_JMAP_PORT = 80;
    private static final Logger LOGGER = LoggerFactory.getLogger(JMAPModule.class);
    public static final CamelMailetContainerModule.DefaultProcessorsConfigurationSupplier DEFAULT_JMAP_PROCESSORS_CONFIGURATION_SUPPLIER =
        () -> {
            try {
                return FileConfigurationProvider.getConfig(ClassLoader.getSystemResourceAsStream("defaultJmapMailetContainer.xml"));
            } catch (ConfigurationException e) {
                throw new RuntimeException(e);
            }
        };
    public static final CamelMailetContainerModule.TransportProcessorCheck VACATION_MAILET_CHECK =
        new CamelMailetContainerModule.TransportProcessorCheck.Impl(
            RecipientIsLocal.class,
            VacationMailet.class);
    public static final CamelMailetContainerModule.TransportProcessorCheck FILTERING_MAILET_CHECK =
        new CamelMailetContainerModule.TransportProcessorCheck.Impl(
            RecipientIsLocal.class,
            JMAPFiltering.class);

    @Override
    protected void configure() {
        install(new JMAPCommonModule());
        install(new DraftMethodsModule());
        install(new RFC8621MethodsModule());
        install(binder -> binder
            .bind(CamelMailetContainerModule.DefaultProcessorsConfigurationSupplier.class)
            .toInstance(DEFAULT_JMAP_PROCESSORS_CONFIGURATION_SUPPLIER));

        bind(JMAPServer.class).in(Scopes.SINGLETON);
        bind(RequestHandler.class).in(Scopes.SINGLETON);
        bind(JsoupHtmlTextExtractor.class).in(Scopes.SINGLETON);

        bind(HtmlTextExtractor.class).to(JsoupHtmlTextExtractor.class);
        Multibinder.newSetBinder(binder(), StartUpCheck.class).addBinding().to(RequiredCapabilitiesStartUpCheck.class);

        Multibinder<CamelMailetContainerModule.TransportProcessorCheck> transportProcessorChecks = Multibinder.newSetBinder(binder(), CamelMailetContainerModule.TransportProcessorCheck.class);
        transportProcessorChecks.addBinding().toInstance(VACATION_MAILET_CHECK);
        transportProcessorChecks.addBinding().toInstance(FILTERING_MAILET_CHECK);

        bind(MailQueueItemDecoratorFactory.class).to(PostDequeueDecoratorFactory.class).in(Scopes.SINGLETON);

        Multibinder.newSetBinder(binder(), MailboxListener.GroupMailboxListener.class).addBinding().to(PropagateLookupRightListener.class);

        Multibinder<Version> supportedVersions = Multibinder.newSetBinder(binder(), Version.class);
        supportedVersions.addBinding().toInstance(Version.DRAFT);
        supportedVersions.addBinding().toInstance(Version.RFC8621);
    }

    @Provides
    @Singleton
    JMAPConfiguration provideConfiguration(PropertiesProvider propertiesProvider) throws ConfigurationException, IOException {
        try {
            Configuration configuration = propertiesProvider.getConfiguration("jmap");
            return JMAPConfiguration.builder()
                .enabled(configuration.getBoolean("enabled", true))
                .port(Port.of(configuration.getInt("jmap.port", DEFAULT_JMAP_PORT)))
                .build();
        } catch (FileNotFoundException e) {
            LOGGER.warn("Could not find JMAP configuration file. JMAP server will not be enabled.");
            return JMAPConfiguration.builder()
                .disable()
                .build();
        }
    }

    @Provides
    @Singleton
    JMAPDraftConfiguration provideDraftConfiguration(PropertiesProvider propertiesProvider, FileSystem fileSystem) throws ConfigurationException, IOException {
        try {
            Configuration configuration = propertiesProvider.getConfiguration("jmap");
            return JMAPDraftConfiguration.builder()
                .enabled(configuration.getBoolean("enabled", true))
                .keystore(configuration.getString("tls.keystoreURL"))
                .secret(configuration.getString("tls.secret"))
                .jwtPublicKeyPem(loadPublicKey(fileSystem, Optional.ofNullable(configuration.getString("jwt.publickeypem.url"))))
                .build();
        } catch (FileNotFoundException e) {
            LOGGER.warn("Could not find JMAP configuration file. JMAP server will not be enabled.");
            return JMAPDraftConfiguration.builder()
                .disable()
                .build();
        }
    }

    @Provides
    @Singleton
    JwtConfiguration providesJwtConfiguration(JMAPDraftConfiguration jmapConfiguration) {
        return new JwtConfiguration(jmapConfiguration.getJwtPublicKeyPem());
    }

    private Optional<String> loadPublicKey(FileSystem fileSystem, Optional<String> jwtPublickeyPemUrl) {
        return jwtPublickeyPemUrl.map(Throwing.function(url -> FileUtils.readFileToString(fileSystem.getFile(url), StandardCharsets.US_ASCII)));
    }

    @Singleton
    public static class RequiredCapabilitiesStartUpCheck implements StartUpCheck {

        public static final String CHECK_NAME = "MailboxCapabilitiesForJMAP";

        private final MailboxManager mailboxManager;

        @Inject
        public RequiredCapabilitiesStartUpCheck(MailboxManager mailboxManager) {
            this.mailboxManager = mailboxManager;
        }

        @Override
        public CheckResult check() {
            EnumSet<MailboxManager.MessageCapabilities> messageCapabilities = mailboxManager.getSupportedMessageCapabilities();
            EnumSet<SearchCapabilities> searchCapabilities = mailboxManager.getSupportedSearchCapabilities();

            ImmutableList<String> badCheckDescriptions = Stream.of(
                    badCheckDescription(mailboxManager.hasCapability(MailboxManager.MailboxCapabilities.Move),
                        "MOVE support in MailboxManager is required by JMAP Module"),
                    badCheckDescription(mailboxManager.hasCapability(MailboxManager.MailboxCapabilities.ACL),
                        "ACL support in MailboxManager is required by JMAP Module"),
                    badCheckDescription(messageCapabilities.contains(MailboxManager.MessageCapabilities.UniqueID),
                        "MessageIdManager is not defined by this Mailbox implementation"),
                    badCheckDescription(searchCapabilities.contains(SearchCapabilities.MultimailboxSearch),
                        "Multimailbox search in MailboxManager is required by JMAP Module"),
                    badCheckDescription(searchCapabilities.contains(SearchCapabilities.Attachment),
                        "Attachment Search support in MailboxManager is required by JMAP Module"),
                    badCheckDescription(searchCapabilities.contains(SearchCapabilities.AttachmentFileName),
                    "Attachment file name Search support in MailboxManager is required by JMAP Module"))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Guavate.toImmutableList());

            if (!badCheckDescriptions.isEmpty()) {
                return CheckResult.builder()
                    .checkName(checkName())
                    .resultType(ResultType.BAD)
                    .description(Joiner.on(",").join(badCheckDescriptions))
                    .build();
            }
            return CheckResult.builder()
                .checkName(checkName())
                .resultType(ResultType.GOOD)
                .build();
        }

        private Optional<String> badCheckDescription(boolean expressionResult, String expressionFailsDescription) {
            if (expressionResult) {
                return Optional.empty();
            }
            return Optional.ofNullable(expressionFailsDescription);
        }

        @Override
        public String checkName() {
            return CHECK_NAME;
        }
    }
}
