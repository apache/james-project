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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
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
import org.apache.james.protocols.api.utils.ProtocolServerUtils;
import org.apache.james.protocols.smtp.hook.HeloHook;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.protocols.smtp.hook.HookReturnCode;
import org.apache.james.protocols.smtp.hook.MailHook;
import org.apache.james.protocols.smtp.hook.MessageHook;
import org.apache.james.protocols.smtp.hook.RcptHook;
import org.apache.james.protocols.smtp.utils.TestMessageHook;
import org.junit.Test;

import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;

public abstract class AbstractSMTPServerTest {
    
    protected final static String MSG1 = "Subject: Testmessage\r\n\r\nThis is a message\r\n";
    protected final static String SENDER = "me@sender";
    protected final static String RCPT1 ="rpct1@domain";
    protected final static String RCPT2 ="rpct2@domain";

    
    @Test
    public void testSimpleDelivery() throws Exception {
        TestMessageHook hook = new TestMessageHook();
        ProtocolServer server = null;
        try {
            server = createServer(createProtocol(hook));
            server.bind();
            
            InetSocketAddress bindedAddress = new ProtocolServerUtils(server).retrieveBindedAddress();

            send(server, bindedAddress, MSG1);

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
    public void testDeliveryWith4SimultaneousThreads() throws Exception {
        TestMessageHook hook = new TestMessageHook();
        ProtocolServer server = null;
        try {
            server = createServer(createProtocol(hook));
            server.bind();
            InetSocketAddress bindedAddress = new ProtocolServerUtils(server).retrieveBindedAddress();

            String mailContent = CharStreams.toString(new InputStreamReader(ClassLoader.getSystemResourceAsStream("a50.eml"), Charsets.US_ASCII));
            Thread thread1 = new SendThread(server, bindedAddress, mailContent);
            Thread thread2 = new SendThread(server, bindedAddress, mailContent);
            Thread thread3 = new SendThread(server, bindedAddress, mailContent);
            Thread thread4 = new SendThread(server, bindedAddress, mailContent);
            thread1.start();
            thread2.start();
            thread3.start();
            thread4.start();
            thread1.join(1000);
            thread2.join(1000);
            thread3.join(1000);
            thread4.join(1000);

            Iterator<MailEnvelope> queued = hook.getQueued().iterator();
            assertTrue(queued.hasNext());

            MailEnvelope env = queued.next();
            checkEnvelope(env, SENDER, Arrays.asList(RCPT1, RCPT2), mailContent);
            assertTrue(queued.hasNext());
            MailEnvelope env2 = queued.next();
            checkEnvelope(env2, SENDER, Arrays.asList(RCPT1, RCPT2), mailContent);
            assertTrue(queued.hasNext());
            MailEnvelope env3 = queued.next();
            checkEnvelope(env3, SENDER, Arrays.asList(RCPT1, RCPT2), mailContent);
            assertTrue(queued.hasNext());
            MailEnvelope env4 = queued.next();
            checkEnvelope(env4, SENDER, Arrays.asList(RCPT1, RCPT2), mailContent);
            assertFalse(queued.hasNext());

        } finally {
            if (server != null) {
                server.unbind();
            }
        }
    }
    
    public class SendThread extends Thread {
        private ProtocolServer server;
        private InetSocketAddress bindedAddress;
        private String msg;

        public SendThread(ProtocolServer server, InetSocketAddress bindedAddress, String msg) {
            this.server = server;
            this.bindedAddress = bindedAddress;
            this.msg = msg;
        }
        
        @Override
        public void run() {
            try {
                send(server, bindedAddress, msg);
            } catch (SocketException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

    private void send(ProtocolServer server, InetSocketAddress bindedAddress, String msg) throws SocketException, IOException {
        SMTPClient client = createClient();
        client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
        assertTrue(SMTPReply.isPositiveCompletion(client.getReplyCode()));
        
        client.helo("localhost");
        assertTrue(SMTPReply.isPositiveCompletion(client.getReplyCode()));

        client.setSender(SENDER);
        assertTrue("Reply="+ client.getReplyString(), SMTPReply.isPositiveCompletion(client.getReplyCode()));

        client.addRecipient(RCPT1);
        assertTrue("Reply="+ client.getReplyString(), SMTPReply.isPositiveCompletion(client.getReplyCode()));

        client.addRecipient(RCPT2);
        assertTrue("Reply="+ client.getReplyString(), SMTPReply.isPositiveCompletion(client.getReplyCode()));

        assertTrue(client.sendShortMessageData(msg));
        assertTrue("Reply="+ client.getReplyString(), SMTPReply.isPositiveCompletion(client.getReplyCode()));
        
        client.quit();
        assertTrue("Reply="+ client.getReplyString(), SMTPReply.isPositiveCompletion(client.getReplyCode()));
        client.disconnect();
    }
    
    @Test
    public void testStartTlsNotSupported() throws Exception {
        TestMessageHook hook = new TestMessageHook();
        
        ProtocolServer server = null;
        try {
            server = createServer(createProtocol(hook));
            server.bind();
            
            SMTPClient client = createClient();
            InetSocketAddress bindedAddress = new ProtocolServerUtils(server).retrieveBindedAddress();
            client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
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
        
        ProtocolServer server = null;
        try {
            server = createServer(createProtocol(hook));
            server.bind();
            
            SMTPClient client = createClient();
            InetSocketAddress bindedAddress = new ProtocolServerUtils(server).retrieveBindedAddress();
            client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
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
        
        ProtocolServer server = null;
        try {
            server = createServer(createProtocol(hook));
            server.bind();
            
            SMTPClient client = createClient();
            InetSocketAddress bindedAddress = new ProtocolServerUtils(server).retrieveBindedAddress();
            client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
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
        
        ProtocolServer server = null;
        try {
            server = createServer(createProtocol(hook));
            server.bind();
            
            SMTPClient client = createClient();
            InetSocketAddress bindedAddress = new ProtocolServerUtils(server).retrieveBindedAddress();
            client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
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
        
        ProtocolServer server = null;
        try {
            server = createServer(createProtocol(hook));
            server.bind();
            
            SMTPClient client = createClient();
            InetSocketAddress bindedAddress = new ProtocolServerUtils(server).retrieveBindedAddress();
            client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
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
        
        ProtocolServer server = null;
        try {
            server = createServer(createProtocol(hook));
            server.bind();
            
            SMTPClient client = createClient();
            InetSocketAddress bindedAddress = new ProtocolServerUtils(server).retrieveBindedAddress();
            client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
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
        
        ProtocolServer server = null;
        try {
            Protocol protocol = createProtocol(hook);
            ((SMTPConfigurationImpl) protocol.getConfiguration()).setUseAddressBracketsEnforcement(false);
            server = createServer(protocol);
            server.bind();
            
            SMTPClient client = createClient();
            InetSocketAddress bindedAddress = new ProtocolServerUtils(server).retrieveBindedAddress();
            client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
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
        
        ProtocolServer server = null;
        try {
            server = createServer(createProtocol(hook));
            server.bind();
            
            SMTPClient client = createClient();
            InetSocketAddress bindedAddress = new ProtocolServerUtils(server).retrieveBindedAddress();
            client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
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
        
        ProtocolServer server = null;
        try {
            Protocol protocol = createProtocol(hook);
            ((SMTPConfigurationImpl) protocol.getConfiguration()).setHeloEhloEnforcement(false);
            server = createServer(protocol);
            server.bind();
            
            SMTPClient client = createClient();
            InetSocketAddress bindedAddress = new ProtocolServerUtils(server).retrieveBindedAddress();
            client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
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

            @Override
            public void init(Configuration config) throws ConfigurationException {

            }

            @Override
            public void destroy() {

            }

            public HookResult doHelo(SMTPSession session, String helo) {
                return new HookResult(HookReturnCode.DENY);
            }
        };
        
        ProtocolServer server = null;
        try {
            server = createServer(createProtocol(hook));
            server.bind();
            
            SMTPClient client = createClient();
            InetSocketAddress bindedAddress = new ProtocolServerUtils(server).retrieveBindedAddress();
            client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
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

            @Override
            public void init(Configuration config) throws ConfigurationException {

            }

            @Override
            public void destroy() {

            }

            public HookResult doHelo(SMTPSession session, String helo) {
                return new HookResult(HookReturnCode.DENYSOFT);
            }
        };
        
        ProtocolServer server = null;
        try {
            server = createServer(createProtocol(hook));
            server.bind();
            
            SMTPClient client = createClient();
            InetSocketAddress bindedAddress = new ProtocolServerUtils(server).retrieveBindedAddress();
            client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
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

            @Override
            public void init(Configuration config) throws ConfigurationException {

            }

            @Override
            public void destroy() {

            }

            public HookResult doMail(SMTPSession session, MailAddress sender) {
                return new HookResult(HookReturnCode.DENY);
            }
        };
        
        ProtocolServer server = null;
        try {
            server = createServer(createProtocol(hook));
            server.bind();
            
            SMTPClient client = createClient();
            InetSocketAddress bindedAddress = new ProtocolServerUtils(server).retrieveBindedAddress();
            client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
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

            @Override
            public void init(Configuration config) throws ConfigurationException {

            }

            @Override
            public void destroy() {

            }

            public HookResult doMail(SMTPSession session, MailAddress sender) {
                return new HookResult(HookReturnCode.DENYSOFT);
            }
        };
        
        ProtocolServer server = null;
        try {
            server = createServer(createProtocol(hook));
            server.bind();
            
            SMTPClient client = createClient();
            InetSocketAddress bindedAddress = new ProtocolServerUtils(server).retrieveBindedAddress();
            client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
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

            @Override
            public void init(Configuration config) throws ConfigurationException {

            }

            @Override
            public void destroy() {

            }

            public HookResult doRcpt(SMTPSession session, MailAddress sender, MailAddress rcpt) {
                if (RCPT1.equals(rcpt.toString())) {
                    return new HookResult(HookReturnCode.DENY);
                } else {
                    return new HookResult(HookReturnCode.DECLINED);
                }
            }

        };
        
        ProtocolServer server = null;
        try {
            server = createServer(createProtocol(hook));
            server.bind();
            
            SMTPClient client = createClient();
            InetSocketAddress bindedAddress = new ProtocolServerUtils(server).retrieveBindedAddress();
            client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
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

            @Override
            public void init(Configuration config) throws ConfigurationException {

            }

            @Override
            public void destroy() {

            }

            public HookResult doRcpt(SMTPSession session, MailAddress sender, MailAddress rcpt) {
                if (RCPT1.equals(rcpt.toString())) {
                    return new HookResult(HookReturnCode.DENYSOFT);
                } else {
                    return new HookResult(HookReturnCode.DECLINED);
                }
            }

        };
        
        ProtocolServer server = null;
        try {
            server = createServer(createProtocol(hook));
            server.bind();
            
            SMTPClient client = createClient();
            InetSocketAddress bindedAddress = new ProtocolServerUtils(server).retrieveBindedAddress();
            client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
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
        ProtocolServer server = null;
        try {
            server = createServer(createProtocol(new ProtocolHandler[0]));
            server.bind();
            
            SMTPClient client = createClient();
            InetSocketAddress bindedAddress = new ProtocolServerUtils(server).retrieveBindedAddress();
            client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
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

            @Override
            public void init(Configuration config) throws ConfigurationException {

            }

            @Override
            public void destroy() {

            }

            public HookResult onMessage(SMTPSession session, MailEnvelope mail) {
                return new HookResult(HookReturnCode.DENY);
            }


        };
        
        ProtocolServer server = null;
        try {
            server = createServer(createProtocol(hook, testHook));
            server.bind();
            
            SMTPClient client = createClient();
            InetSocketAddress bindedAddress = new ProtocolServerUtils(server).retrieveBindedAddress();
            client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
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

            @Override
            public void init(Configuration config) throws ConfigurationException {

            }

            @Override
            public void destroy() {

            }

            public HookResult onMessage(SMTPSession session, MailEnvelope mail) {
                return new HookResult(HookReturnCode.DENYSOFT);
            }


        };
        
        ProtocolServer server = null;
        try {
            server = createServer(createProtocol(hook, testHook));
            server.bind();
            
            SMTPClient client = createClient();
            InetSocketAddress bindedAddress = new ProtocolServerUtils(server).retrieveBindedAddress();
            client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
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

            @Override
            public void init(Configuration config) throws ConfigurationException {

            }

            @Override
            public void destroy() {

            }

            public Response onConnect(SMTPSession session) {
                return new SMTPResponse("554", "Bye Bye");
            }
        };
        
        ProtocolServer server = null;
        try {
            
            server = createServer(createProtocol(connectHandler));
            server.bind();
            
            SMTPClient client = createClient();
            InetSocketAddress bindedAddress = new ProtocolServerUtils(server).retrieveBindedAddress();
            client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
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

            @Override
            public void init(Configuration config) throws ConfigurationException {

            }

            @Override
            public void destroy() {

            }

            public Response onConnect(SMTPSession session) {
                return new SMTPResponse("451", "Bye Bye");
            }
        };
        
        ProtocolServer server = null;
        try {
            server = createServer(createProtocol(connectHandler));
            server.bind();
            
            SMTPClient client = createClient();
            InetSocketAddress bindedAddress = new ProtocolServerUtils(server).retrieveBindedAddress();
            client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
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

            @Override
            public void init(Configuration config) throws ConfigurationException {

            }

            @Override
            public void destroy() {

            }

            public void onDisconnect(SMTPSession session) {
                called.set(true);
            }
        };
        
        ProtocolServer server = null;
        try {
            server = createServer(createProtocol(handler));
            server.bind();
            
            SMTPClient client = createClient();
            InetSocketAddress bindedAddress = new ProtocolServerUtils(server).retrieveBindedAddress();
            client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
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

    protected abstract ProtocolServer createServer(Protocol protocol);

    
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
            String msgQueued = sb.subSequence(0, sb.length()).toString();

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
