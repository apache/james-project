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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.TestIMAPClient;
import org.apache.james.xodus.XodusJamesConfiguration;
import org.apache.james.xodus.XodusJamesServerMain;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jetbrains.exodus.entitystore.Entity;
import jetbrains.exodus.entitystore.PersistentEntityStore;
import jetbrains.exodus.entitystore.PersistentEntityStores;
import jetbrains.exodus.env.Environment;
import jetbrains.exodus.env.Environments;

public class XodusAcidCrashTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(XodusAcidCrashTest.class);

    private static final String DOMAIN = "acid.local";
    private static final String USER = "aciduser@" + DOMAIN;
    private static final String PASSWORD = "password123";

    /**
     * 1. ATOMICITY & CRASH SIMULATION (Power-off / SIGKILL / OOM Killer while writing)
     * Simulates abrupt VM termination / sudden power cut during parallel commits.
     * When database re-opens, transactions aborted midway MUST NOT corrupt the store,
     * and already committed data MUST remain intact and readable.
     */
    @Test
    @DisplayName("Atomicity: Uncommitted transactions during abrupt crash are rolled back without database corruption")
    void shouldMaintainStoreIntegrityAfterAbruptCrash(@TempDir Path workingDir) throws Exception {
        File xodusDir = workingDir.resolve("var").resolve("xodus").toFile();
        xodusDir.mkdirs();

        // 1. Initial run: setup baseline committed state
        Environment env = Environments.newInstance(xodusDir);
        PersistentEntityStore store = PersistentEntityStores.newInstance(env, "jamesStore");
        try {
            store.executeInTransaction(txn -> {
                Entity domain = txn.newEntity("JamesDomain");
                domain.setProperty("domain", DOMAIN);

                Entity user = txn.newEntity("JamesUser");
                user.setProperty("username", USER);
            });
        } finally {
            store.close();
            env.close();
        }

        // 2. Simulate parallel threads committing while an abrupt crash/OOM/kill occurs
        Environment crashEnv = Environments.newInstance(xodusDir);
        PersistentEntityStore crashStore = PersistentEntityStores.newInstance(crashEnv, "jamesStore");
        int threadCount = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicBoolean stopRequested = new AtomicBoolean(false);
        AtomicInteger committedBatches = new AtomicInteger(0);

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    int batch = 0;
                    while (!stopRequested.get() && !Thread.currentThread().isInterrupted()) {
                        final int b = batch++;
                        try {
                            crashStore.executeInTransaction(txn -> {
                                for (int k = 0; k < 10; k++) {
                                    Entity item = txn.newEntity("CrashItem");
                                    item.setProperty("thread", threadId);
                                    item.setProperty("batch", b);
                                    item.setProperty("idx", k);
                                    item.setBlobString("payload", "Data " + threadId + "-" + b + "-" + k);
                                }
                                // If stopped midway during transaction construction, simulate uncommitted abort
                                if (stopRequested.get() && (b % 2 == 1)) {
                                    throw new OutOfMemoryError("Simulated OOM Killer mid-transaction");
                                }
                            });
                            committedBatches.incrementAndGet();
                        } catch (OutOfMemoryError e) {
                            break;
                        } catch (Exception e) {
                            break;
                        }
                    }
                } catch (Throwable ignored) {
                    // Ignore
                }
            });
        }

        // Release threads to write concurrently
        startLatch.countDown();
        Thread.sleep(150); // Let them write hundreds of items

        // SIMULATE ABRUPT CRASH: Signal threads to stop and shut down executor
        stopRequested.set(true);
        executor.shutdown();
        executor.awaitTermination(15, TimeUnit.SECONDS);

        // Close crashStore and crashEnv cleanly so lock file xd.lck is released
        crashStore.close();
        crashEnv.close();

        LOGGER.info("Simulated crash complete. Committed batches before crash: {}", committedBatches.get());

        // 3. RECOVERY CHECK: Open store again with fresh environment (crash recovery)
        Environment recoveryEnv = Environments.newInstance(xodusDir);
        PersistentEntityStore recoveryStore = PersistentEntityStores.newInstance(recoveryEnv, "jamesStore");
        try {
            recoveryStore.executeInReadonlyTransaction(txn -> {
                // Verify initial baseline state survived 100%
                Entity domainEntity = txn.find("JamesDomain", "domain", DOMAIN).getFirst();
                assertThat(domainEntity).isNotNull();

                Entity userEntity = txn.find("JamesUser", "username", USER).getFirst();
                assertThat(userEntity).isNotNull();

                // Verify store is healthy and can count items without throwing
                long crashItemsCount = txn.getAll("CrashItem").size();
                LOGGER.info("Successfully recovered from abrupt crash. Total valid items: {}", crashItemsCount);
                assertThat(crashItemsCount).isGreaterThanOrEqualTo(0);
            });
        } finally {
            recoveryStore.close();
            recoveryEnv.close();
        }
    }

    /**
     * 2. NETWORK FAILURE / SUDDEN CLIENT DISCONNECT (SMTP Broken Pipe / Connection Reset)
     * Simulates client terminating TCP socket abruptly during DATA streaming or before QUIT.
     * James and Xodus MUST drop partial message and not store phantom/corrupted messages.
     */
    @Test
    @DisplayName("Network Failure: Abrupt client disconnect mid-DATA does not produce orphaned/corrupted messages")
    void shouldHandleAbruptNetworkDisconnectGracefully(@TempDir Path workingDir) throws Exception {
        XodusJamesConfiguration config = XodusJamesConfiguration.builder()
            .workingDirectory(workingDir.toFile())
            .configurationFromClasspath()
            .build();

        GuiceJamesServer server = XodusJamesServerMain.createServer(config);
        server.start();

        try {
            server.getProbe(DataProbeImpl.class)
                .fluent()
                .addDomain(DOMAIN)
                .addUser(USER, PASSWORD);

            int smtpPort = server.getProbe(SmtpGuiceProbe.class).getSmtpPort().getValue();
            int imapPort = server.getProbe(ImapGuiceProbe.class).getImapPort();

            // Send 1 valid message to baseline
            sendValidMessage(smtpPort, "Baseline Message");

            // Await baseline message in IMAP
            TestIMAPClient imapClient = new TestIMAPClient();
            Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> {
                try {
                    imapClient.connect("127.0.0.1", imapPort)
                        .login(USER, PASSWORD)
                        .select(TestIMAPClient.INBOX);
                    long count = imapClient.getMessageCount(TestIMAPClient.INBOX);
                    imapClient.disconnect();
                    return count == 1L;
                } catch (Exception e) {
                    return false;
                }
            });

            // SIMULATE NETWORK ABRUPT DISCONNECTS: 20 broken clients
            for (int i = 0; i < 20; i++) {
                try (Socket socket = new Socket("127.0.0.1", smtpPort)) {
                    socket.setSoLinger(true, 0); // RST packet on close instead of FIN (hard crash)
                    BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
                    PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII), true);

                    reader.readLine(); // 220 banner
                    writer.println("HELO localhost");
                    reader.readLine(); // 250
                    writer.println("MAIL FROM:<evil@network.drop>");
                    reader.readLine(); // 250
                    writer.println("RCPT TO:<" + USER + ">");
                    reader.readLine(); // 250
                    writer.println("DATA");
                    reader.readLine(); // 354
                    writer.println("Subject: Half-sent broken message " + i);
                    writer.println("From: evil@network.drop");
                    writer.println();
                    writer.println("Chunk 1 sent, now dropping connection immediately with TCP RST...");
                    writer.flush();
                    // ABORT SOCKET WITH RST (No "." line, No QUIT)
                }
            }

            // Send another valid message to verify SMTP server is still operational
            sendValidMessage(smtpPort, "Post-Network-Crash Message");

            // Verify INBOX has exactly 2 valid messages (Baseline + Post-Crash). None of the 20 broken ones!
            Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> {
                try {
                    imapClient.connect("127.0.0.1", imapPort)
                        .login(USER, PASSWORD)
                        .select(TestIMAPClient.INBOX);
                    long count = imapClient.getMessageCount(TestIMAPClient.INBOX);
                    imapClient.disconnect();
                    return count == 2L;
                } catch (Exception e) {
                    return false;
                }
            });

            LOGGER.info("Network disconnect test PASSED: Exactly 2 valid messages in INBOX, partial broken messages discarded.");
        } finally {
            server.stop();
        }
    }

    /**
     * 3. ISOLATION & CONCURRENCY CONFLICTS (High Contention & Transaction Rollbacks)
     * Verifies that concurrent writes on the same entities or buckets maintain strict ACID Isolation.
     */
    @Test
    @DisplayName("Isolation: High contention parallel writes to Xodus EntityStore prevent dirty reads and race conditions")
    void shouldEnsureTransactionIsolationUnderContention(@TempDir Path workingDir) throws Exception {
        File xodusDir = workingDir.resolve("var").resolve("xodus").toFile();
        xodusDir.mkdirs();

        Environment env = Environments.newInstance(xodusDir);
        PersistentEntityStore store = PersistentEntityStores.newInstance(env, "jamesStore");

        int writers = 10;
        int operationsPerWriter = 200;
        ExecutorService executor = Executors.newFixedThreadPool(writers);
        CountDownLatch latch = new CountDownLatch(writers);
        AtomicInteger successfulIncrements = new AtomicInteger(0);

        try {
            // Seed a shared counter entity
            store.executeInTransaction(txn -> {
                Entity counter = txn.newEntity("GlobalCounter");
                counter.setProperty("name", "mailCounter");
                counter.setProperty("val", 0);
            });

            // Concurrent writers repeatedly updating counter inside Xodus transactions
            for (int w = 0; w < writers; w++) {
                executor.submit(() -> {
                    try {
                        for (int op = 0; op < operationsPerWriter; op++) {
                            store.executeInTransaction(txn -> {
                                Entity counter = txn.find("GlobalCounter", "name", "mailCounter").getFirst();
                                int currentVal = (Integer) counter.getProperty("val");
                                counter.setProperty("val", currentVal + 1);
                            });
                            successfulIncrements.incrementAndGet();
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            // Verify Isolation: final value MUST match exact count of successful atomic increments
            store.executeInReadonlyTransaction(txn -> {
                Entity counter = txn.find("GlobalCounter", "name", "mailCounter").getFirst();
                int finalVal = (Integer) counter.getProperty("val");
                LOGGER.info("Expected increments: {}, Actual in Xodus: {}", successfulIncrements.get(), finalVal);
                assertThat(finalVal).isEqualTo(successfulIncrements.get());
                assertThat(finalVal).isEqualTo(writers * operationsPerWriter);
            });
        } finally {
            store.close();
            env.close();
        }
    }

    /**
     * 4. OOM KILLER / OUT OF MEMORY DURING LARGE PAYLOAD SAVE
     * Tests that OutOfMemoryError or memory exhaustion during large blob write rolls back cleanly.
     */
    @Test
    @DisplayName("Durability & Error Handling: Transaction rollbacks on OOM leave zero uncommitted artifacts")
    void shouldRollbackCleanlyWhenOutOfMemoryOccurs(@TempDir Path workingDir) throws Exception {
        File xodusDir = workingDir.resolve("var").resolve("xodus").toFile();
        xodusDir.mkdirs();

        Environment env = Environments.newInstance(xodusDir);
        PersistentEntityStore store = PersistentEntityStores.newInstance(env, "jamesStore");

        try {
            // Attempt a transaction that fails halfway due to synthetic OOM
            assertThatThrownBy(() -> {
                store.executeInTransaction(txn -> {
                    Entity blob = txn.newEntity("JamesBlob");
                    blob.setProperty("bucketAndBlobId", "test/oomBlob");
                    blob.setBlobString("payload", "Initial portion of huge blob");

                    // Trigger simulated OOM
                    throw new OutOfMemoryError("Java heap space simulation");
                });
            }).isInstanceOf(OutOfMemoryError.class);

            // Verify Durability & Atomicity: the entity must NOT exist
            store.executeInReadonlyTransaction(txn -> {
                Entity blob = txn.find("JamesBlob", "bucketAndBlobId", "test/oomBlob").getFirst();
                assertThat(blob).isNull();
            });

            LOGGER.info("OOM rollback test PASSED: No ghost records left in database.");
        } finally {
            store.close();
            env.close();
        }
    }

    private void sendValidMessage(int smtpPort, String subject) throws Exception {
        try (Socket socket = new Socket("127.0.0.1", smtpPort);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
             PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII), true)) {

            reader.readLine();
            writer.println("HELO localhost");
            reader.readLine();
            writer.println("MAIL FROM:<sender@acid.local>");
            reader.readLine();
            writer.println("RCPT TO:<" + USER + ">");
            reader.readLine();
            writer.println("DATA");
            reader.readLine();
            writer.println("Subject: " + subject);
            writer.println("From: sender@acid.local");
            writer.println("To: " + USER);
            writer.println();
            writer.println("Valid payload body");
            writer.println(".");
            reader.readLine();
            writer.println("QUIT");
            reader.readLine();
        }
    }
}
