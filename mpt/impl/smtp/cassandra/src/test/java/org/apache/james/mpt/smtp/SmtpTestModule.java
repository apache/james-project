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
package org.apache.james.mpt.smtp;

import java.io.IOException;
import java.util.Optional;

import org.apache.james.CassandraJamesServerMain;
import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.init.CassandraModuleComposite;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.cassandra.CassandraDomainListModule;
import org.apache.james.jmap.JMAPConfiguration;
import org.apache.james.jmap.PortConfiguration;
import org.apache.james.mailbox.cassandra.modules.CassandraMailboxModule;
import org.apache.james.mailbox.cassandra.modules.CassandraMessageModule;
import org.apache.james.mailbox.elasticsearch.ClientProvider;
import org.apache.james.mailbox.elasticsearch.EmbeddedElasticSearch;
import org.apache.james.modules.TestFilesystemModule;
import org.apache.james.mpt.api.SmtpHostSystem;
import org.apache.james.mpt.smtp.dns.InMemoryDNSService;
import org.apache.james.mpt.smtp.host.JamesSmtpHostSystem;
import org.apache.james.rrt.cassandra.CassandraRRTModule;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.cassandra.CassandraUsersRepositoryModule;
import org.apache.james.utils.ConfigurationsPerformer;
import org.junit.rules.TemporaryFolder;

import com.datastax.driver.core.Session;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.util.Modules;

public class SmtpTestModule extends AbstractModule {

    private final TemporaryFolder folder = new TemporaryFolder();
    private final CassandraCluster cassandraClusterSingleton;
    private final EmbeddedElasticSearch embeddedElasticSearch;

    public SmtpTestModule() throws IOException {
        folder.create();
        CassandraModuleComposite cassandraModuleComposite = new CassandraModuleComposite(
                new CassandraMailboxModule(),
                new CassandraMessageModule(),
                new CassandraDomainListModule(),
                new CassandraUsersRepositoryModule(),
                new CassandraRRTModule());
        cassandraClusterSingleton = CassandraCluster.create(cassandraModuleComposite);

        embeddedElasticSearch = new EmbeddedElasticSearch();
    }

    @Override
    protected void configure() {
        Module cassandra = (binder) -> binder.bind(Session.class).toInstance(cassandraClusterSingleton.getConf());
        Module dns = (binder) -> {
            binder.bind(InMemoryDNSService.class).in(Scopes.SINGLETON);
            binder.bind(DNSService.class).to(InMemoryDNSService.class);
        };
        Module elasticSearch = (binder) -> binder.bind(ClientProvider.class).toInstance(() -> embeddedElasticSearch.getNode().client());
        Module jmap = (binder) -> {
            binder.bind(PortConfiguration.class).toInstance(Optional::empty);
            binder.bind(JMAPConfiguration.class).toInstance(
                    JMAPConfiguration.builder()
                    .keystore("keystore")
                    .secret("james72laBalle")
                    .build());
        };
        
        install(Modules.override(CassandraJamesServerMain.defaultModule)
            .with(Modules.combine(
                new TestFilesystemModule(folder),
                cassandra,
                dns,
                elasticSearch,
                jmap)));
    }


    @Provides
    @Singleton
    public SmtpHostSystem provideHostSystem(ConfigurationsPerformer configurationsPerformer, DomainList domainList, UsersRepository usersRepository) throws Exception {
        return new JamesSmtpHostSystem(configurationsPerformer, domainList, usersRepository);
    }
}
