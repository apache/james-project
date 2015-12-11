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
import static org.junit.Assert.assertTrue;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Arrays;
import java.util.Locale;
import java.util.Properties;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.apache.commons.net.smtp.SMTPReply;
import org.apache.commons.net.smtp.SMTPSClient;
import org.apache.james.protocols.api.Encryption;
import org.apache.james.protocols.api.Protocol;
import org.apache.james.protocols.api.ProtocolServer;
import org.apache.james.protocols.api.handler.ProtocolHandler;
import org.apache.james.protocols.api.handler.WiringException;
import org.apache.james.protocols.api.utils.BogusSSLSocketFactory;
import org.apache.james.protocols.api.utils.BogusSslContextFactory;
import org.apache.james.protocols.api.utils.BogusTrustManagerFactory;
import org.apache.james.protocols.api.utils.MockLogger;
import org.apache.james.protocols.api.utils.TestUtils;
import org.apache.james.protocols.smtp.utils.TestMessageHook;
import org.junit.Test;

import com.sun.mail.smtp.SMTPTransport;

public abstract class AbstractStartTlsSMTPServerTest {
    
    protected SMTPSClient createClient() {
        SMTPSClient client = new SMTPSClient(false, BogusSslContextFactory.getClientContext());
        client.setTrustManager(BogusTrustManagerFactory.getTrustManagers()[0]);
        return client;
    }

    protected abstract ProtocolServer createServer(Protocol protocol, InetSocketAddress address, Encryption enc);

    
    protected Protocol createProtocol(ProtocolHandler... handlers) throws WiringException {
        SMTPProtocolHandlerChain chain = new SMTPProtocolHandlerChain();
        chain.addAll(0, Arrays.asList(handlers));
        chain.wireExtensibleHandlers();
        return new SMTPProtocol(chain, new SMTPConfigurationImpl(), new MockLogger());
    }


    @Test
    public void testStartTLS() throws Exception {
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", TestUtils.getFreePort());
        
        
        ProtocolServer server = null;
        try {
            server = createServer(createProtocol(new ProtocolHandler[0]), address, Encryption.createStartTls(BogusSslContextFactory.getServerContext()));  
            server.bind();
            
            SMTPSClient client = createClient();
            client.connect(address.getAddress().getHostAddress(), address.getPort());
            assertTrue(SMTPReply.isPositiveCompletion(client.getReplyCode()));
            
            client.sendCommand("EHLO localhost");
            assertTrue(SMTPReply.isPositiveCompletion(client.getReplyCode()));
            
            boolean startTLSAnnounced = false;
            for (String reply: client.getReplyStrings()) {
                if (reply.toUpperCase(Locale.UK).endsWith("STARTTLS")) {
                    startTLSAnnounced = true;
                    break;
                }
            }
            assertTrue(startTLSAnnounced);
            
            assertTrue(client.execTLS());
            
            // TODO: Add back once commons-net 3.1.0 was released.
            // See: NET-421
            //
            //client.quit();
            //assertTrue("Reply="+ client.getReplyString(), SMTPReply.isPositiveCompletion(client.getReplyCode()));
            
            client.disconnect();


        } finally {
            if (server != null) {
                server.unbind();
            }
        }
        
    }
    
    @Test
    public void testStartTLSWithJavamail() throws Exception {
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", TestUtils.getFreePort());
        
        
        ProtocolServer server = null;
        try {
            TestMessageHook hook = new TestMessageHook();
            server = createServer(createProtocol(hook) , address, Encryption.createStartTls(BogusSslContextFactory.getServerContext()));  
            server.bind();
            
            
            Properties mailProps = new Properties();
            mailProps.put("mail.smtp.from", "test@localhost");
            mailProps.put("mail.smtp.host", address.getHostName());
            mailProps.put("mail.smtp.port", address.getPort());
            mailProps.put("mail.smtp.socketFactory.class", BogusSSLSocketFactory.class.getName());
            mailProps.put("mail.smtp.socketFactory.fallback", "false");
            mailProps.put("mail.smtp.starttls.enable", "true");

            Session mailSession = Session.getDefaultInstance(mailProps);

            MimeMessage message = new MimeMessage(mailSession);
            message.setFrom(new InternetAddress("test@localhost"));
            String[] emails = { "valid@localhost" };
            Address rcpts[] = new Address[emails.length];
            for (int i = 0; i < emails.length; i++) {
                rcpts[i] = new InternetAddress(emails[i].trim().toLowerCase());
            }
            message.setRecipients(Message.RecipientType.TO, rcpts);
            message.setSubject("Testmail", "UTF-8");
            message.setText("Test.....");

            SMTPTransport transport = (SMTPTransport) mailSession.getTransport("smtps");
            
            transport.connect(new Socket(address.getHostName(), address.getPort()));
            transport.sendMessage(message, rcpts);
            
           
            assertEquals(1, hook.getQueued().size());
            


        } finally {
            if (server != null) {
                server.unbind();
            }
        }
        
    }
    
}
