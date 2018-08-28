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
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.inject.Module;

public abstract class AbstractJamesServerTest {

    protected static final String JAMES_SERVER_HOST = "127.0.0.1";

    public static final Module DOMAIN_LIST_CONFIGURATION_MODULE = binder -> binder.bind(DomainListConfiguration.class)
        .toInstance(DomainListConfiguration.builder()
            .autoDetect(true)
            .autoDetectIp(false)
            .build());

    protected GuiceJamesServer server;
    private SocketChannel socketChannel;

    @Before
    public void setup() throws Exception {
        server = createJamesServer();
        socketChannel = SocketChannel.open();
        server.start();
    }

    protected abstract GuiceJamesServer createJamesServer() throws IOException;

    protected abstract void clean();

    @After
    public void tearDown() throws Exception {
        server.stop();
        clean();
    }

    @Test
    public void hostnameShouldBeUsedAsDefaultDomain() throws Exception {
        String expectedDefaultDomain = InetAddress.getLocalHost().getHostName();

        assertThat(server.getProbe(DataProbeImpl.class).getDefaultDomain()).isEqualTo(expectedDefaultDomain);
    }

    @Test
    public void hostnameShouldBeRetrievedWhenRestarting() throws Exception {
        server.stop();
        server.start();
        String expectedDefaultDomain = InetAddress.getLocalHost().getHostName();

        assertThat(server.getProbe(DataProbeImpl.class).getDefaultDomain()).isEqualTo(expectedDefaultDomain);
    }

    @Test
    public void connectIMAPServerShouldSendShabangOnConnect() throws Exception {
        socketChannel.connect(new InetSocketAddress(JAMES_SERVER_HOST, server.getProbe(ImapGuiceProbe.class).getImapPort()));
        assertThat(getServerConnectionResponse(socketChannel)).startsWith("* OK JAMES IMAP4rev1 Server");
    }

    @Test
    public void connectOnSecondaryIMAPServerIMAPServerShouldSendShabangOnConnect() throws Exception {
        socketChannel.connect(new InetSocketAddress(JAMES_SERVER_HOST, server.getProbe(ImapGuiceProbe.class).getImapsPort()));
        assertThat(getServerConnectionResponse(socketChannel)).startsWith("* OK JAMES IMAP4rev1 Server");
    }

    @Test
    public void connectPOP3ServerShouldSendShabangOnConnect() throws Exception {
        socketChannel.connect(new InetSocketAddress(JAMES_SERVER_HOST, server.getProbe(Pop3GuiceProbe.class).getPop3Port()));
        assertThat(getServerConnectionResponse(socketChannel)).contains("POP3 server (JAMES POP3 Server ) ready");
    }

    @Test
    public void connectSMTPServerShouldSendShabangOnConnect() throws Exception {
        socketChannel.connect(new InetSocketAddress(JAMES_SERVER_HOST, server.getProbe(SmtpGuiceProbe.class).getSmtpPort()));
        assertThat(getServerConnectionResponse(socketChannel)).startsWith("220 JAMES Linagora's SMTP awesome Server");
    }

    @Test
    public void connectLMTPServerShouldSendShabangOnConnect() throws Exception {
        socketChannel.connect(new InetSocketAddress(JAMES_SERVER_HOST, server.getProbe(LmtpGuiceProbe.class).getLmtpPort()));
        assertThat(getServerConnectionResponse(socketChannel)).contains("LMTP Server (JAMES Protocols Server) ready");
    }

    private String getServerConnectionResponse(SocketChannel socketChannel) throws IOException {
        ByteBuffer byteBuffer = ByteBuffer.allocate(1000);
        socketChannel.read(byteBuffer);
        byte[] bytes = byteBuffer.array();
        return new String(bytes, Charset.forName("UTF-8"));
    }
}
