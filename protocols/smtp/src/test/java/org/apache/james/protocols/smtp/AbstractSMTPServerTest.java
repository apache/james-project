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
package org.apache.james.protocols.smtp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.net.smtp.SMTPClient;
import org.apache.commons.net.smtp.SMTPReply;
import org.apache.james.protocols.api.Protocol;
import org.apache.james.protocols.api.ProtocolServer;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.api.handler.ConnectHandler;
import org.apache.james.protocols.api.handler.DisconnectHandler;
import org.apache.james.protocols.api.handler.ProtocolHandler;
import org.apache.james.protocols.api.handler.WiringException;
import org.apache.james.protocols.api.utils.MockLogger;
import org.apache.james.protocols.api.utils.TestUtils;
import org.apache.james.protocols.smtp.hook.HeloHook;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.protocols.smtp.hook.HookReturnCode;
import org.apache.james.protocols.smtp.hook.MailHook;
import org.apache.james.protocols.smtp.hook.MessageHook;
import org.apache.james.protocols.smtp.hook.RcptHook;
import org.apache.james.protocols.smtp.utils.TestMessageHook;
import org.junit.Test;
import static org.junit.Assert.*;

public abstract class AbstractSMTPServerTest {
    
    protected final static String MSG1 = "Subject: Testmessage\r\n\r\nThis is a message";
    protected final static String SENDER = "me@sender";
    protected final static String RCPT1 ="rpct1@domain";
    protected final static String RCPT2 ="rpct2@domain";

    
    @Test
    public void testSimpleDelivery() throws Exception {
        TestMessageHook hook = new TestMessageHook();
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", TestUtils.getFreePort());
        
        ProtocolServer server = null;
        try {
            server = createServer(createProtocol(hook), address);  
            server.bind();
            
            SMTPClient client = createClient();
            client.connect(address.getAddress().getHostAddress(), address.getPort());
            assertTrue(SMTPReply.isPositiveCompletion(client.getReplyCode()));
            
            client.helo("localhost");
            assertTrue(SMTPReply.isPositiveCompletion(client.getReplyCode()));

            client.setSender(SENDER);
            assertTrue("Reply="+ client.getReplyString(), SMTPReply.isPositiveCompletion(client.getReplyCode()));

            client.addRecipient(RCPT1);
            assertTrue("Reply="+ client.getReplyString(), SMTPReply.isPositiveCompletion(client.getReplyCode()));

            client.addRecipient(RCPT2);
            assertTrue("Reply="+ client.getReplyString(), SMTPReply.isPositiveCompletion(client.getReplyCode()));

            assertTrue(client.sendShortMessageData(MSG1));
            assertTrue("Reply="+ client.getReplyString(), SMTPReply.isPositiveCompletion(client.getReplyCode()));
            
            client.quit();
            assertTrue("Reply="+ client.getReplyString(), SMTPReply.isPositiveCompletion(client.getReplyCode()));
            client.disconnect();

            Iterator<MailEnvelope> queued = hook.getQueued().iterator();
            assertTrue(queued.hasNext());
            
            MailEnvelope env = queued.next();
            checkEnvelope(env, SENDER, Arrays.asList(RCPT1, RCPT2), MSG1);
            assertFalse(queued.hasNext());

        } finally {
            if (server != null) {
                server.unbind();
            }
        }
        
    }
    
    @Test
    public void testStartTlsNotSupported() throws Exception {
        TestMessageHook hook = new TestMessageHook();
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", TestUtils.getFreePort());
        
        
        ProtocolServer server = null;
        try {
            server = createServer(createProtocol(hook), address);  
            server.bind();
            
            SMTPClient client = createClient();
            client.connect(address.getAddress().getHostAddress(), address.getPort());
            assertTrue(SMTPReply.isPositiveCompletion(client.getReplyCode()));
            
            client.sendCommand("STARTTLS");
            assertTrue(SMTPReply.isNegativePermanent(client.getReplyCode()));

            
            client.quit();
            assertTrue("Reply="+ client.getReplyString(), SMTPReply.isPositiveCompletion(client.getReplyCode()));
            client.disconnect();
            Iterator<MailEnvelope> queued = hook.getQueued().iterator();
            assertFalse(queued.hasNext());

        } finally {
            if (server != null) {
                server.unbind();
            }
        }
        
    }
    
    @Test
    public void testUnknownCommand() throws Exception {
        TestMessageHook hook = new TestMessageHook();
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", TestUtils.getFreePort());
        
        
        ProtocolServer server = null;
        try {
            server = createServer(createProtocol(hook), address);  
            server.bind();
            
            SMTPClient client = createClient();
            client.connect(address.getAddress().getHostAddress(), address.getPort());
            assertTrue(SMTPReply.isPositiveCompletion(client.getReplyCode()));
            
            client.sendCommand("UNKNOWN");
            assertTrue(SMTPReply.isNegativePermanent(client.getReplyCode()));

            
            client.quit();
            assertTrue("Reply="+ client.getReplyString(), SMTPReply.isPositiveCompletion(client.getReplyCode()));
            client.disconnect();
            Iterator<MailEnvelope> queued = hook.getQueued().iterator();
            assertFalse(queued.hasNext());

        } finally {
            if (server != null) {
                server.unbind();
            }
        }
        
    }
    
    @Test
    public void testNoop() throws Exception {
        TestMessageHook hook = new TestMessageHook();
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", TestUtils.getFreePort());
        
        
        ProtocolServer server = null;
        try {
            server = createServer(createProtocol(hook), address);  
            server.bind();
            
            SMTPClient client = createClient();
            client.connect(address.getAddress().getHostAddress(), address.getPort());
            assertTrue(SMTPReply.isPositiveCompletion(client.getReplyCode()));
            
            client.noop();
            assertTrue(SMTPReply.isPositiveCompletion(client.getReplyCode()));

            
            client.quit();
            assertTrue("Reply="+ client.getReplyString(), SMTPReply.isPositiveCompletion(client.getReplyCode()));
            client.disconnect();
            Iterator<MailEnvelope> queued = hook.getQueued().iterator();
            assertFalse(queued.hasNext());

        } finally {
            if (server != null) {
                server.unbind();
            }
        }
        
    }
    
    
    @Test
    public void testMailWithoutBrackets() throws Exception {
        TestMessageHook hook = new TestMessageHook();
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", TestUtils.getFreePort());

        
        ProtocolServer server = null;
        try {
            server = createServer(createProtocol(hook), address);  
            server.bind();
            
            SMTPClient client = createClient();
            client.connect(address.getAddress().getHostAddress(), address.getPort());
            assertTrue(SMTPReply.isPositiveCompletion(client.getReplyCode()));
            
            client.helo("localhost");
            assertTrue(SMTPReply.isPositiveCompletion(client.getReplyCode()));

            client.mail("invalid");
            assertTrue("Reply="+ client.getReplyString(), SMTPReply.isNegativePermanent(client.getReplyCode()));

            client.addRecipient(RCPT1);
            assertTrue("Reply="+ client.getReplyString(), SMTPReply.isNegativePermanent(client.getReplyCode()));

           
            
            client.quit();
            assertTrue("Reply="+ client.getReplyString(), SMTPReply.isPositiveCompletion(client.getReplyCode()));
            client.disconnect();

            Iterator<MailEnvelope> queued = hook.getQueued().iterator();
            assertFalse(queued.hasNext());

        } finally {
            if (server != null) {
                server.unbind();
            }
        }
        
    }



    @Test
    public void testInvalidHelo() throws Exception {
        TestMessageHook hook = new TestMessageHook();
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", TestUtils.getFreePort());
        
        
        ProtocolServer server = null;
        try {
            server = createServer(createProtocol(hook), address);  
            server.bind();
            
            SMTPClient client = createClient();
            client.connect(address.getAddress().getHostAddress(), address.getPort());
            assertTrue(SMTPReply.isPositiveCompletion(client.getReplyCode()));
            
            client.helo("");
            assertTrue("Reply="+ client.getReplyString(), SMTPReply.isNegativePermanent(client.getReplyCode()));
            
            client.quit();
            assertTrue("Reply="+ client.getReplyString(), SMTPReply.isPositiveCompletion(client.getReplyCode()));
            client.disconnect();

            Iterator<MailEnvelope> queued = hook.getQueued().iterator();
            assertFalse(queued.hasNext());

        } finally {
            if (server != null) {
                server.unbind();
            }
        }
        
    }
    

    @Test
    public void testRcptWithoutBrackets() throws Exception {
        TestMessageHook hook = new TestMessageHook();
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", TestUtils.getFreePort());
        
        
        ProtocolServer server = null;
        try {
            server = createServer(createProtocol(hook), address);  
            server.bind();
            
            SMTPClient client = createClient();
            client.connect(address.getAddress().getHostAddress(), address.getPort());
            assertTrue(SMTPReply.isPositiveCompletion(client.getReplyCode()));
            
            client.helo("localhost");
            assertTrue(SMTPReply.isPositiveCompletion(client.getReplyCode()));

            client.setSender(SENDER);
            assertTrue("Reply="+ client.getReplyString(), SMTPReply.isPositiveCompletion(client.getReplyCode()));

            client.rcpt(RCPT1);
            assertTrue("Reply="+ client.getReplyString(), SMTPReply.isNegativePermanent(client.getReplyCode()));

           
            
            client.quit();
            assertTrue("Reply="+ client.getReplyString(), SMTPReply.isPositiveCompletion(client.getReplyCode()));
            client.disconnect();

            Iterator<MailEnvelope> queued = hook.getQueued().iterator();
            assertFalse(queued.hasNext());

        } finally {
            if (server != null) {
                server.unbind();
            }
        }
        
    }
    
    
    @Test
    public void testInvalidNoBracketsEnformance() throws Exception {
        TestMessageHook hook = new TestMessageHook();
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", TestUtils.getFreePort());
        
        ProtocolServer server = null;
        try {
            Protocol protocol = createProtocol(hook);
            ((SMTPConfigurationImpl) protocol.getConfiguration()).setUseAddressBracketsEnforcement(false);
            server = createServer(protocol, address);  
            server.bind();
            
            SMTPClient client = createClient();
            client.connect(address.getAddress().getHostAddress(), address.getPort());
            assertTrue("Reply="+ client.getReplyString(), SMTPReply.isPositiveCompletion(client.getReplyCode()));
            
            client.helo("localhost");
            assertTrue("Reply="+ client.getReplyString(), SMTPReply.isPositiveCompletion(client.getReplyCode()));

            client.mail(SENDER);
            assertTrue("Reply="+ client.getReplyString(), SMTPReply.isPositiveCompletion(client.getReplyCode()));

            client.addRecipient(RCPT1);
            assertTrue("Reply="+ client.getReplyString(), SMTPReply.isPositiveCompletion(client.getReplyCode()));

           
            
            client.quit();
            assertTrue("Reply="+ client.getReplyString(), SMTPReply.isPositiveCompletion(client.getReplyCode()));
            client.disconnect();

            Iterator<MailEnvelope> queued = hook.getQueued().iterator();
            assertFalse(queued.hasNext());

        } finally {
            if (server != null) {
                server.unbind();
            }
        }
        
    }
    
    
    @Test
    public void testHeloEnforcement() throws Exception {
        TestMessageHook hook = new TestMessageHook();
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", TestUtils.getFreePort());
        
        
        ProtocolServer server = null;
        try {
            server = createServer(createProtocol(hook), address);  
            server.bind();
            
            SMTPClient client = createClient();
            client.connect(address.getAddress().getHostAddress(), address.getPort());
            assertTrue("Reply="+ client.getReplyString(), SMTPReply.isPositiveCompletion(client.getReplyCode()));
            
           
            client.setSender(SENDER);
            assertTrue("Reply="+ client.getReplyString(), SMTPReply.isNegativePermanent(client.getReplyCode()));

         
            client.quit();
            assertTrue("Reply="+ client.getReplyString(), SMTPReply.isPositiveCompletion(client.getReplyCode()));
            client.disconnect();

            Iterator<MailEnvelope> queued = hook.getQueued().iterator();
            assertFalse(queued.hasNext());

        } finally {
            if (server != null) {
                server.unbind();
            }
        }
        
    }
    
    @Test
    public void testHeloEnforcementDisabled() throws Exception {
        TestMessageHook hook = new TestMessageHook();
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", TestUtils.getFreePort());
        
        ProtocolServer server = null;
        try {
            Protocol protocol = createProtocol(hook);
            ((SMTPConfigurationImpl) protocol.getConfiguration()).setHeloEhloEnforcement(false);
            server = createServer(protocol, address);
            server.bind();
            
            SMTPClient client = createClient();
            client.connect(address.getAddress().getHostAddress(), address.getPort());
            assertTrue("Reply="+ client.getReplyString(), SMTPReply.isPositiveCompletion(client.getReplyCode()));
            
           
            client.setSender(SENDER);
            assertTrue("Reply="+ client.getReplyString(), SMTPReply.isPositiveCompletion(client.getReplyCode()));

         
            client.quit();
            assertTrue("Reply="+ client.getReplyString(), SMTPReply.isPositiveCompletion(client.getReplyCode()));
            client.disconnect();

            Iterator<MailEnvelope> queued = hook.getQueued().iterator();
            assertFalse(queued.hasNext());

        } finally {
            if (server != null) {
                server.unbind();
            }
        }
        
    }
    
    
    @Test
    public void testHeloHookPermanentError() throws Exception {
        HeloHook hook = new HeloHook() {

            public HookResult doHelo(SMTPSession session, String helo) {
                return new HookResult(HookReturnCode.DENY);
            }
        };
        
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", TestUtils.getFreePort());
        
        
        ProtocolServer server = null;
        try {
            server = createServer(createProtocol(hook), address);  
            server.bind();
            
            SMTPClient client = createClient();
            client.connect(address.getAddress().getHostAddress(), address.getPort());
            assertTrue("Reply="+ client.getReplyString(), SMTPReply.isPositiveCompletion(client.getReplyCode()));
            
           
            client.helo("localhost");
            assertTrue("Reply="+ client.getReplyString(), SMTPReply.isNegativePermanent(client.getReplyCode()));

         
            client.quit();
            assertTrue("Reply="+ client.getReplyString(), SMTPReply.isPositiveCompletion(client.getReplyCode()));
            client.disconnect();

        } finally {
            if (server != null) {
                server.unbind();
            }
        }
        
    }
    
    
    @Test
    public void testHeloHookTempraryError() throws Exception {
        HeloHook hook = new HeloHook() {

            public HookResult doHelo(SMTPSession session, String helo) {
                return new HookResult(HookReturnCode.DENYSOFT);
            }
        };
        
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", TestUtils.getFreePort());
        
        
        ProtocolServer server = null;
        try {
            server = createServer(createProtocol(hook), address);  
            server.bind();
            
            SMTPClient client = createClient();
            client.connect(address.getAddress().getHostAddress(), address.getPort());
            assertTrue("Reply="+ client.getReplyString(), SMTPReply.isPositiveCompletion(client.getReplyCode()));
            
           
            client.helo("localhost");
            assertTrue("Reply="+ client.getReplyString(), SMTPReply.isNegativeTransient(client.getReplyCode()));

         
            client.quit();
            assertTrue("Reply="+ client.getReplyString(), SMTPReply.isPositiveCompletion(client.getReplyCode()));
            client.disconnect();

        } finally {
            if (server != null) {
                server.unbind();
            }
        }
        
    }
    
    @Test
    public void testMailHookPermanentError() throws Exception {
        MailHook hook = new MailHook() {

            public HookResult doMail(SMTPSession session, MailAddress sender) {
                return new HookResult(HookReturnCode.DENY);
            }
        };
        
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", TestUtils.getFreePort());
        
        
        ProtocolServer server = null;
        try {
            server = createServer(createProtocol(hook), address);  
            server.bind();
            
            SMTPClient client = createClient();
            client.connect(address.getAddress().getHostAddress(), address.getPort());
            assertTrue("Reply="+ client.getReplyString(), SMTPReply.isPositiveCompletion(client.getReplyCode()));
            
           
            client.helo("localhost");
            assertTrue("Reply="+ client.getReplyString(), SMTPReply.isPositiveCompletion(client.getReplyCode()));

            client.setSender(SENDER);
            assertTrue("Reply="+ client.getReplyString(), SMTPReply.isNegativePermanent(client.getReplyCode()));

         
            client.quit();
            assertTrue("Reply="+ client.getReplyString(), SMTPReply.isPositiveCompletion(client.getReplyCode()));
            client.disconnect();

        } finally {
            if (server != null) {
                server.unbind();
            }
        }
        
    }
    
    @Test
    public void testMailHookTemporaryError() throws Exception {
        MailHook hook = new MailHook() {

            public HookResult doMail(SMTPSession session, MailAddress sender) {
                return new HookResult(HookReturnCode.DENYSOFT);
            }
        };
        
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", TestUtils.getFreePort());
        
        
        ProtocolServer server = null;
        try {
            server = createServer(createProtocol(hook), address);  
            server.bind();
            
            SMTPClient client = createClient();
            client.connect(address.getAddress().getHostAddress(), address.getPort());
            assertTrue("Reply="+ client.getReplyString(), SMTPReply.isPositiveCompletion(client.getReplyCode()));
            
           
            client.helo("localhost");
            assertTrue("Reply="+ client.getReplyString(), SMTPReply.isPositiveCompletion(client.getReplyCode()));

            client.setSender(SENDER);
            assertTrue("Reply="+ client.getReplyString(), SMTPReply.isNegativeTransient(client.getReplyCode()));

         
            client.quit();
            assertTrue("Reply="+ client.getReplyString(), SMTPReply.isPositiveCompletion(client.getReplyCode()));
            client.disconnect();

        } finally {
            if (server != null) {
                server.unbind();
            }
        }
        
    }
    
    
    @Test
    public void testRcptHookPermanentError() throws Exception {
        RcptHook hook = new RcptHook() {

            public HookResult doRcpt(SMTPSession session, MailAddress sender, MailAddress rcpt) {
                if (RCPT1.equals(rcpt.toString())) {
                    return new HookResult(HookReturnCode.DENY);
                } else {
                    return new HookResult(HookReturnCode.DECLINED);
                }
            }

        };
        
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", TestUtils.getFreePort());
        
        
        ProtocolServer server = null;
        try {
            server = createServer(createProtocol(hook), address);  
            server.bind();
            
            SMTPClient client = createClient();
            client.connect(address.getAddress().getHostAddress(), address.getPort());
            assertTrue("Reply="+ client.getReplyString(), SMTPReply.isPositiveCompletion(client.getReplyCode()));
            
           
            client.helo("localhost");
            assertTrue("Reply="+ client.getReplyString(), SMTPReply.isPositiveCompletion(client.getReplyCode()));

            client.setSender(SENDER);
            assertTrue("Reply="+ client.getReplyString(), SMTPReply.isPositiveCompletion(client.getReplyCode()));

            client.addRecipient(RCPT1);
            assertTrue("Reply="+ client.getReplyString(), SMTPReply.isNegativePermanent(client.getReplyCode()));

         
            client.addRecipient(RCPT2);
            assertTrue("Reply="+ client.getReplyString(), SMTPReply.isPositiveCompletion(client.getReplyCode()));

            
            client.quit();
            assertTrue("Reply="+ client.getReplyString(), SMTPReply.isPositiveCompletion(client.getReplyCode()));
            client.disconnect();

        } finally {
            if (server != null) {
                server.unbind();
            }
        }
        
    }
    
    

    @Test
    public void testRcptHookTemporaryError() throws Exception {
        RcptHook hook = new RcptHook() {

            public HookResult doRcpt(SMTPSession session, MailAddress sender, MailAddress rcpt) {
                if (RCPT1.equals(rcpt.toString())) {
                    return new HookResult(HookReturnCode.DENYSOFT);
                } else {
                    return new HookResult(HookReturnCode.DECLINED);
                }
            }

        };
        
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", TestUtils.getFreePort());
        
        
        ProtocolServer server = null;
        try {
            server = createServer(createProtocol(hook), address);  
            server.bind();
            
            SMTPClient client = createClient();
            client.connect(address.getAddress().getHostAddress(), address.getPort());
            assertTrue("Reply="+ client.getReplyString(), SMTPReply.isPositiveCompletion(client.getReplyCode()));
            
           
            client.helo("localhost");
            assertTrue("Reply="+ client.getReplyString(), SMTPReply.isPositiveCompletion(client.getReplyCode()));

            client.setSender(SENDER);
            assertTrue("Reply="+ client.getReplyString(), SMTPReply.isPositiveCompletion(client.getReplyCode()));

            client.addRecipient(RCPT1);
            assertTrue("Reply="+ client.getReplyString(), SMTPReply.isNegativeTransient(client.getReplyCode()));

         
            client.addRecipient(RCPT2);
            assertTrue("Reply="+ client.getReplyString(), SMTPReply.isPositiveCompletion(client.getReplyCode()));

            
            client.quit();
            assertTrue("Reply="+ client.getReplyString(), SMTPReply.isPositiveCompletion(client.getReplyCode()));
            client.disconnect();

        } finally {
            if (server != null) {
                server.unbind();
            }
        }
        
    }
    
    @Test
    public void testNullSender() throws Exception {

        InetSocketAddress address = new InetSocketAddress("127.0.0.1", TestUtils.getFreePort());
        
        ProtocolServer server = null;
        try {
            server = createServer(createProtocol(new ProtocolHandler[0]), address);
            server.bind();
            
            SMTPClient client = createClient();
            client.connect(address.getAddress().getHostAddress(), address.getPort());
            assertTrue("Reply="+ client.getReplyString(), SMTPReply.isPositiveCompletion(client.getReplyCode()));
            
           
            client.helo("localhost");
            assertTrue("Reply="+ client.getReplyString(), SMTPReply.isPositiveCompletion(client.getReplyCode()));

            client.setSender("");
            assertTrue("Reply="+ client.getReplyString(), SMTPReply.isPositiveCompletion(client.getReplyCode()));
         
            client.addRecipient(RCPT1);
            assertTrue("Reply="+ client.getReplyString(), SMTPReply.isPositiveCompletion(client.getReplyCode()));

            
            client.quit();
            assertTrue("Reply="+ client.getReplyString(), SMTPReply.isPositiveCompletion(client.getReplyCode()));
            client.disconnect();

        } finally {
            if (server != null) {
                server.unbind();
            }
        }
        
    }
    
    @Test
    public void testMessageHookPermanentError() throws Exception {
        TestMessageHook testHook = new TestMessageHook();

        MessageHook hook = new MessageHook() {

            public HookResult onMessage(SMTPSession session, MailEnvelope mail) {
                return new HookResult(HookReturnCode.DENY);
            }


        };
        
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", TestUtils.getFreePort());
        
        ProtocolServer server = null;
        try {
            server = createServer(createProtocol(hook, testHook), address);
            server.bind();
            
            SMTPClient client = createClient();
            client.connect(address.getAddress().getHostAddress(), address.getPort());
            assertTrue("Reply="+ client.getReplyString(), SMTPReply.isPositiveCompletion(client.getReplyCode()));
            
           
            client.helo("localhost");
            assertTrue("Reply="+ client.getReplyString(), SMTPReply.isPositiveCompletion(client.getReplyCode()));

            client.setSender(SENDER);
            assertTrue("Reply="+ client.getReplyString(), SMTPReply.isPositiveCompletion(client.getReplyCode()));
         
            client.addRecipient(RCPT2);
            assertTrue("Reply="+ client.getReplyString(), SMTPReply.isPositiveCompletion(client.getReplyCode()));

            assertFalse(client.sendShortMessageData(MSG1));
            assertTrue("Reply="+ client.getReplyString(), SMTPReply.isNegativePermanent(client.getReplyCode()));
            
            client.quit();
            assertTrue("Reply="+ client.getReplyString(), SMTPReply.isPositiveCompletion(client.getReplyCode()));
            client.disconnect();

            Iterator<MailEnvelope> queued = testHook.getQueued().iterator();
            assertFalse(queued.hasNext());


        } finally {
            if (server != null) {
                server.unbind();
            }
        }
        
    }
    
    
    @Test
    public void testMessageHookTemporaryError() throws Exception {
        TestMessageHook testHook = new TestMessageHook();

        MessageHook hook = new MessageHook() {

            
            public HookResult onMessage(SMTPSession session, MailEnvelope mail) {
                return new HookResult(HookReturnCode.DENYSOFT);
            }


        };
        
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", TestUtils.getFreePort());
        
        ProtocolServer server = null;
        try {
            server = createServer(createProtocol(hook, testHook), address);
            server.bind();
            
            SMTPClient client = createClient();
            client.connect(address.getAddress().getHostAddress(), address.getPort());
            assertTrue("Reply="+ client.getReplyString(), SMTPReply.isPositiveCompletion(client.getReplyCode()));
            
           
            client.helo("localhost");
            assertTrue("Reply="+ client.getReplyString(), SMTPReply.isPositiveCompletion(client.getReplyCode()));

            client.setSender(SENDER);
            assertTrue("Reply="+ client.getReplyString(), SMTPReply.isPositiveCompletion(client.getReplyCode()));
         
            client.addRecipient(RCPT2);
            assertTrue("Reply="+ client.getReplyString(), SMTPReply.isPositiveCompletion(client.getReplyCode()));

            assertFalse(client.sendShortMessageData(MSG1));
            assertTrue("Reply="+ client.getReplyString(), SMTPReply.isNegativeTransient(client.getReplyCode()));
            
            client.quit();
            assertTrue("Reply="+ client.getReplyString(), SMTPReply.isPositiveCompletion(client.getReplyCode()));
            client.disconnect();

            Iterator<MailEnvelope> queued = testHook.getQueued().iterator();
            assertFalse(queued.hasNext());


        } finally {
            if (server != null) {
                server.unbind();
            }
        }
        
    }
    
  
    
    @Test
    public void testConnectHandlerPermananet() throws Exception {
        ConnectHandler<SMTPSession> connectHandler = new ConnectHandler<SMTPSession>() {

            
            public Response onConnect(SMTPSession session) {
                return new SMTPResponse("554", "Bye Bye");
            }
        };
        
        
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", TestUtils.getFreePort());
        
        ProtocolServer server = null;
        try {
            
            server = createServer(createProtocol(connectHandler), address);
            server.bind();
            
            SMTPClient client = createClient();
            client.connect(address.getAddress().getHostAddress(), address.getPort());
            assertTrue("Reply="+ client.getReplyString(), SMTPReply.isNegativePermanent(client.getReplyCode()));
            
            client.disconnect();


        } finally {
            if (server != null) {
                server.unbind();
            }
        }
        
    }
    
    
    @Test
    public void testConnectHandlerTemporary() throws Exception {
        ConnectHandler<SMTPSession> connectHandler = new ConnectHandler<SMTPSession>() {

            
            public Response onConnect(SMTPSession session) {
                return new SMTPResponse("451", "Bye Bye");
            }
        };
        
        
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", TestUtils.getFreePort());
        
        ProtocolServer server = null;
        try {
            server = createServer(createProtocol(connectHandler), address);
            server.bind();
            
            SMTPClient client = createClient();
            client.connect(address.getAddress().getHostAddress(), address.getPort());
            assertTrue("Reply="+ client.getReplyString(), SMTPReply.isNegativeTransient(client.getReplyCode()));
            
            client.disconnect();


        } finally {
            if (server != null) {
                server.unbind();
            }
        }
        
    }
    
    @Test
    public void testDisconnectHandler() throws Exception {
        
        final AtomicBoolean called = new AtomicBoolean(false);
        DisconnectHandler<SMTPSession> handler = new DisconnectHandler<SMTPSession>() {

            
            public void onDisconnect(SMTPSession session) {  
                called.set(true);
            }
        };
        
        
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", TestUtils.getFreePort());
        
        ProtocolServer server = null;
        try {
            server = createServer(createProtocol(handler), address);  
            server.bind();
            
            SMTPClient client = createClient();
            client.connect(address.getAddress().getHostAddress(), address.getPort());
            assertTrue("Reply="+ client.getReplyString(), SMTPReply.isPositiveCompletion(client.getReplyCode()));
            
            client.disconnect();
            
            Thread.sleep(1000);
            assertTrue(called.get());


        } finally {
            if (server != null) {
                server.unbind();
            }
        }
        
    }
    
    protected SMTPClient createClient() {
        return new SMTPClient();
    }

    protected abstract ProtocolServer createServer(Protocol protocol, InetSocketAddress address);

    
    protected Protocol createProtocol(ProtocolHandler... handlers) throws WiringException {
        SMTPProtocolHandlerChain chain = new SMTPProtocolHandlerChain();
        chain.addAll(0, Arrays.asList(handlers));
        chain.wireExtensibleHandlers();
        return new SMTPProtocol(chain, new SMTPConfigurationImpl(), new MockLogger());
    }
    
    protected static void checkEnvelope(MailEnvelope env, String sender, List<String> recipients, String msg) throws IOException {
        assertEquals(sender, env.getSender().toString());

        List<MailAddress> envRecipients = env.getRecipients();
        assertEquals(recipients.size(), envRecipients.size());
        for (int i = 0; i < recipients.size(); i++) {
            MailAddress address = envRecipients.get(i);
            assertEquals(recipients.get(i), address.toString());
        }

        BufferedReader reader = null;

        try {
            reader = new BufferedReader(new InputStreamReader(env.getMessageInputStream()));

            String line = null;
            boolean start = false;
            StringBuilder sb = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("Subject")) {
                    start = true;
                }
                if (start) {
                    sb.append(line);
                    sb.append("\r\n");
                }
            }
            String msgQueued = sb.subSequence(0, sb.length() - 2).toString();

            assertEquals(msg.length(), msgQueued.length());
            for (int i = 0; i < msg.length(); i++) {
                assertEquals(msg.charAt(i), msgQueued.charAt(i));
            }
        } finally {
            if (reader != null) {
                reader.close();
            }
        }

    }
    
}
