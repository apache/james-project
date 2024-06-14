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

import static org.apache.james.data.UsersRepositoryModuleChooser.Implementation.DEFAULT;
import static org.apache.james.jmap.JMAPTestingConstants.LOCALHOST_IP;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

import org.apache.james.core.Domain;
import org.apache.james.modules.TestJMAPServerModule;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.util.ClassLoaderUtils;
import org.apache.james.utils.DataProbeImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class DKIMHookIntegrationTest {
    private static final Domain DOMAIN = Domain.of("avocat.fr");

    @RegisterExtension
    static JamesServerExtension jamesServerExtension = new JamesServerBuilder<MemoryJamesConfiguration>(tmpDir ->
        MemoryJamesConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .usersRepository(DEFAULT)
            .build())
        .server(configuration -> MemoryJamesServerMain.createServer(configuration)
            .overrideWith(new TestJMAPServerModule()))
        .build();

    @BeforeEach
    void setUp(GuiceJamesServer server) throws Exception {
        server.getProbe(DataProbeImpl.class).fluent()
            .addDomain(DOMAIN.asString())
            .addUser("user@" + DOMAIN.asString(), "pass1");
    }

    @Test
    void shouldRejectCheckedDomainWhenNotSigned(GuiceJamesServer guiceJamesServer) throws Exception {
        SocketChannel server = SocketChannel.open();
        server.connect(new InetSocketAddress(LOCALHOST_IP, guiceJamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort().getValue()));
        readBytes(server);

        server.write(ByteBuffer.wrap(("EHLO " + DOMAIN.asString() + "\r\n").getBytes(StandardCharsets.UTF_8)));
        readBytes(server);
        server.write(ByteBuffer.wrap(("MAIL FROM: <user@" + DOMAIN.asString() + ">\r\n").getBytes(StandardCharsets.UTF_8)));
        readBytes(server);
        server.write(ByteBuffer.wrap(("RCPT TO: <user@" + DOMAIN.asString() + ">\r\n").getBytes(StandardCharsets.UTF_8)));
        readBytes(server);
        server.write(ByteBuffer.wrap(("DATA\r\n").getBytes(StandardCharsets.UTF_8)));
        readBytes(server); // needed to synchronize
        server.write(ByteBuffer.wrap(("header:value\r\n\r\nbody").getBytes(StandardCharsets.UTF_8)));
        server.write(ByteBuffer.wrap(("\r\n").getBytes(StandardCharsets.UTF_8)));
        server.write(ByteBuffer.wrap((".").getBytes(StandardCharsets.UTF_8)));
        server.write(ByteBuffer.wrap(("\r\n").getBytes(StandardCharsets.UTF_8)));
        byte[] dataResponse = readBytes(server);
        server.write(ByteBuffer.wrap(("QUIT\r\n").getBytes(StandardCharsets.UTF_8)));

        assertThat(new String(dataResponse, StandardCharsets.UTF_8))
            .contains("530 DKIM check failed. Expecting DKIM signatures. Got none.");
    }

    @Test
    void shouldAcceptNotCheckedDomains(GuiceJamesServer guiceJamesServer) throws Exception {
        SocketChannel server = SocketChannel.open();
        server.connect(new InetSocketAddress(LOCALHOST_IP, guiceJamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort().getValue()));
        readBytes(server);

        server.write(ByteBuffer.wrap(("EHLO whatever.com\r\n").getBytes(StandardCharsets.UTF_8)));
        readBytes(server);
        server.write(ByteBuffer.wrap(("MAIL FROM: <user@whatever.com>\r\n").getBytes(StandardCharsets.UTF_8)));
        readBytes(server);
        server.write(ByteBuffer.wrap(("RCPT TO: <user@" + DOMAIN.asString() + ">\r\n").getBytes(StandardCharsets.UTF_8)));
        readBytes(server);
        server.write(ByteBuffer.wrap(("DATA\r\n").getBytes(StandardCharsets.UTF_8)));
        readBytes(server); // needed to synchronize
        server.write(ByteBuffer.wrap(("header:value\r\n\r\nbody").getBytes(StandardCharsets.UTF_8)));
        server.write(ByteBuffer.wrap(("\r\n").getBytes(StandardCharsets.UTF_8)));
        server.write(ByteBuffer.wrap((".").getBytes(StandardCharsets.UTF_8)));
        server.write(ByteBuffer.wrap(("\r\n").getBytes(StandardCharsets.UTF_8)));
        byte[] dataResponse = readBytes(server);
        server.write(ByteBuffer.wrap(("QUIT\r\n").getBytes(StandardCharsets.UTF_8)));

        assertThat(new String(dataResponse, StandardCharsets.UTF_8))
            .contains("250 2.6.0 Message received");
    }

    @Test
    void shouldAcceptEmailsForCheckedDomainsWhenSigned(GuiceJamesServer guiceJamesServer) throws Exception {
        SocketChannel server = SocketChannel.open();
        server.connect(new InetSocketAddress(LOCALHOST_IP, guiceJamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort().getValue()));
        readBytes(server);

        server.write(ByteBuffer.wrap(("EHLO " + DOMAIN.asString() + "\r\n").getBytes(StandardCharsets.UTF_8)));
        readBytes(server);
        server.write(ByteBuffer.wrap(("MAIL FROM: <user@" + DOMAIN.asString() + ">\r\n").getBytes(StandardCharsets.UTF_8)));
        readBytes(server);
        server.write(ByteBuffer.wrap(("RCPT TO: <user@" + DOMAIN.asString() + ">\r\n").getBytes(StandardCharsets.UTF_8)));
        readBytes(server);
        server.write(ByteBuffer.wrap(("DATA\r\n").getBytes(StandardCharsets.UTF_8)));
        readBytes(server); // needed to synchronize

        server.write(ByteBuffer.wrap(ClassLoaderUtils.getSystemResourceAsByteArray("eml/goodDkim.eml")));

        server.write(ByteBuffer.wrap(("\r\n").getBytes(StandardCharsets.UTF_8)));
        server.write(ByteBuffer.wrap((".").getBytes(StandardCharsets.UTF_8)));
        server.write(ByteBuffer.wrap(("\r\n").getBytes(StandardCharsets.UTF_8)));
        byte[] dataResponse = readBytes(server);
        server.write(ByteBuffer.wrap(("QUIT\r\n").getBytes(StandardCharsets.UTF_8)));

        assertThat(new String(dataResponse, StandardCharsets.UTF_8))
            .contains("250 2.6.0 Message received");
    }

    @Test
    void shouldRejectInvalidDKIMSignatures(GuiceJamesServer guiceJamesServer) throws Exception {
        SocketChannel server = SocketChannel.open();
        server.connect(new InetSocketAddress(LOCALHOST_IP, guiceJamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort().getValue()));
        readBytes(server);

        server.write(ByteBuffer.wrap(("EHLO " + DOMAIN.asString() + "\r\n").getBytes(StandardCharsets.UTF_8)));
        readBytes(server);
        server.write(ByteBuffer.wrap(("MAIL FROM: <user@" + DOMAIN.asString() + ">\r\n").getBytes(StandardCharsets.UTF_8)));
        readBytes(server);
        server.write(ByteBuffer.wrap(("RCPT TO: <user@" + DOMAIN.asString() + ">\r\n").getBytes(StandardCharsets.UTF_8)));
        readBytes(server);
        server.write(ByteBuffer.wrap(("DATA\r\n").getBytes(StandardCharsets.UTF_8)));
        readBytes(server); // needed to synchronize

        server.write(ByteBuffer.wrap(ClassLoaderUtils.getSystemResourceAsByteArray("eml/badDkim.eml")));

        server.write(ByteBuffer.wrap(("\r\n").getBytes(StandardCharsets.UTF_8)));
        server.write(ByteBuffer.wrap((".").getBytes(StandardCharsets.UTF_8)));
        server.write(ByteBuffer.wrap(("\r\n").getBytes(StandardCharsets.UTF_8)));
        byte[] dataResponse = readBytes(server);
        server.write(ByteBuffer.wrap(("QUIT\r\n").getBytes(StandardCharsets.UTF_8)));

        assertThat(new String(dataResponse, StandardCharsets.UTF_8))
            .contains("530 DKIM check failed. Invalid signature.");
    }

    @Test
    void shouldRejectCheckedDomainsWhenSignatureOfOtherDomain(GuiceJamesServer guiceJamesServer) throws Exception {
        SocketChannel server = SocketChannel.open();
        server.connect(new InetSocketAddress(LOCALHOST_IP, guiceJamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort().getValue()));
        readBytes(server);

        server.write(ByteBuffer.wrap(("EHLO " + DOMAIN.asString() + "\r\n").getBytes(StandardCharsets.UTF_8)));
        readBytes(server);
        server.write(ByteBuffer.wrap(("MAIL FROM: <user@" + DOMAIN.asString() + ">\r\n").getBytes(StandardCharsets.UTF_8)));
        readBytes(server);
        server.write(ByteBuffer.wrap(("RCPT TO: <user@" + DOMAIN.asString() + ">\r\n").getBytes(StandardCharsets.UTF_8)));
        readBytes(server);
        server.write(ByteBuffer.wrap(("DATA\r\n").getBytes(StandardCharsets.UTF_8)));
        readBytes(server); // needed to synchronize

        server.write(ByteBuffer.wrap(ClassLoaderUtils.getSystemResourceAsByteArray("eml/otherDomainDkim.eml")));

        server.write(ByteBuffer.wrap(("\r\n").getBytes(StandardCharsets.UTF_8)));
        server.write(ByteBuffer.wrap((".").getBytes(StandardCharsets.UTF_8)));
        server.write(ByteBuffer.wrap(("\r\n").getBytes(StandardCharsets.UTF_8)));
        byte[] dataResponse = readBytes(server);
        server.write(ByteBuffer.wrap(("QUIT\r\n").getBytes(StandardCharsets.UTF_8)));

        assertThat(new String(dataResponse, StandardCharsets.UTF_8))
            .contains("530 DKIM check failed. Wrong d token. Expecting avocat.fr");
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
