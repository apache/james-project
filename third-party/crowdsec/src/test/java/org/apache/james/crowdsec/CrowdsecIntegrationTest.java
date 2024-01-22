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

package org.apache.james.crowdsec;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.james.crowdsec.client.CrowdsecClientConfiguration.DEFAULT_API_KEY;
import static org.apache.james.data.UsersRepositoryModuleChooser.Implementation.DEFAULT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;

import java.io.EOFException;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.stream.IntStream;

import org.apache.commons.net.pop3.POP3Client;
import org.apache.commons.net.smtp.SMTPClient;
import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.MemoryJamesConfiguration;
import org.apache.james.MemoryJamesServerMain;
import org.apache.james.crowdsec.client.CrowdsecClientConfiguration;
import org.apache.james.crowdsec.client.CrowdsecHttpClient;
import org.apache.james.crowdsec.model.CrowdsecDecision;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.modules.protocols.Pop3GuiceProbe;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.TestIMAPClient;
import org.assertj.core.api.SoftAssertions;
import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.utility.MountableFile;

import com.github.fge.lambdas.Throwing;

class CrowdsecIntegrationTest {
    @RegisterExtension
    static CrowdsecExtension crowdsecExtension = new CrowdsecExtension();

    @RegisterExtension
    static JamesServerExtension testExtension = new JamesServerBuilder<MemoryJamesConfiguration>(tmpDir ->
        MemoryJamesConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .usersRepository(DEFAULT)
            .build())
        .server(MemoryJamesServerMain::createServer)
        .extension(crowdsecExtension)
        .lifeCycle(JamesServerExtension.Lifecycle.PER_TEST)
        .build();

    @RegisterExtension
    public TestIMAPClient testIMAPClient = new TestIMAPClient();

    private static final String LOCALHOST_IP = "127.0.0.1";
    private static final String DOMAIN = "domain.tld";
    private static final String BOB = "bob@" + DOMAIN;
    private static final String BOB_PASSWORD = "bobPassword";
    private static final String BAD_PASSWORD = "badPassword";
    private static final ConditionFactory CALMLY_AWAIT = Awaitility
        .with().pollInterval(ONE_HUNDRED_MILLISECONDS)
        .and().pollDelay(ONE_HUNDRED_MILLISECONDS)
        .await();

    private HAProxyExtension haProxyExtension;
    private SMTPClient smtpProtocol;
    private POP3Client pop3Client;
    private CrowdsecHttpClient crowdsecClient;

    @BeforeEach
    void setup(GuiceJamesServer server, @TempDir Path tempDir) throws Exception {
        server.getProbe(DataProbeImpl.class).fluent()
            .addDomain(DOMAIN)
            .addUser(BOB, BOB_PASSWORD);

        haProxyExtension = new HAProxyExtension(MountableFile.forHostPath(createHaProxyConfigFile(server, tempDir).toString()));
        haProxyExtension.start();

        smtpProtocol = new SMTPClient();
        crowdsecClient = new CrowdsecHttpClient(new CrowdsecClientConfiguration(crowdsecExtension.getCrowdSecUrl(), DEFAULT_API_KEY));
        pop3Client = new POP3Client();
    }

    private Path createHaProxyConfigFile(GuiceJamesServer server, Path tempDir) throws IOException {
        String jamesServerWithProxySupportIp = crowdsecExtension.getCrowdsecContainer().getContainerInfo().getNetworkSettings()
            .getGateway(); // James server listens at the docker bridge network's gateway IP
        int smtpWithProxySupportPort = server.getProbe(SmtpGuiceProbe.class).getSmtpAuthRequiredPort().getValue();
        int imapWithProxySupportPort = server.getProbe(ImapGuiceProbe.class)
            .getPort(asyncServer -> asyncServer.getHelloName().equals("imapServerWithProxyEnabled"))
            .get();
        int pop3WithProxySupportPort = server.getProbe(Pop3GuiceProbe.class).getPort(
                asyncServer -> asyncServer.getHelloName().equals("pop3ServerWithProxyEnabled"))
            .get();
        String haproxyConfigContent = String.format("global\n" +
                "  log stdout format raw local0 info\n" +
                "\n" +
                "defaults\n" +
                "  mode tcp\n" +
                "  timeout client 1800s\n" +
                "  timeout connect 5s\n" +
                "  timeout server 1800s\n" +
                "  log global\n" +
                "  option tcplog\n" +
                "\n" +
                "frontend smtp-frontend\n" +
                "  bind :25\n" +
                "  default_backend james-server-smtp\n" +
                "\n" +
                "backend james-server-smtp\n" +
                "  server james1 %s:%d send-proxy\n" +
                "\n" +
                "frontend imap-frontend\n" +
                "  bind :143\n" +
                "  default_backend james-server-imap\n" +
                "\n" +
                "backend james-server-imap\n" +
                "  server james2 %s:%d send-proxy\n" +
                "\n" +
                "frontend pop3-frontend\n" +
                "  bind :110\n" +
                "  default_backend james-server-pop3\n" +
                "\n" +
                "backend james-server-pop3\n" +
                "  server james3 %s:%d send-proxy\n",
            jamesServerWithProxySupportIp, smtpWithProxySupportPort,
            jamesServerWithProxySupportIp, imapWithProxySupportPort,
            jamesServerWithProxySupportIp, pop3WithProxySupportPort);
        Path haProxyConfigFile = tempDir.resolve("haproxy.cfg");
        Files.write(haProxyConfigFile, haproxyConfigContent.getBytes());

        return haProxyConfigFile;
    }

    @AfterEach
    void teardown() {
        haProxyExtension.stop();
    }

    @Nested
    class IMAP {
        @Test
        void ipShouldBeBannedByCrowdSecWhenFailingToImapLoginThreeTimes(GuiceJamesServer server) {
            // GIVEN an IP failed to log in 3 consecutive times in a short period
            IntStream.range(0, 3)
                .forEach(any ->
                    assertThatThrownBy(() -> testIMAPClient.connect("127.0.0.1", server.getProbe(ImapGuiceProbe.class).getImapPort())
                        .login(BOB, BAD_PASSWORD))
                        .isInstanceOf(IOException.class)
                        .hasMessage("Login failed"));

            // THEN connection from the IP would be blocked. CrowdSec takes time to processing the ban decision therefore the await below.
            CALMLY_AWAIT.atMost(Durations.TEN_SECONDS)
                .untilAsserted(() -> assertThatThrownBy(() -> testIMAPClient.connect("127.0.0.1", server.getProbe(ImapGuiceProbe.class).getImapPort()))
                    .isInstanceOf(EOFException.class)
                    .hasMessage("Connection closed without indication."));
        }

        @Test
        void imapConnectionShouldRejectedAfterThreeFailedIMAPAuthenticationsViaProxy() {
            // GIVEN a client connected via proxy failed to log in 3 consecutive times in a short period
            IntStream.range(0, 3)
                .forEach(Throwing.intConsumer(any -> {
                    assertThatThrownBy(() -> testIMAPClient.connect(LOCALHOST_IP, haProxyExtension.getProxiedImapPort())
                        .login(BOB, BAD_PASSWORD))
                        .isInstanceOf(IOException.class)
                        .hasMessage("Login failed");
                }));

            // THEN IMAP connection from the client would be blocked.
            CALMLY_AWAIT.atMost(Durations.TEN_SECONDS)
                .untilAsserted(() -> assertThatThrownBy(() -> testIMAPClient.connect(LOCALHOST_IP, haProxyExtension.getProxiedImapPort())
                    .login(BOB, BOB_PASSWORD))
                    .isInstanceOf(EOFException.class)
                    .hasMessage("Connection closed without indication."));
        }

        @Test
        void shouldBanRealClientIpAndNotProxyIp() {
            // GIVEN a client connected via proxy failed to log in 3 consecutive times in a short period
            IntStream.range(0, 3)
                .forEach(Throwing.intConsumer(any -> {
                    assertThatThrownBy(() -> testIMAPClient.connect(LOCALHOST_IP, haProxyExtension.getProxiedImapPort())
                        .login(BOB, BAD_PASSWORD))
                        .isInstanceOf(IOException.class)
                        .hasMessage("Login failed");
                }));

            CALMLY_AWAIT.atMost(Durations.TEN_SECONDS)
                .untilAsserted(() -> assertThatThrownBy(() -> testIMAPClient.connect(LOCALHOST_IP, haProxyExtension.getProxiedImapPort())
                    .login(BOB, BOB_PASSWORD))
                    .isInstanceOf(EOFException.class)
                    .hasMessage("Connection closed without indication."));

            // THEN real client IP must be banned, not proxy IP
            String realClientIp = haProxyExtension.getHaproxyContainer().getContainerInfo().getNetworkSettings()
                .getGateway(); // client connect to HAProxy container via the docker bridge network's gateway IP
            String haProxyIp = haProxyExtension.getHaproxyContainer().getContainerInfo().getNetworkSettings()
                .getIpAddress();

            List<CrowdsecDecision> decisions = crowdsecClient.getCrowdsecDecisions().block();
            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(decisions).hasSize(1);
                softly.assertThat(decisions.get(0).getValue()).isEqualTo(realClientIp);
                softly.assertThat(decisions.get(0).getValue()).isNotEqualTo(haProxyIp);
            });
        }
    }

    @Nested
    class SMTP {
        @Test
        void ehloShouldRejectAfterThreeFailedSMTPAuthentications(GuiceJamesServer server) {
            // GIVEN a client failed to log in 3 consecutive times in a short period
            IntStream.range(0, 3)
                .forEach(Throwing.intConsumer(any -> {
                    smtpProtocol.connect(LOCALHOST_IP, server.getProbe(SmtpGuiceProbe.class).getSmtpPort().getValue());
                    smtpProtocol.sendCommand("EHLO", InetAddress.getLocalHost().toString());
                    smtpProtocol.sendCommand("AUTH PLAIN");
                    smtpProtocol.sendCommand(Base64.getEncoder().encodeToString(("\0" + BOB + "\0" + BAD_PASSWORD + "\0").getBytes(UTF_8)));
                    assertThat(smtpProtocol.getReplyString())
                        .contains("535 Authentication Failed");
                }));

            // THEN SMTP usage from the IP would be blocked upon the EHLO command. CrowdSec takes time to processing the ban decision therefore the await below.
            CALMLY_AWAIT.atMost(Durations.TEN_SECONDS)
                .untilAsserted(() -> {
                    smtpProtocol.connect(LOCALHOST_IP, server.getProbe(SmtpGuiceProbe.class).getSmtpPort().getValue());
                    smtpProtocol.sendCommand("EHLO", InetAddress.getLocalHost().toString());
                    assertThat(smtpProtocol.getReplyString())
                        .contains("554 Email rejected");
                });
        }

        @Test
        void ehloShouldRejectAfterThreeFailedSMTPAuthenticationsViaProxy() {
            // GIVEN a client connected via proxy failed to log in 3 consecutive times in a short period
            IntStream.range(0, 3)
                .forEach(Throwing.intConsumer(any -> {
                    smtpProtocol.connect(LOCALHOST_IP, haProxyExtension.getProxiedSmtpPort());
                    smtpProtocol.sendCommand("EHLO", InetAddress.getLocalHost().toString());
                    smtpProtocol.sendCommand("AUTH PLAIN");
                    smtpProtocol.sendCommand(Base64.getEncoder().encodeToString(("\0" + BOB + "\0" + BAD_PASSWORD + "\0").getBytes(UTF_8)));
                    assertThat(smtpProtocol.getReplyString())
                        .contains("535 Authentication Failed");
                }));

            // THEN SMTP usage from the client would be blocked upon the EHLO command.
            CALMLY_AWAIT.atMost(Durations.TEN_SECONDS)
                .untilAsserted(() -> {
                    smtpProtocol.connect(LOCALHOST_IP, haProxyExtension.getProxiedSmtpPort());
                    smtpProtocol.sendCommand("EHLO", InetAddress.getLocalHost().toString());
                    assertThat(smtpProtocol.getReplyString())
                        .contains("554 Email rejected");
                });
        }

        @Test
        void shouldBanRealClientIpAndNotProxyIp() {
            // GIVEN a client connected via proxy failed to log in 3 consecutive times in a short period
            IntStream.range(0, 3)
                .forEach(Throwing.intConsumer(any -> {
                    smtpProtocol.connect(LOCALHOST_IP, haProxyExtension.getProxiedSmtpPort());
                    smtpProtocol.sendCommand("EHLO", InetAddress.getLocalHost().toString());
                    smtpProtocol.sendCommand("AUTH PLAIN");
                    smtpProtocol.sendCommand(Base64.getEncoder().encodeToString(("\0" + BOB + "\0" + BAD_PASSWORD + "\0").getBytes(UTF_8)));
                    assertThat(smtpProtocol.getReplyString())
                        .contains("535 Authentication Failed");
                }));

            CALMLY_AWAIT.atMost(Durations.TEN_SECONDS)
                .untilAsserted(() -> {
                    smtpProtocol.connect(LOCALHOST_IP, haProxyExtension.getProxiedSmtpPort());
                    smtpProtocol.sendCommand("EHLO", InetAddress.getLocalHost().toString());
                    assertThat(smtpProtocol.getReplyString())
                        .contains("554 Email rejected");
                });

            // THEN real client IP must be banned, not proxy IP
            String realClientIp = haProxyExtension.getHaproxyContainer().getContainerInfo().getNetworkSettings()
                .getGateway(); // client connect to HAProxy container via the docker bridge network's gateway IP
            String haProxyIp = haProxyExtension.getHaproxyContainer().getContainerInfo().getNetworkSettings()
                .getIpAddress();

            List<CrowdsecDecision> decisions = crowdsecClient.getCrowdsecDecisions().block();
            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(decisions).hasSize(1);
                softly.assertThat(decisions.get(0).getValue()).isEqualTo(realClientIp);
                softly.assertThat(decisions.get(0).getValue()).isNotEqualTo(haProxyIp);
            });
        }

    }

    @Nested
    class POP3 {
        @Test
        void shouldRejectConnectionAfterThreeFailedPop3Authentications(GuiceJamesServer server) {
            IntStream.range(0, 3)
                .forEach(Throwing.intConsumer(any -> {
                    pop3Client.connect(LOCALHOST_IP, server.getProbe(Pop3GuiceProbe.class).getPop3Port());
                    pop3Client.sendCommand("user", BOB);
                    pop3Client.sendCommand("pass", BAD_PASSWORD);
                    pop3Client.sendCommand("noop");
                    assertThat(pop3Client.getReplyString()).startsWith("-ERR");
                }));

            CALMLY_AWAIT.atMost(Durations.TEN_SECONDS)
                .untilAsserted(() -> assertThatThrownBy(() -> pop3Client.connect(LOCALHOST_IP, server.getProbe(Pop3GuiceProbe.class).getPop3Port()))
                    .isInstanceOf(EOFException.class)
                    .hasMessage("Connection closed without indication."));
        }

        @Test
        void shouldRejectConnectionAfterThreeFailedPop3AuthenticationsViaProxy() {
            IntStream.range(0, 3)
                .forEach(Throwing.intConsumer(any -> {
                    pop3Client.connect(LOCALHOST_IP, haProxyExtension.getProxiedPop3Port());
                    pop3Client.sendCommand("user", BOB);
                    pop3Client.sendCommand("pass", BAD_PASSWORD);
                    pop3Client.sendCommand("noop");
                    assertThat(pop3Client.getReplyString()).startsWith("-ERR");
                }));

            CALMLY_AWAIT.atMost(Durations.TEN_SECONDS)
                .untilAsserted(() -> assertThatThrownBy(() -> pop3Client.connect(LOCALHOST_IP, haProxyExtension.getProxiedPop3Port()))
                    .isInstanceOf(EOFException.class)
                    .hasMessage("Connection closed without indication."));
        }

        @Test
        void shouldBanRealClientIpAndNotProxyIp() {
            IntStream.range(0, 3)
                .forEach(Throwing.intConsumer(any -> {
                    pop3Client.connect(LOCALHOST_IP, haProxyExtension.getProxiedPop3Port());
                    pop3Client.sendCommand("user", BOB);
                    pop3Client.sendCommand("pass", BAD_PASSWORD);
                    pop3Client.sendCommand("noop");
                    assertThat(pop3Client.getReplyString()).startsWith("-ERR");
                }));

            CALMLY_AWAIT.atMost(Durations.TEN_SECONDS)
                .untilAsserted(() -> assertThatThrownBy(() -> pop3Client.connect(LOCALHOST_IP, haProxyExtension.getProxiedPop3Port()))
                    .isInstanceOf(EOFException.class)
                    .hasMessage("Connection closed without indication."));

            // THEN real client IP must be banned, not proxy IP
            String realClientIp = haProxyExtension.getHaproxyContainer().getContainerInfo().getNetworkSettings()
                .getGateway(); // client connect to HAProxy container via the docker bridge network's gateway IP
            String haProxyIp = haProxyExtension.getHaproxyContainer().getContainerInfo().getNetworkSettings()
                .getIpAddress();

            List<CrowdsecDecision> decisions = crowdsecClient.getCrowdsecDecisions().block();
            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(decisions).hasSize(1);
                softly.assertThat(decisions.get(0).getValue()).isEqualTo(realClientIp);
                softly.assertThat(decisions.get(0).getValue()).isNotEqualTo(haProxyIp);
            });
        }
    }
}
