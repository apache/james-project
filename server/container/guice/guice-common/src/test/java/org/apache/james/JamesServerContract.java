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
package org.apache.james;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;

import org.apache.james.domainlist.lib.DomainListConfiguration;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.modules.protocols.LmtpGuiceProbe;
import org.apache.james.modules.protocols.Pop3GuiceProbe;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.utils.DataProbeImpl;
import org.junit.jupiter.api.Test;

import com.google.inject.Module;

public interface JamesServerContract {

    String JAMES_SERVER_HOST = "127.0.0.1";
    Module DOMAIN_LIST_CONFIGURATION_MODULE = binder -> binder.bind(DomainListConfiguration.class)
        .toInstance(DomainListConfiguration.builder()
            .autoDetect(true)
            .autoDetectIp(false)
            .build());

    @Test
    default void hostnameShouldBeUsedAsDefaultDomain(GuiceJamesServer jamesServer) throws Exception {
        String expectedDefaultDomain = InetAddress.getLocalHost().getHostName();

        assertThat(jamesServer.getProbe(DataProbeImpl.class).getDefaultDomain()).isEqualTo(expectedDefaultDomain);
    }

    @Test
    default void hostnameShouldBeRetrievedWhenRestarting(GuiceJamesServer jamesServer) throws Exception {
        jamesServer.stop();
        jamesServer.start();
        String expectedDefaultDomain = InetAddress.getLocalHost().getHostName();

        assertThat(jamesServer.getProbe(DataProbeImpl.class).getDefaultDomain()).isEqualTo(expectedDefaultDomain);
    }

    @Test
    default void connectIMAPServerShouldSendShabangOnConnect(GuiceJamesServer jamesServer) throws Exception {
        try (SocketChannel socketChannel = SocketChannel.open()) {
            socketChannel.connect(new InetSocketAddress(JAMES_SERVER_HOST, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort()));
            assertThat(getServerConnectionResponse(socketChannel)).startsWith("* OK JAMES IMAP4rev1 Server");
        }
    }

    @Test
    default void connectOnSecondaryIMAPServerIMAPServerShouldSendShabangOnConnect(GuiceJamesServer jamesServer) throws Exception {
        try (SocketChannel socketChannel = SocketChannel.open()) {
            socketChannel.connect(new InetSocketAddress(JAMES_SERVER_HOST, jamesServer.getProbe(ImapGuiceProbe.class).getImapsPort()));
            assertThat(getServerConnectionResponse(socketChannel)).startsWith("* OK JAMES IMAP4rev1 Server");
        }
    }

    @Test
    default void connectPOP3ServerShouldSendShabangOnConnect(GuiceJamesServer jamesServer) throws Exception {
        try (SocketChannel socketChannel = SocketChannel.open()) {
            socketChannel.connect(new InetSocketAddress(JAMES_SERVER_HOST, jamesServer.getProbe(Pop3GuiceProbe.class).getPop3Port()));
            assertThat(getServerConnectionResponse(socketChannel)).contains("POP3 server (JAMES POP3 Server ) ready");
        }
    }

    @Test
    default void connectSMTPServerShouldSendShabangOnConnect(GuiceJamesServer jamesServer) throws Exception {
        try (SocketChannel socketChannel = SocketChannel.open()) {
            socketChannel.connect(new InetSocketAddress(JAMES_SERVER_HOST, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort().getValue()));
            assertThat(getServerConnectionResponse(socketChannel)).startsWith("220 JAMES Linagora's SMTP awesome Server");
        }
    }

    @Test
    default void connectLMTPServerShouldSendShabangOnConnect(GuiceJamesServer jamesServer) throws Exception {
        try (SocketChannel socketChannel = SocketChannel.open()) {
            socketChannel.connect(new InetSocketAddress(JAMES_SERVER_HOST, jamesServer.getProbe(LmtpGuiceProbe.class).getLmtpPort()));
            assertThat(getServerConnectionResponse(socketChannel)).contains("LMTP Server (JAMES Protocols Server) ready");
        }
    }

    static String getServerConnectionResponse(SocketChannel socketChannel) throws IOException {
        ByteBuffer byteBuffer = ByteBuffer.allocate(1000);
        socketChannel.read(byteBuffer);
        byte[] bytes = byteBuffer.array();
        return new String(bytes, Charset.forName("UTF-8"));
    }
}
