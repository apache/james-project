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
import java.nio.charset.StandardCharsets;

import org.apache.james.modules.AwsS3BlobStoreExtension;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class SMTPJamesServerTest {
    private static final String JAMES_SERVER_HOST = "127.0.0.1";

    @RegisterExtension
    static JamesServerExtension testExtension = TestingSmtpRelayJamesServerBuilder.forConfiguration(c -> c)
        .extension(new PostgresExtension())
        .extension(new PulsarExtension())
        .extension(new AwsS3BlobStoreExtension())
        .server(Main::createServer)
        .lifeCycle(JamesServerExtension.Lifecycle.PER_CLASS)
        .build();


    @Test
    void connectSMTPServerShouldSendShabangOnConnect(GuiceJamesServer jamesServer) throws Exception {
        try (SocketChannel socketChannel = SocketChannel.open()) {
            socketChannel.connect(new InetSocketAddress(JAMES_SERVER_HOST, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort().getValue()));
            assertThat(getServerConnectionResponse(socketChannel)).startsWith("220 Apache JAMES awesome SMTP Server");
        }
    }

    static String getServerConnectionResponse(SocketChannel socketChannel) throws IOException {
        ByteBuffer byteBuffer = ByteBuffer.allocate(1000);
        socketChannel.read(byteBuffer);
        byte[] bytes = byteBuffer.array();
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
