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
package org.apache.james.protocols.pop3;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.util.Arrays;

import org.apache.commons.net.pop3.POP3Reply;
import org.apache.commons.net.pop3.POP3SClient;
import org.apache.james.protocols.api.Encryption;
import org.apache.james.protocols.api.Protocol;
import org.apache.james.protocols.api.ProtocolServer;
import org.apache.james.protocols.api.handler.WiringException;
import org.apache.james.protocols.api.utils.BogusSslContextFactory;
import org.apache.james.protocols.api.utils.BogusTrustManagerFactory;
import org.apache.james.protocols.api.utils.ProtocolServerUtils;
import org.apache.james.protocols.pop3.core.AbstractPassCmdHandler;
import org.apache.james.protocols.pop3.utils.MockMailbox;
import org.apache.james.protocols.pop3.utils.TestPassCmdHandler;
import org.junit.jupiter.api.Test;

public abstract class AbstractStartTlsPOP3ServerTest {

    private static final String LOCALHOST_IP = "127.0.0.1";
    private static final int RANDOM_PORT = 0;

    private POP3Protocol createProtocol(AbstractPassCmdHandler handler) throws WiringException {
        return new POP3Protocol(new POP3ProtocolHandlerChain(handler), new POP3Configuration());
    }
    
    protected POP3SClient createClient() {
        POP3SClient client = new POP3SClient(false, BogusSslContextFactory.getClientContext());
        client.setTrustManager(BogusTrustManagerFactory.getTrustManagers()[0]);
        return client;
    }
    
    protected abstract ProtocolServer createServer(Protocol protocol, InetSocketAddress address, Encryption enc);
    
    
    @Test
    public void testStartTls() throws Exception {
        InetSocketAddress address = new InetSocketAddress(LOCALHOST_IP, RANDOM_PORT);
        
        ProtocolServer server = null;
        try {
            String identifier = "id";
            TestPassCmdHandler handler = new TestPassCmdHandler();
            
            handler.add("valid", new MockMailbox(identifier));
            server = createServer(createProtocol(handler), address, Encryption.createStartTls(BogusSslContextFactory.getServerContext()));
            server.bind();
            
            POP3SClient client = createClient();
            InetSocketAddress bindedAddress = new ProtocolServerUtils(server).retrieveBindedAddress();
            client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
            
            // TODO: Make use of client.capa() once possible
            //       See NET-438
            assertThat(client.sendCommand("CAPA")).isEqualTo(POP3Reply.OK);
            client.getAdditionalReply();

            boolean startTlsCapa = Arrays.stream(client.getReplyStrings())
                .anyMatch(cap -> cap.equalsIgnoreCase("STLS"));
            assertThat(startTlsCapa).isTrue();
            
            assertThat(client.execTLS()).isTrue();
            // TODO: Reenable when commons-net 3.1.0 was released
            //       See NET-430
            //
            //assertTrue(client.logout());
            client.disconnect();
           
        } finally {
            if (server != null) {
                server.unbind();
            }
        }
        
    }
}
