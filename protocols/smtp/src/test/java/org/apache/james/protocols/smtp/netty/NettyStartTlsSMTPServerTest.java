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
package org.apache.james.protocols.smtp.netty;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;

import javax.mail.Message;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.apache.commons.net.smtp.SMTPReply;
import org.apache.commons.net.smtp.SMTPSClient;
import org.apache.james.metrics.api.NoopMetricFactory;
import org.apache.james.protocols.api.Encryption;
import org.apache.james.protocols.api.Protocol;
import org.apache.james.protocols.api.ProtocolServer;
import org.apache.james.protocols.api.handler.ProtocolHandler;
import org.apache.james.protocols.api.handler.WiringException;
import org.apache.james.protocols.api.utils.BogusSSLSocketFactory;
import org.apache.james.protocols.api.utils.BogusSslContextFactory;
import org.apache.james.protocols.api.utils.BogusTrustManagerFactory;
import org.apache.james.protocols.api.utils.ProtocolServerUtils;
import org.apache.james.protocols.netty.AbstractChannelPipelineFactory;
import org.apache.james.protocols.netty.NettyServer;
import org.apache.james.protocols.smtp.AllButStartTlsLineChannelHandlerFactory;
import org.apache.james.protocols.smtp.SMTPConfigurationImpl;
import org.apache.james.protocols.smtp.SMTPProtocol;
import org.apache.james.protocols.smtp.SMTPProtocolHandlerChain;
import org.apache.james.protocols.smtp.utils.TestMessageHook;
import org.assertj.core.api.AssertDelegateTarget;
import org.jboss.netty.util.HashedWheelTimer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.sun.mail.smtp.SMTPTransport;

public class NettyStartTlsSMTPServerTest {

    private static final String LOCALHOST_IP = "127.0.0.1";
    private static final int RANDOM_PORT = 0;

    private SMTPSClient smtpsClient = null;
    private ProtocolServer server = null;
    private HashedWheelTimer hashedWheelTimer;

    @Before
    public void setup() {
        hashedWheelTimer = new HashedWheelTimer();
    }

    @After
    public void tearDown() throws Exception {
        if (smtpsClient != null) {
            smtpsClient.disconnect();
        }
        if (server != null) {
            server.unbind();
        }
        hashedWheelTimer.stop();
    }

    private ProtocolServer createServer(Protocol protocol, Encryption enc) {
        NettyServer server = new NettyServer.Factory(hashedWheelTimer)
                .protocol(protocol)
                .secure(enc)
                .frameHandlerFactory(new AllButStartTlsLineChannelHandlerFactory(AbstractChannelPipelineFactory.MAX_LINE_LENGTH))
                .build();
        server.setListenAddresses(new InetSocketAddress(LOCALHOST_IP, RANDOM_PORT));
        return server;
    }

    private SMTPSClient createClient() {
        SMTPSClient client = new SMTPSClient(false, BogusSslContextFactory.getClientContext());
        client.setTrustManager(BogusTrustManagerFactory.getTrustManagers()[0]);
        return client;
    }

    private Protocol createProtocol(Optional<ProtocolHandler> handler) throws WiringException {
        SMTPProtocolHandlerChain chain = new SMTPProtocolHandlerChain(new NoopMetricFactory());
        if (handler.isPresent()) {
            chain.add(handler.get());
        }
        chain.wireExtensibleHandlers();
        return new SMTPProtocol(chain, new SMTPConfigurationImpl());
    }

    @Test
    public void connectShouldReturnTrueWhenConnecting() throws Exception {
        server = createServer(createProtocol(Optional.empty()), Encryption.createStartTls(BogusSslContextFactory.getServerContext()));
        smtpsClient = createClient();

        server.bind();
        InetSocketAddress bindedAddress = new ProtocolServerUtils(server).retrieveBindedAddress();
        smtpsClient.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
        assertThat(SMTPReply.isPositiveCompletion(smtpsClient.getReplyCode())).isTrue();
    }

    @Test
    public void ehloShouldReturnTrueWhenSendingTheCommand() throws Exception {
        server = createServer(createProtocol(Optional.empty()), Encryption.createStartTls(BogusSslContextFactory.getServerContext()));
        smtpsClient = createClient();

        server.bind();
        InetSocketAddress bindedAddress = new ProtocolServerUtils(server).retrieveBindedAddress();
        smtpsClient.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());

        smtpsClient.sendCommand("EHLO localhost");
        assertThat(SMTPReply.isPositiveCompletion(smtpsClient.getReplyCode())).isTrue();
    }

    @Test
    public void startTlsShouldBeAnnouncedWhenServerSupportsIt() throws Exception {
        server = createServer(createProtocol(Optional.empty()), Encryption.createStartTls(BogusSslContextFactory.getServerContext()));
        smtpsClient = createClient();

        server.bind();
        InetSocketAddress bindedAddress = new ProtocolServerUtils(server).retrieveBindedAddress();
        smtpsClient.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
        smtpsClient.sendCommand("EHLO localhost");

        assertThat(new StartTLSAssert(smtpsClient)).isStartTLSAnnounced();
    }

    private static class StartTLSAssert implements AssertDelegateTarget {

        private final SMTPSClient client;

        public StartTLSAssert(SMTPSClient client) {
            this.client = client;
            
        }

        public boolean isStartTLSAnnounced() {
            return Arrays.stream(client.getReplyStrings())
                .anyMatch(reply -> reply.toUpperCase(Locale.US)
                    .endsWith("STARTTLS"));
        }
    }

    @Test
    public void startTlsShouldReturnTrueWhenServerSupportsIt() throws Exception {
        server = createServer(createProtocol(Optional.empty()), Encryption.createStartTls(BogusSslContextFactory.getServerContext()));
        smtpsClient = createClient();

        server.bind();
        InetSocketAddress bindedAddress = new ProtocolServerUtils(server).retrieveBindedAddress();
        smtpsClient.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
        smtpsClient.sendCommand("EHLO localhost");

        boolean execTLS = smtpsClient.execTLS();
        assertThat(execTLS).isTrue();
    }

    @Test
    public void startTlsShouldFailWhenFollowedByInjectedCommand() throws Exception {
        server = createServer(createProtocol(Optional.empty()), Encryption.createStartTls(BogusSslContextFactory.getServerContext()));
        smtpsClient = createClient();

        server.bind();
        InetSocketAddress bindedAddress = new ProtocolServerUtils(server).retrieveBindedAddress();
        smtpsClient.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
        smtpsClient.sendCommand("EHLO localhost");

        smtpsClient.sendCommand("STARTTLS\r\nRSET\r\n");
        assertThat(SMTPReply.isPositiveCompletion(smtpsClient.getReplyCode())).isFalse();
    }

    @Test
    public void startTlsShouldFailWhenFollowedByInjectedCommandAndNotAtBeginningOfLine() throws Exception {
        server = createServer(createProtocol(Optional.empty()), Encryption.createStartTls(BogusSslContextFactory.getServerContext()));
        smtpsClient = createClient();

        server.bind();
        InetSocketAddress bindedAddress = new ProtocolServerUtils(server).retrieveBindedAddress();
        smtpsClient.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
        smtpsClient.sendCommand("EHLO localhost");

        smtpsClient.sendCommand("RSET\r\nSTARTTLS\r\nRSET\r\n");
        assertThat(SMTPReply.isPositiveCompletion(smtpsClient.getReplyCode())).isFalse();
    }

    @Test
    public void startTlsShouldWorkWhenUsingJavamail() throws Exception {
        TestMessageHook hook = new TestMessageHook();
        server = createServer(createProtocol(Optional.<ProtocolHandler>of(hook)), Encryption.createStartTls(BogusSslContextFactory.getServerContext()));
        server.bind();
        SMTPTransport transport = null;

        try {
            InetSocketAddress bindedAddress = new ProtocolServerUtils(server).retrieveBindedAddress();

            Properties mailProps = new Properties();
            mailProps.put("mail.smtp.from", "test@localhost");
            mailProps.put("mail.smtp.host", bindedAddress.getHostName());
            mailProps.put("mail.smtp.port", bindedAddress.getPort());
            mailProps.put("mail.smtp.socketFactory.class", BogusSSLSocketFactory.class.getName());
            mailProps.put("mail.smtp.socketFactory.fallback", "false");
            mailProps.put("mail.smtp.starttls.enable", "true");

            Session mailSession = Session.getDefaultInstance(mailProps);

            InternetAddress[] rcpts = new InternetAddress[]{new InternetAddress("valid@localhost")};
            MimeMessage message = new MimeMessage(mailSession);
            message.setFrom(new InternetAddress("test@localhost"));
            message.setRecipients(Message.RecipientType.TO, rcpts);
            message.setSubject("Testmail", "UTF-8");
            message.setText("Test.....");

            transport = (SMTPTransport) mailSession.getTransport("smtps");

            transport.connect(new Socket(bindedAddress.getHostName(), bindedAddress.getPort()));
            transport.sendMessage(message, rcpts);

            assertThat(hook.getQueued()).hasSize(1);
        } finally {
            if (transport != null) {
                transport.close();
            }
        }
    }
}
