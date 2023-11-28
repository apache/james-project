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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.commons.net.smtp.SMTPClient;
import org.apache.commons.net.smtp.SMTPSClient;
import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.jwt.OidcTokenFixture;
import org.apache.james.mailbox.Authorizator;
import org.apache.james.protocols.api.OIDCSASLHelper;
import org.apache.james.protocols.api.utils.BogusSslContextFactory;
import org.apache.james.protocols.api.utils.BogusTrustManagerFactory;
import org.apache.james.protocols.lib.mock.ConfigLoader;
import org.apache.james.util.ClassLoaderUtils;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

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



    private final SMTPServerTestSystem testSystem = new SMTPServerTestSystem();
    private ClientAndServer authServer;

    @BeforeEach
    void setUp() throws Exception {
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

        Authorizator authorizator = (userId, otherUserId) -> {
            if (userId.equals(USER) && otherUserId.equals(USER2)) {
                return Authorizator.AuthorizationState.ALLOWED;
            }
            return Authorizator.AuthorizationState.FORBIDDEN;
        };

        testSystem.setUp(config, authorizator);
        testSystem.domainList.addDomain(Domain.of(LOCAL_DOMAIN));
        testSystem.usersRepository.addUser(USER, PASSWORD);
        testSystem.usersRepository.addUser(USER2, PASSWORD);
    }

    private SMTPSClient initSMTPSClient() throws IOException {
        SMTPSClient client = new SMTPSClient(false, BogusSslContextFactory.getClientContext());
        client.setTrustManager(BogusTrustManagerFactory.getTrustManagers()[0]);
        InetSocketAddress bindedAddress = testSystem.getBindedAddress();
        client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
        client.execTLS();
        return client;
    }

    @AfterEach
    void tearDown() {
        testSystem.smtpServer.destroy();
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
        InetSocketAddress bindedAddress = testSystem.getBindedAddress();
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

        assertThat(testSystem.queue.getLastMail())
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

        assertThat(testSystem.queue.getLastMail())
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
        testSystem.smtpServer.destroy();
        HierarchicalConfiguration<ImmutableNode> config = ConfigLoader.getConfig(ClassLoaderUtils.getSystemResourceAsSharedStream("smtpserver-advancedSecurity.xml"));
        testSystem.smtpServer.configure(config);
        testSystem.smtpServer.init();

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
        testSystem.smtpServer.destroy();
        HierarchicalConfiguration<ImmutableNode> config = ConfigLoader.getConfig(ClassLoaderUtils.getSystemResourceAsSharedStream("smtpserver-advancedSecurity.xml"));
        testSystem.smtpServer.configure(config);
        testSystem.smtpServer.init();

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
        testSystem.smtpServer.destroy();
        HierarchicalConfiguration<ImmutableNode> config = ConfigLoader.getConfig(ClassLoaderUtils.getSystemResourceAsSharedStream("smtpserver-advancedSecurity.xml"));
        testSystem.smtpServer.configure(config);
        testSystem.smtpServer.init();

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
        testSystem.smtpServer.destroy();
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
        testSystem.smtpServer.configure(config);
        testSystem.smtpServer.init();

        SMTPSClient client = initSMTPSClient();

        client.sendCommand("AUTH OAUTHBEARER " + VALID_TOKEN);

        assertThat(client.getReplyString()).contains("334 " + FAIL_RESPONSE_TOKEN);

        client.sendCommand("AQ==");
        assertThat(client.getReplyString()).contains("535 Authentication Failed");

    }

    @Test
    void oauthShouldSuccessWhenIntrospectTokenReturnActiveIsTrue() throws Exception {
        testSystem.smtpServer.destroy();
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
        testSystem.smtpServer.configure(config);
        testSystem.smtpServer.init();

        SMTPSClient client = initSMTPSClient();

        client.sendCommand("AUTH OAUTHBEARER " + VALID_TOKEN);

        assertThat(client.getReplyString()).contains("235 Authentication successful.");
    }

    @Test
    void oauthShouldFailWhenIntrospectTokenServerError() throws Exception {
        testSystem.smtpServer.destroy();
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
        testSystem.smtpServer.configure(config);
        testSystem.smtpServer.init();

        SMTPSClient client = initSMTPSClient();

        client.sendCommand("AUTH OAUTHBEARER " + VALID_TOKEN);

        assertThat(client.getReplyString()).contains("451 Unable to process request");
    }

    @Test
    void oauthShouldSuccessWhenCheckTokenByUserInfoIsPassed() throws Exception {
        testSystem.smtpServer.destroy();
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
        testSystem.smtpServer.configure(config);
        testSystem.smtpServer.init();

        SMTPSClient client = initSMTPSClient();

        client.sendCommand("AUTH OAUTHBEARER " + VALID_TOKEN);

        assertThat(client.getReplyString()).contains("235 Authentication successful.");
    }

    @Test
    void oauthShouldFailWhenCheckTokenByUserInfoIsFailed() throws Exception {
        testSystem.smtpServer.destroy();
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
        testSystem.smtpServer.configure(config);
        testSystem.smtpServer.init();

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

        assertThat(testSystem.queue.getLastMail())
            .as("mail received by mail server")
            .isNotNull();
    }
}
