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

package org.apache.james.junit;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import com.google.common.base.Stopwatch;

@ExtendWith(JimfsExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class JimfsExtensionTest {

    private static final String SAMPLE_RFC822_MAIL =
        "From: sender@domain.tld\r\n" +
        "To: recipient@domain.tld\r\n" +
        "Subject: Jimfs Demonstration\r\n" +
        "\r\n" +
        "Hello from in-memory RFC-822 email payload.\r\n";

    @Test
    @Order(1)
    void shouldInjectInMemoryFileSystem(FileSystem fileSystem) {
        assertThat(fileSystem.isOpen()).isTrue();
        assertThat(fileSystem.provider().getScheme()).isEqualTo("jimfs");
    }

    @Test
    @Order(2)
    void shouldCreateConfigurationHierarchyInMemory(Path rootPath) throws IOException {
        Path confDir = rootPath.resolve("conf");
        Files.createDirectories(confDir);

        Path smtpConfig = confDir.resolve("smtpserver.xml");
        Files.writeString(smtpConfig, "<smtpservers><smtpserver><port>25</port></smtpserver></smtpservers>", StandardCharsets.UTF_8);

        assertThat(Files.exists(smtpConfig)).isTrue();
        assertThat(Files.readString(smtpConfig, StandardCharsets.UTF_8)).contains("<port>25</port>");
    }

    @Test
    @Order(3)
    void shouldHandleRfc822StreamProcessingInMemory(Path rootPath) throws IOException {
        Path spoolDir = rootPath.resolve("var/spool");
        Files.createDirectories(spoolDir);

        Path mailFile = spoolDir.resolve("mail-001.eml");
        Files.writeString(mailFile, SAMPLE_RFC822_MAIL, StandardCharsets.UTF_8);

        try (InputStream in = Files.newInputStream(mailFile)) {
            String readMail = IOUtils.toString(in, StandardCharsets.UTF_8);
            assertThat(readMail).isEqualTo(SAMPLE_RFC822_MAIL);
        }
    }

    @Test
    @Order(4)
    void shouldEnsureTestIsolationAcrossExecutions(Path rootPath) {
        assertThat(Files.exists(rootPath.resolve("conf"))).isFalse();
        assertThat(Files.exists(rootPath.resolve("var/spool"))).isFalse();
    }

    @Test
    @Order(5)
    void compareSpeedAndLatencyBetweenDiskAndJimfs(Path jimfsRoot, @TempDir Path diskTempDir) throws Exception {
        int iterations = 1000;
        int payloadSize = 64 * 1024; // 64 KB
        byte[] payload = new byte[payloadSize];
        Arrays.fill(payload, (byte) 'A');

        // 1. Jimfs run
        Path jimfsDir = jimfsRoot.resolve("bench");
        Files.createDirectories(jimfsDir);
        long[] jimfsLatencies = new long[iterations];
        Stopwatch jimfsWatch = Stopwatch.createStarted();
        for (int i = 0; i < iterations; i++) {
            Path file = jimfsDir.resolve("file-" + i + "-" + UUID.randomUUID() + ".tmp");
            long start = System.nanoTime();
            Files.write(file, payload);
            byte[] read = Files.readAllBytes(file);
            Files.delete(file);
            jimfsLatencies[i] = System.nanoTime() - start;
        }
        jimfsWatch.stop();
        long jimfsTotalMs = jimfsWatch.elapsed(TimeUnit.MILLISECONDS);

        // 2. Physical Disk run (using JUnit standard @TempDir)
        long[] diskLatencies = new long[iterations];
        Stopwatch diskWatch = Stopwatch.createStarted();
        for (int i = 0; i < iterations; i++) {
            Path file = diskTempDir.resolve("file-" + i + "-" + UUID.randomUUID() + ".tmp");
            long start = System.nanoTime();
            Files.write(file, payload);
            byte[] read = Files.readAllBytes(file);
            Files.delete(file);
            diskLatencies[i] = System.nanoTime() - start;
        }
        diskWatch.stop();
        long diskTotalMs = diskWatch.elapsed(TimeUnit.MILLISECONDS);

        // Metrics calculations
        Arrays.sort(jimfsLatencies);
        Arrays.sort(diskLatencies);

        double totalMb = ((double) iterations * payloadSize * 2.0) / (1024.0 * 1024.0);
        double jimfsMbSec = totalMb / (jimfsTotalMs / 1000.0);
        double diskMbSec = totalMb / (diskTotalMs / 1000.0);

        double jimfsP50Us = jimfsLatencies[(int) (iterations * 0.50)] / 1000.0;
        double diskP50Us = diskLatencies[(int) (iterations * 0.50)] / 1000.0;

        double jimfsP95Us = jimfsLatencies[(int) (iterations * 0.95)] / 1000.0;
        double diskP95Us = diskLatencies[(int) (iterations * 0.95)] / 1000.0;

        System.out.println("\n[JimfsExtensionTest] Performance Comparison (Jimfs In-Memory vs JUnit @TempDir on Disk):");
        System.out.printf("  Execution Time: Jimfs = %d ms | Disk (@TempDir) = %d ms (%.1fx faster)%n",
            jimfsTotalMs, diskTotalMs, (double) diskTotalMs / Math.max(1, jimfsTotalMs));
        System.out.printf("  Throughput:     Jimfs = %.2f MB/s | Disk (@TempDir) = %.2f MB/s (%.1fx higher)%n",
            jimfsMbSec, diskMbSec, jimfsMbSec / diskMbSec);
        System.out.printf("  Latency p50:    Jimfs = %.2f us | Disk (@TempDir) = %.2f us (%.1fx lower)%n",
            jimfsP50Us, diskP50Us, diskP50Us / jimfsP50Us);
        System.out.printf("  Latency p95:    Jimfs = %.2f us | Disk (@TempDir) = %.2f us (%.1fx lower)%n\n",
            jimfsP95Us, diskP95Us, diskP95Us / jimfsP95Us);

        assertThat(jimfsTotalMs).isLessThan(diskTotalMs);
    }
}
