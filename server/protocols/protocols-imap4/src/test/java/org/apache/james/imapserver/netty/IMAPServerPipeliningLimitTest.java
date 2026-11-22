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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.protocols.lib.mock.ConfigLoader;
import org.apache.james.util.ClassLoaderUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

import reactor.core.publisher.Mono;

class IMAPServerPipeliningLimitTest extends AbstractIMAPServerTest {
    private IMAPServer imapServer;
    private SocketChannel clientConnection;

    @AfterEach
    void tearDown() throws Exception {
        if (clientConnection != null) {
            clientConnection.close();
        }
        imapServer.destroy();
    }

    private void start(int maxPipelinedRequests, int maxPipelinedRequestsBeforeClosing) throws Exception {
        HierarchicalConfiguration<ImmutableNode> config = ConfigLoader.getConfig(ClassLoaderUtils.getSystemResourceAsSharedStream("imapServer.xml"));
        config.addProperty("maxPipelinedRequests", maxPipelinedRequests);
        config.addProperty("maxPipelinedRequestsBeforeClosing", maxPipelinedRequestsBeforeClosing);
        imapServer = createImapServer(config);

        MailboxSession mailboxSession = memoryIntegrationResources.getMailboxManager().createSystemSession(USER);
        memoryIntegrationResources.getMailboxManager().createMailbox(MailboxPath.inbox(USER), mailboxSession);
        // SELECT INBOX stays in flight long enough for the following requests to be queued
        doAnswer((Answer<Object>) invocation -> Mono.delay(Duration.ofMillis(500))
            .then((Mono<?>) invocation.callRealMethod()))
            .when(mailboxManager).getMailboxReactive(eq(MailboxPath.inbox(USER)), any());

        clientConnection = SocketChannel.open();
        clientConnection.connect(new InetSocketAddress(LOCALHOST_IP, imapServer.getListenAddresses().get(0).getPort()));
        readBytes(clientConnection);
        clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
        readStringUntil(clientConnection, s -> s.contains("a0 OK"));
    }

    private void pipelineNoopsBehindASelect(int noopCount) throws Exception {
        String noops = IntStream.range(0, noopCount)
            .mapToObj(i -> "n" + i + " NOOP\r\n")
            .collect(Collectors.joining());
        clientConnection.write(ByteBuffer.wrap(("a1 SELECT INBOX\r\n" + noops).getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void pipelinedRequestsShouldAllBeExecutedWhenReadingIsSuspended() throws Exception {
        start(2, 1000);

        pipelineNoopsBehindASelect(50);

        readStringUntil(clientConnection, s -> s.contains("n49 OK NOOP completed."));
    }

    @Test
    void tooManyPipelinedRequestsShouldCloseTheConnection() throws Exception {
        // Equal limits: the connection is closed before reading is suspended, whatever the reads look like
        start(10, 10);

        pipelineNoopsBehindASelect(50);

        assertThat(String.join("", readStringUntil(clientConnection, s -> s.contains("* BYE Too many pipelined requests"))))
            .doesNotContain("n49 OK");
    }

    @Test
    void suspendedReadingShouldResumeWhenTheRequestInFlightAwaitsClientInput() throws Exception {
        start(2, 1000);
        clientConnection.write(ByteBuffer.wrap("a1 SELECT INBOX\r\n".getBytes(StandardCharsets.UTF_8)));
        readStringUntil(clientConnection, s -> s.contains("a1 OK"));

        String noops = IntStream.range(0, 20)
            .mapToObj(i -> "n" + i + " NOOP\r\n")
            .collect(Collectors.joining());
        clientConnection.write(ByteBuffer.wrap(("a2 IDLE\r\n" + noops + "DONE\r\na3 NOOP\r\n").getBytes(StandardCharsets.UTF_8)));

        readStringUntil(clientConnection, s -> s.contains("a3 OK NOOP completed."));
    }
}
