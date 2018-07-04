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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.testcontainers.shaded.com.google.common.base.Throwables;

public class JamesServerWithRetryConnectionTest {
    private static final int IMAP_PORT = 1143;
    private static final long WAITING_TIME = TimeUnit.MILLISECONDS.convert(10, TimeUnit.SECONDS);

    @ClassRule
    public static DockerCassandraRule dockerCassandraRule = new DockerCassandraRule();
    private final DockerElasticSearchRule dockerElasticSearchRule = new DockerElasticSearchRule();

    @Rule
    public CassandraJmapTestRule cassandraJmapTestRule = new CassandraJmapTestRule(dockerElasticSearchRule);

    private GuiceJamesServer jamesServer;
    private SocketChannel socketChannel;
    private ExecutorService executorService;

    @Before
    public void setUp() throws IOException {
        executorService = Executors.newFixedThreadPool(1);
        socketChannel = SocketChannel.open();
    }

    @After
    public void after() throws IOException {
        socketChannel.close();
        if (jamesServer != null) {
            jamesServer.stop();
        }
        executorService.shutdownNow();
    }

    @Test
    public void serverShouldStartAtDefault() throws Exception {
        jamesServer = cassandraJmapTestRule.jmapServer(dockerCassandraRule.getModule());
        assertThatServerStartCorrectly();
    }

    @Test
    public void serverShouldRetryToConnectToCassandraWhenStartService() throws Exception {
        jamesServer = cassandraJmapTestRule.jmapServer(dockerCassandraRule.getModule());
        dockerCassandraRule.pause();

        waitToStartContainer(WAITING_TIME, dockerCassandraRule::unpause);

        assertThatServerStartCorrectly();
    }

    @Test
    public void serverShouldRetryToConnectToElasticSearchWhenStartService() throws Exception {
        jamesServer = cassandraJmapTestRule.jmapServer(dockerCassandraRule.getModule());
        dockerElasticSearchRule.pause();

        waitToStartContainer(WAITING_TIME, dockerElasticSearchRule::unpause);

        assertThatServerStartCorrectly();
    }

    interface StartAction {
        void execute();
    }
    
    private void waitToStartContainer(long waitingTime, StartAction action) {
        executorService.submit(() -> {
            try {
                Thread.sleep(waitingTime);
            } catch (InterruptedException e) {
                throw Throwables.propagate(e);
            }
            action.execute();
        });
    }

    private void assertThatServerStartCorrectly() throws Exception {
        jamesServer.start();
        socketChannel.connect(new InetSocketAddress("127.0.0.1", IMAP_PORT));
        assertThat(getServerConnectionResponse(socketChannel)).startsWith("* OK JAMES IMAP4rev1 Server");
    }

    private String getServerConnectionResponse(SocketChannel socketChannel) throws IOException {
        ByteBuffer byteBuffer = ByteBuffer.allocate(1000);
        socketChannel.read(byteBuffer);
        byte[] bytes = byteBuffer.array();
        return new String(bytes, Charset.forName("UTF-8"));
    }
}
