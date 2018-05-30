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

package org.apache.james.mpt.smtp.host;

import java.util.Iterator;

import org.apache.james.CassandraJamesServerMain;
import org.apache.james.GuiceJamesServer;
import org.apache.james.backends.es.EmbeddedElasticSearch;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.dnsservice.api.InMemoryDNSService;
import org.apache.james.mailbox.elasticsearch.MailboxElasticSearchConstants;
import org.apache.james.modules.CassandraJmapServerModule;
import org.apache.james.modules.protocols.ProtocolHandlerModule;
import org.apache.james.mpt.monitor.SystemLoggingMonitor;
import org.apache.james.mpt.session.ExternalSessionFactory;
import org.apache.james.mpt.smtp.SmtpHostSystem;
import org.apache.james.server.core.configuration.Configuration;
import org.apache.james.utils.DataProbeImpl;
import org.junit.rules.TemporaryFolder;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;

public class CassandraJamesSmtpHostSystem extends ExternalSessionFactory implements SmtpHostSystem {

    private TemporaryFolder folder;
    private EmbeddedElasticSearch embeddedElasticSearch;

    private GuiceJamesServer jamesServer;
    private InMemoryDNSService inMemoryDNSService;
    private final String cassandraHost;
    private final int cassandraPort;


    public CassandraJamesSmtpHostSystem(int smtpPort, String cassandraHost, int cassandraPort) {
        super("localhost", smtpPort, new SystemLoggingMonitor(), "220 mydomain.tld smtp");
        this.cassandraHost = cassandraHost;
        this.cassandraPort = cassandraPort;
    }

    @Override
    public boolean addUser(String userAtDomain, String password) throws Exception {
        Preconditions.checkArgument(userAtDomain.contains("@"), "The 'user' should contain the 'domain'");
        Iterator<String> split = Splitter.on("@").split(userAtDomain).iterator();
        split.next();
        String domain = split.next();

        createDomainIfNeeded(domain);
        jamesServer.getProbe(DataProbeImpl.class).addUser(userAtDomain, password);
        return true;
    }

    private void createDomainIfNeeded(String domain) throws Exception {
        if (!jamesServer.getProbe(DataProbeImpl.class).containsDomain(domain)) {
            jamesServer.getProbe(DataProbeImpl.class).addDomain(domain);
        }
    }

    @Override
    public void addAddressMapping(String user, String domain, String address) throws Exception {
        jamesServer.getProbe(DataProbeImpl.class).addAddressMapping(user, domain, address);
    }

    @Override
    public void beforeTest() throws Exception {
        inMemoryDNSService = new InMemoryDNSService();
        folder = new TemporaryFolder();
        folder.create();
        embeddedElasticSearch = new EmbeddedElasticSearch(folder.getRoot().toPath(), MailboxElasticSearchConstants.DEFAULT_MAILBOX_INDEX);
        embeddedElasticSearch.before();
        jamesServer = createJamesServer();
        jamesServer.start();
    }

    @Override
    public void afterTest() {
        jamesServer.stop();
        embeddedElasticSearch.after();
        folder.delete();
    }

    @Override
    public InMemoryDNSService getInMemoryDnsService() {
        return inMemoryDNSService;
    }

    protected GuiceJamesServer createJamesServer() throws Exception {
        Configuration configuration = Configuration.builder()
            .workingDirectory(folder.newFolder())
            .configurationFromClasspath()
            .build();

        return new GuiceJamesServer(configuration)
            .combineWith(CassandraJamesServerMain.CASSANDRA_SERVER_MODULE, CassandraJamesServerMain.PROTOCOLS, new ProtocolHandlerModule())
            .overrideWith(new CassandraJmapServerModule(embeddedElasticSearch, cassandraHost, cassandraPort),
                (binder) -> binder.bind(DNSService.class).toInstance(inMemoryDNSService));
    }
}
