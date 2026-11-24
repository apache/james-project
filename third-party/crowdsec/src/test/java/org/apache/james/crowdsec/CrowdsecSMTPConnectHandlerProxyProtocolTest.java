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

package org.apache.james.crowdsec;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.james.crowdsec.client.CrowdsecClientConfiguration;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.protocols.api.ProtocolServer;
import org.apache.james.protocols.netty.NettyServer;
import org.apache.james.protocols.smtp.SMTPConfigurationImpl;
import org.apache.james.protocols.smtp.SMTPProtocol;
import org.apache.james.protocols.smtp.SMTPProtocolHandlerChain;
import org.apache.james.protocols.smtp.hook.HeloHook;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class CrowdsecSMTPConnectHandlerProxyProtocolTest {
    private static final String PROXY_IP = "127.0.0.1";
    private static final String CLIENT_IP = "10.11.12.13";

    @RegisterExtension
    static CrowdsecExtension crowdsecExtension = new CrowdsecExtension();

    private final AtomicBoolean heloProcessed = new AtomicBoolean(false);
    private ProtocolServer server;

    @BeforeEach
    void setUp() throws Exception {
        CrowdsecClientConfiguration configuration = new CrowdsecClientConfiguration(crowdsecExtension.getLocalhostCrowdsecUrl(), CrowdsecClientConfiguration.DEFAULT_API_KEY);
        SMTPProtocolHandlerChain chain = new SMTPProtocolHandlerChain(new RecordingMetricFactory());
        chain.add(new CrowdsecSMTPConnectHandler(new CrowdsecService(configuration)));
        chain.add((HeloHook) (session, helo) -> {
            heloProcessed.set(true);
            return HookResult.OK;
        });
        chain.wireExtensibleHandlers();

        NettyServer nettyServer = new NettyServer.Factory()
            .protocol(new SMTPProtocol(chain, new SMTPConfigurationImpl()))
            .proxyRequired(true)
            .build();
        nettyServer.setListenAddresses(new InetSocketAddress(PROXY_IP, 0));
        nettyServer.bind();
        server = nettyServer;
    }

    @AfterEach
    void tearDown() {
        server.unbind();
    }

    @Test
    void connectionShouldBeClosedBeforeAnyCommandWhenTheProxiedClientIsBanned() throws Exception {
        crowdsecExtension.banIP("--ip", CLIENT_IP);

        String received = sendThroughProxy(CLIENT_IP, "HELO localhost\r\n");

        assertThat(heloProcessed).isFalse();
        assertThat(received.lines()).noneMatch(line -> line.startsWith("250"));
    }

    @Test
    void connectionShouldBeAcceptedWhenOnlyTheProxyIsBanned() throws Exception {
        crowdsecExtension.banIP("--ip", PROXY_IP);

        String received = sendThroughProxy(CLIENT_IP, "HELO localhost\r\nQUIT\r\n");

        assertThat(heloProcessed).isTrue();
        assertThat(received.lines()).anyMatch(line -> line.startsWith("250"));
    }

    private String sendThroughProxy(String clientIp, String commands) throws IOException {
        InetSocketAddress serverAddress = server.getListenAddresses().get(0);
        try (Socket socket = new Socket(serverAddress.getAddress(), serverAddress.getPort())) {
            socket.setSoTimeout(10000);
            socket.getOutputStream().write(("PROXY TCP4 " + clientIp + " " + PROXY_IP + " 12345 " + serverAddress.getPort() + "\r\n" + commands)
                .getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            return readUntilClosed(socket.getInputStream());
        }
    }

    static String readUntilClosed(InputStream inputStream) throws IOException {
        ByteArrayOutputStream received = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        try {
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                received.write(buffer, 0, read);
            }
        } catch (SocketTimeoutException e) {
            throw new AssertionError("The server did not close the connection, received: " + received.toString(StandardCharsets.US_ASCII), e);
        } catch (IOException e) {
            // connection reset by the server
        }
        return received.toString(StandardCharsets.US_ASCII);
    }
}
