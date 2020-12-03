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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.net.smtp.RelayPath;
import org.apache.commons.net.smtp.SMTPClient;
import org.apache.commons.net.smtp.SMTPReply;
import org.apache.james.core.MailAddress;
import org.apache.james.protocols.api.Protocol;
import org.apache.james.protocols.api.ProtocolServer;
import org.apache.james.protocols.api.handler.ProtocolHandler;
import org.apache.james.protocols.api.handler.WiringException;
import org.apache.james.protocols.api.utils.ProtocolServerUtils;
import org.apache.james.protocols.lmtp.hook.DeliverToRecipientHook;
import org.apache.james.protocols.smtp.AbstractSMTPServerTest;
import org.apache.james.protocols.smtp.MailEnvelope;
import org.apache.james.protocols.smtp.SMTPProtocol;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.protocols.smtp.hook.MessageHook;
import org.apache.james.protocols.smtp.utils.TestMessageHook;
import org.junit.Ignore;
import org.junit.jupiter.api.Test;

public abstract class AbstractLMTPServerTest extends AbstractSMTPServerTest {

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
        return new SMTPProtocol(chain, new LMTPConfigurationImpl());
    }
    

    @Ignore("LMTP can't handle the queue")
    @Override
    protected void testDeliveryWith4SimultaneousThreads() {
    }

    @Ignore("Disable")
    @Override
    protected void testInvalidNoBracketsEnformance() throws Exception {
    }


    @Ignore("Disable")
    @Override
    protected void testHeloEnforcement() throws Exception {
    }


    @Ignore("Disable")
    @Override
    public void testHeloEnforcementDisabled() throws Exception {
    }


    @Override
    protected void testMailWithoutBrackets() throws Exception {
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

            client.mail(SENDER);
            assertThat(SMTPReply.isPositiveCompletion(client.getReplyCode())).describedAs("Reply=" + client.getReplyString()).isTrue();
            
            client.quit();
            assertThat(SMTPReply.isPositiveCompletion(client.getReplyCode())).describedAs("Reply=" + client.getReplyString()).isTrue();
            client.disconnect();

            Iterator<MailEnvelope> queued = hook.getQueued().iterator();
            assertThat(queued.hasNext()).isFalse();

        } finally {
            if (server != null) {
                server.unbind();
            }
        }
        
    }


    @Override
    protected void testRcptWithoutBrackets() throws Exception {
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
            assertThat(SMTPReply.isPositiveCompletion(client.getReplyCode())).describedAs("Reply=" + client.getReplyString()).isTrue();

            client.rcpt(RCPT1);
            assertThat(SMTPReply.isPositiveCompletion(client.getReplyCode())).describedAs("Reply=" + client.getReplyString()).isTrue();
            client.quit();
            assertThat(SMTPReply.isPositiveCompletion(client.getReplyCode())).describedAs("Reply=" + client.getReplyString()).isTrue();
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
    protected void testEhloNotSupported() throws Exception {
        TestMessageHook hook = new TestMessageHook();
        ProtocolServer server = null;
        try {
            server = createServer(createProtocol(hook));
            server.bind();
            
            SMTPClient client = createClient();
            InetSocketAddress bindedAddress = new ProtocolServerUtils(server).retrieveBindedAddress();
            client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
            assertThat(SMTPReply.isPositiveCompletion(client.getReplyCode())).isTrue();
            
            client.sendCommand("HELO localhost");
            assertThat(SMTPReply.isNegativePermanent(client.getReplyCode())).isTrue();
            
            client.quit();
            assertThat(SMTPReply.isPositiveCompletion(client.getReplyCode())).describedAs("Reply=" + client.getReplyString()).isTrue();
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
    void testDeliveryHook() throws Exception {
        TestDeliverHook deliverHook = new TestDeliverHook();
        
        ProtocolServer server = null;
        try {
            server = createServer(createProtocol(deliverHook));
            server.bind();
            
            SMTPClient client = createClient();
            InetSocketAddress bindedAddress = new ProtocolServerUtils(server).retrieveBindedAddress();
            client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
            assertThat(SMTPReply.isPositiveCompletion(client.getReplyCode())).isTrue();
            
            client.helo("localhost");
            assertThat(SMTPReply.isPositiveCompletion(client.getReplyCode())).isTrue();

            client.setSender(SENDER);
            assertThat(SMTPReply.isPositiveCompletion(client.getReplyCode())).describedAs("Reply=" + client.getReplyString()).isTrue();

            client.addRecipient(RCPT1);
            assertThat(SMTPReply.isPositiveCompletion(client.getReplyCode())).describedAs("Reply=" + client.getReplyString()).isTrue();

            client.addRecipient(RCPT2);
            assertThat(SMTPReply.isPositiveCompletion(client.getReplyCode())).describedAs("Reply=" + client.getReplyString()).isTrue();

            assertThat(client.sendShortMessageData(MSG1)).isTrue();

            int[] replies = ((LMTPClient)client).getReplies();
            
            assertThat(replies.length).describedAs("Expected two replies").isEqualTo(2);
            
            assertThat(SMTPReply.isNegativePermanent(replies[0])).isTrue();
            assertThat(SMTPReply.isPositiveCompletion(replies[1])).isTrue();

            client.quit();
            assertThat(SMTPReply.isPositiveCompletion(client.getReplyCode())).describedAs("Reply=" + client.getReplyString()).isTrue();
            client.disconnect();

            Iterator<MailEnvelope> queued = deliverHook.getDelivered().iterator();
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
    
    @Override
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

        @Override
        public int[] getReplies() throws IOException {
            int[] codes = new int[replies.size()];
            for (int i = 0; i < codes.length; i++) {
                codes[i] = replies.remove(0);
            }
            return codes;
        }
        
        @Override
        public boolean completePendingCommand() throws IOException {
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

        @Override
        public HookResult deliver(SMTPSession session, MailAddress recipient, MailEnvelope envelope) {
            if (result == null) {
                result = hook.onMessage(session, envelope);
            } 
            return result;
        }
    }
    
    private final class TestDeliverHook implements DeliverToRecipientHook {
        
        private final List<MailEnvelope> delivered = new ArrayList<>();

        @Override
        public HookResult deliver(SMTPSession session, MailAddress recipient, MailEnvelope envelope) {
            if (RCPT1.equals(recipient.toString())) {
                return HookResult.DENY;
            } else {
                delivered.add(envelope);
                return HookResult.OK;
            }
        }
        
        public List<MailEnvelope> getDelivered() {
            return delivered;
        }
    }

}
