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

import static org.apache.james.jmap.JMAPTestingConstants.DOMAIN;
import static org.apache.james.jmap.JMAPTestingConstants.LOCALHOST_IP;
import static org.apache.james.jmap.JMAPTestingConstants.jmapRequestSpecBuilder;
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.net.imap.IMAPClient;
import org.apache.james.CassandraExtension;
import org.apache.james.CassandraJamesServerMain;
import org.apache.james.DockerElasticSearchExtension;
import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.jmap.draft.JmapGuiceProbe;
import org.apache.james.modules.TestJMAPServerModule;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.utils.DataProbeImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.restassured.RestAssured;

class CassandraImapErrorTest {
    private static final String username = "username@" + DOMAIN;
    private static final String PASSWORD = "password";
    private final CassandraExtension cassandraExtension = new CassandraExtension();

    @RegisterExtension
    JamesServerExtension serverExtension = new JamesServerBuilder<>(JamesServerBuilder.defaultConfigurationProvider())
        .extension(new DockerElasticSearchExtension())
        .extension(cassandraExtension)
        .server(configuration -> CassandraJamesServerMain.createServer(configuration)
            .overrideWith(new TestJMAPServerModule()))
        .build();

    @BeforeEach
    void setup(GuiceJamesServer server) throws Exception {
        RestAssured.requestSpecification = jmapRequestSpecBuilder
            .setPort(server.getProbe(JmapGuiceProbe.class).getJmapPort().getValue())
            .build();
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();

        server.getProbe(DataProbeImpl.class)
            .fluent()
            .addDomain(DOMAIN)
            .addUser(username, PASSWORD);
    }

    @Test
    void causingMajorIssueDuringIMAPSessionShouldEndWithNo(GuiceJamesServer server) throws Exception {
        IMAPClient imapClient = new IMAPClient();
        try {
            imapClient.connect(LOCALHOST_IP, server.getProbe(ImapGuiceProbe.class).getImapPort());
            imapClient.login(username, PASSWORD);
            cassandraExtension.pause();

            Thread.sleep(100);

            boolean isSelected = imapClient.select("INBOX");
            assertThat(isSelected).isFalse();
            String[] replyChunks = imapClient.getReplyString().split(" ");
            assertThat(replyChunks).hasSizeGreaterThanOrEqualTo(2);
            assertThat(replyChunks[1]).isEqualTo("NO");
        } finally {
            imapClient.disconnect();
            cassandraExtension.unpause();
        }
    }

}
