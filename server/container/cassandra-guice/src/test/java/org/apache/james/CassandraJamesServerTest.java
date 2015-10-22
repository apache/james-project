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
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;

import org.apache.james.mailbox.cassandra.CassandraClusterSingleton;
import com.google.inject.util.Modules;
import org.apache.james.mailbox.elasticsearch.EmbeddedElasticSearch;
import org.apache.james.modules.TestElasticSearchModule;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;

public class CassandraJamesServerTest {

    private static final CassandraClusterSingleton CASSANDRA = CassandraClusterSingleton.build();
    private static final int IMAP_PORT = 1143; // You need to be root (superuser) to bind to ports under 1024.
    private static final int IMAP_PORT_SSL = 1993;
    private static final int POP3_PORT = 1110;
    private static final int SMTP_PORT = 1025;
    private static final int LMTP_PORT = 1024;
    public static final int BUFFER_SIZE = 1000;

    private CassandraJamesServer server;
    private TemporaryFolder temporaryFolder = new TemporaryFolder();
    private EmbeddedElasticSearch embeddedElasticSearch = new EmbeddedElasticSearch(temporaryFolder);
    private SocketChannel socketChannel;

    @Rule
    public RuleChain chain = RuleChain.outerRule(temporaryFolder).around(embeddedElasticSearch);

    @Before
    public void setup() throws Exception {
        socketChannel = SocketChannel.open();
        CASSANDRA.ensureAllTables();

        server = new CassandraJamesServer(Modules.override(CassandraJamesServerMain.defaultModule).with(new TestElasticSearchModule(embeddedElasticSearch)));
        server.start();
    }

    @After
    public void tearDown() throws Exception {
        server.stop();
        CASSANDRA.clearAllTables();
    }

    @Test
    public void connectIMAPServerShouldThrowWhenNoCredentials() throws Exception {
        socketChannel.connect(new InetSocketAddress("127.0.0.1", IMAP_PORT));
        assertThat(getServerConnectionResponse(socketChannel)).startsWith("* OK JAMES IMAP4rev1 Server");
    }

    @Test
    public void connectOnSecondaryIMAPServerIMAPServerShouldThrowWhenNoCredentials() throws Exception {
        socketChannel.connect(new InetSocketAddress("127.0.0.1", IMAP_PORT_SSL));
        assertThat(getServerConnectionResponse(socketChannel)).startsWith("* OK JAMES IMAP4rev1 Server");
    }

    @Test
    public void connectPOP3ServerShouldThrowWhenNoCredentials() throws Exception {
        socketChannel.connect(new InetSocketAddress("127.0.0.1", POP3_PORT));
        assertThat(getServerConnectionResponse(socketChannel)).contains("POP3 server (JAMES POP3 Server ) ready");
    }

    @Test
    public void connectSMTPServerShouldNotThrowWhenNoCredentials() throws Exception {
        socketChannel.connect(new InetSocketAddress("127.0.0.1", SMTP_PORT));
        assertThat(getServerConnectionResponse(socketChannel)).startsWith("220 JAMES Linagora's SMTP awesome Server");
    }

    @Test
    public void connectLMTPServerShouldNotThrowWhenNoCredentials() throws Exception {
        socketChannel.connect(new InetSocketAddress("127.0.0.1", LMTP_PORT));
        assertThat(getServerConnectionResponse(socketChannel)).contains("LMTP Server (JAMES Protocols Server) ready");
    }

    private String getServerConnectionResponse(SocketChannel socketChannel) throws IOException {
        ByteBuffer byteBuffer = ByteBuffer.allocate(1000);
        socketChannel.read(byteBuffer);
        byte[] bytes = byteBuffer.array();
        return new String(bytes, Charset.forName("UTF-8"));
    }

}
