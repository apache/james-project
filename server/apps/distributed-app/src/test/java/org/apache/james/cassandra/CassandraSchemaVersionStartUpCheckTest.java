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
package org.apache.james.cassandra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.apache.james.CassandraExtension;
import org.apache.james.CassandraRabbitMQJamesConfiguration;
import org.apache.james.CassandraRabbitMQJamesServerMain;
import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.SearchConfiguration;
import org.apache.james.StartUpChecksPerformer.StartUpChecksException;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionDAO;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionManager;
import org.apache.james.backends.cassandra.versions.SchemaVersion;
import org.apache.james.modules.RabbitMQExtension;
import org.apache.james.modules.blobstore.BlobStoreConfiguration;
import org.apache.james.modules.mailbox.CassandraSchemaVersionStartUpCheck;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import reactor.core.publisher.Mono;

class CassandraSchemaVersionStartUpCheckTest {
    private static final String LOCAL_HOST = "127.0.0.1";
    private static final String EXPECTED_SERVER_CONNECTED_MESSAGE = "* OK JAMES IMAP4rev1 Server";
    private static final SchemaVersion MIN_VERSION = new SchemaVersion(2);
    private static final SchemaVersion MAX_VERSION = new SchemaVersion(4);

    private static CassandraSchemaVersionDAO versionDAO = mock(CassandraSchemaVersionDAO.class);

    @RegisterExtension
    static JamesServerExtension testExtension = new JamesServerBuilder<CassandraRabbitMQJamesConfiguration>(tmpDir ->
        CassandraRabbitMQJamesConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .blobStore(BlobStoreConfiguration.cassandra().deduplication().noCryptoConfig())
            .searchConfiguration(SearchConfiguration.scanning())
            .build())
        .extension(new CassandraExtension())
        .extension(new RabbitMQExtension())
        .server(configuration -> CassandraRabbitMQJamesServerMain.createServer(configuration)
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
            .thenReturn(Mono.just(Optional.of(MAX_VERSION)));

        server.start();
        assertThat(responseAfterConnectTo(server))
            .startsWith(EXPECTED_SERVER_CONNECTED_MESSAGE);
    }

    @Test
    void serverShouldStartSuccessfullyWhenBetweenMinAndMaxVersion(GuiceJamesServer server) throws Exception {
        when(versionDAO.getCurrentSchemaVersion())
            .thenReturn(Mono.just(Optional.of(MIN_VERSION.next())));

        server.start();
        assertThat(responseAfterConnectTo(server))
            .startsWith(EXPECTED_SERVER_CONNECTED_MESSAGE);
    }

    @Test
    void serverShouldStartSuccessfullyWhenMinVersion(GuiceJamesServer server) throws Exception {
        when(versionDAO.getCurrentSchemaVersion())
            .thenReturn(Mono.just(Optional.of(MIN_VERSION)));

        server.start();
        assertThat(responseAfterConnectTo(server))
            .startsWith(EXPECTED_SERVER_CONNECTED_MESSAGE);
    }

    @Test
    void serverShouldNotStartWhenUnderMinVersion(GuiceJamesServer server) {
        when(versionDAO.getCurrentSchemaVersion())
            .thenReturn(Mono.just(Optional.of(MIN_VERSION.previous())));

        assertThatThrownBy(server::start)
            .isInstanceOfSatisfying(
                StartUpChecksException.class,
                exception -> assertThat(exception.badCheckNames())
                    .containsOnly(CassandraSchemaVersionStartUpCheck.CHECK_NAME));
    }

    @Test
    void serverShouldNotStartWhenAboveMaxVersion(GuiceJamesServer server) {
        when(versionDAO.getCurrentSchemaVersion())
            .thenReturn(Mono.just(Optional.of(MAX_VERSION.next())));

        assertThatThrownBy(server::start)
            .isInstanceOfSatisfying(
                StartUpChecksException.class,
                exception -> assertThat(exception.badCheckNames())
                    .containsOnly(CassandraSchemaVersionStartUpCheck.CHECK_NAME));
    }

    private String responseAfterConnectTo(GuiceJamesServer server) throws IOException {
        socketChannel.connect(new InetSocketAddress(LOCAL_HOST, server.getProbe(ImapGuiceProbe.class).getImapPort()));

        ByteBuffer byteBuffer = ByteBuffer.allocate(1000);
        socketChannel.read(byteBuffer);
        byte[] bytes = byteBuffer.array();

        return new String(bytes, StandardCharsets.UTF_8);
    }
}
