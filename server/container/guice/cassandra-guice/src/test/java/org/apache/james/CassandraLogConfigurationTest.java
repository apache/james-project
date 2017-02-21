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

import com.google.inject.ProvisionException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.james.modules.mailbox.CassandraSessionConfiguration;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;

import static org.assertj.core.api.Assertions.assertThat;

public class CassandraLogConfigurationTest {

    private static final int IMAP_PORT = 1143;

    private final DockerCassandraRule dockerCassandraRule = new DockerCassandraRule();

    @Rule
    public CassandraJmapTestRule cassandraJmapTestRule = new CassandraJmapTestRule(dockerCassandraRule,
            new EmbeddedElasticSearchRule());

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private GuiceJamesServerImpl jamesServer;
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
    public void serverShouldStartWithMinimalConfigAboutAConstantThresholdSlowQueryLogger() throws Exception {
        jamesServer = cassandraJmapTestRule.jmapServer(
            (binder) -> binder.bind(CassandraSessionConfiguration.class).toInstance(() -> {
                PropertiesConfiguration configuration = dockerCassandraRule.getCassandraConfigurationForDocker();

                configuration.addProperty("cassandra.query.logger.constant.threshold", 100);

                return configuration;
            }));

        assertThatServerStartCorrectly();
    }

    @Test
    public void serverShouldStartWithPersonalizedConfigAboutPercentileSlowQuerryLogger() throws Exception {
        jamesServer = cassandraJmapTestRule.jmapServer(
            (binder) ->
                binder.bind(CassandraSessionConfiguration.class).toInstance(() -> {
                    PropertiesConfiguration configuration = dockerCassandraRule.getCassandraConfigurationForDocker();

                    configuration.addProperty("cassandra.query.slow.query.latency.threshold.percentile", 90);
                    configuration.addProperty("cassandra.query.logger.max.logged.parameters", 9);
                    configuration.addProperty("cassandra.query.logger.max.query.string.length", 9000);
                    configuration.addProperty("cassandra.query.logger.max.parameter.value.length", 90);

                    return configuration;
                }));

        assertThatServerStartCorrectly();
    }

    @Test
    public void serverStartShouldFailIfConfigAboutLoggerIsInvalid() throws Exception {
        thrown.expect(ProvisionException.class);

        jamesServer = cassandraJmapTestRule.jmapServer(
            (binder) ->
                binder.bind(CassandraSessionConfiguration.class).toInstance(() -> {
                    PropertiesConfiguration configuration = dockerCassandraRule.getCassandraConfigurationForDocker();

                    configuration.addProperty("cassandra.query.slow.query.latency.threshold.percentile", 90);
                    configuration.addProperty("cassandra.query.logger.constant.threshold", 100);

                    return configuration;
                }));

        assertThatServerStartCorrectly();
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
