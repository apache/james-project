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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.james.mailbox.elasticsearch.IndexAttachments;
import org.apache.james.mailbox.extractor.TextExtractor;
import org.apache.james.mailbox.store.search.PDFTextExtractor;
import org.apache.james.modules.TestJMAPServerModule;
import org.apache.james.modules.mailbox.ElasticSearchConfiguration;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.util.Host;
import org.apache.james.util.docker.Images;
import org.apache.james.util.docker.SwarmGenericContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;

import com.google.inject.Module;

class JamesServerWithRetryConnectionTest {
    private static class DockerElasticSearchRegistrableExtension implements GuiceModuleTestExtension {
        private final SwarmGenericContainer elasticSearchContainer;

        private DockerElasticSearchRegistrableExtension(SwarmGenericContainer elasticSearchContainer) {
            this.elasticSearchContainer = elasticSearchContainer;
        }

        @Override
        public void beforeEach(ExtensionContext extensionContext) {
            elasticSearchContainer.start();
        }

        @Override
        public void afterEach(ExtensionContext extensionContext) {
            elasticSearchContainer.stop();
        }

        @Override
        public Module getModule() {
            return binder -> binder.bind(ElasticSearchConfiguration.class)
                    .toInstance(getElasticSearchConfigurationForDocker());
        }

        private ElasticSearchConfiguration getElasticSearchConfigurationForDocker() {
            return ElasticSearchConfiguration.builder()
                .addHost(Host.from(elasticSearchContainer.getHostIp(), elasticSearchContainer.getMappedPort(ELASTIC_SEARCH_PORT)))
                .indexAttachment(IndexAttachments.NO)
                .build();
        }
    }

    private static final int LIMIT_TO_10_MESSAGES = 10;
    private static final long WAITING_TIME = TimeUnit.MILLISECONDS.convert(10, TimeUnit.SECONDS);

    private static final int ELASTIC_SEARCH_PORT = 9300;
    private static final int ELASTIC_SEARCH_HTTP_PORT = 9200;

    private static SwarmGenericContainer elasticSearchContainer = new SwarmGenericContainer(Images.ELASTICSEARCH)
        .withExposedPorts(ELASTIC_SEARCH_HTTP_PORT, ELASTIC_SEARCH_PORT);
    private static final DockerCassandraRule cassandraRule = new DockerCassandraRule();

    static JamesServerExtension testExtension = new JamesServerExtensionBuilder()
        .extension(new DockerElasticSearchRegistrableExtension(elasticSearchContainer))
        .extension(new CassandraExtension())
        .server(configuration -> GuiceJamesServer.forConfiguration(configuration)
            .combineWith(ALL_BUT_JMX_CASSANDRA_MODULE)
            .overrideWith(binder -> binder.bind(TextExtractor.class).to(PDFTextExtractor.class))
            .overrideWith(new TestJMAPServerModule(LIMIT_TO_10_MESSAGES)))
        .disableAutoStart()
        .build();

    private SocketChannel socketChannel;
    private ExecutorService executorService;

    @BeforeEach
    void setUp() throws IOException {
        executorService = Executors.newFixedThreadPool(1);
        socketChannel = SocketChannel.open();
    }

    @AfterEach
    void after() throws IOException {
        socketChannel.close();
        executorService.shutdownNow();
    }

    @Test
    void serverShouldStartAtDefault(GuiceJamesServer server) throws Exception {
        assertThatServerStartCorrectly(server);
    }

    @Test
    void serverShouldRetryToConnectToCassandraWhenStartService(GuiceJamesServer server) throws Exception {
        cassandraRule.pause();

        waitToStartContainer(WAITING_TIME, cassandraRule::unpause);

        assertThatServerStartCorrectly(server);
    }

    @Test
    void serverShouldRetryToConnectToElasticSearchWhenStartService(GuiceJamesServer server) throws Exception {
        elasticSearchContainer.pause();

        waitToStartContainer(WAITING_TIME, elasticSearchContainer::unpause);

        assertThatServerStartCorrectly(server);
    }

    interface StartAction {
        void execute();
    }
    
    private void waitToStartContainer(long waitingTime, StartAction action) {
        executorService.submit(() -> {
            try {
                Thread.sleep(waitingTime);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            action.execute();
        });
    }

    private void assertThatServerStartCorrectly(GuiceJamesServer server) throws Exception {
        server.start();
        socketChannel.connect(new InetSocketAddress("127.0.0.1", server.getProbe(ImapGuiceProbe.class).getImapPort()));
        assertThat(getServerConnectionResponse(socketChannel)).startsWith("* OK JAMES IMAP4rev1 Server");
    }

    private String getServerConnectionResponse(SocketChannel socketChannel) throws IOException {
        ByteBuffer byteBuffer = ByteBuffer.allocate(1000);
        socketChannel.read(byteBuffer);
        byte[] bytes = byteBuffer.array();
        return new String(bytes, Charset.forName("UTF-8"));
    }
}
