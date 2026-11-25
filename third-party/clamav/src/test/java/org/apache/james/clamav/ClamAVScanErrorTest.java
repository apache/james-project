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

package org.apache.james.clamav;

import static org.apache.james.clamav.ClamAVScan.INFECTED_HEADER_NAME;
import static org.apache.james.clamav.ClamAVScan.INFECTED_MAIL_ATTRIBUTE_NAME;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.mailet.Mail;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Against a fake CLAMD: a reply other than OK or FOUND must not mark the mail as clean, nor stop it.
 */
class ClamAVScanErrorTest {
    private static final String SIZE_LIMIT_EXCEEDED = "INSTREAM size limit exceeded. ERROR\0";

    private ServerSocket fakeClamd;
    private Thread fakeClamdThread;

    @AfterEach
    void tearDown() throws Exception {
        if (fakeClamd != null) {
            fakeClamd.close();
        }
        if (fakeClamdThread != null) {
            fakeClamdThread.join(5000);
        }
    }

    @FunctionalInterface
    interface Session {
        void handle(DataInputStream in, OutputStream out) throws IOException;
    }

    private ClamAVScan clamAVScanAgainst(Session session) throws Exception {
        fakeClamd = new ServerSocket(0);
        fakeClamdThread = new Thread(() -> {
            try (Socket socket = fakeClamd.accept()) {
                DataInputStream in = new DataInputStream(socket.getInputStream());
                in.readFully(new byte[10]); // zINSTREAM\0
                session.handle(in, socket.getOutputStream());
            } catch (IOException e) {
                // the client closed the connection
            }
        });
        fakeClamdThread.start();

        ClamAVScan clamAVScan = new ClamAVScan();
        clamAVScan.init(FakeMailetConfig.builder()
            .mailetName("Test")
            .setProperty("host", "localhost")
            .setProperty("port", String.valueOf(fakeClamd.getLocalPort()))
            .setProperty("maxPings", "0")
            .setProperty("streamBufferSize", "1024")
            .build());
        return clamAVScan;
    }

    private static void readUntilTheEndOfTheStream(DataInputStream in) throws IOException {
        int chunkSize = in.readInt();
        while (chunkSize > 0) {
            in.readFully(new byte[chunkSize]);
            chunkSize = in.readInt();
        }
    }

    private static Mail mail() throws Exception {
        return FakeMail.builder()
            .name("name")
            .recipient("user1@example.com")
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .setSubject("big")
                .setText("x".repeat(100_000)))
            .build();
    }

    private static Mail mailClaimingToBeClean() throws Exception {
        return FakeMail.builder()
            .name("name")
            .recipient("user1@example.com")
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .addHeader(INFECTED_HEADER_NAME, "false")
                .setSubject("big")
                .setText("x".repeat(100_000)))
            .build();
    }

    private static void assertNotScanned(ClamAVScan clamAVScan, Mail mail) throws Exception {
        clamAVScan.service(mail);

        assertThat(mail.getAttribute(INFECTED_MAIL_ATTRIBUTE_NAME)).isEmpty();
        assertThat(mail.getMessage().getHeader(INFECTED_HEADER_NAME)).isNull();
    }

    @Test
    void anErrorReplyAfterTheEndOfTheStreamShouldNotMarkTheMailAsClean() throws Exception {
        ClamAVScan clamAVScan = clamAVScanAgainst((in, out) -> {
            readUntilTheEndOfTheStream(in);
            out.write(SIZE_LIMIT_EXCEEDED.getBytes(StandardCharsets.US_ASCII));
        });

        assertNotScanned(clamAVScan, mail());
    }

    @Test
    void anErrorReplyDuringTheStreamShouldNotMarkTheMailAsClean() throws Exception {
        ClamAVScan clamAVScan = clamAVScanAgainst((in, out) -> {
            in.readFully(new byte[in.readInt()]);
            out.write(SIZE_LIMIT_EXCEEDED.getBytes(StandardCharsets.US_ASCII));
            out.flush();
        });

        assertNotScanned(clamAVScan, mail());
    }

    @Test
    void anEmptyReplyShouldNotMarkTheMailAsClean() throws Exception {
        ClamAVScan clamAVScan = clamAVScanAgainst((in, out) -> readUntilTheEndOfTheStream(in));

        assertNotScanned(clamAVScan, mail());
    }

    @Test
    void anOkReplyShouldMarkTheMailAsClean() throws Exception {
        ClamAVScan clamAVScan = clamAVScanAgainst((in, out) -> {
            readUntilTheEndOfTheStream(in);
            out.write("stream: OK\0".getBytes(StandardCharsets.US_ASCII));
        });
        Mail mail = mail();

        clamAVScan.service(mail);

        assertThat(mail.getMessage().getHeader(INFECTED_HEADER_NAME)).containsExactly("false");
    }

    @Test
    void aFoundReplyShouldMarkTheMailAsInfected() throws Exception {
        ClamAVScan clamAVScan = clamAVScanAgainst((in, out) -> {
            readUntilTheEndOfTheStream(in);
            out.write("stream: Eicar-Signature FOUND\0".getBytes(StandardCharsets.US_ASCII));
        });
        Mail mail = mail();

        clamAVScan.service(mail);

        assertThat(mail.getMessage().getHeader(INFECTED_HEADER_NAME)).containsExactly("true");
    }

    @Test
    void anUnscannedMailShouldNotKeepTheVerdictOfItsSender() throws Exception {
        ClamAVScan clamAVScan = clamAVScanAgainst((in, out) -> {
            readUntilTheEndOfTheStream(in);
            out.write(SIZE_LIMIT_EXCEEDED.getBytes(StandardCharsets.US_ASCII));
        });

        assertNotScanned(clamAVScan, mailClaimingToBeClean());
    }
}
