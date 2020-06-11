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

import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.server.core.configuration.Configuration;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.testcontainers.containers.MariaDBContainer;

public class JPAJamesServerTest {

    private GuiceJamesServer server;
    private SocketChannel socketChannel;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Rule
    public MariaDBContainer<?> mariaDB = new MariaDBContainer<>();

    @Before
    public void setup() throws Exception {
        server = createJamesServer(mariaDB.getJdbcUrl());
        socketChannel = SocketChannel.open();
        server.start();
    }

    @After
    public void teardown() {
        server.stop();
    }

    private org.apache.james.GuiceJamesServer createJamesServer(String mariaDBUrl) throws IOException {
        Configuration configuration = Configuration.builder()
            .workingDirectory(temporaryFolder.newFolder())
            .configurationFromClasspath()
            .build();

        return JPAJamesServerMain.createServer(configuration)
                .overrideWith(new TestJPAConfigurationModule(mariaDBUrl));
    }

    @After
    public void tearDown() throws Exception {
        server.stop();
    }

    @Test
    public void connectSMTPServerShouldSendShabangOnConnect() throws Exception {
        socketChannel.connect(new InetSocketAddress("127.0.0.1", server.getProbe(SmtpGuiceProbe.class).getSmtpPort().getValue()));
        assertThat(getServerConnectionResponse(socketChannel)).startsWith("220 Apache JAMES awesome SMTP Server");
    }
    
    private String getServerConnectionResponse(SocketChannel socketChannel) throws IOException {
        ByteBuffer byteBuffer = ByteBuffer.allocate(1000);
        socketChannel.read(byteBuffer);
        byte[] bytes = byteBuffer.array();
        return new String(bytes, Charset.forName("UTF-8"));
    }

}
