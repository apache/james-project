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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.search.AndTerm;
import javax.mail.search.BodyTerm;
import javax.mail.search.FromStringTerm;
import javax.mail.search.RecipientStringTerm;
import javax.mail.search.SearchTerm;
import javax.mail.search.SubjectTerm;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.commons.net.imap.AuthenticatingIMAPClient;
import org.apache.commons.net.imap.IMAPReply;
import org.apache.commons.net.imap.IMAPSClient;
import org.apache.james.core.Username;
import org.apache.james.imap.encode.main.DefaultImapEncoderFactory;
import org.apache.james.imap.main.DefaultImapDecoderFactory;
import org.apache.james.imap.processor.main.DefaultImapProcessorFactory;
import org.apache.james.jwt.OidcTokenFixture;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.FakeAuthenticator;
import org.apache.james.mailbox.store.FakeAuthorizator;
import org.apache.james.mailbox.store.StoreSubscriptionManager;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.protocols.api.OIDCSASLHelper;
import org.apache.james.protocols.api.utils.BogusSslContextFactory;
import org.apache.james.protocols.api.utils.BogusTrustManagerFactory;
import org.apache.james.protocols.lib.mock.ConfigLoader;
import org.apache.james.server.core.configuration.Configuration;
import org.apache.james.server.core.filesystem.FileSystemImpl;
import org.apache.james.util.ClassLoaderUtils;
import org.apache.james.utils.TestIMAPClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import nl.altindag.ssl.exception.GenericKeyStoreException;
import nl.altindag.ssl.exception.PrivateKeyParseException;

class IMAPServerTest {
    private static final String _129K_MESSAGE = "header: value\r\n" + "012345678\r\n".repeat(13107);
    private static final String _65K_MESSAGE = "header: value\r\n" + "012345678\r\n".repeat(6553);
    private static final Username USER = Username.of("user@domain.org");
    private static final Username USER2 = Username.of("bobo@domain.org");
    private static final String USER_PASS = "pass";
    public static final String SMALL_MESSAGE = "header: value\r\n\r\nBODY";
    private InMemoryIntegrationResources memoryIntegrationResources;

    @RegisterExtension
    public TestIMAPClient testIMAPClient = new TestIMAPClient();

    private IMAPServer createImapServer(HierarchicalConfiguration<ImmutableNode> config) throws Exception {
        FakeAuthenticator authenticator = new FakeAuthenticator();
        authenticator.addUser(USER, USER_PASS);
        authenticator.addUser(USER2, USER_PASS);

        memoryIntegrationResources = InMemoryIntegrationResources.builder()
            .authenticator(authenticator)
            .authorizator(FakeAuthorizator.defaultReject())
            .inVmEventBus()
            .defaultAnnotationLimits()
            .defaultMessageParser()
            .scanningSearchIndex()
            .noPreDeletionHooks()
            .storeQuotaManager()
            .build();
        RecordingMetricFactory metricFactory = new RecordingMetricFactory();
        IMAPServer imapServer = new IMAPServer(
            DefaultImapDecoderFactory.createDecoder(),
            new DefaultImapEncoderFactory().buildImapEncoder(),
            DefaultImapProcessorFactory.createXListSupportingProcessor(
                memoryIntegrationResources.getMailboxManager(),
                memoryIntegrationResources.getEventBus(),
                new StoreSubscriptionManager(memoryIntegrationResources.getMailboxManager().getMapperFactory()),
                null,
                memoryIntegrationResources.getQuotaManager(),
                memoryIntegrationResources.getQuotaRootResolver(),
                metricFactory),
            new ImapMetrics(metricFactory));

        Configuration configuration = Configuration.builder()
            .workingDirectory("../")
            .configurationFromClasspath()
            .build();
        FileSystemImpl fileSystem = new FileSystemImpl(configuration.directories());
        imapServer.setFileSystem(fileSystem);

        imapServer.configure(config);
        imapServer.init();

        return imapServer;
    }

    private IMAPServer createImapServer(String configurationFile) throws Exception {
        return createImapServer(ConfigLoader.getConfig(ClassLoaderUtils.getSystemResourceAsSharedStream(configurationFile)));
    }

    @Nested
    class NoLimit {
        IMAPServer imapServer;
        private int port;

        @BeforeEach
        void beforeEach() throws Exception {
            imapServer = createImapServer("imapServerNoLimits.xml");
            port = imapServer.getListenAddresses().get(0).getPort();
        }

        @AfterEach
        void tearDown() {
            imapServer.destroy();
        }

        @Test
        void smallAppendsShouldWork() throws Exception {
            assertThatCode(() ->
                testIMAPClient.connect("127.0.0.1", port)
                    .login(USER.asString(), USER_PASS)
                    .append("INBOX", SMALL_MESSAGE))
                .doesNotThrowAnyException();

            assertThat(testIMAPClient.select("INBOX")
                    .readFirstMessage())
                .contains("\r\n" + SMALL_MESSAGE + ")\r\n");
        }

        @Test
        void mediumAppendsShouldWork() throws Exception {
            assertThatCode(() ->
                testIMAPClient.connect("127.0.0.1", port)
                    .login(USER.asString(), USER_PASS)
                    .append("INBOX", _65K_MESSAGE))
                .doesNotThrowAnyException();

            assertThat(testIMAPClient.select("INBOX")
                    .readFirstMessage())
                .contains("\r\n" + _65K_MESSAGE + ")\r\n");
        }

        @Test
        void loginFixationShouldBeRejected() throws Exception {
            InMemoryMailboxManager mailboxManager = memoryIntegrationResources.getMailboxManager();
            mailboxManager.createMailbox(
                MailboxPath.forUser(USER, "pwnd"),
                mailboxManager.createSystemSession(USER));
            mailboxManager.createMailbox(
                MailboxPath.forUser(USER2, "notvuln"),
                mailboxManager.createSystemSession(USER2));

            testIMAPClient.connect("127.0.0.1", port)
                // Injected by a man in the middle attacker
                .rawLogin(USER.asString(), USER_PASS);

            assertThatThrownBy(() -> testIMAPClient.rawLogin(USER2.asString(), USER_PASS))
                .isInstanceOf(IOException.class)
                .hasMessage("Login failed");
        }

        @RepeatedTest(200)
        void largeAppendsShouldWork() throws Exception {
            assertThatCode(() ->
                testIMAPClient.connect("127.0.0.1", port)
                    .login(USER.asString(), USER_PASS)
                    .append("INBOX", _129K_MESSAGE))
                .doesNotThrowAnyException();

            assertThat(testIMAPClient.select("INBOX")
                    .readFirstMessage())
                .contains("\r\n" + _129K_MESSAGE + ")\r\n");
        }
    }

    @Nested
    class StartTLS {
        IMAPServer imapServer;
        private int port;

        @BeforeEach
        void beforeEach() throws Exception {
            imapServer = createImapServer("imapServerStartTLS.xml");
            port = imapServer.getListenAddresses().get(0).getPort();
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
    }

    @Nested
    class Ssl {
        IMAPServer imapServer;

        @AfterEach
        void tearDown() {
            if (imapServer != null) {
                imapServer.destroy();
            }
        }

        @Test
        void initShouldAcceptJKSFormat() {
            assertThatCode(() -> imapServer = createImapServer("imapServerSslJKS.xml"))
                .doesNotThrowAnyException();
        }

        @Test
        void initShouldAcceptPKCS12Format() {
            assertThatCode(() -> imapServer = createImapServer("imapServerSslPKCS12.xml"))
                .doesNotThrowAnyException();
        }

        @Test
        void initShouldAcceptPEMKeysWithPassword() {
            assertThatCode(() -> imapServer = createImapServer("imapServerSslPEM.xml"))
                .doesNotThrowAnyException();
        }

        @Test
        void initShouldAcceptPEMKeysWithoutPassword() {
            assertThatCode(() -> imapServer = createImapServer("imapServerSslPEMNoPass.xml"))
                .doesNotThrowAnyException();
        }

        @Test
        void initShouldAcceptJKSByDefault() {
            assertThatCode(() -> imapServer = createImapServer("imapServerSslDefaultJKS.xml"))
                .doesNotThrowAnyException();
        }

        @Test
        void initShouldThrowWhenSslEnabledWithoutKeys() {
            assertThatThrownBy(() -> createImapServer("imapServerSslNoKeys.xml"))
                .isInstanceOf(ConfigurationException.class)
                .hasMessageContaining("keystore or (privateKey and certificates) needs to get configured");
        }

        @Test
        void initShouldThrowWhenJKSWithBadPassword() {
            assertThatThrownBy(() -> createImapServer("imapServerSslJKSBadPassword.xml"))
                .isInstanceOf(GenericKeyStoreException.class)
                .hasMessageContaining("keystore password was incorrect");
        }

        @Test
        void initShouldThrowWhenPEMWithBadPassword() {
            assertThatThrownBy(() -> createImapServer("imapServerSslPEMBadPass.xml"))
                .isInstanceOf(PrivateKeyParseException.class);
        }

        @Test
        void initShouldThrowWhenPEMWithMissingPassword() {
            assertThatThrownBy(() -> createImapServer("imapServerSslPEMMissingPass.xml"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A password is mandatory with an encrypted key");
        }

        @Test
        void initShouldNotThrowWhenPEMWithExtraPassword() {
            assertThatCode(() -> imapServer = createImapServer("imapServerSslPEMExtraPass.xml"))
                .doesNotThrowAnyException();
        }

        @Test
        void initShouldThrowWhenJKSWenNotFound() {
            assertThatThrownBy(() -> createImapServer("imapServerSslJKSNotFound.xml"))
                .isInstanceOf(FileNotFoundException.class)
                .hasMessage("class path resource [keystore.notfound.jks] cannot be resolved to URL because it does not exist");
        }

        @Test
        void initShouldThrowWhenPKCS12WithBadPassword() {
            assertThatThrownBy(() -> createImapServer("imapServerSslPKCS12WrongPassword.xml"))
                .isInstanceOf(GenericKeyStoreException.class)
                .hasMessageContaining("keystore password was incorrect");
        }

        @Test
        void initShouldThrowWhenPKCS12WithMissingPassword() {
            assertThatThrownBy(() -> createImapServer("imapServerSslPKCS12MissingPassword.xml"))
                .isInstanceOf(GenericKeyStoreException.class)
                .hasMessageContaining("keystore password was incorrect");
        }
    }

    @Nested
    class Limit {
        IMAPServer imapServer;
        private int port;

        @BeforeEach
        void beforeEach() throws Exception {
            imapServer = createImapServer("imapServer.xml");
            port = imapServer.getListenAddresses().get(0).getPort();
        }

        @AfterEach
        void tearDown() {
            imapServer.destroy();
        }

        @Test
        void smallAppendsShouldWork() throws Exception {
            assertThatCode(() ->
                testIMAPClient.connect("127.0.0.1", port)
                    .login(USER.asString(), USER_PASS)
                    .append("INBOX", SMALL_MESSAGE))
                .doesNotThrowAnyException();

            assertThat(testIMAPClient.select("INBOX")
                    .readFirstMessage())
                .contains("\r\n" + SMALL_MESSAGE + ")\r\n");
        }

        @Test
        void mediumAppendsShouldWork() throws Exception {
            assertThatCode(() ->
                testIMAPClient.connect("127.0.0.1", port)
                    .login(USER.asString(), USER_PASS)
                    .append("INBOX", _65K_MESSAGE))
                .doesNotThrowAnyException();

            assertThat(testIMAPClient.select("INBOX")
                    .readFirstMessage())
                .contains("\r\n" + _65K_MESSAGE + ")\r\n");
        }

        @Test
        void largeAppendsShouldBeRejected() {
            assertThatThrownBy(() ->
                testIMAPClient.connect("127.0.0.1", port)
                    .login(USER.asString(), USER_PASS)
                    .append("INBOX", _129K_MESSAGE))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("BAD APPEND failed.");
        }
    }

    @Nested
    class PlainAuthDisabled {
        IMAPServer imapServer;
        private int port;

        @BeforeEach
        void beforeEach() throws Exception {
            imapServer = createImapServer("imapServerPlainAuthDisabled.xml");
            port = imapServer.getListenAddresses().get(0).getPort();
        }

        @AfterEach
        void tearDown() {
            imapServer.destroy();
        }

        @Test
        void loginShouldFail() {
            assertThatThrownBy(() ->
                testIMAPClient.connect("127.0.0.1", port)
                    .login(USER.asString(), USER_PASS))
                .hasMessage("Login failed");
        }

        @Test
        void authenticatePlainShouldFail() {
            assertThatThrownBy(() ->
                testIMAPClient.connect("127.0.0.1", port)
                    .authenticatePlain(USER.asString(), USER_PASS))
                .hasMessage("Login failed");
        }

        @Test
        void capabilityShouldNotAdvertiseLoginAndAuthenticationPlain() throws Exception {
            testIMAPClient.connect("127.0.0.1", port);

            assertThat(testIMAPClient.capability())
                .contains("LOGINDISABLED")
                .doesNotContain("AUTH=PLAIN");
        }
    }

    @Nested
    class PlainAuthEnabledWithoutRequireSSL {
        IMAPServer imapServer;
        private int port;

        @BeforeEach
        void beforeEach() throws Exception {
            imapServer = createImapServer("imapServerPlainAuthEnabledWithoutRequireSSL.xml");
            port = imapServer.getListenAddresses().get(0).getPort();
        }

        @AfterEach
        void tearDown() {
            imapServer.destroy();
        }

        @Test
        void loginShouldSucceed() {
            assertThatCode(() ->
                testIMAPClient.connect("127.0.0.1", port)
                    .login(USER.asString(), USER_PASS))
                .doesNotThrowAnyException();
        }

        @Test
        void authenticatePlainShouldSucceed() {
            assertThatCode(() ->
                testIMAPClient.connect("127.0.0.1", port)
                    .authenticatePlain(USER.asString(), USER_PASS))
                .doesNotThrowAnyException();
        }

        @Test
        void capabilityShouldAdvertiseLoginAndAuthenticationPlain() throws Exception {
            testIMAPClient.connect("127.0.0.1", port);

            assertThat(testIMAPClient.capability())
                .doesNotContain("LOGINDISABLED")
                .contains("AUTH=PLAIN");
        }
    }

    @Nested
    class PlainAuthDisallowed {
        IMAPServer imapServer;
        private int port;

        @BeforeEach
        void beforeEach() throws Exception {
            imapServer = createImapServer("imapServerPlainAuthDisallowed.xml");
            port = imapServer.getListenAddresses().get(0).getPort();
        }

        @AfterEach
        void tearDown() {
            imapServer.destroy();
        }

        @Test
        void loginShouldFailOnUnEncryptedChannel() {
            assertThatThrownBy(() ->
                testIMAPClient.connect("127.0.0.1", port)
                    .login(USER.asString(), USER_PASS))
                .hasMessage("Login failed");
        }

        @Test
        void capabilityShouldNotAdvertiseLoginOnUnEncryptedChannel() throws Exception {
            testIMAPClient.connect("127.0.0.1", port);

            assertThat(testIMAPClient.capability())
                .contains("LOGINDISABLED")
                .doesNotContain("AUTH=PLAIN");
        }
    }

    @Nested
    class PlainAuthDisallowedSSL {
        IMAPServer imapServer;
        private int port;

        @BeforeEach
        void beforeEach() throws Exception {
            imapServer = createImapServer("imapServerPlainAuthAllowed.xml");
            port = imapServer.getListenAddresses().get(0).getPort();
        }

        @AfterEach
        void tearDown() {
            imapServer.destroy();
        }

        @Test
        void loginShouldSucceedOnUnEncryptedChannel() {
            assertThatCode(() ->
                testIMAPClient.connect("127.0.0.1", port)
                    .login(USER.asString(), USER_PASS))
                .doesNotThrowAnyException();
        }

        @Test
        void capabilityShouldAdvertiseLoginOnUnEncryptedChannel() throws Exception {
            testIMAPClient.connect("127.0.0.1", port);

            assertThat(testIMAPClient.capability())
                .doesNotContain("LOGINDISABLED")
                .contains("AUTH=PLAIN");
        }
    }

    @Nested
    class AuthenticationRequireSSL {
        IMAPServer imapServer;

        @AfterEach
        void tearDown() {
            if (imapServer != null) {
                imapServer.destroy();
            }
        }

        @Test
        void loginShouldFailWhenRequireSSLAndUnEncryptedChannel() throws Exception {
            imapServer = createImapServer("imapServerRequireSSLIsTrueAndStartSSLIsFalse.xml");
            int port = imapServer.getListenAddresses().get(0).getPort();

            assertThatThrownBy(() ->
                testIMAPClient.connect("127.0.0.1", port)
                    .login(USER.asString(), USER_PASS))
                .hasMessage("Login failed");

        }

        @Test
        void loginShouldSuccessWhenRequireSSLAndEncryptedChannel() throws Exception {
            imapServer = createImapServer("imapServerRequireSSLIsTrueAndStartSSLIsTrue.xml");
            int port = imapServer.getListenAddresses().get(0).getPort();

            IMAPSClient client = new IMAPSClient(false, BogusSslContextFactory.getClientContext());
            client.setTrustManager(BogusTrustManagerFactory.getTrustManagers()[0]);
            client.connect("127.0.0.1", port);
            client.execTLS();
            client.login(USER.asString(), USER_PASS);

            assertThat(client.getReplyString()).contains("OK LOGIN completed.");
        }

        @Test
        void loginShouldSuccessWhenNOTRequireSSLAndUnEncryptedChannel() throws Exception {
            imapServer = createImapServer("imapServerRequireSSLIsFalseAndStartSSLIsFalse.xml");
            int port = imapServer.getListenAddresses().get(0).getPort();

            assertThatCode(() ->
                testIMAPClient.connect("127.0.0.1", port)
                    .login(USER.asString(), USER_PASS)
                    .append("INBOX", SMALL_MESSAGE))
                .doesNotThrowAnyException();
        }

        @Test
        void loginShouldSuccessWhenNOTRequireSSLAndEncryptedChannel() throws Exception {
            imapServer = createImapServer("imapServerRequireSSLIsFalseAndStartSSLIsTrue.xml");
            int port = imapServer.getListenAddresses().get(0).getPort();

            IMAPSClient client = new IMAPSClient(false, BogusSslContextFactory.getClientContext());
            client.setTrustManager(BogusTrustManagerFactory.getTrustManagers()[0]);
            client.connect("127.0.0.1", port);
            client.execTLS();
            client.login(USER.asString(), USER_PASS);

            assertThat(client.getReplyString()).contains("OK LOGIN completed.");
        }
    }

    @Nested
    class Oauth {
        String JWKS_URI_PATH = "/jwks";
        ClientAndServer authServer;
        IMAPServer imapServer;
        int port;

        @BeforeEach
        void authSetup() throws Exception {
            authServer = ClientAndServer.startClientAndServer(0);
            authServer
                .when(HttpRequest.request().withPath(JWKS_URI_PATH))
                .respond(HttpResponse.response().withStatusCode(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(OidcTokenFixture.JWKS_RESPONSE, StandardCharsets.UTF_8));

            HierarchicalConfiguration<ImmutableNode> config = ConfigLoader.getConfig(ClassLoaderUtils.getSystemResourceAsSharedStream("oauth.xml"));
            config.addProperty("auth.oauth.jwksURL", String.format("http://127.0.0.1:%s%s", authServer.getLocalPort(), JWKS_URI_PATH));
            config.addProperty("auth.oauth.claim", OidcTokenFixture.CLAIM);
            config.addProperty("auth.oauth.oidcConfigurationURL", "https://example.com/jwks");
            config.addProperty("auth.oauth.scope", "email");
            imapServer = createImapServer(config);
            port = imapServer.getListenAddresses().get(0).getPort();
        }

        @AfterEach
        void tearDown() {
            if (imapServer != null) {
                imapServer.destroy();
            }
            authServer.stop();
        }

        @Test
        void oauthShouldSuccessWhenValidToken() throws Exception {
            String oauthBearer = OIDCSASLHelper.generateOauthBearer(USER.asString(), OidcTokenFixture.VALID_TOKEN);
            IMAPSClient client = imapsClient(port);
            client.sendCommand("AUTHENTICATE OAUTHBEARER " + oauthBearer);
            assertThat(client.getReplyString()).contains("OK AUTHENTICATE completed.");
        }

        @Test
        void oauthShouldFailWhenInValidToken() throws Exception {
            IMAPSClient client = imapsClient(port);
            client.sendCommand("AUTHENTICATE OAUTHBEARER invalidtoken");
            assertThat(client.getReplyString()).contains("NO AUTHENTICATE failed.");
        }

        @Test
        void oauthShouldFailWhenConfigIsNotProvided() throws Exception {
            imapServer.destroy();
            HierarchicalConfiguration<ImmutableNode> config = ConfigLoader.getConfig(ClassLoaderUtils.getSystemResourceAsSharedStream("imapServerRequireSSLIsTrueAndStartSSLIsTrue.xml"));
            imapServer = createImapServer(config);
            int port = imapServer.getListenAddresses().get(0).getPort();

            IMAPSClient client = imapsClient(port);
            client.sendCommand("AUTHENTICATE OAUTHBEARER " + OidcTokenFixture.VALID_TOKEN);
            assertThat(client.getReplyString()).contains("NO AUTHENTICATE failed. Authentication mechanism is unsupported.");
        }

        @Test
        void capabilityShouldAdvertiseOAUTHBEARERWhenConfigIsProvided() throws Exception {
            IMAPSClient client = imapsClient(port);
            client.capability();
            assertThat(client.getReplyString()).contains("AUTH=OAUTHBEARER");
        }

        @Test
        void capabilityShouldAdvertiseXOAUTH2WhenConfigIsProvided() throws Exception {
            IMAPSClient client = imapsClient(port);
            client.capability();
            assertThat(client.getReplyString()).contains("AUTH=XOAUTH2");
        }

        @Test
        void oauthShouldSupportOAUTH2Type() throws Exception {
            String oauthBearer = OIDCSASLHelper.generateOauthBearer(USER.asString(), OidcTokenFixture.VALID_TOKEN);
            IMAPSClient client = imapsClient(port);
            client.sendCommand("AUTHENTICATE XOAUTH2 " + oauthBearer);
            assertThat(client.getReplyString()).contains("OK AUTHENTICATE completed.");
        }

        @Test
        void capabilityShouldNotAdvertiseOAUTHBEARERWhenConfigIsNotProvided() throws Exception {
            imapServer.destroy();
            HierarchicalConfiguration<ImmutableNode> config = ConfigLoader.getConfig(ClassLoaderUtils.getSystemResourceAsSharedStream("imapServerRequireSSLIsTrueAndStartSSLIsTrue.xml"));
            imapServer = createImapServer(config);
            int port = imapServer.getListenAddresses().get(0).getPort();

            IMAPSClient client = imapsClient(port);
            client.capability();
            assertThat(client.getReplyString()).doesNotContain("AUTH=OAUTHBEARER");
            assertThat(client.getReplyString()).doesNotContain("AUTH=XOAUTH2");
        }

        @Test
        void shouldNotOauthWhenAuthIsReady() throws Exception {
            String oauthBearer = OIDCSASLHelper.generateOauthBearer(USER.asString(), OidcTokenFixture.VALID_TOKEN);
            IMAPSClient client = imapsClient(port);
            client.sendCommand("AUTHENTICATE OAUTHBEARER " + oauthBearer);
            client.sendCommand("AUTHENTICATE OAUTHBEARER " + oauthBearer);
            assertThat(client.getReplyString()).contains("NO AUTHENTICATE failed. Command not valid in this state.");
        }

        @Test
        void appendShouldSuccessWhenAuthenticated() throws Exception {
            String oauthBearer = OIDCSASLHelper.generateOauthBearer(USER.asString(), OidcTokenFixture.VALID_TOKEN);
            IMAPSClient imapsClient = imapsClient(port);
            imapsClient.sendCommand("AUTHENTICATE OAUTHBEARER " + oauthBearer);
            imapsClient.create("INBOX");
            imapsClient.append("INBOX", null, null, SMALL_MESSAGE);

            assertThat(imapsClient.getReplyString()).contains("APPEND completed.");
        }

        @Test
        void appendShouldFailWhenNotAuthenticated() throws Exception {
            IMAPSClient imapsClient = imapsClient(port);
            imapsClient.create("INBOX");
            assertThat(imapsClient.getReplyString()).contains("Command not valid in this state.");
        }
    }

    private AuthenticatingIMAPClient imapsClient(int port) throws Exception {
        AuthenticatingIMAPClient client = new AuthenticatingIMAPClient(false, BogusSslContextFactory.getClientContext());
        client.setTrustManager(BogusTrustManagerFactory.getTrustManagers()[0]);
        client.connect("127.0.0.1", port);
        client.execTLS();
        return client;
    }

    @Nested
    class Search {
        IMAPServer imapServer;
        private int port;

        @BeforeEach
        void beforeEach() throws Exception {
            imapServer = createImapServer("imapServer.xml");
            port = imapServer.getListenAddresses().get(0).getPort();
        }

        @AfterEach
        void tearDown() {
            imapServer.destroy();
        }

        @Disabled("JAMES-1489 IMAP Search do not support continuation")
        @Test
        void searchingShouldSupportMultipleUTF8Criteria() throws Exception {
            String host = "127.0.0.1";
            Properties props = new Properties();
            props.put("mail.debug", "true");
            Session session = Session.getDefaultInstance(props, null);
            Store store = session.getStore("imap");
            store.connect(host, port, USER.asString(), USER_PASS);
            Folder folder = store.getFolder("INBOX");
            folder.open(Folder.READ_ONLY);

            SearchTerm subjectTerm = new SubjectTerm("java培训");
            SearchTerm fromTerm = new FromStringTerm("采购");
            SearchTerm recipientTerm = new RecipientStringTerm(Message.RecipientType.TO,"张三");
            SearchTerm ccRecipientTerm = new RecipientStringTerm(Message.RecipientType.CC,"李四");
            SearchTerm bccRecipientTerm = new RecipientStringTerm(Message.RecipientType.BCC,"王五");
            SearchTerm bodyTerm = new BodyTerm("天天向上");
            SearchTerm[] searchTerms = new SearchTerm[6];
            searchTerms[0] = subjectTerm;
            searchTerms[1] = bodyTerm;
            searchTerms[2] = fromTerm;
            searchTerms[3] = recipientTerm;
            searchTerms[4] = ccRecipientTerm;
            searchTerms[5] = bccRecipientTerm;
            SearchTerm andTerm = new AndTerm(searchTerms);

            assertThatCode(() -> folder.search(andTerm)).doesNotThrowAnyException();

            folder.close(false);
            store.close();
        }

        @Test
        void searchingASingleUTF8CriterionShouldComplete() throws Exception {
            MailboxSession mailboxSession = memoryIntegrationResources.getMailboxManager().createSystemSession(USER);
            memoryIntegrationResources.getMailboxManager()
                .createMailbox(MailboxPath.inbox(USER), mailboxSession);
            memoryIntegrationResources.getMailboxManager()
                .getMailbox(MailboxPath.inbox(USER), mailboxSession)
                .appendMessage(MessageManager.AppendCommand.builder().build("MIME-Version: 1.0\r\n" +
                    "Content-Type: text/html; charset=UTF-8\r\n" +
                    "Content-Transfer-Encoding: quoted-printable\r\n" +
                    "From: =?ISO-8859-1?Q?Beno=EEt_TELLIER?= <b@linagora.com>\r\n" +
                    "Sender: =?ISO-8859-1?Q?Beno=EEt_TELLIER?= <b@linagora.com>\r\n" +
                    "Reply-To: b@linagora.com\r\n" +
                    "To: =?ISO-8859-1?Q?Beno=EEt_TELLIER?= <b@linagora.com>\r\n" +
                    "Subject: Test utf-8 charset\r\n" +
                    "Message-ID: <Mime4j.5f1.9a40f68264d6f2fa.17876fb5605@linagora.com>\r\n" +
                    "Date: Sun, 28 Mar 2021 03:58:06 +0000\r\n" +
                    "\r\n" +
                    "<p>=E5=A4=A9=E5=A4=A9=E5=90=91=E4=B8=8A<br></p>\r\n"), mailboxSession);

            String host = "127.0.0.1";
            Properties props = new Properties();
            props.put("mail.debug", "true");
            Session session = Session.getDefaultInstance(props, null);
            Store store = session.getStore("imap");
            store.connect(host, port, USER.asString(), USER_PASS);
            Folder folder = store.getFolder("INBOX");
            folder.open(Folder.READ_ONLY);

            SearchTerm bodyTerm = new BodyTerm("天天向上");

            assertThat(folder.search(bodyTerm)).hasSize(1);

            folder.close(false);
            store.close();
        }
    }
}
