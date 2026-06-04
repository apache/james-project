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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.commons.net.imap.IMAPSClient;
import org.apache.james.protocols.api.utils.BogusSslContextFactory;
import org.apache.james.protocols.api.utils.BogusTrustManagerFactory;
import org.apache.james.protocols.lib.mock.ConfigLoader;
import org.apache.james.util.ClassLoaderUtils;
import org.apache.james.util.concurrency.ConcurrentTestRunner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


@SuppressWarnings("checkstyle:membername")
class IMAPServerSSLConcurrentConnectionsTest extends AbstractIMAPServerTest {
    IMAPServer imapServer;
    int port;

    @BeforeEach
    void setup() throws Exception {
        HierarchicalConfiguration<ImmutableNode> config = ConfigLoader.getConfig(ClassLoaderUtils.getSystemResourceAsSharedStream("imapSSL.xml"));
        imapServer = createImapServer(config);
        port = imapServer.getListenAddresses().get(0).getPort();
    }

    @AfterEach
    void tearDown() {
        if (imapServer != null) {
            imapServer.destroy();
        }
    }

    @Test
    void shouldSupportManyConcurrentSSLConnections() throws Exception {
        //Failed for 3.7.0, this serves as a non regression test
        ConcurrentTestRunner.builder()
            .operation((a, b) -> {
                IMAPSClient imapsClient = imapsImplicitClient(port);
                boolean capability = imapsClient.capability();
                assertThat(capability).isTrue();
                imapsClient.close();
            })
            .threadCount(10)
            .operationCount(200)
            .runSuccessfullyWithin(Duration.ofMinutes(10));
    }

    private IMAPSClient imapsImplicitClient(int port) throws Exception {
        IMAPSClient client = new IMAPSClient(true, BogusSslContextFactory.getClientContext());
        client.setTrustManager(BogusTrustManagerFactory.getTrustManagers()[0]);
        client.connect("127.0.0.1", port);
        return client;
    }
}
