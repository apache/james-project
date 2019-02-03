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

import static org.apache.james.CassandraJamesServerMain.ALL_BUT_JMX_CASSANDRA_MODULE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionDAO;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionManager;
import org.apache.james.backends.cassandra.versions.SchemaVersion;
import org.apache.james.mailbox.extractor.TextExtractor;
import org.apache.james.mailbox.store.search.PDFTextExtractor;
import org.apache.james.modules.TestJMAPServerModule;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class CassandraVersionCheckingTest {
    private static final int LIMIT_TO_10_MESSAGES = 10;
    private static final String LOCAL_HOST = "127.0.0.1";
    private static final SchemaVersion MIN_VERSION = new SchemaVersion(2);
    private static final SchemaVersion MAX_VERSION = new SchemaVersion(4);

    private static CassandraSchemaVersionDAO versionDAO = mock(CassandraSchemaVersionDAO.class);

    @RegisterExtension
    static JamesServerExtension testExtension = new JamesServerExtensionBuilder()
        .extension(new EmbeddedElasticSearchExtension())
        .extension(new CassandraExtension())
        .server(configuration -> GuiceJamesServer.forConfiguration(configuration)
            .combineWith(ALL_BUT_JMX_CASSANDRA_MODULE)
            .overrideWith(binder -> binder.bind(TextExtractor.class).to(PDFTextExtractor.class))
            .overrideWith(new TestJMAPServerModule(LIMIT_TO_10_MESSAGES))
            .overrideWith(binder -> binder.bind(CassandraSchemaVersionDAO.class)
                .toInstance(versionDAO))
            .overrideWith(binder -> binder.bind(CassandraSchemaVersionManager.class)
                .toInstance(new CassandraSchemaVersionManager(versionDAO, MIN_VERSION, MAX_VERSION))))
        .disableAutoStart()
        .build();

    private SocketChannel socketChannel;

    @BeforeEach
    void setUp() throws IOException {
        socketChannel = SocketChannel.open();
    }

    @AfterEach
    void tearDown() throws IOException {
        socketChannel.close();
    }

    @Test
    void serverShouldStartSuccessfullyWhenMaxVersion(GuiceJamesServer server) throws Exception {
        when(versionDAO.getCurrentSchemaVersion())
            .thenReturn(CompletableFuture.completedFuture(Optional.of(MAX_VERSION)));

        assertThatServerStartCorrectly(server);
    }

    @Test
    void serverShouldStartSuccessfullyWhenBetweenMinAndMaxVersion(GuiceJamesServer server) throws Exception {
        when(versionDAO.getCurrentSchemaVersion())
            .thenReturn(CompletableFuture.completedFuture(Optional.of(MIN_VERSION.next())));

        assertThatServerStartCorrectly(server);
    }

    @Test
    void serverShouldStartSuccessfullyWhenMinVersion(GuiceJamesServer server) throws Exception {
        when(versionDAO.getCurrentSchemaVersion())
            .thenReturn(CompletableFuture.completedFuture(Optional.of(MIN_VERSION)));

        assertThatServerStartCorrectly(server);
    }

    @Test
    void serverShouldNotStartWhenUnderMinVersion(GuiceJamesServer server) {
        when(versionDAO.getCurrentSchemaVersion())
            .thenReturn(CompletableFuture.completedFuture(Optional.of(MIN_VERSION.previous())));

        assertThatThrownBy(server::start).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void serverShouldNotStartWhenAboveMaxVersion(GuiceJamesServer server) {
        when(versionDAO.getCurrentSchemaVersion())
            .thenReturn(CompletableFuture.completedFuture(Optional.of(MAX_VERSION.next())));

        assertThatThrownBy(server::start).isInstanceOf(IllegalStateException.class);
    }

    private void assertThatServerStartCorrectly(GuiceJamesServer server) throws Exception {
        server.start();
        socketChannel.connect(new InetSocketAddress(LOCAL_HOST, server.getProbe(ImapGuiceProbe.class).getImapPort()));
        assertThat(getServerConnectionResponse(socketChannel))
            .startsWith("* OK JAMES IMAP4rev1 Server");
    }

    private String getServerConnectionResponse(SocketChannel socketChannel) throws IOException {
        ByteBuffer byteBuffer = ByteBuffer.allocate(1000);
        socketChannel.read(byteBuffer);
        byte[] bytes = byteBuffer.array();

        return new String(bytes, Charset.forName("UTF-8"));
    }
}
