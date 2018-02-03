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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.net.smtp.SMTPClient;
import org.apache.commons.net.smtp.SMTPReply;
import org.apache.james.core.MailAddress;
import org.apache.james.metrics.api.NoopMetricFactory;
import org.apache.james.protocols.api.Protocol;
import org.apache.james.protocols.api.ProtocolServer;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.api.handler.ConnectHandler;
import org.apache.james.protocols.api.handler.DisconnectHandler;
import org.apache.james.protocols.api.handler.ProtocolHandler;
import org.apache.james.protocols.api.handler.WiringException;
import org.apache.james.protocols.api.utils.ProtocolServerUtils;
import org.apache.james.protocols.smtp.hook.HeloHook;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.protocols.smtp.hook.HookReturnCode;
import org.apache.james.protocols.smtp.hook.MailHook;
import org.apache.james.protocols.smtp.hook.MessageHook;
import org.apache.james.protocols.smtp.hook.RcptHook;
import org.apache.james.protocols.smtp.utils.TestMessageHook;
import org.apache.james.util.concurrency.ConcurrentTestRunner;
import org.junit.Test;

import com.google.common.io.CharStreams;

public abstract class AbstractSMTPServerTest {

    protected static final String MSG1 = "Subject: Testmessage\r\n\r\nThis is a message\r\n";
    protected static final String SENDER = "me@sender";
    protected static final String RCPT1 = "rpct1@domain";
    protected static final String RCPT2 = "rpct2@domain";

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
            assertThat(queued.hasNext()).isTrue();
            
            MailEnvelope env = queued.next();
            checkEnvelope(env, SENDER, Arrays.asList(RCPT1, RCPT2), MSG1);
            assertThat(queued.hasNext()).isFalse();

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

            final ProtocolServer finalServer = server;
            final InetSocketAddress bindedAddress = new ProtocolServerUtils(server).retrieveBindedAddress();
            final String mailContent = CharStreams.toString(new InputStreamReader(ClassLoader.getSystemResourceAsStream("a50.eml"), StandardCharsets.US_ASCII));
            int threadCount = 4;
            int updateCount = 1;
            assertThat(new ConcurrentTestRunner(threadCount, updateCount,
                (threadNumber, step) -> send(finalServer, bindedAddress, mailContent)).run()
                .awaitTermination(1, TimeUnit.MINUTES))
                .isTrue();

            Iterator<MailEnvelope> queued = hook.getQueued().iterator();
            assertThat(queued.hasNext()).isTrue();

            MailEnvelope env = queued.next();
            checkEnvelope(env, SENDER, Arrays.asList(RCPT1, RCPT2), mailContent);
            assertThat(queued.hasNext()).isTrue();
            MailEnvelope env2 = queued.next();
            checkEnvelope(env2, SENDER, Arrays.asList(RCPT1, RCPT2), mailContent);
            assertThat(queued.hasNext()).isTrue();
            MailEnvelope env3 = queued.next();
            checkEnvelope(env3, SENDER, Arrays.asList(RCPT1, RCPT2), mailContent);
            assertThat(queued.hasNext()).isTrue();
            MailEnvelope env4 = queued.next();
            checkEnvelope(env4, SENDER, Arrays.asList(RCPT1, RCPT2), mailContent);
            assertThat(queued.hasNext()).isFalse();

        } finally {
            if (server != null) {
                server.unbind();
            }
        }
    }

    private void send(ProtocolServer server, InetSocketAddress bindedAddress, String msg) throws SocketException, IOException {
        SMTPClient client = createClient();
        client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
        assertThat(SMTPReply.isPositiveCompletion(client.getReplyCode())).isTrue();
        
        client.helo("localhost");
        assertThat(SMTPReply.isPositiveCompletion(client.getReplyCode())).isTrue();

        client.setSender(SENDER);
        assertThat(SMTPReply.isPositiveCompletion(client.getReplyCode())).as("Reply=" + client.getReplyString()).isTrue();

        client.addRecipient(RCPT1);
        assertThat(SMTPReply.isPositiveCompletion(client.getReplyCode())).as("Reply=" + client.getReplyString()).isTrue();

        client.addRecipient(RCPT2);
        assertThat(SMTPReply.isPositiveCompletion(client.getReplyCode())).as("Reply=" + client.getReplyString()).isTrue();

        assertThat(client.sendShortMessageData(msg)).isTrue();
        assertThat(SMTPReply.isPositiveCompletion(client.getReplyCode())).as("Reply=" + client.getReplyString()).isTrue();
        
        client.quit();
        assertThat(SMTPReply.isPositiveCompletion(client.getReplyCode())).as("Reply=" + client.getReplyString()).isTrue();
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
            assertThat(SMTPReply.isPositiveCompletion(client.getReplyCode())).isTrue();
            
            client.sendCommand("STARTTLS");
            assertThat(SMTPReply.isNegativePermanent(client.getReplyCode())).isTrue();

            
            client.quit();
            assertThat(SMTPReply.isPositiveCompletion(client.getReplyCode())).as("Reply=" + client.getReplyString()).isTrue();
            client.disconnect();
            Iterator<MailEnvelope> queued = hook.getQueued().iterator();
            assertThat(queued.hasNext()).isFalse();

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
            assertThat(SMTPReply.isPositiveCompletion(client.getReplyCode())).isTrue();
            
            client.sendCommand("UNKNOWN");
            assertThat(SMTPReply.isNegativePermanent(client.getReplyCode())).isTrue();

            
            client.quit();
            assertThat(SMTPReply.isPositiveCompletion(client.getReplyCode())).as("Reply=" + client.getReplyString()).isTrue();
            client.disconnect();
            Iterator<MailEnvelope> queued = hook.getQueued().iterator();
            assertThat(queued.hasNext()).isFalse();

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
            assertThat(SMTPReply.isPositiveCompletion(client.getReplyCode())).isTrue();
            
            client.noop();
            assertThat(SMTPReply.isPositiveCompletion(client.getReplyCode())).isTrue();

            
            client.quit();
            assertThat(SMTPReply.isPositiveCompletion(client.getReplyCode())).as("Reply=" + client.getReplyString()).isTrue();
            client.disconnect();
            Iterator<MailEnvelope> queued = hook.getQueued().iterator();
            assertThat(queued.hasNext()).isFalse();

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
            assertThat(SMTPReply.isPositiveCompletion(client.getReplyCode())).isTrue();
            
            client.helo("localhost");
            assertThat(SMTPReply.isPositiveCompletion(client.getReplyCode())).isTrue();

            client.mail("invalid");
            assertThat(SMTPReply.isNegativePermanent(client.getReplyCode())).as("Reply=" + client.getReplyString()).isTrue();

            client.addRecipient(RCPT1);
            assertThat(SMTPReply.isNegativePermanent(client.getReplyCode())).as("Reply=" + client.getReplyString()).isTrue();

           
            
            client.quit();
            assertThat(SMTPReply.isPositiveCompletion(client.getReplyCode())).as("Reply=" + client.getReplyString()).isTrue();
            client.disconnect();

            Iterator<MailEnvelope> queued = hook.getQueued().iterator();
            assertThat(queued.hasNext()).isFalse();

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
            assertThat(SMTPReply.isPositiveCompletion(client.getReplyCode())).isTrue();
            
            client.helo("");
            assertThat(SMTPReply.isNegativePermanent(client.getReplyCode())).as("Reply=" + client.getReplyString()).isTrue();
            
            client.quit();
            assertThat(SMTPReply.isPositiveCompletion(client.getReplyCode())).as("Reply=" + client.getReplyString()).isTrue();
            client.disconnect();

            Iterator<MailEnvelope> queued = hook.getQueued().iterator();
            assertThat(queued.hasNext()).isFalse();

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
            assertThat(SMTPReply.isPositiveCompletion(client.getReplyCode())).isTrue();
            
            client.helo("localhost");
            assertThat(SMTPReply.isPositiveCompletion(client.getReplyCode())).isTrue();

            client.setSender(SENDER);
            assertThat(SMTPReply.isPositiveCompletion(client.getReplyCode())).as("Reply=" + client.getReplyString()).isTrue();

            client.rcpt(RCPT1);
            assertThat(SMTPReply.isNegativePermanent(client.getReplyCode())).as("Reply=" + client.getReplyString()).isTrue();

           
            
            client.quit();
            assertThat(SMTPReply.isPositiveCompletion(client.getReplyCode())).as("Reply=" + client.getReplyString()).isTrue();
            client.disconnect();

            Iterator<MailEnvelope> queued = hook.getQueued().iterator();
            assertThat(queued.hasNext()).isFalse();

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
            assertThat(SMTPReply.isPositiveCompletion(client.getReplyCode())).as("Reply=" + client.getReplyString()).isTrue();
            
            client.helo("localhost");
            assertThat(SMTPReply.isPositiveCompletion(client.getReplyCode())).as("Reply=" + client.getReplyString()).isTrue();

            client.mail(SENDER);
            assertThat(SMTPReply.isPositiveCompletion(client.getReplyCode())).as("Reply=" + client.getReplyString()).isTrue();

            client.addRecipient(RCPT1);
            assertThat(SMTPReply.isPositiveCompletion(client.getReplyCode())).as("Reply=" + client.getReplyString()).isTrue();

           
            
            client.quit();
            assertThat(SMTPReply.isPositiveCompletion(client.getReplyCode())).as("Reply=" + client.getReplyString()).isTrue();
            client.disconnect();

            Iterator<MailEnvelope> queued = hook.getQueued().iterator();
            assertThat(queued.hasNext()).isFalse();

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
            assertThat(SMTPReply.isPositiveCompletion(client.getReplyCode())).as("Reply=" + client.getReplyString()).isTrue();
            
           
            client.setSender(SENDER);
            assertThat(SMTPReply.isNegativePermanent(client.getReplyCode())).as("Reply=" + client.getReplyString()).isTrue();

         
            client.quit();
            assertThat(SMTPReply.isPositiveCompletion(client.getReplyCode())).as("Reply=" + client.getReplyString()).isTrue();
            client.disconnect();

            Iterator<MailEnvelope> queued = hook.getQueued().iterator();
            assertThat(queued.hasNext()).isFalse();

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
            assertThat(SMTPReply.isPositiveCompletion(client.getReplyCode())).as("Reply=" + client.getReplyString()).isTrue();
            
           
            client.setSender(SENDER);
            assertThat(SMTPReply.isPositiveCompletion(client.getReplyCode())).as("Reply=" + client.getReplyString()).isTrue();

         
            client.quit();
            assertThat(SMTPReply.isPositiveCompletion(client.getReplyCode())).as("Reply=" + client.getReplyString()).isTrue();
            client.disconnect();

            Iterator<MailEnvelope> queued = hook.getQueued().iterator();
            assertThat(queued.hasNext()).isFalse();

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
            assertThat(SMTPReply.isPositiveCompletion(client.getReplyCode())).as("Reply=" + client.getReplyString()).isTrue();
            
           
            client.helo("localhost");
            assertThat(SMTPReply.isNegativePermanent(client.getReplyCode())).as("Reply=" + client.getReplyString()).isTrue();

         
            client.quit();
            assertThat(SMTPReply.isPositiveCompletion(client.getReplyCode())).as("Reply=" + client.getReplyString()).isTrue();
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
            assertThat(SMTPReply.isPositiveCompletion(client.getReplyCode())).as("Reply=" + client.getReplyString()).isTrue();
            
           
            client.helo("localhost");
            assertThat(SMTPReply.isNegativeTransient(client.getReplyCode())).as("Reply=" + client.getReplyString()).isTrue();

         
            client.quit();
            assertThat(SMTPReply.isPositiveCompletion(client.getReplyCode())).as("Reply=" + client.getReplyString()).isTrue();
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
            assertThat(SMTPReply.isPositiveCompletion(client.getReplyCode())).as("Reply=" + client.getReplyString()).isTrue();
            
           
            client.helo("localhost");
            assertThat(SMTPReply.isPositiveCompletion(client.getReplyCode())).as("Reply=" + client.getReplyString()).isTrue();

            client.setSender(SENDER);
            assertThat(SMTPReply.isNegativePermanent(client.getReplyCode())).as("Reply=" + client.getReplyString()).isTrue();

         
            client.quit();
            assertThat(SMTPReply.isPositiveCompletion(client.getReplyCode())).as("Reply=" + client.getReplyString()).isTrue();
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
            assertThat(SMTPReply.isPositiveCompletion(client.getReplyCode())).as("Reply=" + client.getReplyString()).isTrue();
            
           
            client.helo("localhost");
            assertThat(SMTPReply.isPositiveCompletion(client.getReplyCode())).as("Reply=" + client.getReplyString()).isTrue();

            client.setSender(SENDER);
            assertThat(SMTPReply.isNegativeTransient(client.getReplyCode())).as("Reply=" + client.getReplyString()).isTrue();

         
            client.quit();
            assertThat(SMTPReply.isPositiveCompletion(client.getReplyCode())).as("Reply=" + client.getReplyString()).isTrue();
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
            assertThat(SMTPReply.isPositiveCompletion(client.getReplyCode())).as("Reply=" + client.getReplyString()).isTrue();
            
           
            client.helo("localhost");
            assertThat(SMTPReply.isPositiveCompletion(client.getReplyCode())).as("Reply=" + client.getReplyString()).isTrue();

            client.setSender(SENDER);
            assertThat(SMTPReply.isPositiveCompletion(client.getReplyCode())).as("Reply=" + client.getReplyString()).isTrue();

            client.addRecipient(RCPT1);
            assertThat(SMTPReply.isNegativePermanent(client.getReplyCode())).as("Reply=" + client.getReplyString()).isTrue();

         
            client.addRecipient(RCPT2);
            assertThat(SMTPReply.isPositiveCompletion(client.getReplyCode())).as("Reply=" + client.getReplyString()).isTrue();

            
            client.quit();
            assertThat(SMTPReply.isPositiveCompletion(client.getReplyCode())).as("Reply=" + client.getReplyString()).isTrue();
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
            assertThat(SMTPReply.isPositiveCompletion(client.getReplyCode())).as("Reply=" + client.getReplyString()).isTrue();
            
           
            client.helo("localhost");
            assertThat(SMTPReply.isPositiveCompletion(client.getReplyCode())).as("Reply=" + client.getReplyString()).isTrue();

            client.setSender(SENDER);
            assertThat(SMTPReply.isPositiveCompletion(client.getReplyCode())).as("Reply=" + client.getReplyString()).isTrue();

            client.addRecipient(RCPT1);
            assertThat(SMTPReply.isNegativeTransient(client.getReplyCode())).as("Reply=" + client.getReplyString()).isTrue();

         
            client.addRecipient(RCPT2);
            assertThat(SMTPReply.isPositiveCompletion(client.getReplyCode())).as("Reply=" + client.getReplyString()).isTrue();

            
            client.quit();
            assertThat(SMTPReply.isPositiveCompletion(client.getReplyCode())).as("Reply=" + client.getReplyString()).isTrue();
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
            assertThat(SMTPReply.isPositiveCompletion(client.getReplyCode())).as("Reply=" + client.getReplyString()).isTrue();
            
           
            client.helo("localhost");
            assertThat(SMTPReply.isPositiveCompletion(client.getReplyCode())).as("Reply=" + client.getReplyString()).isTrue();

            client.setSender("");
            assertThat(SMTPReply.isPositiveCompletion(client.getReplyCode())).as("Reply=" + client.getReplyString()).isTrue();
         
            client.addRecipient(RCPT1);
            assertThat(SMTPReply.isPositiveCompletion(client.getReplyCode())).as("Reply=" + client.getReplyString()).isTrue();

            
            client.quit();
            assertThat(SMTPReply.isPositiveCompletion(client.getReplyCode())).as("Reply=" + client.getReplyString()).isTrue();
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
            assertThat(SMTPReply.isPositiveCompletion(client.getReplyCode())).as("Reply=" + client.getReplyString()).isTrue();
            
           
            client.helo("localhost");
            assertThat(SMTPReply.isPositiveCompletion(client.getReplyCode())).as("Reply=" + client.getReplyString()).isTrue();

            client.setSender(SENDER);
            assertThat(SMTPReply.isPositiveCompletion(client.getReplyCode())).as("Reply=" + client.getReplyString()).isTrue();
         
            client.addRecipient(RCPT2);
            assertThat(SMTPReply.isPositiveCompletion(client.getReplyCode())).as("Reply=" + client.getReplyString()).isTrue();

            assertThat(client.sendShortMessageData(MSG1)).isFalse();
            assertThat(SMTPReply.isNegativePermanent(client.getReplyCode())).as("Reply=" + client.getReplyString()).isTrue();
            
            client.quit();
            assertThat(SMTPReply.isPositiveCompletion(client.getReplyCode())).as("Reply=" + client.getReplyString()).isTrue();
            client.disconnect();

            Iterator<MailEnvelope> queued = testHook.getQueued().iterator();
            assertThat(queued.hasNext()).isFalse();


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
            assertThat(SMTPReply.isPositiveCompletion(client.getReplyCode())).as("Reply=" + client.getReplyString()).isTrue();
            
           
            client.helo("localhost");
            assertThat(SMTPReply.isPositiveCompletion(client.getReplyCode())).as("Reply=" + client.getReplyString()).isTrue();

            client.setSender(SENDER);
            assertThat(SMTPReply.isPositiveCompletion(client.getReplyCode())).as("Reply=" + client.getReplyString()).isTrue();
         
            client.addRecipient(RCPT2);
            assertThat(SMTPReply.isPositiveCompletion(client.getReplyCode())).as("Reply=" + client.getReplyString()).isTrue();

            assertThat(client.sendShortMessageData(MSG1)).isFalse();
            assertThat(SMTPReply.isNegativeTransient(client.getReplyCode())).as("Reply=" + client.getReplyString()).isTrue();
            
            client.quit();
            assertThat(SMTPReply.isPositiveCompletion(client.getReplyCode())).as("Reply=" + client.getReplyString()).isTrue();
            client.disconnect();

            Iterator<MailEnvelope> queued = testHook.getQueued().iterator();
            assertThat(queued.hasNext()).isFalse();


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
            assertThat(SMTPReply.isNegativePermanent(client.getReplyCode())).as("Reply=" + client.getReplyString()).isTrue();
            
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
            assertThat(SMTPReply.isNegativeTransient(client.getReplyCode())).as("Reply=" + client.getReplyString()).isTrue();
            
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
            assertThat(SMTPReply.isPositiveCompletion(client.getReplyCode())).as("Reply=" + client.getReplyString()).isTrue();
            
            client.disconnect();
            
            Thread.sleep(1000);
            assertThat(called.get()).isTrue();


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
        SMTPProtocolHandlerChain chain = new SMTPProtocolHandlerChain(new NoopMetricFactory());
        chain.addAll(0, Arrays.asList(handlers));
        chain.wireExtensibleHandlers();
        return new SMTPProtocol(chain, new SMTPConfigurationImpl());
    }
    
    protected static void checkEnvelope(MailEnvelope env, String sender, List<String> recipients, String msg) throws IOException {
        assertThat(env.getSender().toString()).isEqualTo(sender);

        List<MailAddress> envRecipients = env.getRecipients();
        assertThat(envRecipients.size()).isEqualTo(recipients.size());
        for (int i = 0; i < recipients.size(); i++) {
            MailAddress address = envRecipients.get(i);
            assertThat(address.toString()).isEqualTo(recipients.get(i));
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

            assertThat(msgQueued.length()).isEqualTo(msg.length());
            for (int i = 0; i < msg.length(); i++) {
                assertThat(msgQueued.charAt(i)).isEqualTo(msg.charAt(i));
            }
        } finally {
            if (reader != null) {
                reader.close();
            }
        }

    }
    
}
