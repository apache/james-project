/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.james;

import static org.apache.james.jmap.JMAPTestingConstants.LOCALHOST_IP;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import org.apache.james.backends.cassandra.StatementRecorder;
import org.apache.james.backends.cassandra.TestingSession;
import org.apache.james.backends.cassandra.init.SessionWithInitializedTablesFactory;
import org.apache.james.core.Domain;
import org.apache.james.mailbox.DefaultMailboxes;
import org.apache.james.mailbox.store.BatchSizes;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.modules.TestJMAPServerModule;
import org.apache.james.util.Port;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.GuiceProbe;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.SpoolerProbe;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.Statement;
import com.datastax.oss.protocol.internal.request.Batch;
import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

class WithCassandraBlobStoreTest implements MailsShouldBeWellReceivedConcreteContract {
    private static class TestingSessionProbe implements GuiceProbe {
        private final TestingSession testingSession;

        @Inject
        private TestingSessionProbe(TestingSession testingSession) {
            this.testingSession = testingSession;
        }

        public TestingSession getTestingSession() {
            return testingSession;
        }
    }

    private static class TestingSessionModule extends AbstractModule {
        @Override
        protected void configure() {
            Multibinder.newSetBinder(binder(), GuiceProbe.class)
                .addBinding()
                .to(TestingSessionProbe.class);

            bind(CqlSession.class).to(TestingSession.class);
        }

        @Provides
        @Singleton
        TestingSession provideSession(SessionWithInitializedTablesFactory factory) {
            return new TestingSession(factory.get());
        }
    }

    @RegisterExtension
    static JamesServerExtension jamesServerExtension = TestingDistributedJamesServerBuilder.withSearchConfiguration(SearchConfiguration.scanning())
        .extension(new CassandraExtension())
        .server(configuration -> CassandraJamesServerMain.createServer(configuration)
            .overrideWith(new TestJMAPServerModule()))
        .overrideServerModule(new TestingSessionModule())
        .overrideServerModule(binder -> binder.bind(BatchSizes.class).toInstance(BatchSizes.uniqueBatchSize(5)))
        .lifeCycle(JamesServerExtension.Lifecycle.PER_TEST)
        .build();

    @Test
    void imapFetchBackPressureShouldNotLoadMoreDataThanNecessary(GuiceJamesServer server) throws Exception {
        // 800K message
        String msgIn = "MIME-Version: 1.0\r\n\r\nCONTENT\r\n\r\n" + "0123456789\r\n0123456789\r\n0123456789\r\n".repeat(25 * 1024);

        server.getProbe(DataProbeImpl.class).fluent()
            .addDomain(DOMAIN)
            .addUser(JAMES_USER, PASSWORD)
            .addUser(SENDER, PASSWORD);

        MailboxProbeImpl mailboxProbe = server.getProbe(MailboxProbeImpl.class);
        mailboxProbe.createMailbox("#private", JAMES_USER, DefaultMailboxes.INBOX);

        int messageCount = 20;

        Port smtpPort = Port.of(smtpPort(server));
        try (SMTPMessageSender sender = new SMTPMessageSender(Domain.LOCALHOST.asString())) {
            Mono.fromRunnable(
                    Throwing.runnable(() -> {
                        sender.connect(JAMES_SERVER_HOST, smtpPort).authenticate(SENDER, PASSWORD);
                        sender.sendMessageWithHeaders("bob@apache.org", JAMES_USER, msgIn);
                    }))
                .repeat(messageCount - 1)
                .subscribeOn(Schedulers.fromExecutor(EXECUTOR))
                .blockLast();
        }

        CALMLY_AWAIT_FIVE_MINUTE.until(() -> server.getProbe(SpoolerProbe.class).processingFinished());

        SocketChannel clientConnection = SocketChannel.open();
        clientConnection.connect(new InetSocketAddress(LOCALHOST_IP, imapPort(server)));
        readBytes(clientConnection);
        clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", JAMES_USER, PASSWORD).getBytes(StandardCharsets.UTF_8)));
        readBytes(clientConnection);

        clientConnection.write(ByteBuffer.wrap(("A1 SELECT INBOX\r\n").getBytes(StandardCharsets.UTF_8)));
        // Select completes first
        readStringUntil(clientConnection, s -> s.contains("A1 OK [READ-WRITE] SELECT completed."));

        TestingSession testingSession = server.getProbe(TestingSessionProbe.class).getTestingSession();

        StatementRecorder statementRecorder = testingSession.recordStatements();
        clientConnection.write(ByteBuffer.wrap(("A2 UID FETCH 1:500 (BODY.PEEK[])\r\n").getBytes(StandardCharsets.UTF_8)));

        // Await backpressure to trigger
        Thread.sleep(2000);
        assertThat(statementRecorder
            .listExecutedStatements(StatementRecorder.Selector.preparedStatement("SELECT * FROM blobs WHERE id=:id")))
            .hasSizeLessThanOrEqualTo(30); // times 2 for header and blob, 10 skipped 5 prefetch.
    }

    private byte[] readBytes(SocketChannel channel) throws IOException {
        ByteBuffer line = ByteBuffer.allocate(1024);
        channel.read(line);
        line.rewind();
        byte[] bline = new byte[line.remaining()];
        line.get(bline);
        return bline;
    }

    private List<String> readStringUntil(SocketChannel channel, Predicate<String> condition) throws IOException {
        ImmutableList.Builder<String> result = ImmutableList.builder();
        while (true) {
            String line = new String(readBytes(channel), StandardCharsets.US_ASCII);
            System.out.println(line);
            result.add(line);
            if (condition.test(line)) {
                return result.build();
            }
        }
    }
}
