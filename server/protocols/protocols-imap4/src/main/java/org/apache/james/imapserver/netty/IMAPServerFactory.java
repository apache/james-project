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
package org.apache.james.imapserver.netty;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

import jakarta.inject.Inject;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.core.ConnectionDescription;
import org.apache.james.core.ConnectionDescriptionSupplier;
import org.apache.james.core.Disconnector;
import org.apache.james.core.Username;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.imap.ImapSuite;
import org.apache.james.imap.api.ConnectionCheckFactory;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.decode.ImapDecoder;
import org.apache.james.imap.encode.ImapEncoder;
import org.apache.james.metrics.api.GaugeRegistry;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.protocols.lib.netty.AbstractConfigurableAsyncServer;
import org.apache.james.protocols.lib.netty.AbstractServerFactory;

import com.github.fge.lambdas.functions.ThrowingFunction;

public class IMAPServerFactory extends AbstractServerFactory implements Disconnector, ConnectionDescriptionSupplier {

    protected final FileSystem fileSystem;
    protected final ThrowingFunction<HierarchicalConfiguration<ImmutableNode>, ImapSuite> imapSuiteProvider;
    protected final ImapMetrics imapMetrics;
    protected final GaugeRegistry gaugeRegistry;
    protected final ConnectionCheckFactory connectionCheckFactory;

    @Inject
    @Deprecated
    public IMAPServerFactory(FileSystem fileSystem, ImapDecoder decoder, ImapEncoder encoder, ImapProcessor processor,
                             MetricFactory metricFactory, GaugeRegistry gaugeRegistry, ConnectionCheckFactory connectionCheckFactory) {
        this.fileSystem = fileSystem;
        this.connectionCheckFactory = connectionCheckFactory;
        this.imapSuiteProvider = any -> new ImapSuite(decoder, encoder, processor);
        this.imapMetrics = new ImapMetrics(metricFactory);
        this.gaugeRegistry = gaugeRegistry;
    }

    public IMAPServerFactory(FileSystem fileSystem, ThrowingFunction<HierarchicalConfiguration<ImmutableNode>, ImapSuite> imapSuiteProvider,
                             MetricFactory metricFactory, GaugeRegistry gaugeRegistry, ConnectionCheckFactory connectionCheckFactory) {
        this.fileSystem = fileSystem;
        this.imapSuiteProvider = imapSuiteProvider;
        this.imapMetrics = new ImapMetrics(metricFactory);
        this.gaugeRegistry = gaugeRegistry;
        this.connectionCheckFactory = connectionCheckFactory;
    }

    protected IMAPServer createServer(HierarchicalConfiguration<ImmutableNode> config) {
        ImapSuite imapSuite = imapSuiteProvider.apply(config);

        return new IMAPServer(imapSuite.getDecoder(), imapSuite.getEncoder(), imapSuite.getProcessor(), imapMetrics, gaugeRegistry, connectionCheckFactory.create(config));
    }

    @Override
    protected List<AbstractConfigurableAsyncServer> createServers(HierarchicalConfiguration<ImmutableNode> config) throws Exception {
        List<AbstractConfigurableAsyncServer> servers = new ArrayList<>();
        List<HierarchicalConfiguration<ImmutableNode>> configs = config.configurationsAt("imapserver");

        for (HierarchicalConfiguration<ImmutableNode> serverConfig: configs) {
            IMAPServer server = createServer(serverConfig);
            server.setFileSystem(fileSystem);
            server.configure(serverConfig);
            servers.add(server);
        }

        return servers;
    }

    public List<IMAPServer> getImapServers() {
        return getServers()
            .stream()
            .filter(AbstractConfigurableAsyncServer::isEnabled)
            .map(IMAPServer.class::cast)
            .toList();
    }

    @Override
    public void disconnect(Predicate<Username> username) {
        getServers()
            .stream()
            .map(server -> (IMAPServer) server)
            .forEach(imapServer -> imapServer.disconnect(username));
    }

    @Override
    public Stream<ConnectionDescription> describeConnections() {
        return getServers()
            .stream()
            .map(server -> (IMAPServer) server)
            .flatMap(IMAPServer::describeConnections);
    }
}
