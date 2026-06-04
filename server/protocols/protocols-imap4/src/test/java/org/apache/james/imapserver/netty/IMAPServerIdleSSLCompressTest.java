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

package org.apache.james.imapserver.netty;

import static org.apache.james.jmap.JMAPTestingConstants.LOCALHOST_IP;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.model.MailboxPath;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.fge.lambdas.Throwing;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.compression.JdkZlibDecoder;
import io.netty.handler.codec.compression.JdkZlibEncoder;
import io.netty.handler.codec.compression.ZlibWrapper;
import io.netty.handler.ssl.SslContextBuilder;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.Connection;
import reactor.netty.tcp.TcpClient;


@SuppressWarnings("checkstyle:membername")
class IMAPServerIdleSSLCompressTest extends AbstractIMAPServerTest {
    IMAPServer imapServer;
    private Connection connection;
    private ConcurrentLinkedDeque<String> responses;

    @BeforeEach
    void beforeEach() throws Exception {
        imapServer = createImapServer("imapServerSSLCompress.xml");
        int port = imapServer.getListenAddresses().get(0).getPort();
        MailboxSession mailboxSession = memoryIntegrationResources.getMailboxManager().createSystemSession(USER);
        memoryIntegrationResources.getMailboxManager()
            .createMailbox(MailboxPath.inbox(USER), mailboxSession);

        connection = TcpClient.create()
            .secure(Throwing.consumer(ssl -> ssl.sslContext(SslContextBuilder.forClient().trustManager(new BlindTrustManager()).build())))
            .remoteAddress(() -> new InetSocketAddress(LOCALHOST_IP, port))
            .connectNow();
        responses = new ConcurrentLinkedDeque<>();
        readBytes(connection);
    }

    @AfterEach
    void tearDown() {
        try {
            connection.disposeNow();
        } finally {
            imapServer.destroy();
        }
    }

    @Test
    void idleShouldBeInterruptible() {
        send(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS));

        send("a1 COMPRESS DEFLATE\r\n");

        Awaitility.await().until(() -> responses.stream().anyMatch(s -> s.contains("a1 OK COMPRESS DEFLATE active")));
        responses.clear();

        connection.addHandlerFirst(new JdkZlibDecoder(ZlibWrapper.ZLIB_OR_NONE));
        connection.addHandlerFirst(new JdkZlibEncoder(ZlibWrapper.NONE, 5));

        send("a2 SELECT INBOX\r\n");
        Awaitility.await().until(() -> responses.stream().anyMatch(s -> s.contains("a2 OK [READ-WRITE] SELECT completed.")));
        responses.clear();

        send("a3 IDLE\r\n");
        Awaitility.await().until(() -> responses.stream().anyMatch(s -> s.contains("+ Idling")));
        assertThat(responses).hasSize(1); // No pollution
        responses.clear();

        send("DONE\r\n");
        Awaitility.await().until(() -> responses.stream().anyMatch(s -> s.contains("a3 OK IDLE completed.")));
        assertThat(responses).hasSize(1); // No pollution
    }

    private void send(String format) {
        connection.outbound()
            .send(Mono.just(Unpooled.wrappedBuffer(format
                .getBytes(StandardCharsets.UTF_8))))
            .then()
            .subscribe();
    }

    private void readBytes(Connection connection) {
        connection.inbound().receive().asString()
            .doOnNext(responses::addLast)
            .subscribeOn(Schedulers.newSingle("test"))
            .subscribe();
    }
}
