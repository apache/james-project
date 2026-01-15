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

package org.apache.james.mariadb;

import static org.apache.james.data.UsersRepositoryModuleChooser.Implementation.DEFAULT;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.apache.james.GuiceJamesServer;
import org.apache.james.JPAJamesConfiguration;
import org.apache.james.JPAJamesServerMain;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public class JPAMariaDBJamesServerTest {

    private GuiceJamesServer server;
    private SocketChannel socketChannel;

    @TempDir
    public Path tempDir;

    @Container
    public MariaDBContainer<?> mariaDB = new MariaDBContainer<>();

    @BeforeEach
    public void setup() throws Exception {
        server = createJamesServer(mariaDB.getJdbcUrl());
        socketChannel = SocketChannel.open();
        server.start();
    }

    @AfterEach
    public void teardown() {
        server.stop();
    }

    private org.apache.james.GuiceJamesServer createJamesServer(String mariaDBUrl) {
        JPAJamesConfiguration configuration = JPAJamesConfiguration.builder()
            .workingDirectory(tempDir.toFile())
            .usersRepository(DEFAULT)
            .configurationFromClasspath()
            .build();

        return JPAJamesServerMain.createServer(configuration)
                .overrideWith(new TestJPAMariaDBConfigurationModule(mariaDBUrl));
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
        return new String(bytes, StandardCharsets.UTF_8);
    }

}
