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
package org.apache.james.smtpserver;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.james.jwt.OidcTokenFixture.INTROSPECTION_RESPONSE;
import static org.apache.james.jwt.OidcTokenFixture.USERINFO_RESPONSE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.commons.net.smtp.SMTPClient;
import org.apache.commons.net.smtp.SMTPSClient;
import org.apache.james.UserEntityValidator;
import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.lib.DomainListConfiguration;
import org.apache.james.domainlist.memory.MemoryDomainList;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.jwt.OidcTokenFixture;
import org.apache.james.mailbox.Authorizator;
import org.apache.james.mailrepository.api.MailRepositoryStore;
import org.apache.james.mailrepository.api.Protocol;
import org.apache.james.mailrepository.memory.MailRepositoryStoreConfiguration;
import org.apache.james.mailrepository.memory.MemoryMailRepository;
import org.apache.james.mailrepository.memory.MemoryMailRepositoryStore;
import org.apache.james.mailrepository.memory.MemoryMailRepositoryUrlStore;
import org.apache.james.mailrepository.memory.SimpleMailRepositoryLoader;
import org.apache.james.metrics.api.Metric;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.protocols.api.OIDCSASLHelper;
import org.apache.james.protocols.api.utils.BogusSslContextFactory;
import org.apache.james.protocols.api.utils.BogusTrustManagerFactory;
import org.apache.james.protocols.api.utils.ProtocolServerUtils;
import org.apache.james.protocols.lib.mock.ConfigLoader;
import org.apache.james.protocols.lib.mock.MockProtocolHandlerLoader;
import org.apache.james.queue.api.MailQueueFactory;
import org.apache.james.queue.api.RawMailQueueItemDecoratorFactory;
import org.apache.james.queue.memory.MemoryMailQueueFactory;
import org.apache.james.rrt.api.AliasReverseResolver;
import org.apache.james.rrt.api.CanSendFrom;
import org.apache.james.rrt.api.RecipientRewriteTable;
import org.apache.james.rrt.api.RecipientRewriteTableConfiguration;
import org.apache.james.rrt.lib.AliasReverseResolverImpl;
import org.apache.james.rrt.lib.CanSendFromImpl;
import org.apache.james.rrt.memory.MemoryRecipientRewriteTable;
import org.apache.james.server.core.configuration.Configuration;
import org.apache.james.server.core.filesystem.FileSystemImpl;
import org.apache.james.smtpserver.netty.SMTPServer;
import org.apache.james.smtpserver.netty.SmtpMetricsImpl;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.apache.james.util.ClassLoaderUtils;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import com.google.common.collect.ImmutableList;
import com.google.inject.TypeLiteral;

class SMTPSaslTest {
    public static final String LOCAL_DOMAIN = "domain.org";
    public static final Username USER = Username.of("user@domain.org");
    public static final Username USER2 = Username.of("user2@domain.org");
    public static final String PASSWORD = "userpassword";
    public static final String JWKS_URI_PATH = "/jwks";
    public static final String INTROSPECT_TOKEN_URI_PATH = "/introspect";
    public static final String USERINFO_URI_PATH = "/userinfo";
    public static final String OIDC_URL = "https://example.com/jwks";
    public static final String SCOPE = "scope";
    public static final String FAIL_RESPONSE_TOKEN = Base64.getEncoder().encodeToString(
        String.format("{\"status\":\"invalid_token\",\"scope\":\"%s\",\"schemes\":\"%s\"}", SCOPE, OIDC_URL).getBytes(UTF_8));
    public static final String VALID_TOKEN = OIDCSASLHelper.generateOauthBearer(USER.asString(), OidcTokenFixture.VALID_TOKEN);
    public static final String INVALID_TOKEN = OIDCSASLHelper.generateOauthBearer(USER.asString(), OidcTokenFixture.INVALID_TOKEN);


    protected MemoryDomainList domainList;
    protected MemoryUsersRepository usersRepository;
    protected SMTPServerTest.AlterableDNSServer dnsServer;
    protected MemoryMailRepositoryStore mailRepositoryStore;
    protected FileSystemImpl fileSystem;
    protected Configuration configuration;
    protected MockProtocolHandlerLoader chain;
    protected MemoryMailQueueFactory queueFactory;
    protected MemoryMailQueueFactory.MemoryCacheableMailQueue queue;

    private SMTPServer smtpServer;
    private ClientAndServer authServer;

    @BeforeEach
    void setUp() throws Exception {
        domainList = new MemoryDomainList();
        domainList.configure(DomainListConfiguration.DEFAULT);

        domainList.addDomain(Domain.of(LOCAL_DOMAIN));
        usersRepository = MemoryUsersRepository.withVirtualHosting(domainList);
        usersRepository.addUser(USER, PASSWORD);
        usersRepository.addUser(USER2, PASSWORD);

        createMailRepositoryStore();

        setUpFakeLoader();
        setUpSMTPServer();

        authServer = ClientAndServer.startClientAndServer(0);
        authServer
            .when(HttpRequest.request().withPath(JWKS_URI_PATH))
            .respond(HttpResponse.response().withStatusCode(200)
                .withHeader("Content-Type", "application/json")
                .withBody(OidcTokenFixture.JWKS_RESPONSE, StandardCharsets.UTF_8));

        HierarchicalConfiguration<ImmutableNode> config = ConfigLoader.getConfig(ClassLoaderUtils.getSystemResourceAsSharedStream("smtpserver-advancedSecurity.xml"));
        config.addProperty("auth.oidc.jwksURL", String.format("http://127.0.0.1:%s%s", authServer.getLocalPort(), JWKS_URI_PATH));
        config.addProperty("auth.oidc.claim", OidcTokenFixture.CLAIM);
        config.addProperty("auth.oidc.oidcConfigurationURL", OIDC_URL);
        config.addProperty("auth.oidc.scope", SCOPE);
        smtpServer.configure(config);
        smtpServer.init();
    }

    protected void createMailRepositoryStore() throws Exception {
        configuration = Configuration.builder()
            .workingDirectory("../")
            .configurationFromClasspath()
            .build();
        fileSystem = new FileSystemImpl(configuration.directories());
        MemoryMailRepositoryUrlStore urlStore = new MemoryMailRepositoryUrlStore();

        MailRepositoryStoreConfiguration configuration = MailRepositoryStoreConfiguration.forItems(
            new MailRepositoryStoreConfiguration.Item(
                ImmutableList.of(new Protocol("memory")),
                MemoryMailRepository.class.getName(),
                new BaseHierarchicalConfiguration()));

        mailRepositoryStore = new MemoryMailRepositoryStore(urlStore, new SimpleMailRepositoryLoader(), configuration);
        mailRepositoryStore.init();
    }

    protected SMTPServer createSMTPServer(SmtpMetricsImpl smtpMetrics) {
        return new SMTPServer(smtpMetrics);
    }

    protected void setUpSMTPServer() {
        SmtpMetricsImpl smtpMetrics = mock(SmtpMetricsImpl.class);
        when(smtpMetrics.getCommandsMetric()).thenReturn(mock(Metric.class));
        when(smtpMetrics.getConnectionMetric()).thenReturn(mock(Metric.class));
        smtpServer = createSMTPServer(smtpMetrics);
        smtpServer.setDnsService(dnsServer);
        smtpServer.setFileSystem(fileSystem);
        smtpServer.setProtocolHandlerLoader(chain);
    }

    protected void setUpFakeLoader() {
        dnsServer = new SMTPServerTest.AlterableDNSServer();

        MemoryRecipientRewriteTable rewriteTable = new MemoryRecipientRewriteTable();
        rewriteTable.setConfiguration(RecipientRewriteTableConfiguration.DEFAULT_ENABLED);
        AliasReverseResolver aliasReverseResolver = new AliasReverseResolverImpl(rewriteTable);
        CanSendFrom canSendFrom = new CanSendFromImpl(rewriteTable, aliasReverseResolver);
        queueFactory = new MemoryMailQueueFactory(new RawMailQueueItemDecoratorFactory());
        queue = queueFactory.createQueue(MailQueueFactory.SPOOL);

        Authorizator authorizator = (userId, otherUserId) -> {
            if (userId.equals(USER) && otherUserId.equals(USER2)) {
                return Authorizator.AuthorizationState.ALLOWED;
            }
            return Authorizator.AuthorizationState.FORBIDDEN;
        };

        chain = MockProtocolHandlerLoader.builder()
            .put(binder -> binder.bind(DomainList.class).toInstance(domainList))
            .put(binder -> binder.bind(new TypeLiteral<MailQueueFactory<?>>() {}).toInstance(queueFactory))
            .put(binder -> binder.bind(RecipientRewriteTable.class).toInstance(rewriteTable))
            .put(binder -> binder.bind(CanSendFrom.class).toInstance(canSendFrom))
            .put(binder -> binder.bind(FileSystem.class).toInstance(fileSystem))
            .put(binder -> binder.bind(MailRepositoryStore.class).toInstance(mailRepositoryStore))
            .put(binder -> binder.bind(DNSService.class).toInstance(dnsServer))
            .put(binder -> binder.bind(UsersRepository.class).toInstance(usersRepository))
            .put(binder -> binder.bind(MetricFactory.class).to(RecordingMetricFactory.class))
            .put(binder -> binder.bind(UserEntityValidator.class).toInstance(UserEntityValidator.NOOP))
            .put(binder -> binder.bind(Authorizator.class).toInstance(authorizator))
            .build();
    }

    private SMTPSClient initSMTPSClient() throws IOException {
        SMTPSClient client = new SMTPSClient(false, BogusSslContextFactory.getClientContext());
        client.setTrustManager(BogusTrustManagerFactory.getTrustManagers()[0]);
        InetSocketAddress bindedAddress = new ProtocolServerUtils(smtpServer).retrieveBindedAddress();
        client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
        client.execTLS();
        return client;
    }

    @AfterEach
    void tearDown() {
        smtpServer.destroy();
        authServer.stop();
    }

    @Test
    void oauthShouldSuccessWhenValidToken() throws Exception {
        SMTPSClient client = initSMTPSClient();

        client.sendCommand("AUTH OAUTHBEARER " + VALID_TOKEN);

        assertThat(client.getReplyString()).contains("235 Authentication successful.");
    }

    @Test
    void oauthShouldSupportXOAUTH2Type() throws Exception {
        SMTPSClient client = initSMTPSClient();

        client.sendCommand("AUTH XOAUTH2 " + VALID_TOKEN);

        assertThat(client.getReplyString()).contains("235 Authentication successful.");
    }

    @Test
    void oauthWithNoTLSConnectShouldFail() throws Exception {
        SMTPClient client = new SMTPClient();
        InetSocketAddress bindedAddress = new ProtocolServerUtils(smtpServer).retrieveBindedAddress();
        client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());

        client.sendCommand("EHLO localhost");
        assertThat(client.getReplyString())
            .as("Should not advertise OAUTHBEARER when no TLS connect.")
            .doesNotContain("OAUTHBEARER");

        client.sendCommand("AUTH OAUTHBEARER " + VALID_TOKEN);
        assertThat(client.getReplyString()).contains("504 Unrecognized Authentication Type");
    }

    @Test
    void oauthShouldFailWhenInvalidToken() throws Exception {
        SMTPSClient client = initSMTPSClient();

        client.sendCommand("AUTH OAUTHBEARER " + INVALID_TOKEN);
        assertThat(client.getReplyString()).contains("334 " + FAIL_RESPONSE_TOKEN);

        client.sendCommand("AQ==");
        assertThat(client.getReplyString()).contains("535 Authentication Failed");
    }

    @Test
    void sendMailShouldSuccessWhenAuthenticatedByOAuthBearer() throws Exception {
        SMTPSClient client = initSMTPSClient();

        client.sendCommand("EHLO localhost");

        client.sendCommand("AUTH OAUTHBEARER " + VALID_TOKEN);

        client.setSender(USER.asString());
        client.addRecipient("mail@domain.org");
        client.sendShortMessageData("Subject: test\r\n\r\nTest body testAuth\r\n");
        client.quit();

        assertThat(queue.getLastMail())
            .as("mail received by mail server")
            .isNotNull();
    }

    @Test
    void sendMailShouldFailWhenNotAuthenticated() throws Exception {
        SMTPSClient client = initSMTPSClient();

        client.sendCommand("EHLO localhost");

        client.setSender(USER.asString());
        client.addRecipient("mail@domain.org");
        client.sendShortMessageData("Subject: test\r\n\r\nTest body testAuth\r\n");
        client.quit();

        assertThat(queue.getLastMail())
            .as("mail received by mail server")
            .isNull();
    }

    @Test
    void shouldNotOauthWhenAlreadyAuthenticated() throws Exception {
        SMTPSClient client = initSMTPSClient();

        client.sendCommand("AUTH OAUTHBEARER " + VALID_TOKEN);
        client.sendCommand("AUTH OAUTHBEARER " + VALID_TOKEN);

        assertThat(client.getReplyString()).contains("503 5.5.0 User has previously authenticated.  Further authentication is not required!");
    }

    @Test
    void oauthShouldFailWhenConfigIsNotProvided() throws Exception {
        smtpServer.destroy();
        HierarchicalConfiguration<ImmutableNode> config = ConfigLoader.getConfig(ClassLoaderUtils.getSystemResourceAsSharedStream("smtpserver-advancedSecurity.xml"));
        smtpServer.configure(config);
        smtpServer.init();

        SMTPSClient client = initSMTPSClient();

        client.sendCommand("AUTH OAUTHBEARER " + VALID_TOKEN);
        assertThat(client.getReplyString()).contains("504 Unrecognized Authentication Type");
    }

    @Test
    void ehloShouldAdvertiseOAUTHBEARERWhenConfigIsProvided() throws Exception {
        SMTPSClient client = initSMTPSClient();

        client.sendCommand("EHLO localhost");

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(client.getReplyCode()).isEqualTo(250);
            softly.assertThat(client.getReplyString())
                .contains("250-AUTH OAUTHBEARER");
        });
    }

    @Test
    void ehloShouldAdvertiseXOAUTH2WhenConfigIsProvided() throws Exception {
        SMTPSClient client = initSMTPSClient();

        client.sendCommand("EHLO localhost");

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(client.getReplyCode()).isEqualTo(250);
            softly.assertThat(client.getReplyString())
                .contains("XOAUTH2");
        });
    }

    @Test
    void ehloShouldNotAdvertiseOAUTHBEARERWhenConfigIsNotProvided() throws Exception {
        smtpServer.destroy();
        HierarchicalConfiguration<ImmutableNode> config = ConfigLoader.getConfig(ClassLoaderUtils.getSystemResourceAsSharedStream("smtpserver-advancedSecurity.xml"));
        smtpServer.configure(config);
        smtpServer.init();

        SMTPSClient client = initSMTPSClient();
        client.sendCommand("EHLO localhost");

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(client.getReplyCode()).isEqualTo(250);
            softly.assertThat(client.getReplyString())
                .doesNotContain("OAUTHBEARER");
        });
    }

    @Test
    void ehloShouldNotAdvertiseXOAUTH2WhenConfigIsNotProvided() throws Exception {
        smtpServer.destroy();
        HierarchicalConfiguration<ImmutableNode> config = ConfigLoader.getConfig(ClassLoaderUtils.getSystemResourceAsSharedStream("smtpserver-advancedSecurity.xml"));
        smtpServer.configure(config);
        smtpServer.init();

        SMTPSClient client = initSMTPSClient();
        client.sendCommand("EHLO localhost");

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(client.getReplyCode()).isEqualTo(250);
            softly.assertThat(client.getReplyString())
                .doesNotContain("XOAUTH2");
        });
    }

    @Test
    void oauthShouldFailWhenIntrospectTokenReturnActiveIsFalse() throws Exception {
        smtpServer.destroy();
        authServer
            .when(HttpRequest.request().withPath(INTROSPECT_TOKEN_URI_PATH))
            .respond(HttpResponse.response().withStatusCode(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"active\": false}", StandardCharsets.UTF_8));

        HierarchicalConfiguration<ImmutableNode> config = ConfigLoader.getConfig(ClassLoaderUtils.getSystemResourceAsSharedStream("smtpserver-advancedSecurity.xml"));
        config.addProperty("auth.oidc.jwksURL", String.format("http://127.0.0.1:%s%s", authServer.getLocalPort(), JWKS_URI_PATH));
        config.addProperty("auth.oidc.claim", OidcTokenFixture.CLAIM);
        config.addProperty("auth.oidc.oidcConfigurationURL", OIDC_URL);
        config.addProperty("auth.oidc.scope", SCOPE);
        config.addProperty("auth.oidc.introspection.url", String.format("http://127.0.0.1:%s%s", authServer.getLocalPort(), INTROSPECT_TOKEN_URI_PATH));
        smtpServer.configure(config);
        smtpServer.init();

        SMTPSClient client = initSMTPSClient();

        client.sendCommand("AUTH OAUTHBEARER " + VALID_TOKEN);

        assertThat(client.getReplyString()).contains("334 " + FAIL_RESPONSE_TOKEN);

        client.sendCommand("AQ==");
        assertThat(client.getReplyString()).contains("535 Authentication Failed");

    }

    @Test
    void oauthShouldSuccessWhenIntrospectTokenReturnActiveIsTrue() throws Exception {
        smtpServer.destroy();
        authServer
            .when(HttpRequest.request().withPath(INTROSPECT_TOKEN_URI_PATH))
            .respond(HttpResponse.response().withStatusCode(200)
                .withHeader("Content-Type", "application/json")
                .withBody(INTROSPECTION_RESPONSE, StandardCharsets.UTF_8));

        HierarchicalConfiguration<ImmutableNode> config = ConfigLoader.getConfig(ClassLoaderUtils.getSystemResourceAsSharedStream("smtpserver-advancedSecurity.xml"));
        config.addProperty("auth.oidc.jwksURL", String.format("http://127.0.0.1:%s%s", authServer.getLocalPort(), JWKS_URI_PATH));
        config.addProperty("auth.oidc.claim", OidcTokenFixture.CLAIM);
        config.addProperty("auth.oidc.oidcConfigurationURL", OIDC_URL);
        config.addProperty("auth.oidc.scope", SCOPE);
        config.addProperty("auth.oidc.introspection.url", String.format("http://127.0.0.1:%s%s", authServer.getLocalPort(), INTROSPECT_TOKEN_URI_PATH));
        smtpServer.configure(config);
        smtpServer.init();

        SMTPSClient client = initSMTPSClient();

        client.sendCommand("AUTH OAUTHBEARER " + VALID_TOKEN);

        assertThat(client.getReplyString()).contains("235 Authentication successful.");
    }

    @Test
    void oauthShouldFailWhenIntrospectTokenServerError() throws Exception {
        smtpServer.destroy();
        String invalidURI = "/invalidURI";
        authServer
            .when(HttpRequest.request().withPath(invalidURI))
            .respond(HttpResponse.response().withStatusCode(503)
                .withHeader("Content-Type", "application/json"));

        HierarchicalConfiguration<ImmutableNode> config = ConfigLoader.getConfig(ClassLoaderUtils.getSystemResourceAsSharedStream("smtpserver-advancedSecurity.xml"));
        config.addProperty("auth.oidc.jwksURL", String.format("http://127.0.0.1:%s%s", authServer.getLocalPort(), JWKS_URI_PATH));
        config.addProperty("auth.oidc.claim", OidcTokenFixture.CLAIM);
        config.addProperty("auth.oidc.oidcConfigurationURL", OIDC_URL);
        config.addProperty("auth.oidc.scope", SCOPE);
        config.addProperty("auth.oidc.introspection.url", String.format("http://127.0.0.1:%s%s", authServer.getLocalPort(), invalidURI));
        smtpServer.configure(config);
        smtpServer.init();

        SMTPSClient client = initSMTPSClient();

        client.sendCommand("AUTH OAUTHBEARER " + VALID_TOKEN);

        assertThat(client.getReplyString()).contains("451 Unable to process request");
    }

    @Test
    void oauthShouldSuccessWhenCheckTokenByUserInfoIsPassed() throws Exception {
        smtpServer.destroy();
        authServer
            .when(HttpRequest.request().withPath(USERINFO_URI_PATH))
            .respond(HttpResponse.response().withStatusCode(200)
                .withHeader("Content-Type", "application/json")
                .withBody(USERINFO_RESPONSE, StandardCharsets.UTF_8));

        HierarchicalConfiguration<ImmutableNode> config = ConfigLoader.getConfig(ClassLoaderUtils.getSystemResourceAsSharedStream("smtpserver-advancedSecurity.xml"));
        config.addProperty("auth.oidc.jwksURL", String.format("http://127.0.0.1:%s%s", authServer.getLocalPort(), JWKS_URI_PATH));
        config.addProperty("auth.oidc.claim", OidcTokenFixture.CLAIM);
        config.addProperty("auth.oidc.oidcConfigurationURL", OIDC_URL);
        config.addProperty("auth.oidc.scope", SCOPE);
        config.addProperty("auth.oidc.userinfo.url", String.format("http://127.0.0.1:%s%s", authServer.getLocalPort(), USERINFO_URI_PATH));
        smtpServer.configure(config);
        smtpServer.init();

        SMTPSClient client = initSMTPSClient();

        client.sendCommand("AUTH OAUTHBEARER " + VALID_TOKEN);

        assertThat(client.getReplyString()).contains("235 Authentication successful.");
    }

    @Test
    void oauthShouldFailWhenCheckTokenByUserInfoIsFailed() throws Exception {
        smtpServer.destroy();
        authServer
            .when(HttpRequest.request().withPath(USERINFO_URI_PATH))
            .respond(HttpResponse.response().withStatusCode(401)
                .withHeader("Content-Type", "application/json"));

        HierarchicalConfiguration<ImmutableNode> config = ConfigLoader.getConfig(ClassLoaderUtils.getSystemResourceAsSharedStream("smtpserver-advancedSecurity.xml"));
        config.addProperty("auth.oidc.jwksURL", String.format("http://127.0.0.1:%s%s", authServer.getLocalPort(), JWKS_URI_PATH));
        config.addProperty("auth.oidc.claim", OidcTokenFixture.CLAIM);
        config.addProperty("auth.oidc.oidcConfigurationURL", OIDC_URL);
        config.addProperty("auth.oidc.scope", SCOPE);
        config.addProperty("auth.oidc.userinfo.url", String.format("http://127.0.0.1:%s%s", authServer.getLocalPort(), USERINFO_URI_PATH));
        smtpServer.configure(config);
        smtpServer.init();

        SMTPSClient client = initSMTPSClient();

        client.sendCommand("AUTH OAUTHBEARER " + VALID_TOKEN);

        assertThat(client.getReplyString()).contains("451 Unable to process request");
    }

    @Test
    void oauthShouldImpersonateFailWhenNOTDelegated() throws Exception {
        SMTPSClient client = initSMTPSClient();
        String tokenWithImpersonation = OIDCSASLHelper.generateOauthBearer("another@domain.org", OidcTokenFixture.VALID_TOKEN);
        client.sendCommand("AUTH OAUTHBEARER " + tokenWithImpersonation);

        assertThat(client.getReplyString()).contains("334 ");

        client.sendCommand("AQ==");
        assertThat(client.getReplyString()).contains("535 Authentication Failed");
    }
    @Test
    void oauthShouldImpersonateSuccessWhenDelegated() throws Exception {
        SMTPSClient client = initSMTPSClient();
        String tokenWithImpersonation = OIDCSASLHelper.generateOauthBearer(USER2.asString(), OidcTokenFixture.VALID_TOKEN);
        client.sendCommand("AUTH OAUTHBEARER " + tokenWithImpersonation);

        assertThat(client.getReplyString()).contains("235 Authentication successful.");
    }

    @Test
    void impersonationShouldWorkWhenDelegated() throws Exception {
        SMTPSClient client = initSMTPSClient();

        client.sendCommand("EHLO localhost");

        client.sendCommand("AUTH OAUTHBEARER " + OIDCSASLHelper.generateOauthBearer(USER2.asString(), OidcTokenFixture.VALID_TOKEN));

        client.setSender(USER2.asString());
        client.addRecipient("mail@domain.org");
        client.sendShortMessageData("Subject: test\r\n\r\nTest body testAuth\r\n");
        client.quit();

        assertThat(queue.getLastMail())
            .as("mail received by mail server")
            .isNotNull();
    }
}
