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

package org.apache.james.managesieveserver.netty;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import jakarta.inject.Inject;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.mailbox.Authenticator;
import org.apache.james.mailbox.Authorizator;
import org.apache.james.managesieve.core.CoreProcessor;
import org.apache.james.managesieve.jsieve.Parser;
import org.apache.james.managesieve.transcode.ArgumentParser;
import org.apache.james.managesieve.transcode.ManageSieveProcessor;
import org.apache.james.protocols.api.sasl.SaslMechanism;
import org.apache.james.protocols.api.sasl.SaslMechanismFactory;
import org.apache.james.protocols.lib.netty.AbstractConfigurableAsyncServer;
import org.apache.james.protocols.lib.netty.AbstractServerFactory;
import org.apache.james.protocols.netty.Encryption;
import org.apache.james.protocols.sasl.BuiltInSaslMechanismFactories;
import org.apache.james.protocols.sasl.JamesSaslAuthenticator;
import org.apache.james.protocols.sasl.OauthBearerSaslMechanismFactory;
import org.apache.james.protocols.sasl.PlainSaslMechanismFactory;
import org.apache.james.protocols.sasl.XOauth2SaslMechanismFactory;
import org.apache.james.sieverepository.api.SieveRepository;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;

public class ManageSieveServerFactory extends AbstractServerFactory {
    @FunctionalInterface
    public interface ManageSieveSaslMechanismLoader {
        static ManageSieveSaslMechanismLoader defaultLoader() {
            ImmutableList<SaslMechanismFactory> defaultFactories = ImmutableList.of(
                new PlainSaslMechanismFactory(false),
                new OauthBearerSaslMechanismFactory(),
                new XOauth2SaslMechanismFactory());
            return configuration -> loadBuiltInMechanisms(defaultFactories, normalizeSaslConfiguration(configuration));
        }

        ImmutableList<SaslMechanism> load(HierarchicalConfiguration<ImmutableNode> configuration) throws ConfigurationException;
    }

    private static final Authorizator DENY_DELEGATION = (authenticationId, authorizationId) -> Authorizator.AuthorizationState.FORBIDDEN;

    private static ImmutableList<SaslMechanism> loadBuiltInMechanisms(ImmutableList<SaslMechanismFactory> defaultFactories,
                                                                      HierarchicalConfiguration<ImmutableNode> configuration) throws ConfigurationException {
        ImmutableList<SaslMechanismFactory> enabledFactories = BuiltInSaslMechanismFactories.enabledForServer(defaultFactories, configuration);
        ImmutableList<String> configuredFactories = retrieveSaslMechanismFactoryClassNames(configuration);
        ImmutableList<SaslMechanismFactory> selectedFactories = configuredFactories.isEmpty()
            ? enabledFactories
            : configuredFactories.stream()
                .map(className -> findBuiltInFactory(className, enabledFactories))
                .collect(ImmutableList.toImmutableList());
        try {
            return selectedFactories.stream()
                .map(Throwing.function(factory -> factory.create(configuration)))
                .collect(ImmutableList.toImmutableList());
        } catch (RuntimeException e) {
            if (e.getCause() instanceof ConfigurationException configurationException) {
                throw configurationException;
            }
            throw e;
        }
    }

    private static SaslMechanismFactory findBuiltInFactory(String className,
                                                           ImmutableList<SaslMechanismFactory> enabledFactories) {
        return enabledFactories.stream()
            .filter(factory -> className.equals(factory.getClass().getCanonicalName())
                || (!className.contains(".") && className.equals(factory.getClass().getSimpleName())))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unsupported SASL mechanism factory: " + className));
    }

    public static ImmutableList<String> retrieveSaslMechanismFactoryClassNames(HierarchicalConfiguration<ImmutableNode> configuration) throws ConfigurationException {
        if (!configuration.containsKey("auth.saslMechanisms")) {
            return ImmutableList.of();
        }
        ImmutableList<String> factoryClassNames = Arrays.stream(configuration.getStringArray("auth.saslMechanisms"))
            .flatMap(value -> Arrays.stream(value.split(",")))
            .map(String::trim)
            .collect(ImmutableList.toImmutableList());
        if (factoryClassNames.isEmpty() || factoryClassNames.stream().anyMatch(String::isBlank)) {
            throw new ConfigurationException("auth.saslMechanisms must not be blank when configured");
        }
        return factoryClassNames;
    }

    private FileSystem fileSystem;
    private Encryption.Factory encryptionFactory;
    private SieveRepository sieveRepository;
    private Authenticator authenticator;
    private Parser sieveParser;
    private ManageSieveSaslMechanismLoader saslMechanismLoader = ManageSieveSaslMechanismLoader.defaultLoader();

    public ManageSieveServerFactory() {
    }

    @Inject
    public ManageSieveServerFactory(ManageSieveSaslMechanismLoader saslMechanismLoader) {
        this.saslMechanismLoader = saslMechanismLoader;
    }

    @Inject
    public void setFileSystem(FileSystem fileSystem) {
        this.fileSystem = fileSystem;
    }

    @Inject
    public void setEncryptionFactory(Encryption.Factory encryptionFactory) {
        this.encryptionFactory = encryptionFactory;
    }

    @Inject
    public void setSieveRepository(SieveRepository sieveRepository) {
        this.sieveRepository = sieveRepository;
    }

    @Inject
    public void setAuthenticator(Authenticator authenticator) {
        this.authenticator = authenticator;
    }

    @Inject
    public void setParser(Parser sieveParser) {
        this.sieveParser = sieveParser;
    }

    @Override
    protected List<AbstractConfigurableAsyncServer> createServers(HierarchicalConfiguration<ImmutableNode> config) throws Exception {
        List<AbstractConfigurableAsyncServer> servers = new ArrayList<>();
        List<HierarchicalConfiguration<ImmutableNode>> configs = config.configurationsAt("managesieveserver");

        for (HierarchicalConfiguration<ImmutableNode> serverConfig : configs) {
            HierarchicalConfiguration<ImmutableNode> saslConfiguration = normalizeSaslConfiguration(serverConfig);
            ImmutableList<SaslMechanism> saslMechanisms = saslMechanismLoader.load(saslConfiguration);
            ManageSieveProcessor processor = new ManageSieveProcessor(
                new ArgumentParser(new CoreProcessor(sieveRepository, sieveParser, saslMechanisms)),
                saslMechanisms,
                new JamesSaslAuthenticator(authenticator, DENY_DELEGATION));
            ManageSieveServer server = new ManageSieveServer(8000, processor);
            server.setFileSystem(fileSystem);
            server.setEncryptionFactory(encryptionFactory);
            server.configure(serverConfig);
            servers.add(server);
        }
        return servers;
    }

    static HierarchicalConfiguration<ImmutableNode> normalizeSaslConfiguration(HierarchicalConfiguration<ImmutableNode> serverConfiguration) throws ConfigurationException {
        if (!serverConfiguration.immutableConfigurationsAt("auth.oidc").isEmpty()) {
            if (!serverConfiguration.immutableConfigurationsAt("oidc").isEmpty()) {
                throw new ConfigurationException("Configure OIDC only once using auth.oidc");
            }
            return serverConfiguration;
        }
        if (serverConfiguration.immutableConfigurationsAt("oidc").isEmpty()) {
            return serverConfiguration;
        }

        BaseHierarchicalConfiguration normalized = new BaseHierarchicalConfiguration();
        copyProperties(serverConfiguration, normalized, "");
        copyProperties(serverConfiguration.configurationAt("oidc"), normalized, "auth.oidc.");
        return normalized;
    }

    private static void copyProperties(HierarchicalConfiguration<ImmutableNode> source,
                                       BaseHierarchicalConfiguration target,
                                       String prefix) {
        Iterator<String> keys = source.getKeys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (!prefix.isEmpty() || !key.startsWith("oidc.")) {
                target.setProperty(prefix + key, source.getProperty(key));
            }
        }
    }
}
