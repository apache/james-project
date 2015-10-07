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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.net.pop3.POP3Client;
import org.apache.commons.net.pop3.POP3MessageInfo;
import org.apache.commons.net.pop3.POP3Reply;
import org.apache.james.protocols.api.Protocol;
import org.apache.james.protocols.api.ProtocolServer;
import org.apache.james.protocols.api.handler.WiringException;
import org.apache.james.protocols.api.utils.MockLogger;
import org.apache.james.protocols.api.utils.TestUtils;
import org.apache.james.protocols.pop3.core.AbstractApopCmdHandler;
import org.apache.james.protocols.pop3.core.AbstractPassCmdHandler;
import org.apache.james.protocols.pop3.mailbox.Mailbox;
import org.apache.james.protocols.pop3.utils.MockMailbox;
import org.apache.james.protocols.pop3.utils.MockMailbox.Message;
import org.apache.james.protocols.pop3.utils.TestPassCmdHandler;
import org.junit.Test;

public abstract class AbstractPOP3ServerTest {

    private static final Message MESSAGE1 = new Message("Subject: test\r\nX-Header: value\r\n", "My Body\r\n");
    private static final Message MESSAGE2 = new Message("Subject: test2\r\nX-Header: value2\r\n", "My Body with a DOT.\r\n.\r\n");

    private POP3Protocol createProtocol(AbstractPassCmdHandler handler) throws WiringException {
        return new POP3Protocol(new POP3ProtocolHandlerChain(handler), new POP3Configuration(), new MockLogger());
    }
    
    protected abstract ProtocolServer createServer(Protocol protocol, InetSocketAddress address);
    
    protected POP3Client createClient() {
        return new POP3Client();
    }
    
    @Test
    public void testInvalidAuth() throws Exception {
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", TestUtils.getFreePort());
        
        ProtocolServer server = null;
        try {
            server = createServer(createProtocol(new TestPassCmdHandler()), address);
            server.bind();
            
            POP3Client client =  createClient();
            client.connect(address.getAddress().getHostAddress(), address.getPort());
            
            assertFalse(client.login("invalid", "invalid"));
           
            assertTrue(client.logout());
           
        } finally {
            if (server != null) {
                server.unbind();
            }
        }
        
    }
    
    @Test
    public void testEmptyInbox() throws Exception {
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", TestUtils.getFreePort());
        
        ProtocolServer server = null;
        try {
            String identifier = "id";
            TestPassCmdHandler handler = new TestPassCmdHandler();
            
            handler.add("valid", new MockMailbox(identifier));
            server = createServer(createProtocol(handler), address);
            server.bind();
            
            POP3Client client =  createClient();
            client.connect(address.getAddress().getHostAddress(), address.getPort());
            
            assertTrue(client.login("valid", "valid"));
            POP3MessageInfo[] info = client.listMessages();
            assertEquals(0, info.length);
            
            info = client.listUniqueIdentifiers();
            assertEquals(0, info.length);
            assertTrue(client.logout());
           
        } finally {
            if (server != null) {
                server.unbind();
            }
        }
        
    }
    
    @Test
    public void testInboxWithMessages() throws Exception {
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", TestUtils.getFreePort());
        
        ProtocolServer server = null;
        try {
            String identifier = "id";
            TestPassCmdHandler handler = new TestPassCmdHandler();
            
            handler.add("valid", new MockMailbox(identifier, MESSAGE1, MESSAGE2));
            server = createServer(createProtocol(handler), address);
            server.bind();
            
            POP3Client client =  createClient();
            client.connect(address.getAddress().getHostAddress(), address.getPort());
            
            assertTrue(client.login("valid", "valid"));
            POP3MessageInfo[] info = client.listMessages();
            assertEquals(2, info.length);
            assertEquals((int) MESSAGE1.meta.getSize(), info[0].size);
            assertEquals((int) MESSAGE2.meta.getSize(), info[1].size);
            assertEquals(1, info[0].number);
            assertEquals(2, info[1].number);

            POP3MessageInfo mInfo = client.listMessage(1);
            assertEquals((int) MESSAGE1.meta.getSize(), mInfo.size);
            assertEquals(1, mInfo.number);

            // try to retrieve message that not exist
            mInfo = client.listMessage(10);
            assertNull(mInfo);

            info = client.listUniqueIdentifiers();
            assertEquals(2, info.length);
            assertEquals(identifier + "-" + MESSAGE1.meta.getUid(), info[0].identifier);
            assertEquals(identifier + "-" + MESSAGE2.meta.getUid(), info[1].identifier);
            assertEquals(1, info[0].number);
            assertEquals(2, info[1].number);

            mInfo = client.listUniqueIdentifier(1);
            assertEquals(identifier + "-" + MESSAGE1.meta.getUid(), mInfo.identifier);
            assertEquals(1, mInfo.number);

            // try to retrieve message that not exist
            mInfo = client.listUniqueIdentifier(10);
            assertNull(mInfo);
            
            assertTrue(client.logout());
           
        } finally {
            if (server != null) {
                server.unbind();
            }
        }
        
    }
    
    @Test
    public void testRetr() throws Exception {
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", TestUtils.getFreePort());
        
        ProtocolServer server = null;
        try {
            String identifier = "id";
            TestPassCmdHandler factory = new TestPassCmdHandler();
            
            factory.add("valid", new MockMailbox(identifier, MESSAGE1, MESSAGE2));
            server = createServer(createProtocol(factory), address);
            server.bind();
            
            POP3Client client =  createClient();
            client.connect(address.getAddress().getHostAddress(), address.getPort());
            
            assertTrue(client.login("valid", "valid"));
            Reader reader = client.retrieveMessage(1);
            assertNotNull(reader);
            checkMessage(MESSAGE1, reader);
            reader.close();
            
            // does not exist
            reader = client.retrieveMessage(10);
            assertNull(reader);
            
            
            // delete and check for the message again, should now be deleted
            assertTrue(client.deleteMessage(1));
            reader = client.retrieveMessage(1);
            assertNull(reader);

            
            assertTrue(client.logout());
           
        } finally {
            if (server != null) {
                server.unbind();
            }
        }
        
    }
    
    @Test
    public void testTop() throws Exception {
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", TestUtils.getFreePort());
        
        ProtocolServer server = null;
        try {
            String identifier = "id";
            TestPassCmdHandler factory = new TestPassCmdHandler();
            
            factory.add("valid", new MockMailbox(identifier, MESSAGE1, MESSAGE2));
            server = createServer(createProtocol(factory), address);
            server.bind();
            
            POP3Client client =  createClient();
            client.connect(address.getAddress().getHostAddress(), address.getPort());
            
            assertTrue(client.login("valid", "valid"));
            Reader reader = client.retrieveMessageTop(1, 1000);
            assertNotNull(reader);
            checkMessage(MESSAGE1, reader);
            reader.close();
            
            reader = client.retrieveMessageTop(2, 1);
            assertNotNull(reader);
            checkMessage(MESSAGE2, reader,1);
            reader.close();
            
            // does not exist
            reader = client.retrieveMessageTop(10,100);
            assertNull(reader);
            
            // delete and check for the message again, should now be deleted
            assertTrue(client.deleteMessage(1));
            reader = client.retrieveMessageTop(1, 1000);
            assertNull(reader);

            assertTrue(client.logout());
           
        } finally {
            if (server != null) {
                server.unbind();
            }
        }
        
    }
    
    @Test
    public void testDele() throws Exception {
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", TestUtils.getFreePort());
        
        ProtocolServer server = null;
        try {
            String identifier = "id";
            TestPassCmdHandler factory = new TestPassCmdHandler();
            
            factory.add("valid", new MockMailbox(identifier, MESSAGE1, MESSAGE2));
            server = createServer(createProtocol(factory), address);
            server.bind();
            
            POP3Client client =  createClient();
            client.connect(address.getAddress().getHostAddress(), address.getPort());
            
            assertTrue(client.login("valid", "valid"));
            POP3MessageInfo[] info = client.listMessages();
            assertEquals(2, info.length);
            
            assertTrue(client.deleteMessage(1));
            info = client.listMessages();
            assertEquals(1, info.length);

            
            assertFalse(client.deleteMessage(1));
            info = client.listMessages();
            assertEquals(1, info.length);
            
            
            assertTrue(client.deleteMessage(2));
            info = client.listMessages();
            assertEquals(0, info.length);
            
            // logout so the messages get expunged
            assertTrue(client.logout());

            client.connect(address.getAddress().getHostAddress(), address.getPort());
  
            assertTrue(client.login("valid", "valid"));
            info = client.listMessages();
            assertEquals(0, info.length);

            assertTrue(client.logout());
           
        } finally {
            if (server != null) {
                server.unbind();
            }
        }
        
    }
    
    @Test
    public void testNoop() throws Exception {
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", TestUtils.getFreePort());
        
        ProtocolServer server = null;
        try {
            String identifier = "id";
            TestPassCmdHandler factory = new TestPassCmdHandler();
            
            factory.add("valid", new MockMailbox(identifier));
            server = createServer(createProtocol(factory), address);
            server.bind();
            
            POP3Client client =  createClient();
            client.connect(address.getAddress().getHostAddress(), address.getPort());
            
            assertTrue(client.login("valid", "valid"));
            assertTrue(client.noop());
            assertTrue(client.logout());
           
        } finally {
            if (server != null) {
                server.unbind();
            }
        }
        
    }
    
    @Test
    public void testRset() throws Exception {
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", TestUtils.getFreePort());
        
        ProtocolServer server = null;
        try {
            String identifier = "id";
            TestPassCmdHandler factory = new TestPassCmdHandler();
            
            factory.add("valid", new MockMailbox(identifier, MESSAGE1));
            server = createServer(createProtocol(factory), address);
            server.bind();
            
            POP3Client client =  createClient();
            client.connect(address.getAddress().getHostAddress(), address.getPort());
            
            assertTrue(client.login("valid", "valid"));
            assertEquals(1, client.listMessages().length);
            assertTrue(client.deleteMessage(1));
            assertEquals(0, client.listMessages().length);
            
            // call RSET. After this the deleted mark should be removed again
            assertTrue(client.reset());
            assertEquals(1, client.listMessages().length);

            assertTrue(client.logout());
           
        } finally {
            if (server != null) {
                server.unbind();
            }
        }
        
    }
    
    @Test
    public void testStat() throws Exception {
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", TestUtils.getFreePort());
        
        ProtocolServer server = null;
        try {
            String identifier = "id";
            TestPassCmdHandler factory = new TestPassCmdHandler();
            
            factory.add("valid", new MockMailbox(identifier, MESSAGE1, MESSAGE2));
            server = createServer(createProtocol(factory), address);
            server.bind();
            
            POP3Client client =  createClient();
            client.connect(address.getAddress().getHostAddress(), address.getPort());
            
            assertTrue(client.login("valid", "valid"));
            POP3MessageInfo info = client.status();
            assertEquals((int)(MESSAGE1.meta.getSize() + MESSAGE2.meta.getSize()), info.size);
            assertEquals(2, info.number);
            
            assertTrue(client.logout());
           
        } finally {
            if (server != null) {
                server.unbind();
            }
        }
        
    }
    @Test
    public void testDifferentStates() throws Exception {
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", TestUtils.getFreePort());
        
        ProtocolServer server = null;
        try {
            String identifier = "id";
            TestPassCmdHandler factory = new TestPassCmdHandler();
            
            factory.add("valid", new MockMailbox(identifier, MESSAGE1, MESSAGE2));
            server = createServer(createProtocol(factory), address);
            server.bind();
            
            POP3Client client =  createClient();
            
            client.connect(address.getAddress().getHostAddress(), address.getPort());
            assertNull(client.listMessages());
            assertNull(client.listUniqueIdentifiers());
            assertFalse(client.deleteMessage(1));
            assertNull(client.retrieveMessage(1));
            assertNull(client.retrieveMessageTop(1, 10));
            assertNull(client.status());
            assertFalse(client.reset());
            client.logout();
            
            client.connect(address.getAddress().getHostAddress(), address.getPort());

            assertTrue(client.login("valid", "valid"));
            assertNotNull(client.listMessages());
            assertNotNull(client.listUniqueIdentifiers());
            Reader reader = client.retrieveMessage(1);
            assertNotNull(reader);
            reader.close();
            assertNotNull(client.status());
            reader = client.retrieveMessageTop(1, 1);
            assertNotNull(reader);
            reader.close();
            assertTrue(client.deleteMessage(1));
            assertTrue(client.reset());

            assertTrue(client.logout());

        } finally {
            if (server != null) {
                server.unbind();
            }
        }
        
    }
    
    
    @Test
    public void testAPop() throws Exception {
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", TestUtils.getFreePort());
        
        ProtocolServer server = null;
        try {
            TestApopCmdHandler handler = new TestApopCmdHandler();
            server = createServer(createProtocol(handler), address);
            server.bind();
            
            POP3Client client =  createClient();
            client.connect(address.getAddress().getHostAddress(), address.getPort());
            String welcomeMessage = client.getReplyString();
            
            // check for valid syntax that include all info needed for APOP
            assertTrue(welcomeMessage.trim().matches("\\+OK \\<\\d+\\.\\d+@.+\\> .+"));
            
            int reply = client.sendCommand("APOP invalid invalid");
            assertEquals(POP3Reply.ERROR, reply);
            
            handler.add("valid", new MockMailbox("id"));
            reply = client.sendCommand("APOP valid valid");
            assertEquals(POP3Reply.OK, reply);
            
            assertTrue(client.logout());
           
        } finally {
            if (server != null) {
                server.unbind();
            }
        }
        
    }
    private void checkMessage(Message message, Reader reader) throws IOException {
        int read = 0;
        int i = -1;
        String content = message.toString();
        while ((i = reader.read()) != -1) {
            assertEquals(content.charAt(read++), (char)i);
        }
        assertEquals(content.length(), read);
    }
    
    private void checkMessage(Message message, Reader reader, int lines) throws IOException {
        int read = 0;
        String headers = message.headers + "\r\n";
        
        while (read < headers.length()) {
            assertEquals(headers.charAt(read++), reader.read());
        }
        assertEquals(headers.length(), read);
        
        BufferedReader bufReader = new BufferedReader(reader);
        String line = null;
        int linesRead = 0;
        String parts[] = message.body.split("\r\n");
        while ((line = bufReader.readLine()) != null) {
            assertEquals(parts[linesRead++], line);
            
            if (linesRead == lines) {
                break;
            }
        }
        
        assertEquals(lines, linesRead);
        
    }

    private final class TestApopCmdHandler extends AbstractApopCmdHandler {
        private final Map<String, Mailbox> mailboxes = new HashMap<String, Mailbox>();
       
        public void add(String username, Mailbox mailbox) {
            mailboxes.put(username, mailbox);
        }

        @Override
        protected Mailbox auth(POP3Session session, String apopTimestamp, String user, String digest) throws Exception {
            return mailboxes.get(user);
        }
        
        
    }
    
   
}
