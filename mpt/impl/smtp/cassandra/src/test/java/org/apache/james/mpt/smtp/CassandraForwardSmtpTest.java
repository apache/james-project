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

import static org.apache.james.modules.protocols.SmtpGuiceProbe.SmtpServerConnectedType.SMTP_GLOBAL_SERVER;

import org.apache.james.CassandraExtension;
import org.apache.james.CassandraJamesServerMain;
import org.apache.james.DockerOpenSearchExtension;
import org.apache.james.JamesServerExtension;
import org.apache.james.SearchConfiguration;
import org.apache.james.TestingDistributedJamesServerBuilder;
import org.apache.james.backends.cassandra.DockerCassandra;
import org.apache.james.backends.cassandra.init.configuration.ClusterConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.extension.RegisterExtension;

public class CassandraForwardSmtpTest implements ForwardSmtpTest {


    private static final CassandraExtension cassandraExtension = new CassandraExtension();

    @BeforeAll
    static void setUp() {
        Thread.currentThread().setContextClassLoader(CassandraForwardSmtpTest.class.getClassLoader());
    }

    @Order(1)
    @RegisterExtension
    static JamesServerExtension testExtension = TestingDistributedJamesServerBuilder.withSearchConfiguration(SearchConfiguration.openSearch())
        .extension(new DockerOpenSearchExtension())
        .extension(cassandraExtension)
        .extension(new InMemoryDnsExtension())
        .server(CassandraJamesServerMain::createServer)
        .overrideServerModule(binder -> binder.bind(ClusterConfiguration.class)
            .toInstance(DockerCassandra.configurationBuilder(cassandraExtension.getCassandra().getHost())
                .username(DockerCassandra.CASSANDRA_TESTING_USER)
                .password(DockerCassandra.CASSANDRA_TESTING_PASSWORD)
                .build()))
        .build();


    @Order(2)
    @RegisterExtension
    static SmtpTestExtension smtpTestExtension = new SmtpTestExtension(SMTP_GLOBAL_SERVER, testExtension);


}
