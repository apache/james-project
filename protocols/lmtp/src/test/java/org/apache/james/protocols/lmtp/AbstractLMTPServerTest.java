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
package org.apache.james.protocols.lmtp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.net.smtp.RelayPath;
import org.apache.commons.net.smtp.SMTPClient;
import org.apache.commons.net.smtp.SMTPReply;
import org.apache.james.protocols.api.Protocol;
import org.apache.james.protocols.api.ProtocolServer;
import org.apache.james.protocols.api.handler.ProtocolHandler;
import org.apache.james.protocols.api.handler.WiringException;
import org.apache.james.protocols.api.utils.MockLogger;
import org.apache.james.protocols.api.utils.ProtocolServerUtils;
import org.apache.james.protocols.lmtp.hook.DeliverToRecipientHook;
import org.apache.james.protocols.smtp.AbstractSMTPServerTest;
import org.apache.james.protocols.smtp.MailAddress;
import org.apache.james.protocols.smtp.MailEnvelope;
import org.apache.james.protocols.smtp.SMTPProtocol;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.protocols.smtp.hook.HookReturnCode;
import org.apache.james.protocols.smtp.hook.MessageHook;
import org.apache.james.protocols.smtp.utils.TestMessageHook;
import org.junit.Ignore;
import org.junit.Test;

public abstract class AbstractLMTPServerTest extends AbstractSMTPServerTest{

    @Override
    protected Protocol createProtocol(ProtocolHandler... handlers) throws WiringException {
        LMTPProtocolHandlerChain chain = new LMTPProtocolHandlerChain();
        List<ProtocolHandler> hList = new ArrayList<>();

        for (ProtocolHandler handler : handlers) {
            if (handler instanceof MessageHook) {
                handler = new MessageHookAdapter((MessageHook) handler);
            }
            hList.add(handler);
        }
        chain.addAll(0, hList);
        chain.wireExtensibleHandlers();
        return new SMTPProtocol(chain, new LMTPConfigurationImpl(), new MockLogger());
    }
    

    @Ignore("LMTP can't handle the queue")
    @Override
    public void testDeliveryWith4SimultaneousThreads() {
    }

    @Ignore("Disable")
    @Override
    public void testInvalidNoBracketsEnformance() throws Exception {
    }


    @Ignore("Disable")
    @Override
    public void testHeloEnforcement() throws Exception {
    }


    @Ignore("Disable")
    @Override
    public void testHeloEnforcementDisabled() throws Exception {
    }


    @Override
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

            client.mail(SENDER);
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


    @Override
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
    public void testEhloNotSupported() throws Exception {
        TestMessageHook hook = new TestMessageHook();
        ProtocolServer server = null;
        try {
            server = createServer(createProtocol(hook));
            server.bind();
            
            SMTPClient client = createClient();
            InetSocketAddress bindedAddress = new ProtocolServerUtils(server).retrieveBindedAddress();
            client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
            assertTrue(SMTPReply.isPositiveCompletion(client.getReplyCode()));
            
            client.sendCommand("HELO localhost");
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
    public void testDeliveryHook() throws Exception {
        TestDeliverHook deliverHook = new TestDeliverHook();
        
        ProtocolServer server = null;
        try {
            server = createServer(createProtocol(deliverHook));
            server.bind();
            
            SMTPClient client = createClient();
            InetSocketAddress bindedAddress = new ProtocolServerUtils(server).retrieveBindedAddress();
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

            assertTrue(client.sendShortMessageData(MSG1));

            int[] replies = ((LMTPClient)client).getReplies();
            
            assertEquals("Expected two replies",2, replies.length);
            
            assertTrue(SMTPReply.isNegativePermanent(replies[0]));
            assertTrue(SMTPReply.isPositiveCompletion(replies[1]));

            client.quit();
            assertTrue("Reply="+ client.getReplyString(), SMTPReply.isPositiveCompletion(client.getReplyCode()));
            client.disconnect();

            Iterator<MailEnvelope> queued = deliverHook.getDelivered().iterator();
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
    
    protected SMTPClient createClient() {
        return new LMTPClientImpl();
    }
    
    private final class LMTPClientImpl extends SMTPClient implements LMTPClient {

        private final List<Integer> replies = new ArrayList<>();
        private int rcptCount = 0;
        
        
        @Override
        public boolean addRecipient(String address) throws IOException {
            boolean ok = super.addRecipient(address);
            if (ok) {
                rcptCount++;
            }
            return ok;
        }

        @Override
        public boolean addRecipient(RelayPath path) throws IOException {
            boolean ok = super.addRecipient(path);
            if (ok) {
                rcptCount++;
            }
            return ok;
        }

        /**
         * Issue the LHLO command
         */
        @Override
        public int helo(String hostname) throws IOException {
            return sendCommand("LHLO", hostname);
        }

        public int[] getReplies() throws IOException
        {
            int[] codes = new int[replies.size()];
            for (int i = 0; i < codes.length; i++) {
                codes[i] = replies.remove(0);
            }
            return codes;
        }
        
        @Override
        public boolean completePendingCommand() throws IOException
        {
            for (int i = 0; i < rcptCount; i++) {
                replies.add(getReply());
            }

            return replies.stream()
                .mapToInt(code -> code)
                .anyMatch(SMTPReply::isPositiveCompletion);
        }

        
    }
    
    private final class MessageHookAdapter implements DeliverToRecipientHook {

        private final MessageHook hook;
        private  HookResult result;

        public MessageHookAdapter(MessageHook hook) {
            this.hook = hook;
        }
        
        /*
         * (non-Javadoc)
         * @see org.apache.james.protocols.lmtp.hook.DeliverToRecipientHook#deliver(org.apache.james.protocols.smtp.SMTPSession, org.apache.james.protocols.smtp.MailAddress, org.apache.james.protocols.smtp.MailEnvelope)
         */
        public HookResult deliver(SMTPSession session, MailAddress recipient, MailEnvelope envelope) {
            if (result == null) {
                result = hook.onMessage(session, envelope);
            } 
            return result;
        }

        @Override
        public void init(Configuration config) throws ConfigurationException {

        }

        @Override
        public void destroy() {

        }
    }
    
    private final class TestDeliverHook implements DeliverToRecipientHook {
        
        private final List<MailEnvelope> delivered = new ArrayList<>();
        
        /*
         * (non-Javadoc)
         * @see org.apache.james.protocols.lmtp.hook.DeliverToRecipientHook#deliver(org.apache.james.protocols.smtp.SMTPSession, org.apache.james.protocols.smtp.MailAddress, org.apache.james.protocols.smtp.MailEnvelope)
         */
        public HookResult deliver(SMTPSession session, MailAddress recipient, MailEnvelope envelope) {
            if (RCPT1.equals(recipient.toString())) {
                return new HookResult(HookReturnCode.DENY);
            } else {
                delivered.add(envelope);
                return new HookResult(HookReturnCode.OK);
            }
        }
        
        public List<MailEnvelope> getDelivered() {
            return delivered;
        }

        @Override
        public void init(Configuration config) throws ConfigurationException {

        }

        @Override
        public void destroy() {

        }
    }

}
