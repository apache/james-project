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
import org.apache.james.GuiceJamesServerImpl;
import org.apache.james.backends.cassandra.EmbeddedCassandra;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.mailbox.elasticsearch.EmbeddedElasticSearch;
import org.apache.james.modules.CassandraJmapServerModule;
import org.apache.james.mpt.monitor.SystemLoggingMonitor;
import org.apache.james.mpt.session.ExternalSessionFactory;
import org.apache.james.mpt.smtp.SmtpHostSystem;
import org.apache.james.mpt.smtp.dns.InMemoryDNSService;
import org.junit.rules.TemporaryFolder;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;

public class CassandraJamesSmtpHostSystem extends ExternalSessionFactory implements SmtpHostSystem {

    private TemporaryFolder folder;
    private EmbeddedCassandra embeddedCassandra;
    private EmbeddedElasticSearch embeddedElasticSearch;

    private GuiceJamesServerImpl jamesServer;
    private InMemoryDNSService inMemoryDNSService;


    public CassandraJamesSmtpHostSystem() {
        super("localhost", 1025, new SystemLoggingMonitor(), "220 mydomain.tld smtp");
    }

    @Override
    public boolean addUser(String userAtDomain, String password) throws Exception {
        Preconditions.checkArgument(userAtDomain.contains("@"), "The 'user' should contain the 'domain'");
        Iterator<String> split = Splitter.on("@").split(userAtDomain).iterator();
        split.next();
        String domain = split.next();

        createDomainIfNeeded(domain);
        jamesServer.serverProbe().addUser(userAtDomain, password);
        return true;
    }

    private void createDomainIfNeeded(String domain) throws Exception {
        if (!jamesServer.serverProbe().containsDomain(domain)) {
            jamesServer.serverProbe().addDomain(domain);
        }
    }

    @Override
    public void addAddressMapping(String user, String domain, String address) throws Exception {
        jamesServer.serverProbe().addAddressMapping(user, domain, address);
    }

    @Override
    public void beforeTests() throws Exception {
    }

    @Override
    public void afterTests() throws Exception {
    }

    @Override
    public void beforeTest() throws Exception {
        inMemoryDNSService = new InMemoryDNSService();
        folder = new TemporaryFolder();
        folder.create();
        embeddedElasticSearch = new EmbeddedElasticSearch(folder.getRoot().toPath());
        embeddedElasticSearch.before();
        embeddedCassandra = EmbeddedCassandra.createStartServer();
        jamesServer = createJamesServer();
        jamesServer.start();
    }

    @Override
    public void afterTest() throws Exception {
        jamesServer.stop();
        embeddedElasticSearch.after();
        folder.delete();
    }

    public InMemoryDNSService getInMemoryDnsService() {
        return inMemoryDNSService;
    }

    protected GuiceJamesServerImpl createJamesServer() {
        return new GuiceJamesServerImpl()
            .combineWith(CassandraJamesServerMain.cassandraServerModule)
            .overrideWith(new CassandraJmapServerModule(folder::getRoot, embeddedElasticSearch, embeddedCassandra),
                (binder) -> binder.bind(DNSService.class).toInstance(inMemoryDNSService));
    }
}
