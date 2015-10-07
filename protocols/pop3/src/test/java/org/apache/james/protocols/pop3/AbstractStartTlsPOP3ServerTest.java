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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.net.InetSocketAddress;

import org.apache.commons.net.pop3.POP3Reply;
import org.apache.commons.net.pop3.POP3SClient;
import org.apache.james.protocols.api.Encryption;
import org.apache.james.protocols.api.Protocol;
import org.apache.james.protocols.api.ProtocolServer;
import org.apache.james.protocols.api.handler.WiringException;
import org.apache.james.protocols.api.utils.BogusSslContextFactory;
import org.apache.james.protocols.api.utils.BogusTrustManagerFactory;
import org.apache.james.protocols.api.utils.MockLogger;
import org.apache.james.protocols.api.utils.TestUtils;
import org.apache.james.protocols.pop3.core.AbstractPassCmdHandler;
import org.apache.james.protocols.pop3.utils.MockMailbox;
import org.apache.james.protocols.pop3.utils.TestPassCmdHandler;
import org.junit.Test;

public abstract class AbstractStartTlsPOP3ServerTest {

    private POP3Protocol createProtocol(AbstractPassCmdHandler handler) throws WiringException {
        return new POP3Protocol(new POP3ProtocolHandlerChain(handler), new POP3Configuration(), new MockLogger());
    }
    
    protected POP3SClient createClient() {
        POP3SClient client = new POP3SClient(false, BogusSslContextFactory.getClientContext());
        client.setTrustManager(BogusTrustManagerFactory.getTrustManagers()[0]);
        return client;
    }
    
    protected abstract ProtocolServer createServer(Protocol protocol, InetSocketAddress address, Encryption enc);
    
    
    @Test
    public void testStartTls() throws Exception {
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", TestUtils.getFreePort());
        
        ProtocolServer server = null;
        try {
            String identifier = "id";
            TestPassCmdHandler handler = new TestPassCmdHandler();
            
            handler.add("valid", new MockMailbox(identifier));
            server = createServer(createProtocol(handler), address, Encryption.createStartTls(BogusSslContextFactory.getServerContext()));
            server.bind();
            
            POP3SClient client = createClient();
            client.connect(address.getAddress().getHostAddress(), address.getPort());
            
            // TODO: Make use of client.capa() once possible
            //       See NET-438
            assertEquals(POP3Reply.OK, client.sendCommand("CAPA"));
            client.getAdditionalReply();

            boolean startTlsCapa = false;
            for (String cap: client.getReplyStrings()) {
                if (cap.equalsIgnoreCase("STLS")) {
                    startTlsCapa = true;
                    break;
                }
            }
            assertTrue(startTlsCapa);
            
            assertTrue(client.execTLS());
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
