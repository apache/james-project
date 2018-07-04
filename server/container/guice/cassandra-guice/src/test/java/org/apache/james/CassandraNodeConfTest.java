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

import org.apache.james.backends.cassandra.init.configuration.ClusterConfiguration;
import org.apache.james.util.Host;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.testcontainers.DockerClientFactory;

public class CassandraNodeConfTest {

    private static final int CASSANDRA_PORT = 9042;
    private static final int IMAP_PORT = 1143;

    private static String getDockerHostIp() {
        return DockerClientFactory.instance().dockerHostIpAddress();
    }

    @ClassRule
    public static final DockerCassandraRule dockerCassandraRule = new DockerCassandraRule();

    @Rule
    public CassandraJmapTestRule cassandraJmapTestRule = CassandraJmapTestRule.defaultTestRule();

    private GuiceJamesServer jamesServer;
    private SocketChannel socketChannel;

    @Before
    public void setUp() throws IOException {
        socketChannel = SocketChannel.open();
    }

    @After
    public void after() throws IOException {
        socketChannel.close();
        if (jamesServer != null) {
            jamesServer.stop();
        }
    }

    @Test
    public void serverShouldStartServiceWhenNodeIsReachable() throws Exception {
        jamesServer = cassandraJmapTestRule.jmapServer(dockerCassandraRule.getModule());

        assertThatServerStartCorrectly();
    }

    @Test
    public void serverShouldStartWhenOneCassandraNodeIsUnreachable() throws Exception {
        String unreachableNode = "10.2.3.42";


        jamesServer = cassandraJmapTestRule.jmapServer(dockerCassandraRule.getModule())
            .overrideWith(
                (binder) -> binder.bind(ClusterConfiguration.class)
                    .toInstance(clusterWithHosts(
                        Host.from(unreachableNode, 9042),
                        dockerCassandraRule.getHost())));

        assertThatServerStartCorrectly();
    }


    @Test
    public void configShouldWorkWithNonDefaultPort() throws Exception {
        jamesServer = cassandraJmapTestRule.jmapServer(dockerCassandraRule.getModule())
            .overrideWith(
                (binder) -> binder.bind(ClusterConfiguration.class)
                    .toInstance(clusterWithHosts(
                        Host.from(getDockerHostIp(), dockerCassandraRule.getMappedPort(CASSANDRA_PORT)))));

        assertThatServerStartCorrectly();
    }

    private void assertThatServerStartCorrectly() throws Exception {
        jamesServer.start();
        socketChannel.connect(new InetSocketAddress("127.0.0.1", IMAP_PORT));
        assertThat(getServerConnectionResponse(socketChannel)).startsWith("* OK JAMES IMAP4rev1 Server");
    }

    private ClusterConfiguration clusterWithHosts(Host... hosts) {
        return ClusterConfiguration.builder()
            .hosts(hosts)
            .keyspace("apache_james")
            .replicationFactor(1)
            .maxRetry(10)
            .minDelay(5000)
            .build();
    }

    private String getServerConnectionResponse(SocketChannel socketChannel) throws IOException {
        ByteBuffer byteBuffer = ByteBuffer.allocate(1000);
        socketChannel.read(byteBuffer);
        byte[] bytes = byteBuffer.array();
        return new String(bytes, Charset.forName("UTF-8"));
    }

}
