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
package org.apache.james.pop3server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.mail.util.SharedByteArrayInputStream;

import org.apache.commons.configuration2.XMLConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.net.pop3.POP3Client;
import org.apache.commons.net.pop3.POP3MessageInfo;
import org.apache.commons.net.pop3.POP3Reply;
import org.apache.commons.net.pop3.POP3SClient;
import org.apache.james.UserEntityValidator;
import org.apache.james.core.Username;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.jwt.OidcJwtTokenVerifier;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.pop3server.mailbox.DefaultMailboxAdapterFactory;
import org.apache.james.pop3server.mailbox.MailboxAdapterFactory;
import org.apache.james.pop3server.netty.POP3Server;
import org.apache.james.pop3server.netty.POP3ServerFactory;
import org.apache.james.protocols.api.OIDCSASLHelper;
import org.apache.james.protocols.api.sasl.SaslAuthenticator;
import org.apache.james.protocols.api.sasl.SaslExchange;
import org.apache.james.protocols.api.sasl.SaslIdentity;
import org.apache.james.protocols.api.sasl.SaslInitialRequest;
import org.apache.james.protocols.api.sasl.SaslMechanism;
import org.apache.james.protocols.api.sasl.SaslMechanismNames;
import org.apache.james.protocols.api.sasl.SaslStep;
import org.apache.james.protocols.api.utils.BogusSslContextFactory;
import org.apache.james.protocols.api.utils.BogusTrustManagerFactory;
import org.apache.james.protocols.api.utils.ProtocolServerUtils;
import org.apache.james.protocols.lib.LegacyJavaEncryptionFactory;
import org.apache.james.protocols.lib.POP3BeforeSMTPHelper;
import org.apache.james.protocols.lib.mock.ConfigLoader;
import org.apache.james.protocols.lib.mock.MockProtocolHandlerLoader;
import org.apache.james.protocols.netty.AbstractChannelPipelineFactory;
import org.apache.james.protocols.sasl.oidc.OAuthSaslMechanism;
import org.apache.james.server.core.filesystem.FileSystemImpl;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;
import com.google.inject.name.Names;

import reactor.core.publisher.Flux;

public class POP3ServerTest {
    private static final DomainList NO_DOMAIN_LIST = null;
    private static final String OAUTH_TOKEN = "token";
    private static final byte[] INVALID_TOKEN_RESPONSE = "{\"status\":\"invalid_token\"}".getBytes(StandardCharsets.UTF_8);
    private static final int OVERSIZED_SASL_CONTINUATION_LENGTH = 65537;

    private static class TestOidcJwtTokenVerifier extends OidcJwtTokenVerifier {
        private final Optional<Username> validationResult;

        private TestOidcJwtTokenVerifier(Optional<Username> validationResult) {
            super(null);
            this.validationResult = validationResult;
        }

        @Override
        public Optional<Username> validateToken(String token) {
            return validationResult;
        }
    }

    private static class ServerDataSaslMechanism implements SaslMechanism {
        private static final byte[] CHALLENGE = "challenge".getBytes(StandardCharsets.US_ASCII);
        private static final byte[] SERVER_DATA = "server-data".getBytes(StandardCharsets.US_ASCII);

        private final Username username;
        private final AtomicBoolean closed;
        private final String name;

        private ServerDataSaslMechanism(Username username, AtomicBoolean closed) {
            this("TEST", username, closed);
        }

        private ServerDataSaslMechanism(String name, Username username, AtomicBoolean closed) {
            this.name = name;
            this.username = username;
            this.closed = closed;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public SaslExchange start(SaslInitialRequest request, SaslAuthenticator authenticator) {
            return new SaslExchange() {
                @Override
                public SaslStep firstStep() {
                    return new SaslStep.Challenge(Optional.of(CHALLENGE));
                }

                @Override
                public SaslStep onResponse(byte[] clientResponse) {
                    return new SaslStep.Success(new SaslIdentity(username, username), Optional.of(SERVER_DATA));
                }

                @Override
                public void close() {
                    closed.set(true);
                }
            };
        }
    }

    private XMLConfiguration pop3Configuration;
    protected final MemoryUsersRepository usersRepository = MemoryUsersRepository.withoutVirtualHosting(NO_DOMAIN_LIST);
    private POP3Client pop3Client = null;
    protected FileSystemImpl fileSystem;
    protected MockProtocolHandlerLoader protocolHandlerChain;
    protected StoreMailboxManager mailboxManager;
    private final byte[] content = ("Return-path: return@test.com\r\n"
        + "Content-Transfer-Encoding: plain\r\n"
        + "Subject: test\r\n\r\n"
        + "Body Text POP3ServerTest.setupTestMails\r\n").getBytes();
    private POP3Server pop3Server;

    @BeforeEach
    void setUp() throws Exception {
        setUpServiceManager();
        setUpPOP3Server();
        pop3Configuration = ConfigLoader.getConfig(fileSystem.getResource("classpath://pop3server.xml"));
        configureSaslMechanismsFromConfiguration();
    }

    private void configureSaslMechanismsFromConfiguration() throws ConfigurationException {
        pop3Server.setSaslMechanisms(POP3ServerFactory.Pop3SaslMechanismLoader.defaultLoader().load(pop3Configuration));
    }

    @AfterEach
    void tearDown() throws Exception {
        try {
            if (pop3Client != null) {
                if (pop3Client.isConnected()) {
                    pop3Client.sendCommand("quit");
                    pop3Client.disconnect();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        protocolHandlerChain.dispose();
        pop3Server.destroy();
    }

    @Nested
    class StartTlsSanitizing {
        @Test
        void connectionAreClosedWhenSTLSFollowedByACommand() throws Exception {
            finishSetUp(pop3Configuration);

            pop3Client = new POP3SClient();
            InetSocketAddress bindedAddress = new ProtocolServerUtils(pop3Server).retrieveBindedAddress();
            pop3Client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());

            pop3Client.sendCommand("STLS\r\nCAPA\r\n");

            assertThatThrownBy(() -> pop3Client.sendCommand("quit"))
                .isInstanceOf(IOException.class);
        }

        @Test
        void startTLSShouldFailWhenAuthenticated() throws Exception {
            // Avoids session fixation attacks as described in https://www.usenix.org/system/files/sec21-poddebniak.pdf
            // section 6.2
            finishSetUp(pop3Configuration);

            pop3Client = new POP3SClient();
            InetSocketAddress bindedAddress = new ProtocolServerUtils(pop3Server).retrieveBindedAddress();
            pop3Client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
            usersRepository.addUser(Username.of("known"), "test");
            pop3Client.login("known", "test");

            int replyCode = pop3Client.sendCommand("STLS\r\n");
            assertThat(replyCode).isEqualTo(POP3Reply.ERROR);
        }

        @Test
        void connectionAreClosedWhenSTLSFollowedByText() throws Exception {
            finishSetUp(pop3Configuration);

            pop3Client = new POP3SClient();
            InetSocketAddress bindedAddress = new ProtocolServerUtils(pop3Server).retrieveBindedAddress();
            pop3Client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());

            pop3Client.sendCommand("STLS unexpected\r\n");

            assertThatThrownBy(() -> pop3Client.sendCommand("quit"))
                .isInstanceOf(IOException.class);
        }
    }

    @Test
    void testAuthenticationFail() throws Exception {
        finishSetUp(pop3Configuration);

        pop3Client = new POP3Client();
        InetSocketAddress bindedAddress = new ProtocolServerUtils(pop3Server).retrieveBindedAddress();
        pop3Client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());

        usersRepository.addUser(Username.of("known"), "test2");

        pop3Client.login("known", "test");
        assertThat(pop3Client.getState()).isEqualTo(0);
        assertThat(pop3Client.getReplyString().startsWith("-ERR")).isTrue();
    }

    @Test
    void testUnknownUser() throws Exception {
        finishSetUp(pop3Configuration);

        pop3Client = new POP3Client();
        InetSocketAddress bindedAddress = new ProtocolServerUtils(pop3Server).retrieveBindedAddress();
        pop3Client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());

        pop3Client.login("unknown", "test");
        assertThat(pop3Client.getState()).isEqualTo(0);
        assertThat(pop3Client.getReplyString().startsWith("-ERR")).isTrue();
    }

    @Test
    void testKnownUserEmptyInbox() throws Exception {
        finishSetUp(pop3Configuration);

        pop3Client = new POP3Client();
        InetSocketAddress bindedAddress = new ProtocolServerUtils(pop3Server).retrieveBindedAddress();
        pop3Client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());

        usersRepository.addUser(Username.of("foo"), "bar");

        // not authenticated
        POP3MessageInfo[] entries = pop3Client.listMessages();
        assertThat(entries).isNull();

        pop3Client.login("foo", "bar");
        System.err.println(pop3Client.getState());
        assertThat(pop3Client.getState()).isEqualTo(1);

        entries = pop3Client.listMessages();
        assertThat(pop3Client.getState()).isEqualTo(1);

        assertThat(entries).isNotNull();
        assertThat(0).isEqualTo(entries.length);

        POP3MessageInfo p3i = pop3Client.listMessage(1);
        assertThat(pop3Client.getState()).isEqualTo(1);
        assertThat(p3i).isNull();
    }

    // TODO: This currently fails with Async implementation because
    // it use Charset US-ASCII to decode / Encode the protocol
    // from the RFC I'm currently not understand if NON-ASCII chars
    // are allowed at all. So this needs to be checked
    /*
     * public void testNotAsciiCharsInPassword() throws Exception {
     * finishSetUp(m_testConfiguration);
     *
     * m_pop3Protocol = new POP3Client();
     * m_pop3Protocol.connect("127.0.0.1",m_pop3ListenerPort);
     *
     * String pass = "bar" + (new String(new char[] { 200, 210 })) + "foo";
     * m_usersRepository.addUser("foo", pass); InMemorySpoolRepository
     * mockMailRepository = new InMemorySpoolRepository();
     * m_mailServer.setUserInbox("foo", mockMailRepository);
     *
     * m_pop3Protocol.login("foo", pass); assertEquals(1,
     * m_pop3Protocol.getState()); ContainerUtil.dispose(mockMailRepository); }
     */

    @Test
    void testUnknownCommand() throws Exception {
        finishSetUp(pop3Configuration);

        pop3Client = new POP3Client();
        InetSocketAddress bindedAddress = new ProtocolServerUtils(pop3Server).retrieveBindedAddress();
        pop3Client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());

        pop3Client.sendCommand("unkn");
        assertThat(pop3Client.getState()).isEqualTo(0);
        assertThat("-ERR").describedAs("Expected -ERR as result for an unknown command").isEqualTo(pop3Client.getReplyString().substring(0, 4));
    }

    @Test
    void testUidlCommand() throws Exception {
        finishSetUp(pop3Configuration);

        Username username = Username.of("foo");
        usersRepository.addUser(username, "bar");

        pop3Client = new POP3Client();
        InetSocketAddress bindedAddress = new ProtocolServerUtils(pop3Server).retrieveBindedAddress();
        pop3Client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());

        pop3Client.sendCommand("uidl");
        assertThat(pop3Client.getState()).isEqualTo(0);

        pop3Client.login("foo", "bar");

        POP3MessageInfo[] list = pop3Client.listUniqueIdentifiers();
        assertThat(list.length).describedAs("Found unexpected messages").isEqualTo(0);

        pop3Client.disconnect();
        MailboxPath mailboxPath = MailboxPath.inbox(username);
        MailboxSession session = mailboxManager.authenticate(username, "bar").withoutDelegation();
        if (!mailboxManager.mailboxExists(mailboxPath, session).block()) {
            mailboxManager.createMailbox(mailboxPath, session);
        }
        setupTestMails(session, mailboxManager.getMailbox(mailboxPath, session));

        pop3Client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
        pop3Client.login("foo", "bar");

        list = pop3Client.listUniqueIdentifiers();
        assertThat(list.length).describedAs("Expected 2 messages, found: " + list.length).isEqualTo(2);

        POP3MessageInfo p3i = pop3Client.listUniqueIdentifier(1);
        assertThat(p3i).isNotNull();

        mailboxManager.deleteMailbox(mailboxPath, session);

    }

    @Test
    void testMiscCommandsWithWithoutAuth() throws Exception {
        finishSetUp(pop3Configuration);

        usersRepository.addUser(Username.of("foo"), "bar");

        pop3Client = new POP3Client();
        InetSocketAddress bindedAddress = new ProtocolServerUtils(pop3Server).retrieveBindedAddress();
        pop3Client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());

        pop3Client.sendCommand("noop");
        assertThat(pop3Client.getState()).isEqualTo(0);
        assertThat(pop3Client.getReplyString().substring(0, 4)).isEqualTo("-ERR");

        pop3Client.sendCommand("stat");
        assertThat(pop3Client.getState()).isEqualTo(0);
        assertThat(pop3Client.getReplyString().substring(0, 4)).isEqualTo("-ERR");

        pop3Client.sendCommand("pass");
        assertThat(pop3Client.getState()).isEqualTo(0);
        assertThat(pop3Client.getReplyString().substring(0, 4)).isEqualTo("-ERR");

        pop3Client.sendCommand("auth");
        assertThat(pop3Client.getState()).isEqualTo(0);
        assertThat(pop3Client.getReplyString().substring(0, 4)).isEqualTo("-ERR");

        pop3Client.sendCommand("rset");
        assertThat(pop3Client.getState()).isEqualTo(0);
        assertThat(pop3Client.getReplyString().substring(0, 4)).isEqualTo("-ERR");

        pop3Client.login("foo", "bar");

        POP3MessageInfo[] list = pop3Client.listUniqueIdentifiers();
        assertThat(list.length).describedAs("Found unexpected messages").isEqualTo(0);

        pop3Client.sendCommand("noop");
        assertThat(pop3Client.getState()).isEqualTo(1);

        pop3Client.sendCommand("pass");
        assertThat(pop3Client.getState()).isEqualTo(1);
        assertThat(pop3Client.getReplyString().substring(0, 4)).isEqualTo("-ERR");

        pop3Client.sendCommand("auth");
        assertThat(pop3Client.getState()).isEqualTo(1);
        assertThat(pop3Client.getReplyString().substring(0, 4)).isEqualTo("-ERR");

        pop3Client.sendCommand("user");
        assertThat(pop3Client.getState()).isEqualTo(1);
        assertThat(pop3Client.getReplyString().substring(0, 4)).isEqualTo("-ERR");

        pop3Client.sendCommand("rset");
        assertThat(pop3Client.getState()).isEqualTo(1);

    }

    @Test
    void retrShouldNotHangForeverOnMalformedMessageEndingWithLF() throws Exception {
        // GIVEN a user with one malformed message in its INBOX
        Username username = Username.of("foo2");
        usersRepository.addUser(username, "bar2");
        MailboxPath mailboxPath = MailboxPath.inbox(username);
        MailboxSession session = mailboxManager.authenticate(username, "bar2").withoutDelegation();
        mailboxManager.createMailbox(mailboxPath, session);
        byte[] content = ("Return-path: return@test.com\r\n"
            + "Content-Transfer-Encoding: plain\r\n"
            + "Subject: test\r\n\r\n"
            + "Body Text POP3ServerTest.setupTestMails\n").getBytes();
        mailboxManager.getMailbox(mailboxPath, session)
            .appendMessage(MessageManager.AppendCommand.from(
                new SharedByteArrayInputStream(content)), session);

        // THEN retrieving it in POP3 times out
        finishSetUp(pop3Configuration);
        pop3Client = new POP3Client();
        InetSocketAddress bindedAddress = new ProtocolServerUtils(pop3Server).retrieveBindedAddress();
        pop3Client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
        pop3Client.login("foo2", "bar2");
        POP3MessageInfo[] entries = pop3Client.listMessages();
        Reader r = pop3Client.retrieveMessage(entries[0].number);

        // This no longer times out
        Executors.newSingleThreadExecutor()
            .submit(() -> {
                try {
                    r.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            })
            .get(5, TimeUnit.SECONDS);
    }

    @Test
    void retrShouldNotHangForeverOnMalformedMessageEndingWithoutLineBreak() throws Exception {
        // GIVEN a user with one malformed message in its INBOX
        Username username = Username.of("foo2");
        usersRepository.addUser(username, "bar2");
        MailboxPath mailboxPath = MailboxPath.inbox(username);
        MailboxSession session = mailboxManager.authenticate(username, "bar2").withoutDelegation();
        mailboxManager.createMailbox(mailboxPath, session);
        byte[] content = ("Return-path: return@test.com\r\n"
            + "Content-Transfer-Encoding: plain\r\n"
            + "Subject: test\r\n\r\n"
            + "Body Text POP3ServerTest.setupTestMails").getBytes();
        mailboxManager.getMailbox(mailboxPath, session)
            .appendMessage(MessageManager.AppendCommand.from(
                new SharedByteArrayInputStream(content)), session);

        // THEN retrieving it in POP3 times out
        finishSetUp(pop3Configuration);
        pop3Client = new POP3Client();
        InetSocketAddress bindedAddress = new ProtocolServerUtils(pop3Server).retrieveBindedAddress();
        pop3Client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
        pop3Client.login("foo2", "bar2");
        POP3MessageInfo[] entries = pop3Client.listMessages();
        Reader r = pop3Client.retrieveMessage(entries[0].number);

        // This do not time out
        Executors.newSingleThreadExecutor()
            .submit(() -> {
                try {
                    r.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            })
            .get(5, TimeUnit.SECONDS);
    }

    @Test
    void retrShouldNotHangForeverOnMalformedMessageEndingWithCR() throws Exception {
        // GIVEN a user with one malformed message in its INBOX
        Username username = Username.of("foo2");
        usersRepository.addUser(username, "bar2");
        MailboxPath mailboxPath = MailboxPath.inbox(username);
        MailboxSession session = mailboxManager.authenticate(username, "bar2").withoutDelegation();
        mailboxManager.createMailbox(mailboxPath, session);
        byte[] content = ("Return-path: return@test.com\r\n"
            + "Content-Transfer-Encoding: plain\r\n"
            + "Subject: test\r\n\r\n"
            + "Body Text POP3ServerTest.setupTestMails\r").getBytes();
        mailboxManager.getMailbox(mailboxPath, session)
            .appendMessage(MessageManager.AppendCommand.from(
                new SharedByteArrayInputStream(content)), session);

        // THEN retrieving it in POP3 times out
        finishSetUp(pop3Configuration);
        pop3Client = new POP3Client();
        InetSocketAddress bindedAddress = new ProtocolServerUtils(pop3Server).retrieveBindedAddress();
        pop3Client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
        pop3Client.login("foo2", "bar2");
        POP3MessageInfo[] entries = pop3Client.listMessages();
        Reader r = pop3Client.retrieveMessage(entries[0].number);

        // This do not time out
        Executors.newSingleThreadExecutor()
            .submit(() -> {
                try {
                    r.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            })
            .get(5, TimeUnit.SECONDS);
    }

    @Test
    void testKnownUserInboxWithMessages() throws Exception {
        finishSetUp(pop3Configuration);

        pop3Client = new POP3Client();
        InetSocketAddress bindedAddress = new ProtocolServerUtils(pop3Server).retrieveBindedAddress();
        pop3Client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());

        Username username = Username.of("foo2");
        usersRepository.addUser(username, "bar2");

        MailboxPath mailboxPath = MailboxPath.inbox(username);
        MailboxSession session = mailboxManager.authenticate(username, "bar2").withoutDelegation();

        if (!mailboxManager.mailboxExists(mailboxPath, session).block()) {
            mailboxManager.createMailbox(mailboxPath, session);
        }

        setupTestMails(session, mailboxManager.getMailbox(mailboxPath, session));

        pop3Client.sendCommand("retr", "1");
        assertThat(pop3Client.getState()).isEqualTo(0);
        assertThat(pop3Client.getReplyString().substring(0, 4)).isEqualTo("-ERR");

        pop3Client.login("foo2", "bar2");
        assertThat(pop3Client.getState()).isEqualTo(1);

        POP3MessageInfo[] entries = pop3Client.listMessages();

        assertThat(entries).isNotNull();
        assertThat(entries.length).isEqualTo(2);
        assertThat(pop3Client.getState()).isEqualTo(1);

        Reader r = pop3Client.retrieveMessageTop(entries[0].number, 0);

        assertThat(r).isNotNull();

        r.close();

        Reader r2 = pop3Client.retrieveMessage(entries[0].number);
        assertThat(r2).isNotNull();
        r2.close();

        // existing message
        boolean deleted = pop3Client.deleteMessage(entries[0].number);
        assertThat(deleted).isTrue();

        // already deleted message
        deleted = pop3Client.deleteMessage(entries[0].number);

        // TODO: Understand why this fails...
        assertThat(deleted).isFalse();

        // unexisting message
        deleted = pop3Client.deleteMessage(10);
        assertThat(deleted).isFalse();

        pop3Client.logout();
        //m_pop3Protocol.disconnect();

        pop3Client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());

        pop3Client.login("foo2", "bar2");
        assertThat(pop3Client.getState()).isEqualTo(1);

        entries = null;

        POP3MessageInfo stats = pop3Client.status();
        assertThat(stats.number).isEqualTo(1);
        assertThat(stats.size).isEqualTo(5);

        entries = pop3Client.listMessages();

        assertThat(entries).isNotNull();
        assertThat(entries.length).isEqualTo(1);
        assertThat(pop3Client.getState()).isEqualTo(1);

        // top without arguments
        pop3Client.sendCommand("top");
        assertThat(pop3Client.getReplyString().substring(0, 4)).isEqualTo("-ERR");

        Reader r3 = pop3Client.retrieveMessageTop(entries[0].number, 0);
        assertThat(r3).isNotNull();
        r3.close();
        mailboxManager.deleteMailbox(mailboxPath, session);
    }

    @Test
    void topWithZeroLinesShouldReturnOnlyHeaders() throws Exception {
        finishSetUp(pop3Configuration);

        pop3Client = new POP3Client();
        InetSocketAddress bindedAddress = new ProtocolServerUtils(pop3Server).retrieveBindedAddress();
        pop3Client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());

        Username username = Username.of("topzero");
        usersRepository.addUser(username, "password");

        MailboxPath mailboxPath = MailboxPath.inbox(username);
        MailboxSession session = mailboxManager.authenticate(username, "password").withoutDelegation();
        mailboxManager.createMailbox(mailboxPath, session);
        setupTestMails(session, mailboxManager.getMailbox(mailboxPath, session));

        pop3Client.login("topzero", "password");
        POP3MessageInfo[] entries = pop3Client.listMessages();

        Reader reader = pop3Client.retrieveMessageTop(entries[0].number, 0);
        assertThat(reader).isNotNull();

        StringBuilder responseBody = new StringBuilder();
        char[] buffer = new char[1024];
        int n;
        while ((n = reader.read(buffer)) != -1) {
            responseBody.append(buffer, 0, n);
        }
        reader.close();

        String response = responseBody.toString();
        assertThat(response).contains("Return-path: return@test.com");
        assertThat(response).contains("Subject: test");
        assertThat(response).doesNotContain("Body Text POP3ServerTest.setupTestMails");

        mailboxManager.deleteMailbox(mailboxPath, session);
    }

    @Test
    void pop3SessionShouldTolerateConcurrentDeletes() throws Exception {
        finishSetUp(pop3Configuration);

        pop3Client = new POP3Client();
        InetSocketAddress bindedAddress = new ProtocolServerUtils(pop3Server).retrieveBindedAddress();
        pop3Client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());

        Username username = Username.of("foo2");
        usersRepository.addUser(username, "bar2");

        MailboxPath mailboxPath = MailboxPath.inbox(username);
        MailboxSession session = mailboxManager.authenticate(username, "bar2").withoutDelegation();

        if (!mailboxManager.mailboxExists(mailboxPath, session).block()) {
            mailboxManager.createMailbox(mailboxPath, session);
        }

        setupTestMails(session, mailboxManager.getMailbox(mailboxPath, session));

        pop3Client.login("foo2", "bar2");
        assertThat(pop3Client.getState()).isEqualTo(1);

        POP3MessageInfo[] entries = pop3Client.listMessages();

        assertThat(entries).isNotNull();
        assertThat(entries.length).isEqualTo(2);
        assertThat(pop3Client.getState()).isEqualTo(1);

        mailboxManager.getMailbox(mailboxPath, session).delete(
            Flux.from(mailboxManager.getMailbox(mailboxPath, session).listMessagesMetadata(MessageRange.all(), session))
                .map(i -> i.getComposedMessageId().getUid())
                .collectList().block(),
            session);

        Reader r = pop3Client.retrieveMessageTop(entries[0].number, 0);

        assertThat(r).isNull(); // = message not found

        // POP3 session must still be valid afterwards
        assertThatCode(() -> pop3Client.listMessages()).doesNotThrowAnyException();
    }

    /**
     * Test for JAMES-1202 -  Which shows that UIDL,STAT and LIST all show the same message numbers.
     */
    @Test
    void testStatUidlList() throws Exception {
        finishSetUp(pop3Configuration);

        pop3Client = new POP3Client();
        InetSocketAddress bindedAddress = new ProtocolServerUtils(pop3Server).retrieveBindedAddress();
        pop3Client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());

        Username username = Username.of("foo2");
        usersRepository.addUser(username, "bar2");

        MailboxPath mailboxPath = MailboxPath.inbox(username);
        MailboxSession session = mailboxManager.authenticate(username, "bar2").withoutDelegation();

        if (!mailboxManager.mailboxExists(mailboxPath, session).block()) {
            mailboxManager.createMailbox(mailboxPath, session);
        }

        int msgCount = 100;
        for (int i = 0; i < msgCount; i++) {
            mailboxManager.getMailbox(mailboxPath, session).appendMessage(MessageManager.AppendCommand.from(
                    Message.Builder.of()
                        .setSubject("test")
                        .setBody(String.valueOf(i), StandardCharsets.UTF_8)),
                session);
        }

        pop3Client.login("foo2", "bar2");
        assertThat(pop3Client.getState()).isEqualTo(1);

        POP3MessageInfo[] listEntries = pop3Client.listMessages();
        POP3MessageInfo[] uidlEntries = pop3Client.listUniqueIdentifiers();
        POP3MessageInfo statInfo = pop3Client.status();
        assertThat(listEntries.length).isEqualTo(msgCount);
        assertThat(uidlEntries.length).isEqualTo(msgCount);
        assertThat(statInfo.number).isEqualTo(msgCount);

        pop3Client.sendCommand("quit");
        pop3Client.disconnect();

        pop3Client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());

        pop3Client.login("foo2", "bar2");
        assertThat(pop3Client.getState()).isEqualTo(1);

        mailboxManager.deleteMailbox(mailboxPath, session);

    }

    @Test
    @Disabled("Test for JAMES-1202 - This was failing before as the more then one connection to the same mailbox was not handled the right way")
    void testStatUidlListTwoConnections() throws Exception {
        finishSetUp(pop3Configuration);

        pop3Client = new POP3Client();
        InetSocketAddress bindedAddress = new ProtocolServerUtils(pop3Server).retrieveBindedAddress();
        pop3Client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());

        Username username = Username.of("foo2");
        usersRepository.addUser(username, "bar2");

        MailboxPath mailboxPath = MailboxPath.inbox(username);
        MailboxSession session = mailboxManager.authenticate(username, "bar2").withoutDelegation();

        if (!mailboxManager.mailboxExists(mailboxPath, session).block()) {
            mailboxManager.createMailbox(mailboxPath, session);
        }

        int msgCount = 100;
        for (int i = 0; i < msgCount; i++) {
            mailboxManager.getMailbox(mailboxPath, session).appendMessage(MessageManager.AppendCommand.from(
                Message.Builder.of()
                    .setSubject("test")
                    .setBody(String.valueOf(i), StandardCharsets.UTF_8)), session);
        }

        pop3Client.login("foo2", "bar2");
        assertThat(pop3Client.getState()).isEqualTo(1);

        POP3MessageInfo[] listEntries = pop3Client.listMessages();
        POP3MessageInfo[] uidlEntries = pop3Client.listUniqueIdentifiers();
        POP3MessageInfo statInfo = pop3Client.status();
        assertThat(listEntries.length).isEqualTo(msgCount);
        assertThat(uidlEntries.length).isEqualTo(msgCount);
        assertThat(statInfo.number).isEqualTo(msgCount);

        POP3Client pop3Protocol2 = new POP3Client();
        pop3Protocol2.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
        pop3Protocol2.login("foo2", "bar2");
        assertThat(pop3Protocol2.getState()).isEqualTo(1);

        POP3MessageInfo[] listEntries2 = pop3Protocol2.listMessages();
        POP3MessageInfo[] uidlEntries2 = pop3Protocol2.listUniqueIdentifiers();
        POP3MessageInfo statInfo2 = pop3Protocol2.status();
        assertThat(listEntries2.length).isEqualTo(msgCount);
        assertThat(uidlEntries2.length).isEqualTo(msgCount);
        assertThat(statInfo2.number).isEqualTo(msgCount);

        pop3Client.deleteMessage(1);
        listEntries = pop3Client.listMessages();
        uidlEntries = pop3Client.listUniqueIdentifiers();
        statInfo = pop3Client.status();
        assertThat(listEntries.length).isEqualTo(msgCount - 1);
        assertThat(uidlEntries.length).isEqualTo(msgCount - 1);
        assertThat(statInfo.number).isEqualTo(msgCount - 1);

        // even after the message was deleted it should get displayed in the
        // second connection
        listEntries2 = pop3Protocol2.listMessages();
        uidlEntries2 = pop3Protocol2.listUniqueIdentifiers();
        statInfo2 = pop3Protocol2.status();
        assertThat(listEntries2.length).isEqualTo(msgCount);
        assertThat(uidlEntries2.length).isEqualTo(msgCount);
        assertThat(statInfo2.number).isEqualTo(msgCount);

        assertThat(pop3Client.logout()).isTrue();
        pop3Client.disconnect();

        // even after the message was deleted and the session was quit it should
        // get displayed in the second connection
        listEntries2 = pop3Protocol2.listMessages();
        uidlEntries2 = pop3Protocol2.listUniqueIdentifiers();
        statInfo2 = pop3Protocol2.status();
        assertThat(listEntries2.length).isEqualTo(msgCount);
        assertThat(uidlEntries2.length).isEqualTo(msgCount);
        assertThat(statInfo2.number).isEqualTo(msgCount);

        // This both should error and so return null
        assertThat(pop3Protocol2.retrieveMessageTop(1, 100)).isNull();
        assertThat(pop3Protocol2.retrieveMessage(1)).isNull();

        pop3Protocol2.sendCommand("quit");
        pop3Protocol2.disconnect();

        mailboxManager.deleteMailbox(mailboxPath, session);

    }

    /*
     * public void testTwoSimultaneousMails() throws Exception {
     * finishSetUp(m_testConfiguration);
     *
     * // make two user/repositories, open both
     * m_usersRepository.addUser("foo1", "bar1"); InMemorySpoolRepository
     * mailRep1 = new InMemorySpoolRepository(); setupTestMails(mailRep1);
     * m_mailServer.setUserInbox("foo1", mailRep1);
     *
     * m_usersRepository.addUser("foo2", "bar2"); InMemorySpoolRepository
     * mailRep2 = new InMemorySpoolRepository(); //do not setupTestMails, this
     * is done later m_mailServer.setUserInbox("foo2", mailRep2);
     *
     * POP3Client pop3Protocol2 = null; try { // open two connections
     * m_pop3Protocol = new POP3Client(); m_pop3Protocol.connect("127.0.0.1",
     * m_pop3ListenerPort); pop3Protocol2 = new POP3Client();
     * pop3Protocol2.connect("127.0.0.1", m_pop3ListenerPort);
     *
     * assertEquals("first connection taken", 0, m_pop3Protocol.getState());
     * assertEquals("second connection taken", 0, pop3Protocol2.getState());
     *
     * // open two accounts m_pop3Protocol.login("foo1", "bar1");
     *
     * pop3Protocol2.login("foo2", "bar2");
     *
     * POP3MessageInfo[] entries = m_pop3Protocol.listMessages();
     * assertEquals("foo1 has mails", 2, entries.length);
     *
     * entries = pop3Protocol2.listMessages(); assertEquals("foo2 has no mails",
     * 0, entries.length);
     *
     * } finally { // put both to rest, field var is handled by tearDown() if
     * (pop3Protocol2 != null) { pop3Protocol2.sendCommand("quit");
     * pop3Protocol2.disconnect(); } } }
     */

    @Test
    void testIpStored() throws Exception {

        finishSetUp(pop3Configuration);

        pop3Client = new POP3Client();
        InetSocketAddress bindedAddress = new ProtocolServerUtils(pop3Server).retrieveBindedAddress();
        pop3Client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());

        String pass = "password";
        usersRepository.addUser(Username.of("foo"), pass);

        pop3Client.login("foo", pass);
        assertThat(pop3Client.getState()).isEqualTo(1);
        assertThat(POP3BeforeSMTPHelper.isAuthorized("127.0.0.1")).isTrue();

    }

    @Test
    void testCapa() throws Exception {
        finishSetUp(pop3Configuration);

        pop3Client = new POP3Client();
        InetSocketAddress bindedAddress = new ProtocolServerUtils(pop3Server).retrieveBindedAddress();
        pop3Client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());

        String pass = "password";
        usersRepository.addUser(Username.of("foo"), pass);

        assertThat(pop3Client.sendCommand("CAPA")).isEqualTo(POP3Reply.OK);

        pop3Client.getAdditionalReply();
        pop3Client.getReplyString();
        List<String> replies = Arrays.asList(pop3Client.getReplyStrings());

        assertThat(replies.contains("USER")).describedAs("contains USER").isTrue();

        pop3Client.login("foo", pass);
        assertThat(pop3Client.sendCommand("CAPA")).isEqualTo(POP3Reply.OK);

        pop3Client.getAdditionalReply();
        pop3Client.getReplyString();
        replies = Arrays.asList(pop3Client.getReplyStrings());
        assertThat(replies.contains("USER")).describedAs("contains USER").isTrue();
        assertThat(replies.contains("UIDL")).describedAs("contains UIDL").isTrue();
        assertThat(replies.contains("TOP")).describedAs("contains TOP").isTrue();

    }

    @Test
    void authPlainWithInitialResponseShouldAuthenticate() throws Exception {
        finishSetUp(pop3Configuration);
        usersRepository.addUser(Username.of("auth-user"), "secret");

        try (Socket socket = connectToPop3Server();
             BufferedReader reader = reader(socket);
             BufferedWriter writer = writer(socket)) {
            assertThat(reader.readLine()).startsWith("+OK");

            send(writer, "AUTH PLAIN " + plainInitialResponse("auth-user", "secret"));
            assertThat(reader.readLine()).isEqualTo("+OK Welcome auth-user");

            send(writer, "STAT");
            assertThat(reader.readLine()).startsWith("+OK");
        }
    }

    @Test
    void authPlainShouldSupportContinuation() throws Exception {
        finishSetUp(pop3Configuration);
        usersRepository.addUser(Username.of("auth-user"), "secret");

        try (Socket socket = connectToPop3Server();
             BufferedReader reader = reader(socket);
             BufferedWriter writer = writer(socket)) {
            assertThat(reader.readLine()).startsWith("+OK");

            send(writer, "AUTH PLAIN");
            assertThat(reader.readLine()).isEqualTo("+ ");

            send(writer, plainInitialResponse("auth-user", "secret"));
            assertThat(reader.readLine()).isEqualTo("+OK Welcome auth-user");
        }
    }

    @Test
    void malformedAuthShouldPreserveUserPassAuthentication() throws Exception {
        finishSetUp(pop3Configuration);
        usersRepository.addUser(Username.of("auth-user"), "secret");

        try (Socket socket = connectToPop3Server();
             BufferedReader reader = reader(socket);
             BufferedWriter writer = writer(socket)) {
            assertThat(reader.readLine()).startsWith("+OK");

            send(writer, "USER auth-user");
            assertThat(reader.readLine()).startsWith("+OK");
            send(writer, "AUTH PLAIN " + encoded("malformed"));
            assertThat(reader.readLine()).isEqualTo("-ERR Invalid AUTH request.");

            send(writer, "PASS secret");
            assertThat(reader.readLine()).isEqualTo("+OK Welcome auth-user");
        }
    }

    @Test
    void capaShouldAdvertiseAvailableSaslMechanisms() throws Exception {
        finishSetUp(pop3Configuration);

        pop3Client = new POP3Client();
        InetSocketAddress bindedAddress = new ProtocolServerUtils(pop3Server).retrieveBindedAddress();
        pop3Client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());

        assertThat(pop3Client.sendCommand("CAPA")).isEqualTo(POP3Reply.OK);
        pop3Client.getAdditionalReply();

        assertThat(pop3Client.getReplyStrings()).contains("SASL PLAIN");
    }

    @Test
    void capaShouldAdvertisePlainOnlyAfterStartTlsWhenRequireSSL() throws Exception {
        pop3Configuration.setProperty("auth.requireSSL", true);
        configureSaslMechanismsFromConfiguration();
        finishSetUp(pop3Configuration);

        POP3SClient client = new POP3SClient(false, BogusSslContextFactory.getClientContext());
        client.setTrustManager(BogusTrustManagerFactory.getTrustManagers()[0]);
        pop3Client = client;
        InetSocketAddress bindedAddress = new ProtocolServerUtils(pop3Server).retrieveBindedAddress();
        client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());

        assertThat(client.sendCommand("CAPA")).isEqualTo(POP3Reply.OK);
        client.getAdditionalReply();
        assertThat(client.getReplyStrings()).doesNotContain("SASL PLAIN");

        assertThat(client.execTLS()).isTrue();
        assertThat(client.sendCommand("CAPA")).isEqualTo(POP3Reply.OK);
        client.getAdditionalReply();
        assertThat(client.getReplyStrings()).contains("SASL PLAIN");
    }

    @Test
    void unknownAuthMechanismShouldBeRejectedAsUnsupported() throws Exception {
        finishSetUp(pop3Configuration);

        try (Socket socket = connectToPop3Server();
             BufferedReader reader = reader(socket);
             BufferedWriter writer = writer(socket)) {
            assertThat(reader.readLine()).startsWith("+OK");

            send(writer, "AUTH UNKNOWN");
            assertThat(reader.readLine()).isEqualTo("-ERR Unsupported authentication mechanism.");
        }
    }

    @Test
    void clearTextPassAndAuthPlainShouldBeRejectedByDefault() throws Exception {
        pop3Configuration.clearProperty("auth.requireSSL"); // absent "auth.requireSSL" defaults to true
        configureSaslMechanismsFromConfiguration();
        finishSetUp(pop3Configuration);
        usersRepository.addUser(Username.of("auth-user"), "secret");

        try (Socket socket = connectToPop3Server();
             BufferedReader reader = reader(socket);
             BufferedWriter writer = writer(socket)) {
            assertThat(reader.readLine()).startsWith("+OK");

            send(writer, "AUTH PLAIN " + plainInitialResponse("auth-user", "secret"));
            assertThat(reader.readLine()).isEqualTo("-ERR Authentication requires TLS.");

            send(writer, "USER auth-user");
            assertThat(reader.readLine()).startsWith("+OK");
            send(writer, "PASS secret");
            assertThat(reader.readLine()).isEqualTo("-ERR Authentication requires TLS.");
        }
    }

    @Test
    void userPassShouldBeAvailableAfterStartTlsWhenRequireSSL() throws Exception {
        pop3Configuration.setProperty("auth.requireSSL", true);
        finishSetUp(pop3Configuration);
        usersRepository.addUser(Username.of("auth-user"), "secret");

        POP3SClient client = new POP3SClient(false, BogusSslContextFactory.getClientContext());
        client.setTrustManager(BogusTrustManagerFactory.getTrustManagers()[0]);
        pop3Client = client;
        InetSocketAddress bindedAddress = new ProtocolServerUtils(pop3Server).retrieveBindedAddress();
        client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());

        assertThat(client.execTLS()).isTrue();
        assertThat(client.login("auth-user", "secret")).isTrue();
    }

    @Test
    void authPlainShouldBeAvailableAfterStartTlsWhenRequireSSL() throws Exception {
        pop3Configuration.setProperty("auth.requireSSL", true);
        configureSaslMechanismsFromConfiguration();
        finishSetUp(pop3Configuration);
        usersRepository.addUser(Username.of("auth-user"), "secret");

        POP3SClient client = new POP3SClient(false, BogusSslContextFactory.getClientContext());
        client.setTrustManager(BogusTrustManagerFactory.getTrustManagers()[0]);
        pop3Client = client;
        InetSocketAddress bindedAddress = new ProtocolServerUtils(pop3Server).retrieveBindedAddress();
        client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());

        assertThat(client.execTLS()).isTrue();
        assertThat(client.sendCommand("AUTH PLAIN " + plainInitialResponse("auth-user", "secret"))).isEqualTo(POP3Reply.OK);
        assertThat(client.getReplyString()).contains("+OK Welcome auth-user");
        assertThat(client.sendCommand("STAT")).isEqualTo(POP3Reply.OK);
    }

    @Test
    void clearTextPassAndAuthPlainShouldBeAvailableWhenRequireSSLIsFalse() throws Exception {
        pop3Configuration.setProperty("auth.requireSSL", false);
        configureSaslMechanismsFromConfiguration();
        finishSetUp(pop3Configuration);
        usersRepository.addUser(Username.of("auth-user"), "secret");

        try (Socket socket = connectToPop3Server();
             BufferedReader reader = reader(socket);
             BufferedWriter writer = writer(socket)) {
            assertThat(reader.readLine()).startsWith("+OK");

            send(writer, "AUTH PLAIN " + plainInitialResponse("auth-user", "secret"));
            assertThat(reader.readLine()).isEqualTo("+OK Welcome auth-user");
        }

        try (Socket socket = connectToPop3Server();
             BufferedReader reader = reader(socket);
             BufferedWriter writer = writer(socket)) {
            assertThat(reader.readLine()).startsWith("+OK");

            send(writer, "USER auth-user");
            assertThat(reader.readLine()).startsWith("+OK");
            send(writer, "PASS secret");
            assertThat(reader.readLine()).isEqualTo("+OK Welcome auth-user");
        }
    }

    @Test
    void authShouldSupportFinalServerDataAndCloseExchange() throws Exception {
        Username username = Username.of("auth-user");
        AtomicBoolean closed = new AtomicBoolean();
        pop3Server.setSaslMechanisms(ImmutableList.of(new ServerDataSaslMechanism(username, closed)));
        finishSetUp(pop3Configuration);
        usersRepository.addUser(username, "secret");

        try (Socket socket = connectToPop3Server();
             BufferedReader reader = reader(socket);
             BufferedWriter writer = writer(socket)) {
            assertThat(reader.readLine()).startsWith("+OK");

            send(writer, "AUTH TEST");
            assertThat(reader.readLine()).isEqualTo("+ " + encoded("challenge"));
            // RFC 5034 allows SASL continuations to exceed normal POP3 command line limits.
            send(writer, encoded("x".repeat(AbstractChannelPipelineFactory.MAX_LINE_LENGTH)));
            assertThat(reader.readLine()).isEqualTo("+ " + encoded("server-data"));
            send(writer, "");

            assertThat(reader.readLine()).isEqualTo("+OK Welcome auth-user");
            assertThat(closed).isTrue();

            send(writer, "AUTH TEST");
            assertThat(reader.readLine()).startsWith("-ERR");
            send(writer, "CAPA");
            assertThat(reader.readLine()).startsWith("+OK");
            assertThat(readMultilineResponse(reader)).contains("SASL TEST");
        }
    }

    @Test
    void authCancellationShouldCloseExchangeAndPreserveUserPassAuthentication() throws Exception {
        Username username = Username.of("auth-user");
        AtomicBoolean closed = new AtomicBoolean();
        pop3Configuration.setProperty("auth.requireSSL", false);
        pop3Server.setSaslMechanisms(ImmutableList.of(new ServerDataSaslMechanism(username, closed)));
        finishSetUp(pop3Configuration);
        usersRepository.addUser(username, "secret");

        try (Socket socket = connectToPop3Server();
             BufferedReader reader = reader(socket);
             BufferedWriter writer = writer(socket)) {
            assertThat(reader.readLine()).startsWith("+OK");

            send(writer, "USER auth-user");
            assertThat(reader.readLine()).startsWith("+OK");
            send(writer, "AUTH TEST");
            assertThat(reader.readLine()).isEqualTo("+ " + encoded("challenge"));
            send(writer, "*");
            assertThat(reader.readLine()).startsWith("-ERR");
            assertThat(closed).isTrue();

            send(writer, "PASS secret");
            assertThat(reader.readLine()).isEqualTo("+OK Welcome auth-user");
        }
    }

    @Test
    void oversizedSaslContinuationShouldCloseExchangeAndConnection() throws Exception {
        Username username = Username.of("auth-user");
        AtomicBoolean closed = new AtomicBoolean();
        pop3Server.setSaslMechanisms(ImmutableList.of(new ServerDataSaslMechanism(username, closed)));
        finishSetUp(pop3Configuration);

        try (Socket socket = connectToPop3Server();
             BufferedReader reader = reader(socket);
             BufferedWriter writer = writer(socket)) {
            socket.setSoTimeout(5_000);
            assertThat(reader.readLine()).startsWith("+OK");

            send(writer, "AUTH TEST");
            assertThat(reader.readLine()).isEqualTo("+ " + encoded("challenge"));
            send(writer, "A".repeat(OVERSIZED_SASL_CONTINUATION_LENGTH));

            assertThat(reader.readLine()).isEqualTo("-ERR Exceed maximal line length");
            assertThat(reader.readLine()).isNull();
            Awaitility.await().atMost(Duration.ofSeconds(2)).untilTrue(closed);
        }
    }

    @Test
    void authShouldEnforceInitialCommandLength() throws Exception {
        Username username = Username.of("auth-user");
        AtomicBoolean closed = new AtomicBoolean();
        pop3Server.setSaslMechanisms(ImmutableList.of(new ServerDataSaslMechanism("TST", username, closed)));
        finishSetUp(pop3Configuration);

        try (Socket socket = connectToPop3Server();
             BufferedReader reader = reader(socket);
             BufferedWriter writer = writer(socket)) {
            assertThat(reader.readLine()).startsWith("+OK");

            String maximumLengthInitialResponse = "A".repeat(244);
            send(writer, "AUTH TST " + maximumLengthInitialResponse);
            assertThat(reader.readLine()).isEqualTo("+ " + encoded("challenge"));
            send(writer, "*");
            assertThat(reader.readLine()).startsWith("-ERR");

            send(writer, "AUTH TST " + maximumLengthInitialResponse + "A");
            assertThat(reader.readLine()).startsWith("-ERR");

            // RFC 5034 applies the 255-octet limit to the original command, before parser whitespace normalization.
            send(writer, "AUTH TST" + " ".repeat(246));
            assertThat(reader.readLine()).startsWith("-ERR");
        }
    }

    @Test
    void authOauthBearerShouldAuthenticateWithValidToken() throws Exception {
        Username username = Username.of("auth-user");
        pop3Server.setSaslMechanisms(ImmutableList.of(oauthBearerMechanism(Optional.of(username))));
        finishSetUp(pop3Configuration);
        usersRepository.addUser(username, "secret");

        try (Socket socket = connectToPop3Server();
             BufferedReader reader = reader(socket);
             BufferedWriter writer = writer(socket)) {
            assertThat(reader.readLine()).startsWith("+OK");

            String initialResponse = OIDCSASLHelper.generateEncodedOauthbearerInitialClientResponse(username.asString(), OAUTH_TOKEN);
            send(writer, "AUTH OAUTHBEARER " + initialResponse);

            assertThat(reader.readLine()).isEqualTo("+OK Welcome auth-user");
        }
    }

    @Test
    void authOauthBearerShouldAuthenticateLargeTokenThroughContinuation() throws Exception {
        Username username = Username.of("auth-user");
        pop3Server.setSaslMechanisms(ImmutableList.of(oauthBearerMechanism(Optional.of(username))));
        finishSetUp(pop3Configuration);
        usersRepository.addUser(username, "secret");

        try (Socket socket = connectToPop3Server();
             BufferedReader reader = reader(socket);
             BufferedWriter writer = writer(socket)) {
            assertThat(reader.readLine()).startsWith("+OK");

            send(writer, "AUTH OAUTHBEARER");
            assertThat(reader.readLine()).isEqualTo("+ ");

            // RFC 5034 permits SASL continuations to exceed regular POP3 command limits. A large token
            // must therefore be sent after the empty challenge instead of as an initial AUTH response.
            String clientResponse = OIDCSASLHelper.generateEncodedOauthbearerInitialClientResponse(
                username.asString(), "x".repeat(AbstractChannelPipelineFactory.MAX_LINE_LENGTH));
            assertThat(clientResponse.length()).isGreaterThan(AbstractChannelPipelineFactory.MAX_LINE_LENGTH);
            send(writer, clientResponse);

            assertThat(reader.readLine()).isEqualTo("+OK Welcome auth-user");
        }
    }

    @Test
    void authOauthBearerShouldSendErrorChallengeBeforeRejectingInvalidToken() throws Exception {
        Username username = Username.of("auth-user");
        pop3Server.setSaslMechanisms(ImmutableList.of(oauthBearerMechanism(Optional.empty())));
        finishSetUp(pop3Configuration);

        try (Socket socket = connectToPop3Server();
             BufferedReader reader = reader(socket);
             BufferedWriter writer = writer(socket)) {
            assertThat(reader.readLine()).startsWith("+OK");

            String initialResponse = OIDCSASLHelper.generateEncodedOauthbearerInitialClientResponse(username.asString(), OAUTH_TOKEN);
            send(writer, "AUTH OAUTHBEARER " + initialResponse);
            assertThat(reader.readLine()).isEqualTo("+ " + Base64.getEncoder().encodeToString(INVALID_TOKEN_RESPONSE));

            // RFC 7628 section 3.2.3 requires a dummy client response before the server completes the failure.
            send(writer, "AQ==");
            assertThat(reader.readLine()).startsWith("-ERR");
        }
    }

    /*
     * See JAMES-649 The same happens when using RETR
     *
     * Comment to not broke the builds!
     *
     * public void testOOMTop() throws Exception {
     * finishSetUp(m_testConfiguration);
     *
     * int messageCount = 30000; m_pop3Protocol = new POP3Client();
     * m_pop3Protocol.connect("127.0.0.1",m_pop3ListenerPort);
     *
     * m_usersRepository.addUser("foo", "bar"); InMemorySpoolRepository
     * mockMailRepository = new InMemorySpoolRepository();
     *
     * Mail m = new MailImpl(); m.setMessage(Util.createMimeMessage("X-TEST",
     * "test")); for (int i = 1; i < messageCount+1; i++ ) { m.setName("test" +
     * i); mockMailRepository.store(m); }
     *
     * m_mailServer.setUserInbox("foo", mockMailRepository);
     *
     * // not authenticated POP3MessageInfo[] entries =
     * m_pop3Protocol.listMessages(); assertNull(entries);
     *
     * m_pop3Protocol.login("foo", "bar");
     * System.err.println(m_pop3Protocol.getState()); assertEquals(1,
     * m_pop3Protocol.getState());
     *
     * entries = m_pop3Protocol.listMessages(); assertEquals(1,
     * m_pop3Protocol.getState());
     *
     * assertNotNull(entries); assertEquals(entries.length, messageCount);
     *
     * for (int i = 1; i < messageCount+1; i++ ) { Reader r =
     * m_pop3Protocol.retrieveMessageTop(i, 100); assertNotNull(r); r.close(); }
     *
     * ContainerUtil.dispose(mockMailRepository); }
     */
    // See JAMES-1136
    @Test
    void testDeadlockOnRetr() throws Exception {
        finishSetUp(pop3Configuration);

        pop3Client = new POP3Client();
        InetSocketAddress bindedAddress = new ProtocolServerUtils(pop3Server).retrieveBindedAddress();
        pop3Client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());

        Username username = Username.of("foo6");
        usersRepository.addUser(username, "bar6");
        MailboxSession session = mailboxManager.authenticate(username, "bar6").withoutDelegation();

        MailboxPath mailboxPath = MailboxPath.inbox(username);

        mailboxManager.startProcessingRequest(session);
        if (!mailboxManager.mailboxExists(mailboxPath, session).block()) {
            mailboxManager.createMailbox(mailboxPath, session);
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(content);

        byte[] bigMail = new byte[1024 * 1024 * 10];
        int c = 0;
        for (int i = 0; i < bigMail.length; i++) {

            bigMail[i] = 'X';
            c++;
            if (c == 1000 || i + 3 == bigMail.length) {
                c = 0;
                bigMail[++i] = '\r';
                bigMail[++i] = '\n';
            }
        }
        out.write(bigMail);
        bigMail = null;

        mailboxManager.getMailbox(mailboxPath, session).appendMessage(MessageManager.AppendCommand.builder()
            .build(out.toByteArray()), session);
        mailboxManager.startProcessingRequest(session);

        pop3Client.login("foo6", "bar6");
        assertThat(pop3Client.getState()).isEqualTo(1);

        POP3MessageInfo[] entries = pop3Client.listMessages();

        assertThat(entries).isNotNull();
        assertThat(entries.length).isEqualTo(1);
        assertThat(pop3Client.getState()).isEqualTo(1);

        Reader r = pop3Client.retrieveMessage(entries[0].number);

        assertThat(r).isNotNull();
        r.close();
        mailboxManager.deleteMailbox(mailboxPath, session);

    }

    protected POP3Server createPOP3Server() {
        return new POP3Server();
    }

    protected void initPOP3Server(XMLConfiguration configuration) throws Exception {
        pop3Server.configure(configuration);
        pop3Server.init();
    }

    protected void setUpPOP3Server() {
        pop3Server = createPOP3Server();
        pop3Server.setFileSystem(fileSystem);
        pop3Server.setEncryptionFactory(new LegacyJavaEncryptionFactory(fileSystem));
        pop3Server.setProtocolHandlerLoader(protocolHandlerChain);
    }

    protected void finishSetUp(XMLConfiguration pop3Configuration) throws Exception {
        initPOP3Server(pop3Configuration);
    }

    private Socket connectToPop3Server() throws IOException {
        InetSocketAddress bindedAddress = new ProtocolServerUtils(pop3Server).retrieveBindedAddress();
        return new Socket(bindedAddress.getAddress(), bindedAddress.getPort());
    }

    private BufferedReader reader(Socket socket) throws IOException {
        return new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
    }

    private BufferedWriter writer(Socket socket) throws IOException {
        return new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII));
    }

    private void send(BufferedWriter writer, String command) throws IOException {
        writer.write(command);
        writer.write("\r\n");
        writer.flush();
    }

    private String plainInitialResponse(String username, String password) {
        return Base64.getEncoder().encodeToString(("\0" + username + "\0" + password).getBytes(StandardCharsets.UTF_8));
    }

    private String encoded(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.US_ASCII));
    }

    private OAuthSaslMechanism oauthBearerMechanism(Optional<Username> validationResult) {
        return new OAuthSaslMechanism(SaslMechanismNames.OAUTHBEARER,
            new TestOidcJwtTokenVerifier(validationResult), false, INVALID_TOKEN_RESPONSE);
    }

    private ImmutableList<String> readMultilineResponse(BufferedReader reader) throws IOException {
        ImmutableList.Builder<String> lines = ImmutableList.builder();
        while (true) {
            String line = reader.readLine();
            if (line.equals(".")) {
                return lines.build();
            }
            lines.add(line);
        }
    }

    protected void setUpServiceManager() {
        mailboxManager = InMemoryIntegrationResources.builder()
            .authenticator((userid, passwd) -> {
                try {
                    return usersRepository.test(userid, passwd.toString());
                } catch (UsersRepositoryException e) {
                    e.printStackTrace();
                    return Optional.empty();
                }
            })
            .fakeAuthorizator()
            .inVmEventBus()
            .defaultAnnotationLimits()
            .defaultMessageParser()
            .scanningSearchIndex()
            .noPreDeletionHooks()
            .storeQuotaManager()
            .build()
            .getMailboxManager();
        fileSystem = FileSystemImpl.forTestingWithConfigurationFromClasspath();

        protocolHandlerChain = MockProtocolHandlerLoader.builder()
            .put(binder -> binder.bind(UsersRepository.class).toInstance(usersRepository))
            .put(binder -> binder.bind(MailboxManager.class).annotatedWith(Names.named("mailboxmanager")).toInstance(mailboxManager))
            .put(binder -> binder.bind(FileSystem.class).toInstance(fileSystem))
            .put(binder -> binder.bind(MetricFactory.class).toInstance(new RecordingMetricFactory()))
            .put(binder -> binder.bind(UserEntityValidator.class).toInstance(UserEntityValidator.NOOP))
            .put(binder -> binder.bind(MailboxAdapterFactory.class).to(DefaultMailboxAdapterFactory.class))
            .put(binder -> binder.bind(UserEntityValidator.class).toInstance(UserEntityValidator.NOOP))
            .build();
    }

    private void setupTestMails(MailboxSession session, MessageManager mailbox) throws MailboxException {
        mailbox.appendMessage(MessageManager.AppendCommand.builder()
            .build(content), session);
        byte[] content2 = ("EMPTY").getBytes();
        mailbox.appendMessage(MessageManager.AppendCommand.builder()
            .build(content2), session);
    }

}
