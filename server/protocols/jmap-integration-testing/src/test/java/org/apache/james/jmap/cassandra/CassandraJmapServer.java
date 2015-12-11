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

package org.apache.james.jmap.cassandra;

import javax.inject.Singleton;

import org.apache.james.CassandraJamesServer;
import org.apache.james.CassandraJamesServerMain;
import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.EmbeddedCassandra;
import org.apache.james.jmap.JmapServer;
import org.apache.james.mailbox.elasticsearch.EmbeddedElasticSearch;
import org.apache.james.modules.TestElasticSearchModule;
import org.apache.james.modules.TestFilesystemModule;
import org.apache.james.modules.TestJMAPServerModule;
import org.apache.james.utils.ExtendedServerProbe;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.util.Modules;

public class CassandraJmapServer implements JmapServer {

    private static final int LIMIT_TO_3_MESSAGES = 3;

    private CassandraJamesServer server;

    private final Module module;

    public static Module defaultOverrideModule(TemporaryFolder temporaryFolder, EmbeddedElasticSearch embeddedElasticSearch, EmbeddedCassandra cassandra) {
        return Modules.combine(new TestElasticSearchModule(embeddedElasticSearch),
                new TestFilesystemModule(temporaryFolder),
                new TestJMAPServerModule(LIMIT_TO_3_MESSAGES),
                new AbstractModule() {

            @Override
            protected void configure() {
                bind(EmbeddedCassandra.class).toInstance(cassandra);
            }

            @Provides
            @Singleton
            com.datastax.driver.core.Session provideSession(CassandraCluster initializedCassandra) {
                return initializedCassandra.getConf();
            }
        });
    }

    
    public CassandraJmapServer(Module overrideModule) {
        this.module = Modules.override(CassandraJamesServerMain.defaultModule).with(overrideModule);
    }
    
    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            
            @Override
            public void evaluate() throws Throwable {
                try {
                    before();
                    base.evaluate();
                } finally {
                    after();
                }
            }
        };
    }

    private void before() throws Throwable {
        server = new CassandraJamesServer(module);
        server.start();
    }

    private void after() {
        server.stop();
    }

    @Override
    public int getPort() {
        return server.getJmapPort();
    }

    @Override
    public ExtendedServerProbe serverProbe() {
        return server.serverProbe();
    }
}
