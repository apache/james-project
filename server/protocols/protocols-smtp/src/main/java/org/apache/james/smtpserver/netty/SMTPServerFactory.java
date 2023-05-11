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

import javax.inject.Inject;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.protocols.lib.handler.ProtocolHandlerLoader;
import org.apache.james.protocols.lib.netty.AbstractConfigurableAsyncServer;
import org.apache.james.protocols.lib.netty.AbstractServerFactory;

public class SMTPServerFactory extends AbstractServerFactory {

    protected final DNSService dns;
    protected final ProtocolHandlerLoader loader;
    protected final FileSystem fileSystem;
    protected final SmtpMetricsImpl smtpMetrics;

    @Inject
    public SMTPServerFactory(DNSService dns, ProtocolHandlerLoader loader, FileSystem fileSystem,
                             MetricFactory metricFactory) {
        this.dns = dns;
        this.loader = loader;
        this.fileSystem = fileSystem;
        this.smtpMetrics = new SmtpMetricsImpl(metricFactory);
    }

    
    @Override
    protected List<AbstractConfigurableAsyncServer> createServers(HierarchicalConfiguration<ImmutableNode> config) throws Exception {
        
        List<AbstractConfigurableAsyncServer> servers = new ArrayList<>();
        List<HierarchicalConfiguration<ImmutableNode>> configs = config.configurationsAt("smtpserver");
        
        for (HierarchicalConfiguration<ImmutableNode> serverConfig: configs) {
            var server = new SMTPServer(smtpMetrics, dns, loader, fileSystem);
            server.configure(serverConfig);
            servers.add(server);
        }

        return servers;
    }

}
