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
package org.apache.james.smtpserver;

import static org.apache.james.jmap.JMAPTestingConstants.DOMAIN;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SMTPProxyProtocolTest {
    private  final SMTPServerTestSystem testSystem = new SMTPServerTestSystem();

    @BeforeEach
    void setUp() throws Exception {
        testSystem.setUp("smtpserver-proxy.xml");
    }

    @AfterEach
    void tearDown() {
        testSystem.smtpServer.destroy();
    }

    @Test
    void rejectFutureReleaseUsageWhenUnauthenticated() throws Exception {
        String protocol = "TCP4";
        String source = "255.255.255.254";
        String destination = "255.255.255.255";

        SocketChannel server = SocketChannel.open();
        server.connect(testSystem.getBindedAddress());
        readBytes(server);

        String proxyMessage = String.format("PROXY %s %s %s %d %d\r\n", protocol, source, destination, 65535, 65535);
        server.write(ByteBuffer.wrap((proxyMessage).getBytes(StandardCharsets.UTF_8)));
        server.write(ByteBuffer.wrap(("EHLO " + DOMAIN + "\r\n").getBytes(StandardCharsets.UTF_8)));
        readBytes(server);
        server.write(ByteBuffer.wrap(("MAIL FROM: <user@" + DOMAIN + ">\r\n").getBytes(StandardCharsets.UTF_8)));
        readBytes(server);
        server.write(ByteBuffer.wrap(("RCPT TO: <user@" + DOMAIN + ">\r\n").getBytes(StandardCharsets.UTF_8)));

        assertThat(new String(readBytes(server))).contains("530 5.7.1 Authentication Required");
    }

    private byte[] readBytes(SocketChannel channel) throws IOException {
        ByteBuffer line = ByteBuffer.allocate(1024);
        channel.read(line);
        line.rewind();
        byte[] bline = new byte[line.remaining()];
        line.get(bline);
        return bline;
    }
}
