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

package org.apache.james.smtpserver.netty;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import jakarta.inject.Inject;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.core.ConnectionDescription;
import org.apache.james.core.ConnectionDescriptionSupplier;
import org.apache.james.core.Disconnector;
import org.apache.james.core.Username;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.protocols.api.sasl.SaslAuthenticator;
import org.apache.james.protocols.api.sasl.SaslMechanism;
import org.apache.james.protocols.api.sasl.SaslMechanismFactory;
import org.apache.james.protocols.lib.handler.ProtocolHandlerLoader;
import org.apache.james.protocols.lib.netty.AbstractConfigurableAsyncServer;
import org.apache.james.protocols.lib.netty.AbstractServerFactory;
import org.apache.james.protocols.netty.Encryption;
import org.apache.james.protocols.sasl.BuiltInSaslMechanismFactories;
import org.apache.james.protocols.sasl.OauthBearerSaslMechanismFactory;
import org.apache.james.protocols.sasl.PlainSaslMechanismFactory;
import org.apache.james.protocols.sasl.XOauth2SaslMechanismFactory;
import org.apache.james.smtpserver.netty.SMTPServer.AuthAnnouncementConfiguration;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;

public class SMTPServerFactory extends AbstractServerFactory implements Disconnector, ConnectionDescriptionSupplier {
    @FunctionalInterface
    public interface SmtpSaslMechanismLoader {
        static SmtpSaslMechanismLoader defaultLoader() {
            ImmutableList<SaslMechanismFactory> defaultFactories = ImmutableList.of(
                // SMTP historically used auth.requireSSL for capability announcement only: PLAIN/LOGIN remain accepted
                // when sent explicitly by clients, even over clear-text test/configuration ports.
                new PlainSaslMechanismFactory(AuthAnnouncementConfiguration.REQUIRE_SSL_DEFAULT,
                    PlainSaslMechanismFactory.IGNORE_REQUIRE_SSL_CONFIGURATION),
                new OauthBearerSaslMechanismFactory(),
                new XOauth2SaslMechanismFactory());
            return configuration -> loadBuiltInMechanisms(defaultFactories, configuration);
        }

        ImmutableList<SaslMechanism> load(HierarchicalConfiguration<ImmutableNode> configuration) throws ConfigurationException;
    }

    private static ImmutableList<SaslMechanism> loadBuiltInMechanisms(ImmutableList<SaslMechanismFactory> defaultFactories,
                                                                       HierarchicalConfiguration<ImmutableNode> configuration) throws ConfigurationException {
        try {
            return BuiltInSaslMechanismFactories.enabledForServer(defaultFactories, configuration)
                .stream()
                .map(Throwing.function(factory -> factory.create(configuration)))
                .collect(ImmutableList.toImmutableList());
        } catch (RuntimeException e) {
            if (e.getCause() instanceof ConfigurationException configurationException) {
                throw configurationException;
            }
            throw e;
        }
    }

    protected final DNSService dns;
    protected final ProtocolHandlerLoader loader;
    protected final FileSystem fileSystem;
    protected final SmtpMetricsImpl smtpMetrics;
    protected final SmtpSaslMechanismLoader saslMechanismLoader;
    protected final Optional<SaslAuthenticator> saslAuthenticator;
    protected Encryption.Factory encryptionFactory;

    public SMTPServerFactory(DNSService dns, ProtocolHandlerLoader loader, FileSystem fileSystem,
                             MetricFactory metricFactory, SmtpSaslMechanismLoader saslMechanismLoader, SaslAuthenticator saslAuthenticator) {
        this(dns, loader, fileSystem, metricFactory, saslMechanismLoader, Optional.of(saslAuthenticator));
    }

    private SMTPServerFactory(DNSService dns, ProtocolHandlerLoader loader, FileSystem fileSystem,
                              MetricFactory metricFactory, SmtpSaslMechanismLoader saslMechanismLoader,
                              Optional<SaslAuthenticator> saslAuthenticator) {
        this.dns = dns;
        this.loader = loader;
        this.fileSystem = fileSystem;
        this.smtpMetrics = new SmtpMetricsImpl(metricFactory);
        this.saslMechanismLoader = saslMechanismLoader;
        this.saslAuthenticator = saslAuthenticator;
    }

    @Inject
    public void setEncryptionFactory(Encryption.Factory encryptionFactory) {
        this.encryptionFactory = encryptionFactory;
    }

    protected SMTPServer createServer() {
       return new SMTPServer(smtpMetrics);
    }
    
    @Override
    protected List<AbstractConfigurableAsyncServer> createServers(HierarchicalConfiguration<ImmutableNode> config) throws Exception {
        
        List<AbstractConfigurableAsyncServer> servers = new ArrayList<>();
        List<HierarchicalConfiguration<ImmutableNode>> configs = config.configurationsAt("smtpserver");
        
        for (HierarchicalConfiguration<ImmutableNode> serverConfig: configs) {
            SMTPServer server = createServer();
            server.setDnsService(dns);
            server.setProtocolHandlerLoader(loader);
            server.setFileSystem(fileSystem);
            server.setEncryptionFactory(encryptionFactory);
            server.setSaslMechanisms(saslMechanismLoader.load(serverConfig));
            server.setSaslAuthenticator(saslAuthenticator);
            server.configure(serverConfig);
            servers.add(server);
        }

        return servers;
    }

    @Override
    public void disconnect(Predicate<Username> username) {
        getServers()
            .stream()
            .map(server -> (SMTPServer) server)
            .forEach(smtpServer -> smtpServer.disconnect(username));
    }

    @Override
    public Stream<ConnectionDescription> describeConnections() {
        return getServers()
            .stream()
            .map(server -> (SMTPServer) server)
            .flatMap(SMTPServer::describeConnections);
    }
}
