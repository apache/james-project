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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Optional;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.io.FileUtils;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.jmap.methods.RequestHandler;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.store.mail.model.MailboxId;
import org.apache.james.utils.ConfigurationPerformer;
import org.apache.james.utils.ConfigurationProvider;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.Preconditions;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;

public class JMAPModule<Id extends MailboxId> extends AbstractModule {
    private static final int DEFAULT_JMAP_PORT = 80;
    private final TypeLiteral<Id> type;

    public JMAPModule(TypeLiteral<Id> type) {
        this.type = type;
    }

    @Override
    protected void configure() {
        install(new JMAPCommonModule());
        install(new MethodsModule<Id>(type));
        bind(RequestHandler.class).in(Singleton.class);
        Multibinder<ConfigurationPerformer> preconditions = Multibinder.newSetBinder(binder(), ConfigurationPerformer.class);
        preconditions.addBinding().to(MailetConfigurationPrecondition.class);
        preconditions.addBinding().to(MoveCapabilityPrecondition.class);
    }

    @Provides
    @Singleton
    JMAPConfiguration provideConfiguration(FileSystem fileSystem) throws ConfigurationException, IOException{
        PropertiesConfiguration configuration = getConfiguration(fileSystem);
        return JMAPConfiguration.builder()
                .keystore(configuration.getString("tls.keystoreURL"))
                .secret(configuration.getString("tls.secret"))
                .jwtPublicKeyPem(loadPublicKey(fileSystem, Optional.ofNullable(configuration.getString("jwt.publickeypem.url"))))
                .port(configuration.getInt("jmap.port", DEFAULT_JMAP_PORT))
                .build();
    }

    private PropertiesConfiguration getConfiguration(FileSystem fileSystem) throws FileNotFoundException, ConfigurationException {
        return new PropertiesConfiguration(fileSystem.getFile(FileSystem.FILE_PROTOCOL_AND_CONF + "jmap.properties"));
    }

    private Optional<String> loadPublicKey(FileSystem fileSystem, Optional<String> jwtPublickeyPemUrl) {
        return jwtPublickeyPemUrl.map(Throwing.function(url -> FileUtils.readFileToString(fileSystem.getFile(url))));
    }

    @Singleton
    public static class MailetConfigurationPrecondition implements ConfigurationPerformer {

        private final ConfigurationProvider configurationProvider;

        @Inject
        public MailetConfigurationPrecondition(ConfigurationProvider configurationProvider) {
            this.configurationProvider = configurationProvider;
        }

        @Override
        public void initModule() throws Exception {
            Optional<HierarchicalConfiguration> removeMimeHeaderMailet = configurationProvider.getConfiguration("mailetcontainer")
                .configurationAt("processors")
                .configurationsAt("processor")
                .stream()
                .filter(processor -> processor.getString("[@state]").equals("transport"))
                .flatMap(transport -> transport.configurationsAt("mailet").stream())
                .filter(mailet -> mailet.getString("[@class]").equals("RemoveMimeHeader"))
                .filter(mailet -> mailet.getString("[@match]").equals("All"))
                .filter(mailet -> mailet.getProperty("name").equals("bcc"))
                .findAny();
            if (!removeMimeHeaderMailet.isPresent()) {
                throw new ConfigurationException("Missing RemoveMimeHeader in mailets configuration (mailetcontainer -> processors -> transport). Should be configured to remove Bcc header");
            }
        }
    }

    @Singleton
    public static class MoveCapabilityPrecondition implements ConfigurationPerformer {

        private final MailboxManager mailboxManager;

        @Inject
        public MoveCapabilityPrecondition(MailboxManager mailboxManager) {
            this.mailboxManager = mailboxManager;
        }

        @Override
        public void initModule() throws Exception {
            Preconditions.checkArgument(mailboxManager.getSupportedCapabilities().contains(MailboxManager.Capabilities.Move),
                    "MOVE support in MailboxManager is required by JMAP Module");
        }
    }
}
