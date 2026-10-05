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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.TestIMAPClient;
import org.apache.james.xodus.XodusJamesConfiguration;
import org.apache.james.xodus.XodusJamesServerMain;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class XodusBenchmarkTest implements JamesServerConcreteContract {
    private static final Logger LOGGER = LoggerFactory.getLogger(XodusBenchmarkTest.class);

    @RegisterExtension
    static JamesServerExtension jamesServerExtension = new JamesServerBuilder<XodusJamesConfiguration>(tmpDir ->
        XodusJamesConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .build())
        .server(XodusJamesServerMain::createServer)
        .lifeCycle(JamesServerExtension.Lifecycle.PER_CLASS)
        .build();

    static final String DOMAIN = "benchmark.local";
    static final String USER = "bench@" + DOMAIN;
    static final String PASSWORD = "secretpassword";
    static final int TOTAL_MESSAGES = 5000;
    static final int CONCURRENCY = 20;

    @Test
    void benchmark5000Messages(GuiceJamesServer jamesServer) throws Exception {
        jamesServer.getProbe(DataProbeImpl.class)
            .fluent()
            .addDomain(DOMAIN)
            .addUser(USER, PASSWORD);

        int smtpPort = jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort().getValue();
        int imapPort = jamesServer.getProbe(ImapGuiceProbe.class).getImapPort();

        LOGGER.info("=== STARTING BENCHMARK: {} messages, concurrency: {} ===", TOTAL_MESSAGES, CONCURRENCY);

        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENCY);
        CountDownLatch latch = new CountDownLatch(TOTAL_MESSAGES);
        List<Long> latenciesMs = Collections.synchronizedList(new ArrayList<>(TOTAL_MESSAGES));
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        long globalStartNs = System.nanoTime();

        for (int i = 0; i < TOTAL_MESSAGES; i++) {
            final int msgIndex = i;
            executor.submit(() -> {
                long startNs = System.nanoTime();
                try (Socket socket = new Socket("127.0.0.1", smtpPort);
                     BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
                     PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII), true)) {

                    reader.readLine(); // 220 banner

                    writer.println("HELO localhost");
                    reader.readLine(); // 250

                    writer.println("MAIL FROM:<sender@benchmark.local>");
                    reader.readLine(); // 250

                    writer.println("RCPT TO:<" + USER + ">");
                    reader.readLine(); // 250

                    writer.println("DATA");
                    reader.readLine(); // 354

                    writer.println("Subject: Benchmark Message #" + msgIndex);
                    writer.println("From: sender@benchmark.local");
                    writer.println("To: " + USER);
                    writer.println();
                    writer.println("Payload content line 1 for Xodus benchmark message " + msgIndex);
                    writer.println("Payload content line 2: Testing JetBrains Xodus persistence and indexing.");
                    writer.println(".");
                    reader.readLine(); // 250 OK

                    writer.println("QUIT");
                    reader.readLine(); // 221

                    long durationMs = (System.nanoTime() - startNs) / 1_000_000;
                    latenciesMs.add(durationMs);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    LOGGER.error("Error sending message #{}", msgIndex, e);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(5, TimeUnit.MINUTES);
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);

        long globalDurationMs = (System.nanoTime() - globalStartNs) / 1_000_000;
        double throughput = (successCount.get() * 1000.0) / globalDurationMs;

        List<Long> sortedLatencies = new ArrayList<>(latenciesMs);
        Collections.sort(sortedLatencies);

        long p50 = sortedLatencies.isEmpty() ? 0 : sortedLatencies.get((int) (sortedLatencies.size() * 0.50));
        long p95 = sortedLatencies.isEmpty() ? 0 : sortedLatencies.get((int) (sortedLatencies.size() * 0.95));
        long p99 = sortedLatencies.isEmpty() ? 0 : sortedLatencies.get((int) (sortedLatencies.size() * 0.99));
        long minLatency = sortedLatencies.isEmpty() ? 0 : sortedLatencies.get(0);
        long maxLatency = sortedLatencies.isEmpty() ? 0 : sortedLatencies.get(sortedLatencies.size() - 1);
        double avgLatency = sortedLatencies.stream().mapToLong(Long::longValue).average().orElse(0.0);

        System.out.println("==========================================================");
        System.out.println("                 XODUS-APP BENCHMARK RESULTS               ");
        System.out.println("==========================================================");
        System.out.println(String.format("Total Messages Sent    : %d", TOTAL_MESSAGES));
        System.out.println(String.format("Successful Injections  : %d", successCount.get()));
        System.out.println(String.format("Failed Injections      : %d", failureCount.get()));
        System.out.println(String.format("Total Elapsed Time     : %d ms (%.2f s)", globalDurationMs, globalDurationMs / 1000.0));
        System.out.println(String.format("Throughput             : %.2f msgs/sec", throughput));
        System.out.println(String.format("Latency Min / Avg / Max: %d ms / %.2f ms / %d ms", minLatency, avgLatency, maxLatency));
        System.out.println(String.format("Latency P50 (Median)   : %d ms", p50));
        System.out.println(String.format("Latency P95            : %d ms", p95));
        System.out.println(String.format("Latency P99            : %d ms", p99));
        System.out.println("==========================================================");

        // Verify with IMAP delivery check
        TestIMAPClient imapClient = new TestIMAPClient();
        Awaitility.await()
            .atMost(2, TimeUnit.MINUTES)
            .pollInterval(1, TimeUnit.SECONDS)
            .until(() -> {
                try {
                    imapClient.connect("127.0.0.1", imapPort)
                        .login(USER, PASSWORD)
                        .select(TestIMAPClient.INBOX);
                    return imapClient.hasAMessage();
                } catch (Exception e) {
                    return false;
                } finally {
                    try {
                        imapClient.disconnect();
                    } catch (Exception ignored) {
                        // Ignore
                    }
                }
            });
    }
}
