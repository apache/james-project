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
import java.util.Optional;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.commons.net.smtp.SMTPClient;
import org.apache.commons.net.smtp.SMTPSClient;
import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.jwt.OidcTokenFixture;
import org.apache.james.mailbox.Authorizator;
import org.apache.james.protocols.api.OIDCSASLHelper;
import org.apache.james.protocols.api.sasl.SaslAuthenticator;
import org.apache.james.protocols.api.sasl.SaslExchange;
import org.apache.james.protocols.api.sasl.SaslFailure;
import org.apache.james.protocols.api.sasl.SaslIdentity;
import org.apache.james.protocols.api.sasl.SaslInitialRequest;
import org.apache.james.protocols.api.sasl.SaslMechanism;
import org.apache.james.protocols.api.sasl.SaslStep;
import org.apache.james.protocols.api.utils.BogusSslContextFactory;
import org.apache.james.protocols.api.utils.BogusTrustManagerFactory;
import org.apache.james.protocols.lib.mock.ConfigLoader;
import org.apache.james.protocols.sasl.OauthBearerSaslMechanismFactory;
import org.apache.james.protocols.sasl.XOauth2SaslMechanismFactory;
import org.apache.james.protocols.sasl.plain.PlainSaslMechanism;
import org.apache.james.protocols.smtp.core.esmtp.LoginSaslMechanismFactory;
import org.apache.james.util.ClassLoaderUtils;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import com.google.common.collect.ImmutableList;

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
    public static final String VALID_OAUTHBEARER_TOKEN = OIDCSASLHelper.generateEncodedOauthbearerInitialClientResponse(USER.asString(), OidcTokenFixture.VALID_TOKEN);
    public static final String INVALID_OAUTHBEARER_TOKEN = OIDCSASLHelper.generateEncodedOauthbearerInitialClientResponse(USER.asString(), OidcTokenFixture.INVALID_TOKEN);
    private static final SaslMechanism ADVERTISED_OAUTHBEARER = new AdvertisedOnlySaslMechanism("OAUTHBEARER");
    private static final SaslMechanism ADVERTISED_XOAUTH2 = new AdvertisedOnlySaslMechanism("XOAUTH2");


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

        setUpServerWithOidcMechanisms(config);
    }

    private void setUpServerWithOidcMechanisms(HierarchicalConfiguration<ImmutableNode> config) throws Exception {
        testSystem.setUpWithSaslMechanisms(config, authorizator(), ImmutableList.of(
            new OauthBearerSaslMechanismFactory().create(config),
            new XOauth2SaslMechanismFactory().create(config)));
        addUsers();
    }

    private void addUsers() throws Exception {
        testSystem.domainList.addDomain(Domain.of(LOCAL_DOMAIN));
        testSystem.usersRepository.addUser(USER, PASSWORD);
        testSystem.usersRepository.addUser(USER2, PASSWORD);
    }

    private Authorizator authorizator() {
        return (userId, otherUserId) -> {
            if (userId.equals(USER) && otherUserId.equals(USER2)) {
                return Authorizator.AuthorizationState.ALLOWED;
            }
            return Authorizator.AuthorizationState.FORBIDDEN;
        };
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

        client.sendCommand("AUTH OAUTHBEARER " + VALID_OAUTHBEARER_TOKEN);

        assertThat(client.getReplyString()).contains("235 Authentication Successful");

        client.sendCommand("NOOP");
        assertThat(client.getReplyString()).contains("250 2.0.0 OK");
    }

    @Test
    void oauthShouldSuccessWhenValidTokenAndContinuation() throws Exception {
        SMTPSClient client = initSMTPSClient();

        client.sendCommand("AUTH OAUTHBEARER");
        assertThat(client.getReplyString()).contains("334");
        client.sendCommand(VALID_OAUTHBEARER_TOKEN);

        assertThat(client.getReplyString()).contains("235 Authentication Successful");

        client.sendCommand("NOOP");
        assertThat(client.getReplyString()).contains("250 2.0.0 OK");
    }

    @Test
    void oauthShouldSuccessWhenValidTokenAndContinuationAndXOauth2() throws Exception {
        SMTPSClient client = initSMTPSClient();

        client.sendCommand("AUTH XOAUTH2");
        assertThat(client.getReplyString()).contains("334");
        client.sendCommand(OIDCSASLHelper.generateEncodedOauthbearerInitialClientResponse(USER.asString(), OidcTokenFixture.VALID_TOKEN));

        assertThat(client.getReplyString()).contains("235 Authentication Successful");

        client.sendCommand("NOOP");
        assertThat(client.getReplyString()).contains("250 2.0.0 OK");
    }

    @Test
    void oauthShouldSupportXOAUTH2Type() throws Exception {
        SMTPSClient client = initSMTPSClient();

        client.sendCommand("AUTH XOAUTH2 " + OIDCSASLHelper.generateEncodedOauthbearerInitialClientResponse(USER.asString(), OidcTokenFixture.VALID_TOKEN));

        assertThat(client.getReplyString()).contains("235 Authentication Successful");
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

        client.sendCommand("AUTH OAUTHBEARER " + VALID_OAUTHBEARER_TOKEN);
        assertThat(client.getReplyString()).contains("504 Unrecognized Authentication Type");
    }

    @Test
    void oauthShouldFailWhenInvalidToken() throws Exception {
        SMTPSClient client = initSMTPSClient();

        client.sendCommand("AUTH OAUTHBEARER " + INVALID_OAUTHBEARER_TOKEN);
        assertThat(client.getReplyString()).contains("334 " + FAIL_RESPONSE_TOKEN);

        client.sendCommand("AQ==");
        assertThat(client.getReplyString()).contains("535 Authentication Failed");
    }

    @Test
    void sendMailShouldSuccessWhenAuthenticatedByOAuthBearer() throws Exception {
        SMTPSClient client = initSMTPSClient();

        client.sendCommand("EHLO localhost");

        client.sendCommand("AUTH OAUTHBEARER " + VALID_OAUTHBEARER_TOKEN);

        client.setSender(USER.asString());
        client.addRecipient("mail@domain.org");
        client.sendShortMessageData("From: " + USER.asString() + "\r\n\r\nSubject: test\r\n\r\nTest body testAuth\r\n");
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

        client.sendCommand("AUTH OAUTHBEARER " + VALID_OAUTHBEARER_TOKEN);
        client.sendCommand("AUTH OAUTHBEARER " + VALID_OAUTHBEARER_TOKEN);

        assertThat(client.getReplyString()).contains("503 5.5.0 User has previously authenticated.  Further authentication is not required!");
    }

    @Test
    void oauthShouldFailWhenConfigIsNotProvided() throws Exception {
        testSystem.smtpServer.destroy();
        HierarchicalConfiguration<ImmutableNode> config = ConfigLoader.getConfig(ClassLoaderUtils.getSystemResourceAsSharedStream("smtpserver-advancedSecurity.xml"));
        testSystem.setUp(config, authorizator());
        addUsers();

        SMTPSClient client = initSMTPSClient();

        client.sendCommand("AUTH OAUTHBEARER " + VALID_OAUTHBEARER_TOKEN);
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
        testSystem.setUp(config, authorizator());
        addUsers();

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
        testSystem.setUp(config, authorizator());
        addUsers();

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
        setUpServerWithOidcMechanisms(config);

        SMTPSClient client = initSMTPSClient();

        client.sendCommand("AUTH OAUTHBEARER " + VALID_OAUTHBEARER_TOKEN);

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
        setUpServerWithOidcMechanisms(config);

        SMTPSClient client = initSMTPSClient();

        client.sendCommand("AUTH OAUTHBEARER " + VALID_OAUTHBEARER_TOKEN);

        assertThat(client.getReplyString()).contains("235 Authentication Successful");
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
        setUpServerWithOidcMechanisms(config);

        SMTPSClient client = initSMTPSClient();

        client.sendCommand("AUTH OAUTHBEARER " + VALID_OAUTHBEARER_TOKEN);

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
        setUpServerWithOidcMechanisms(config);

        SMTPSClient client = initSMTPSClient();

        client.sendCommand("AUTH OAUTHBEARER " + VALID_OAUTHBEARER_TOKEN);

        assertThat(client.getReplyString()).contains("235 Authentication Successful");
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
        setUpServerWithOidcMechanisms(config);

        SMTPSClient client = initSMTPSClient();

        client.sendCommand("AUTH OAUTHBEARER " + VALID_OAUTHBEARER_TOKEN);

        assertThat(client.getReplyString()).contains("451 Unable to process request");
    }

    @Test
    void oauthShouldImpersonateFailWhenNOTDelegated() throws Exception {
        SMTPSClient client = initSMTPSClient();
        String tokenWithImpersonation = OIDCSASLHelper.generateEncodedOauthbearerInitialClientResponse("another@domain.org", OidcTokenFixture.VALID_TOKEN);
        client.sendCommand("AUTH OAUTHBEARER " + tokenWithImpersonation);

        assertThat(client.getReplyString()).contains("535 Authentication Failed");
    }

    @Test
    void oauthShouldImpersonateSuccessWhenDelegated() throws Exception {
        SMTPSClient client = initSMTPSClient();
        String tokenWithImpersonation = OIDCSASLHelper.generateEncodedOauthbearerInitialClientResponse(USER2.asString(), OidcTokenFixture.VALID_TOKEN);
        client.sendCommand("AUTH OAUTHBEARER " + tokenWithImpersonation);

        assertThat(client.getReplyString()).contains("235 Authentication Successful");
    }

    @Test
    void impersonationShouldWorkWhenDelegated() throws Exception {
        SMTPSClient client = initSMTPSClient();

        client.sendCommand("EHLO localhost");

        client.sendCommand("AUTH OAUTHBEARER " + OIDCSASLHelper.generateEncodedOauthbearerInitialClientResponse(USER2.asString(), OidcTokenFixture.VALID_TOKEN));

        client.setSender(USER2.asString());
        client.addRecipient("mail@domain.org");
        client.sendShortMessageData("From: " + USER2.asString() + "\r\n\r\nSubject: test\r\n\r\nTest body testAuth\r\n");
        client.quit();

        assertThat(testSystem.queue.getLastMail())
            .as("mail received by mail server")
            .isNotNull();
    }

    @Test
    void ehloShouldAdvertisePlainAndLoginWhenPlainAndLoginMechanismsAreConfigured() throws Exception {
        PlainSaslMechanism plain = new PlainSaslMechanism(true, false);
        resetWithMechanisms(smtpPlainAndLoginMechanisms(plain));
        SMTPClient client = connectedClient();

        client.sendCommand("EHLO localhost");

        assertThat(client.getReplyString()).contains("250-AUTH LOGIN PLAIN");
    }

    @Test
    void ehloShouldPreserveConfiguredMechanismOrderAndDeduplicateAdvertisement() throws Exception {
        PlainSaslMechanism plain = new PlainSaslMechanism(true, false);
        PlainSaslMechanism duplicatePlain = new PlainSaslMechanism(true, false);
        resetWithMechanisms(ImmutableList.of(
            ADVERTISED_OAUTHBEARER,
            loginMechanism(plain),
            plain,
            ADVERTISED_XOAUTH2,
            loginMechanism(duplicatePlain),
            duplicatePlain));
        SMTPClient client = connectedClient();

        client.sendCommand("EHLO localhost");

        assertThat(client.getReplyString())
            .contains("250-AUTH OAUTHBEARER LOGIN PLAIN XOAUTH2")
            .doesNotContain("LOGIN PLAIN XOAUTH2 LOGIN PLAIN");
    }

    @Test
    void ehloShouldAdvertiseOnlyConfiguredSaslMechanisms() throws Exception {
        resetWithMechanisms(ImmutableList.of(ADVERTISED_XOAUTH2));
        SMTPClient client = connectedClient();

        client.sendCommand("EHLO localhost");

        assertThat(client.getReplyString())
            .contains("250-AUTH XOAUTH2")
            .doesNotContain("LOGIN")
            .doesNotContain("PLAIN")
            .doesNotContain("OAUTHBEARER");
    }

    @Test
    void authPlainShouldBeRejectedWhenPlainMechanismIsNotConfigured() throws Exception {
        resetWithMechanisms(ImmutableList.of(ADVERTISED_XOAUTH2));
        SMTPClient client = connectedClient();

        client.sendCommand("AUTH PLAIN " + plainInitialResponse(SMTPServerTestSystem.BOB.asString(), SMTPServerTestSystem.PASSWORD));

        assertThat(client.getReplyString()).contains("504 Unrecognized Authentication Type");
    }

    @Test
    void authPlainWithInitialResponseShouldSucceedWhenCredentialsAreValid() throws Exception {
        resetWithMechanisms(ImmutableList.of(new PlainSaslMechanism(true, false)));
        SMTPClient client = connectedClient();

        client.sendCommand("AUTH PLAIN " + plainInitialResponse(SMTPServerTestSystem.BOB.asString(), SMTPServerTestSystem.PASSWORD));

        assertThat(client.getReplyString()).contains("235 Authentication Successful");
    }

    @Test
    void authPlainContinuationShouldSucceedWhenCredentialsAreValid() throws Exception {
        resetWithMechanisms(ImmutableList.of(new PlainSaslMechanism(true, false)));
        SMTPClient client = connectedClient();

        client.sendCommand("AUTH PLAIN");
        assertThat(client.getReplyString()).contains("334 ");
        client.sendCommand(plainInitialResponse(SMTPServerTestSystem.BOB.asString(), SMTPServerTestSystem.PASSWORD));

        assertThat(client.getReplyString()).contains("235 Authentication Successful");
    }

    @Test
    void authLoginShouldSucceedWhenCredentialsAreValid() throws Exception {
        PlainSaslMechanism plain = new PlainSaslMechanism(true, false);
        resetWithMechanisms(smtpPlainAndLoginMechanisms(plain));
        SMTPClient client = connectedClient();

        client.sendCommand("AUTH LOGIN");
        assertThat(client.getReplyString()).contains("334 VXNlcm5hbWU6");
        client.sendCommand(base64(SMTPServerTestSystem.BOB.asString()));
        assertThat(client.getReplyString()).contains("334 UGFzc3dvcmQ6");
        client.sendCommand(base64(SMTPServerTestSystem.PASSWORD));

        assertThat(client.getReplyString()).contains("235 Authentication Successful");
    }

    @Test
    void authLoginShouldFailWhenCredentialsAreInvalid() throws Exception {
        PlainSaslMechanism plain = new PlainSaslMechanism(true, false);
        resetWithMechanisms(smtpPlainAndLoginMechanisms(plain));
        SMTPClient client = connectedClient();

        client.sendCommand("AUTH LOGIN");
        assertThat(client.getReplyString()).contains("334 VXNlcm5hbWU6");
        client.sendCommand(base64(SMTPServerTestSystem.BOB.asString()));
        assertThat(client.getReplyString()).contains("334 UGFzc3dvcmQ6");
        client.sendCommand(base64("bad-password"));

        assertThat(client.getReplyString()).contains("535 Authentication Failed");
    }

    @Test
    void authPlainShouldFailWhenCredentialsAreInvalid() throws Exception {
        resetWithMechanisms(ImmutableList.of(new PlainSaslMechanism(true, false)));
        SMTPClient client = connectedClient();

        client.sendCommand("AUTH PLAIN " + plainInitialResponse(SMTPServerTestSystem.BOB.asString(), "bad-password"));

        assertThat(client.getReplyString()).contains("535 Authentication Failed");
    }

    @Test
    void authShouldSendFinalServerDataBeforeSuccess() throws Exception {
        resetWithMechanisms(ImmutableList.of(new ServerDataSaslMechanism()));
        SMTPClient client = connectedClient();

        client.sendCommand("AUTH SERVER-DATA");
        assertThat(client.getReplyString()).contains("334 " + base64("server-data"));

        client.sendCommand("");
        assertThat(client.getReplyString()).contains("235 Authentication Successful");
    }

    @Test
    void mechanismUnavailableOnClearTransportShouldNotBeAdvertisedAndShouldBeRejected() throws Exception {
        PlainSaslMechanism plain = new PlainSaslMechanism(true, true);
        resetWithMechanisms(smtpPlainAndLoginMechanisms(plain));
        SMTPClient client = connectedClient();

        client.sendCommand("EHLO localhost");
        assertThat(client.getReplyString()).doesNotContain("250-AUTH LOGIN PLAIN");

        client.sendCommand("AUTH PLAIN " + plainInitialResponse(SMTPServerTestSystem.BOB.asString(), SMTPServerTestSystem.PASSWORD));
        assertThat(client.getReplyString()).contains("504 Unrecognized Authentication Type");
    }

    private void resetWithMechanisms(ImmutableList<SaslMechanism> saslMechanisms) throws Exception {
        testSystem.smtpServer.destroy();
        HierarchicalConfiguration<ImmutableNode> configuration = ConfigLoader.getConfig(
            ClassLoaderUtils.getSystemResourceAsSharedStream("smtpserver-authAnnounceAlways.xml"));
        testSystem.setUpWithSaslMechanisms(configuration, authorizator(), saslMechanisms);
    }

    private ImmutableList<SaslMechanism> smtpPlainAndLoginMechanisms(PlainSaslMechanism plain) throws Exception {
        return ImmutableList.of(loginMechanism(plain), plain);
    }

    private SaslMechanism loginMechanism(PlainSaslMechanism plain) throws Exception {
        return new LoginSaslMechanismFactory(ignored -> plain).create(new BaseHierarchicalConfiguration());
    }

    private SMTPClient connectedClient() throws IOException {
        SMTPClient client = new SMTPClient();
        InetSocketAddress bindedAddress = testSystem.getBindedAddress();
        client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
        return client;
    }

    private String plainInitialResponse(String username, String password) {
        return base64("\0" + username + "\0" + password);
    }

    private String base64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(UTF_8));
    }

    private static class AdvertisedOnlySaslMechanism implements SaslMechanism {
        private final String name;

        private AdvertisedOnlySaslMechanism(String name) {
            this.name = name;
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
                    return new SaslStep.Failure(SaslFailure.authenticationFailed(
                        Optional.empty(), Optional.empty(), "Test-only mechanism"));
                }

                @Override
                public SaslStep onResponse(byte[] clientResponse) {
                    return new SaslStep.Failure(SaslFailure.authenticationFailed(
                        Optional.empty(), Optional.empty(), "Test-only mechanism"));
                }

                @Override
                public void close() {
                }
            };
        }
    }

    private static class ServerDataSaslMechanism implements SaslMechanism {
        @Override
        public String name() {
            return "SERVER-DATA";
        }

        @Override
        public SaslExchange start(SaslInitialRequest request, SaslAuthenticator authenticator) {
            return new SaslExchange() {
                @Override
                public SaslStep firstStep() {
                    return new SaslStep.Success(
                        new SaslIdentity(USER, USER),
                        Optional.of("server-data".getBytes(UTF_8)));
                }

                @Override
                public SaslStep onResponse(byte[] clientResponse) {
                    return firstStep();
                }

                @Override
                public void close() {
                }
            };
        }
    }
}
