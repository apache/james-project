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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetSocketAddress;
import java.util.Optional;

import org.apache.commons.net.smtp.SMTPConnectionClosedException;
import org.apache.commons.net.smtp.SMTPReply;
import org.apache.commons.net.smtp.SMTPSClient;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.protocols.api.Protocol;
import org.apache.james.protocols.api.ProtocolServer;
import org.apache.james.protocols.api.ProxyInformation;
import org.apache.james.protocols.api.handler.ProtocolHandler;
import org.apache.james.protocols.api.handler.WiringException;
import org.apache.james.protocols.api.utils.BogusSslContextFactory;
import org.apache.james.protocols.api.utils.BogusTrustManagerFactory;
import org.apache.james.protocols.api.utils.ProtocolServerUtils;
import org.apache.james.protocols.netty.NettyServer;
import org.apache.james.protocols.smtp.SMTPConfigurationImpl;
import org.apache.james.protocols.smtp.SMTPProtocol;
import org.apache.james.protocols.smtp.SMTPProtocolHandlerChain;
import org.apache.james.protocols.smtp.hook.HeloHook;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class NettyProxySMTPServerTest {
    private static final String LOCALHOST_IP = "127.0.0.1";
    private static final int RANDOM_PORT = 0;

    private SMTPSClient smptClient = null;
    private ProtocolServer server = null;

    @AfterEach
    void tearDown() throws Exception {
        if (smptClient != null) {
            smptClient.disconnect();
        }
        if (server != null) {
            server.unbind();
        }
    }

    private ProtocolServer createServer(Protocol protocol) {
        NettyServer server = new NettyServer.Factory()
            .protocol(protocol)
            .proxyRequired(true)
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
        SMTPProtocolHandlerChain chain = new SMTPProtocolHandlerChain(new RecordingMetricFactory());
        if (handler.isPresent()) {
            chain.add(handler.get());
        }
        chain.wireExtensibleHandlers();
        return new SMTPProtocol(chain, new SMTPConfigurationImpl());
    }

    @Test
    void heloShouldReturnTrueWhenSendingTheCommandWithProxyCommandTCP4() throws Exception {
        heloShouldReturnTrueWhenSendingTheCommandWithProxyCommand("TCP4", "255.255.255.254", "255.255.255.255");
    }

    @Test
    void heloShouldReturnTrueWhenSendingTheCommandWithProxyCommandTCP6() throws Exception {
        heloShouldReturnTrueWhenSendingTheCommandWithProxyCommand("TCP6", "ffff:ffff:ffff:ffff:ffff:ffff:ffff:fffe", "ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff");
    }

    private void heloShouldReturnTrueWhenSendingTheCommandWithProxyCommand(String protocol, String source, String destination)
        throws Exception {
        server = createServer(createProtocol(Optional.of((HeloHook) (session, helo) -> {
            ProxyInformation proxyInformation = session.getProxyInformation().orElseThrow();
            assertThat(session.getRemoteAddress().getHostString()).isEqualTo(source);
            assertThat(proxyInformation.getSource().getHostString()).isEqualTo(source);
            assertThat(proxyInformation.getDestination().getHostString()).isEqualTo(destination);
            return HookResult.OK;
        })));

        smptClient = createClient();

        server.bind();
        InetSocketAddress bindedAddress = new ProtocolServerUtils(server).retrieveBindedAddress();
        smptClient.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());

        smptClient.sendCommand(String.format("PROXY %s %s %s %d %d\r\nHELO localhost", protocol, source, destination, 65535, 65535));

        assertThat(SMTPReply.isPositiveCompletion(smptClient.getReplyCode())).isTrue();
        assertThat(smptClient.getReplyString()).contains(source);
    }

    @Test
    void heloShouldReturnFalseWhenSendingCommandWithoutProxyCommand() throws Exception {
        server = createServer(createProtocol(Optional.empty()));
        smptClient = createClient();

        server.bind();
        InetSocketAddress bindedAddress = new ProtocolServerUtils(server).retrieveBindedAddress();
        smptClient.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());

        assertThatThrownBy(() -> smptClient.sendCommand("HELO localhost"))
            .isInstanceOf(SMTPConnectionClosedException.class)
            .hasMessage("Connection closed without indication.");
    }

    @Test
    void heloShouldReturnTrueWhenSendingCommandWithProxyCommandUnknown() throws Exception {
        server = createServer(createProtocol(Optional.empty()));
        smptClient = createClient();

        server.bind();
        InetSocketAddress bindedAddress = new ProtocolServerUtils(server).retrieveBindedAddress();
        smptClient.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());

        smptClient.sendCommand("PROXY UNKNOWN\r\nHELO localhost");

        assertThat(SMTPReply.isPositiveCompletion(smptClient.getReplyCode())).isFalse();
    }

}
