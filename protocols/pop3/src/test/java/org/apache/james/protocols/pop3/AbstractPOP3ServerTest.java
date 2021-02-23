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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.commons.net.pop3.POP3Client;
import org.apache.commons.net.pop3.POP3MessageInfo;
import org.apache.commons.net.pop3.POP3Reply;
import org.apache.james.core.Username;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.protocols.api.Protocol;
import org.apache.james.protocols.api.ProtocolServer;
import org.apache.james.protocols.api.handler.WiringException;
import org.apache.james.protocols.api.utils.ProtocolServerUtils;
import org.apache.james.protocols.pop3.core.AbstractApopCmdHandler;
import org.apache.james.protocols.pop3.core.AbstractPassCmdHandler;
import org.apache.james.protocols.pop3.mailbox.Mailbox;
import org.apache.james.protocols.pop3.utils.MockMailbox;
import org.apache.james.protocols.pop3.utils.MockMailbox.Message;
import org.apache.james.protocols.pop3.utils.TestPassCmdHandler;
import org.junit.jupiter.api.Test;

public abstract class AbstractPOP3ServerTest {

    private static final Message MESSAGE1 = new Message("Subject: test\r\nX-Header: value\r\n", "My Body\r\n");
    private static final Message MESSAGE2 = new Message("Subject: test2\r\nX-Header: value2\r\n", "My Body with a DOT.\r\n.\r\n");

    private POP3Protocol createProtocol(AbstractPassCmdHandler handler) throws WiringException {
        return new POP3Protocol(new POP3ProtocolHandlerChain(handler), new POP3Configuration());
    }
    
    protected abstract ProtocolServer createServer(Protocol protocol);
    
    protected POP3Client createClient() {
        return new POP3Client();
    }
    
    @Test
    public void testInvalidAuth() throws Exception {
        ProtocolServer server = null;
        try {
            server = createServer(createProtocol(new TestPassCmdHandler(new RecordingMetricFactory())));
            server.bind();
            
            POP3Client client =  createClient();
            InetSocketAddress bindedAddress = new ProtocolServerUtils(server).retrieveBindedAddress();
            client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
            
            assertThat(client.login("invalid", "invalid")).isFalse();
            assertThat(client.logout()).isTrue();
           
        } finally {
            if (server != null) {
                server.unbind();
            }
        }
        
    }

    @Test
    public void testEmptyInbox() throws Exception {
        ProtocolServer server = null;
        try {
            String identifier = "id";
            TestPassCmdHandler handler = new TestPassCmdHandler(new RecordingMetricFactory());
            
            handler.add("valid", new MockMailbox(identifier));
            server = createServer(createProtocol(handler));
            server.bind();
            
            POP3Client client =  createClient();
            InetSocketAddress bindedAddress = new ProtocolServerUtils(server).retrieveBindedAddress();
            client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
            
            assertThat(client.login("valid", "valid")).isTrue();
            POP3MessageInfo[] info = client.listMessages();
            assertThat(info.length).isEqualTo(0);
            
            info = client.listUniqueIdentifiers();
            assertThat(info.length).isEqualTo(0);
            assertThat(client.logout()).isTrue();
           
        } finally {
            if (server != null) {
                server.unbind();
            }
        }
        
    }
    
    @Test
    public void testInboxWithMessages() throws Exception {
        ProtocolServer server = null;
        try {
            String identifier = "id";
            TestPassCmdHandler handler = new TestPassCmdHandler(new RecordingMetricFactory());
            
            handler.add("valid", new MockMailbox(identifier, MESSAGE1, MESSAGE2));
            server = createServer(createProtocol(handler));
            server.bind();
            
            POP3Client client =  createClient();
            InetSocketAddress bindedAddress = new ProtocolServerUtils(server).retrieveBindedAddress();
            client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
            
            assertThat(client.login("valid", "valid")).isTrue();
            POP3MessageInfo[] info = client.listMessages();
            assertThat(info.length).isEqualTo(2);
            assertThat(info[0].size).isEqualTo((int) MESSAGE1.meta.getSize());
            assertThat(info[1].size).isEqualTo((int) MESSAGE2.meta.getSize());
            assertThat(info[0].number).isEqualTo(1);
            assertThat(info[1].number).isEqualTo(2);

            POP3MessageInfo mInfo = client.listMessage(1);
            assertThat(mInfo.size).isEqualTo((int) MESSAGE1.meta.getSize());
            assertThat(mInfo.number).isEqualTo(1);

            // try to retrieve message that not exist
            mInfo = client.listMessage(10);
            assertThat(mInfo).isNull();

            info = client.listUniqueIdentifiers();
            assertThat(info.length).isEqualTo(2);
            assertThat(info[0].identifier).isEqualTo(identifier + "-" + MESSAGE1.meta.getUid());
            assertThat(info[1].identifier).isEqualTo(identifier + "-" + MESSAGE2.meta.getUid());
            assertThat(info[0].number).isEqualTo(1);
            assertThat(info[1].number).isEqualTo(2);

            mInfo = client.listUniqueIdentifier(1);
            assertThat(mInfo.identifier).isEqualTo(identifier + "-" + MESSAGE1.meta.getUid());
            assertThat(mInfo.number).isEqualTo(1);

            // try to retrieve message that not exist
            mInfo = client.listUniqueIdentifier(10);
            assertThat(mInfo).isNull();

            assertThat(client.logout()).isTrue();
           
        } finally {
            if (server != null) {
                server.unbind();
            }
        }
        
    }
    
    @Test
    public void testRetr() throws Exception {
        ProtocolServer server = null;
        try {
            String identifier = "id";
            TestPassCmdHandler factory = new TestPassCmdHandler(new RecordingMetricFactory());
            
            factory.add("valid", new MockMailbox(identifier, MESSAGE1, MESSAGE2));
            server = createServer(createProtocol(factory));
            server.bind();
            
            POP3Client client =  createClient();
            InetSocketAddress bindedAddress = new ProtocolServerUtils(server).retrieveBindedAddress();
            client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
            
            assertThat(client.login("valid", "valid")).isTrue();
            Reader reader = client.retrieveMessage(1);
            assertThat(reader).isNotNull();
            checkMessage(MESSAGE1, reader);
            reader.close();
            
            // does not exist
            reader = client.retrieveMessage(10);
            assertThat(reader).isNull();
            
            
            // delete and check for the message again, should now be deleted
            assertThat(client.deleteMessage(1)).isTrue();
            reader = client.retrieveMessage(1);
            assertThat(reader).isNull();

            
            assertThat(client.logout()).isTrue();
           
        } finally {
            if (server != null) {
                server.unbind();
            }
        }
        
    }
    
    @Test
    public void testTop() throws Exception {
        ProtocolServer server = null;
        try {
            String identifier = "id";
            TestPassCmdHandler factory = new TestPassCmdHandler(new RecordingMetricFactory());
            
            factory.add("valid", new MockMailbox(identifier, MESSAGE1, MESSAGE2));
            server = createServer(createProtocol(factory));
            server.bind();
            
            POP3Client client =  createClient();
            InetSocketAddress bindedAddress = new ProtocolServerUtils(server).retrieveBindedAddress();
            client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
            
            assertThat(client.login("valid", "valid")).isTrue();
            Reader reader = client.retrieveMessageTop(1, 1000);
            assertThat(reader).isNotNull();
            checkMessage(MESSAGE1, reader);
            reader.close();
            
            reader = client.retrieveMessageTop(2, 1);
            assertThat(reader).isNotNull();
            checkMessage(MESSAGE2, reader,1);
            reader.close();
            
            // does not exist
            reader = client.retrieveMessageTop(10,100);
            assertThat(reader).isNull();
            
            // delete and check for the message again, should now be deleted
            assertThat(client.deleteMessage(1)).isTrue();
            reader = client.retrieveMessageTop(1, 1000);
            assertThat(reader).isNull();

            assertThat(client.logout()).isTrue();
           
        } finally {
            if (server != null) {
                server.unbind();
            }
        }
        
    }
    
    @Test
    public void testDele() throws Exception {
        ProtocolServer server = null;
        try {
            String identifier = "id";
            TestPassCmdHandler factory = new TestPassCmdHandler(new RecordingMetricFactory());
            
            factory.add("valid", new MockMailbox(identifier, MESSAGE1, MESSAGE2));
            server = createServer(createProtocol(factory));
            server.bind();
            
            POP3Client client =  createClient();
            InetSocketAddress bindedAddress = new ProtocolServerUtils(server).retrieveBindedAddress();
            client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
            
            assertThat(client.login("valid", "valid")).isTrue();
            POP3MessageInfo[] info = client.listMessages();
            assertThat(info.length).isEqualTo(2);
            
            assertThat(client.deleteMessage(1)).isTrue();
            info = client.listMessages();
            assertThat(info.length).isEqualTo(1);

            
            assertThat(client.deleteMessage(1)).isFalse();
            info = client.listMessages();
            assertThat(info.length).isEqualTo(1);
            
            
            assertThat(client.deleteMessage(2)).isTrue();
            info = client.listMessages();
            assertThat(info.length).isEqualTo(0);
            
            // logout so the messages get expunged
            assertThat(client.logout()).isTrue();

            client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
  
            assertThat(client.login("valid", "valid")).isTrue();
            info = client.listMessages();
            assertThat(info.length).isEqualTo(0);

            assertThat(client.logout()).isTrue();
           
        } finally {
            if (server != null) {
                server.unbind();
            }
        }
        
    }
    
    @Test
    public void testNoop() throws Exception {
        ProtocolServer server = null;
        try {
            String identifier = "id";
            TestPassCmdHandler factory = new TestPassCmdHandler(new RecordingMetricFactory());
            
            factory.add("valid", new MockMailbox(identifier));
            server = createServer(createProtocol(factory));
            server.bind();
            
            POP3Client client =  createClient();
            InetSocketAddress bindedAddress = new ProtocolServerUtils(server).retrieveBindedAddress();
            client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
            
            assertThat(client.login("valid", "valid")).isTrue();
            assertThat(client.noop()).isTrue();
            assertThat(client.logout()).isTrue();
           
        } finally {
            if (server != null) {
                server.unbind();
            }
        }
        
    }
    
    @Test
    public void testRset() throws Exception {
        ProtocolServer server = null;
        try {
            String identifier = "id";
            TestPassCmdHandler factory = new TestPassCmdHandler(new RecordingMetricFactory());
            
            factory.add("valid", new MockMailbox(identifier, MESSAGE1));
            server = createServer(createProtocol(factory));
            server.bind();
            
            POP3Client client =  createClient();
            InetSocketAddress bindedAddress = new ProtocolServerUtils(server).retrieveBindedAddress();
            client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
            
            assertThat(client.login("valid", "valid")).isTrue();
            assertThat(client.listMessages().length).isEqualTo(1);
            assertThat(client.deleteMessage(1)).isTrue();
            assertThat(client.listMessages().length).isEqualTo(0);
            
            // call RSET. After this the deleted mark should be removed again
            assertThat(client.reset()).isTrue();
            assertThat(client.listMessages().length).isEqualTo(1);

            assertThat(client.logout()).isTrue();
           
        } finally {
            if (server != null) {
                server.unbind();
            }
        }
        
    }
    
    @Test
    public void testStat() throws Exception {
        ProtocolServer server = null;
        try {
            String identifier = "id";
            TestPassCmdHandler factory = new TestPassCmdHandler(new RecordingMetricFactory());
            
            factory.add("valid", new MockMailbox(identifier, MESSAGE1, MESSAGE2));
            server = createServer(createProtocol(factory));
            server.bind();
            
            POP3Client client =  createClient();
            InetSocketAddress bindedAddress = new ProtocolServerUtils(server).retrieveBindedAddress();
            client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
            
            assertThat(client.login("valid", "valid")).isTrue();
            POP3MessageInfo info = client.status();
            assertThat(info.size).isEqualTo((int)(MESSAGE1.meta.getSize() + MESSAGE2.meta.getSize()));
            assertThat(info.number).isEqualTo(2);
            assertThat(client.logout()).isTrue();

        } finally {
            if (server != null) {
                server.unbind();
            }
        }
        
    }
    
    @Test
    public void testDifferentStates() throws Exception {
        ProtocolServer server = null;
        try {
            String identifier = "id";
            TestPassCmdHandler factory = new TestPassCmdHandler(new RecordingMetricFactory());
            
            factory.add("valid", new MockMailbox(identifier, MESSAGE1, MESSAGE2));
            server = createServer(createProtocol(factory));
            server.bind();
            
            POP3Client client =  createClient();
            
            InetSocketAddress bindedAddress = new ProtocolServerUtils(server).retrieveBindedAddress();
            client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
            assertThat(client.listMessages()).isNull();
            assertThat(client.listUniqueIdentifiers()).isNull();
            assertThat(client.deleteMessage(1)).isFalse();
            assertThat(client.retrieveMessage(1)).isNull();
            assertThat(client.retrieveMessageTop(1, 10)).isNull();
            assertThat(client.status()).isNull();
            assertThat(client.reset()).isFalse();
            client.logout();
            
            client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());

            assertThat(client.login("valid", "valid")).isTrue();
            assertThat(client.listMessages()).isNotNull();
            assertThat(client.listUniqueIdentifiers()).isNotNull();
            Reader reader = client.retrieveMessage(1);
            assertThat(reader).isNotNull();
            reader.close();
            assertThat(client.status()).isNotNull();
            reader = client.retrieveMessageTop(1, 1);
            assertThat(reader).isNotNull();
            reader.close();
            assertThat(client.deleteMessage(1)).isTrue();
            assertThat(client.reset()).isTrue();

            assertThat(client.logout()).isTrue();

        } finally {
            if (server != null) {
                server.unbind();
            }
        }
        
    }
    
    
    @Test
    public void testAPop() throws Exception {
        ProtocolServer server = null;
        try {
            TestApopCmdHandler handler = new TestApopCmdHandler(new RecordingMetricFactory());
            server = createServer(createProtocol(handler));
            server.bind();
            
            POP3Client client =  createClient();
            InetSocketAddress bindedAddress = new ProtocolServerUtils(server).retrieveBindedAddress();
            client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
            String welcomeMessage = client.getReplyString();
            
            // check for valid syntax that include all info needed for APOP
            assertThat(welcomeMessage.trim()).matches(Pattern.compile("\\+OK \\<-?\\d+\\.\\d+@.+\\> .+"));
            
            assertThat(client.sendCommand("APOP invalid invalid")).isEqualTo(POP3Reply.ERROR);
            
            handler.add("valid", new MockMailbox("id"));
            assertThat(client.sendCommand("APOP valid valid")).isEqualTo(POP3Reply.OK);
            
            assertThat(client.logout()).isTrue();
           
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
            assertThat(i).isEqualTo(content.charAt(read++));
        }
        assertThat(read).isEqualTo(content.length());
    }
    
    private void checkMessage(Message message, Reader reader, int lines) throws IOException {
        int read = 0;
        String headers = message.headers + "\r\n";
        
        while (read < headers.length()) {
            assertThat(reader.read()).isEqualTo(headers.charAt(read++));
        }
        assertThat(read).isEqualTo(headers.length());
        
        BufferedReader bufReader = new BufferedReader(reader);
        String line = null;
        int linesRead = 0;
        String[] parts = message.body.split("\r\n");
        while ((line = bufReader.readLine()) != null) {
            assertThat(line).isEqualTo(parts[linesRead++]);
            
            if (linesRead == lines) {
                break;
            }
        }
        
        assertThat(linesRead).isEqualTo(lines);
        
    }

    private final class TestApopCmdHandler extends AbstractApopCmdHandler {
        private final Map<String, Mailbox> mailboxes = new HashMap<>();

        public TestApopCmdHandler(MetricFactory metricFactory) {
            super(metricFactory);
        }

        public void add(String username, Mailbox mailbox) {
            mailboxes.put(username, mailbox);
        }

        @Override
        protected Mailbox auth(POP3Session session, String apopTimestamp, Username user, String digest) throws Exception {
            return mailboxes.get(user.asString());
        }
        
        
    }
    
   
}
