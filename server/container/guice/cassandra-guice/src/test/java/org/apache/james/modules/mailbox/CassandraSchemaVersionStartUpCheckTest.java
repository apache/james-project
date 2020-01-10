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
package org.apache.james.modules.mailbox;

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

import org.apache.james.CassandraExtension;
import org.apache.james.DockerElasticSearchExtension;
import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.StartUpChecksPerformer.StartUpChecksException;
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

import reactor.core.publisher.Mono;

class CassandraSchemaVersionStartUpCheckTest {
    private static final String LOCAL_HOST = "127.0.0.1";
    private static final String EXPECTED_SERVER_CONNECTED_MESSAGE = "* OK JAMES IMAP4rev1 Server";
    private static final SchemaVersion MIN_VERSION = new SchemaVersion(2);
    private static final SchemaVersion MAX_VERSION = new SchemaVersion(4);

    private static CassandraSchemaVersionDAO versionDAO = mock(CassandraSchemaVersionDAO.class);

    @RegisterExtension
    static JamesServerExtension testExtension = new JamesServerBuilder()
        .extension(new DockerElasticSearchExtension())
        .extension(new CassandraExtension())
        .server(configuration -> GuiceJamesServer.forConfiguration(configuration)
            .combineWith(ALL_BUT_JMX_CASSANDRA_MODULE)
            .overrideWith(binder -> binder.bind(TextExtractor.class).to(PDFTextExtractor.class))
            .overrideWith(TestJMAPServerModule.limitToTenMessages())
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

        return new String(bytes, Charset.forName("UTF-8"));
    }
}
