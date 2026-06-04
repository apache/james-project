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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.EOFException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.apache.commons.net.imap.IMAPReply;
import org.apache.commons.net.imap.IMAPSClient;
import org.apache.james.imap.processor.base.AbstractProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelOption;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.Connection;
import reactor.netty.tcp.TcpClient;


@SuppressWarnings("checkstyle:membername")
class IMAPServerStartTLSTest extends AbstractIMAPServerTest {
    IMAPServer imapServer;
    private int port;
    private Connection connection;
    private ConcurrentLinkedDeque<String> responses;

    @BeforeEach
    void beforeEach() throws Exception {
        imapServer = createImapServer("imapServerStartTLS.xml");
        port = imapServer.getListenAddresses().get(0).getPort();
        connection = TcpClient.create()
            .noSSL()
            .remoteAddress(() -> new InetSocketAddress(LOCALHOST_IP, port))
            .option(ChannelOption.TCP_NODELAY, true)
            .connectNow();
        responses = new ConcurrentLinkedDeque<>();
        connection.inbound().receive().asString()
            .doOnNext(responses::addLast)
            .subscribeOn(Schedulers.newSingle("imap-test"))
            .subscribe();
    }

    @AfterEach
    void tearDown() {
        imapServer.destroy();
    }

    @Test
    void extraLinesBatchedWithStartTLSShouldBeSanitized() throws Exception {
        IMAPSClient imapClient = new IMAPSClient();
        imapClient.connect("127.0.0.1", port);
        assertThatThrownBy(() -> imapClient.sendCommand("STARTTLS\r\nA1 NOOP\r\n"))
            .isInstanceOf(EOFException.class)
            .hasMessage("Connection closed without indication.");
    }

    @Test
    void extraLFLinesBatchedWithStartTLSShouldBeSanitized() throws Exception {
        IMAPSClient imapClient = new IMAPSClient();
        imapClient.connect("127.0.0.1", port);
        assertThatThrownBy(() -> imapClient.sendCommand("STARTTLS\nA1 NOOP\r\n"))
            .isInstanceOf(EOFException.class)
            .hasMessage("Connection closed without indication.");
    }

    @Test
    void tagsShouldBeWellSanitized() throws Exception {
        IMAPSClient imapClient = new IMAPSClient();
        imapClient.connect("127.0.0.1", port);
        assertThatThrownBy(() -> imapClient.sendCommand("NOOP\r\n A1 STARTTLS\r\nA2 NOOP"))
            .isInstanceOf(EOFException.class)
            .hasMessage("Connection closed without indication.");
    }

    @Test
    void lineFollowingStartTLSShouldBeSanitized() throws Exception {
        IMAPSClient imapClient = new IMAPSClient();
        imapClient.connect("127.0.0.1", port);
        assertThatThrownBy(() -> imapClient.sendCommand("STARTTLS A1 NOOP\r\n"))
            .isInstanceOf(EOFException.class)
            .hasMessage("Connection closed without indication.");
    }

    @Test
    void startTLSShouldFailWhenAuthenticated() throws Exception {
        // Avoids session fixation attacks as described in https://www.usenix.org/system/files/sec21-poddebniak.pdf
        // section 6.2

        IMAPSClient imapClient = new IMAPSClient();
        imapClient.connect("127.0.0.1", port);
        imapClient.login(USER.asString(), USER_PASS);
        int imapCode = imapClient.sendCommand("STARTTLS\r\n");

        assertThat(imapCode).isEqualTo(IMAPReply.NO);
    }

    private void send(String format) {
        connection.outbound()
            .send(Mono.just(Unpooled.wrappedBuffer(format
                .getBytes(StandardCharsets.UTF_8))))
            .then()
            .subscribe();
    }

    @RepeatedTest(10)
    void concurrencyShouldNotLeadToCommandInjection() throws Exception {
        ListAppender<ILoggingEvent> listAppender = getListAppenderForClass(AbstractProcessor.class);

        send("a0 STARTTLS\r\n");
        send("a1 NOOP\r\n");

        Thread.sleep(50);

        assertThat(listAppender.list)
            .filteredOn(event -> event.getFormattedMessage().contains("Processing org.apache.james.imap.message.request.NoopRequest"))
            .isEmpty();
    }
}
