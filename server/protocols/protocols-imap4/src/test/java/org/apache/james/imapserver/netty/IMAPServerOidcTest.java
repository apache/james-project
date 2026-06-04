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

import static org.apache.james.jwt.OidcTokenFixture.INTROSPECTION_RESPONSE;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.commons.net.imap.IMAPSClient;
import org.apache.james.imap.processor.fetch.FetchProcessor;
import org.apache.james.jwt.OidcTokenFixture;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.store.FakeAuthenticator;
import org.apache.james.mailbox.store.FakeAuthorizator;
import org.apache.james.protocols.api.OIDCSASLHelper;
import org.apache.james.protocols.lib.mock.ConfigLoader;
import org.apache.james.util.ClassLoaderUtils;
import org.apache.james.utils.TestIMAPClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;


@SuppressWarnings("checkstyle:membername")
class IMAPServerOidcTest extends AbstractIMAPServerTest {
    String JWKS_URI_PATH = "/jwks";
    String INTROSPECT_TOKEN_URI_PATH = "/introspect";
    String USERINFO_URI_PATH = "/userinfo";
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

        FakeAuthenticator authenticator = new FakeAuthenticator();
        authenticator.addUser(USER, USER_PASS);
        authenticator.addUser(USER2, USER_PASS);

        InMemoryIntegrationResources integrationResources = InMemoryIntegrationResources.builder()
            .authenticator(authenticator)
            .authorizator(FakeAuthorizator.forGivenUserAndDelegatedUser(USER, USER2))
            .inVmEventBus()
            .defaultAnnotationLimits()
            .defaultMessageParser()
            .scanningSearchIndex()
            .noPreDeletionHooks()
            .storeQuotaManager()
            .build();

        HierarchicalConfiguration<ImmutableNode> config = ConfigLoader.getConfig(ClassLoaderUtils.getSystemResourceAsSharedStream("oauth.xml"));
        config.addProperty("auth.oidc.jwksURL", String.format("http://127.0.0.1:%s%s", authServer.getLocalPort(), JWKS_URI_PATH));
        config.addProperty("auth.oidc.claim", OidcTokenFixture.CLAIM);
        config.addProperty("auth.oidc.oidcConfigurationURL", "https://example.com/jwks");
        config.addProperty("auth.oidc.scope", "email");

        imapServer = createImapServer(config, integrationResources, FetchProcessor.LocalCacheConfiguration.DEFAULT);
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
        String oauthBearer = OIDCSASLHelper.generateEncodedOauthbearerInitialClientResponse(USER.asString(), OidcTokenFixture.VALID_TOKEN);
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
        String xoauth2 = OIDCSASLHelper.generateEncodedXOauth2InitialClientResponse(USER.asString(), OidcTokenFixture.VALID_TOKEN);
        IMAPSClient client = imapsClient(port);
        client.sendCommand("AUTHENTICATE XOAUTH2 " + xoauth2);
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
        String oauthBearer = OIDCSASLHelper.generateEncodedOauthbearerInitialClientResponse(USER.asString(), OidcTokenFixture.VALID_TOKEN);
        IMAPSClient client = imapsClient(port);
        client.sendCommand("AUTHENTICATE OAUTHBEARER " + oauthBearer);
        client.sendCommand("AUTHENTICATE OAUTHBEARER " + oauthBearer);
        assertThat(client.getReplyString()).contains("NO AUTHENTICATE failed. Command not valid in this state.");
    }

    @Test
    void appendShouldSuccessWhenAuthenticated() throws Exception {
        String oauthBearer = OIDCSASLHelper.generateEncodedOauthbearerInitialClientResponse(USER.asString(), OidcTokenFixture.VALID_TOKEN);
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

    @Test
    void oauthShouldFailWhenIntrospectTokenReturnActiveIsFalse() throws Exception {
        imapServer.destroy();

        authServer
            .when(HttpRequest.request().withPath(INTROSPECT_TOKEN_URI_PATH))
            .respond(HttpResponse.response().withStatusCode(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"active\": false}", StandardCharsets.UTF_8));

        HierarchicalConfiguration<ImmutableNode> config = ConfigLoader.getConfig(ClassLoaderUtils.getSystemResourceAsSharedStream("oauth.xml"));
        config.addProperty("auth.oidc.jwksURL", String.format("http://127.0.0.1:%s%s", authServer.getLocalPort(), JWKS_URI_PATH));
        config.addProperty("auth.oidc.claim", OidcTokenFixture.CLAIM);
        config.addProperty("auth.oidc.oidcConfigurationURL", "https://example.com/jwks");
        config.addProperty("auth.oidc.scope", "email");
        config.addProperty("auth.oidc.introspection.url", String.format("http://127.0.0.1:%s%s", authServer.getLocalPort(), INTROSPECT_TOKEN_URI_PATH));

        imapServer = createImapServer(config);

        int port = imapServer.getListenAddresses().get(0).getPort();

        String oauthBearer = OIDCSASLHelper.generateEncodedOauthbearerInitialClientResponse(USER.asString(), OidcTokenFixture.VALID_TOKEN);
        IMAPSClient client = imapsClient(port);
        client.sendCommand("AUTHENTICATE OAUTHBEARER " + oauthBearer);
        assertThat(client.getReplyString()).contains("NO AUTHENTICATE failed.");
    }

    @Test
    void oauthShouldSuccessWhenIntrospectTokenReturnActiveIsTrue() throws Exception {
        imapServer.destroy();

        authServer
            .when(HttpRequest.request().withPath(INTROSPECT_TOKEN_URI_PATH))
            .respond(HttpResponse.response().withStatusCode(200)
                .withHeader("Content-Type", "application/json")
                .withBody(INTROSPECTION_RESPONSE, StandardCharsets.UTF_8));

        HierarchicalConfiguration<ImmutableNode> config = ConfigLoader.getConfig(ClassLoaderUtils.getSystemResourceAsSharedStream("oauth.xml"));
        config.addProperty("auth.oidc.jwksURL", String.format("http://127.0.0.1:%s%s", authServer.getLocalPort(), JWKS_URI_PATH));
        config.addProperty("auth.oidc.claim", OidcTokenFixture.CLAIM);
        config.addProperty("auth.oidc.oidcConfigurationURL", "https://example.com/jwks");
        config.addProperty("auth.oidc.scope", "email");
        config.addProperty("auth.oidc.introspection.url", String.format("http://127.0.0.1:%s%s", authServer.getLocalPort(), INTROSPECT_TOKEN_URI_PATH));

        imapServer = createImapServer(config);

        int port = imapServer.getListenAddresses().get(0).getPort();

        String oauthBearer = OIDCSASLHelper.generateEncodedOauthbearerInitialClientResponse(USER.asString(), OidcTokenFixture.VALID_TOKEN);
        IMAPSClient client = imapsClient(port);
        client.sendCommand("AUTHENTICATE OAUTHBEARER " + oauthBearer);
        assertThat(client.getReplyString()).contains("OK AUTHENTICATE completed.");
    }

    @Test
    void oauthShouldFailWhenIntrospectTokenServerError() throws Exception {
        imapServer.destroy();
        String invalidURI = "/invalidURI";
        authServer
            .when(HttpRequest.request().withPath(invalidURI))
            .respond(HttpResponse.response().withStatusCode(401));

        HierarchicalConfiguration<ImmutableNode> config = ConfigLoader.getConfig(ClassLoaderUtils.getSystemResourceAsSharedStream("oauth.xml"));
        config.addProperty("auth.oidc.jwksURL", String.format("http://127.0.0.1:%s%s", authServer.getLocalPort(), JWKS_URI_PATH));
        config.addProperty("auth.oidc.claim", OidcTokenFixture.CLAIM);
        config.addProperty("auth.oidc.oidcConfigurationURL", "https://example.com/jwks");
        config.addProperty("auth.oidc.scope", "email");
        config.addProperty("auth.oidc.introspection.url", String.format("http://127.0.0.1:%s%s", authServer.getLocalPort(), invalidURI));

        imapServer = createImapServer(config);

        int port = imapServer.getListenAddresses().get(0).getPort();

        String oauthBearer = OIDCSASLHelper.generateEncodedOauthbearerInitialClientResponse(USER.asString(), OidcTokenFixture.VALID_TOKEN);
        IMAPSClient client = imapsClient(port);
        client.sendCommand("AUTHENTICATE OAUTHBEARER " + oauthBearer);
        assertThat(client.getReplyString()).contains("NO AUTHENTICATE processing failed.");
    }

    @Test
    void oauthShouldSuccessWhenCheckTokenByUserInfoIsPassed() throws Exception {
        imapServer.destroy();

        authServer
            .when(HttpRequest.request().withPath(USERINFO_URI_PATH))
            .respond(HttpResponse.response().withStatusCode(200)
                .withHeader("Content-Type", "application/json")
                .withBody(OidcTokenFixture.USERINFO_RESPONSE, StandardCharsets.UTF_8));

        HierarchicalConfiguration<ImmutableNode> config = ConfigLoader.getConfig(ClassLoaderUtils.getSystemResourceAsSharedStream("oauth.xml"));
        config.addProperty("auth.oidc.jwksURL", String.format("http://127.0.0.1:%s%s", authServer.getLocalPort(), JWKS_URI_PATH));
        config.addProperty("auth.oidc.claim", OidcTokenFixture.CLAIM);
        config.addProperty("auth.oidc.oidcConfigurationURL", "https://example.com/jwks");
        config.addProperty("auth.oidc.scope", "email");
        config.addProperty("auth.oidc.userinfo.url", String.format("http://127.0.0.1:%s%s", authServer.getLocalPort(), USERINFO_URI_PATH));

        imapServer = createImapServer(config);

        int port = imapServer.getListenAddresses().get(0).getPort();

        String oauthBearer = OIDCSASLHelper.generateEncodedOauthbearerInitialClientResponse(USER.asString(), OidcTokenFixture.VALID_TOKEN);
        IMAPSClient client = imapsClient(port);
        client.sendCommand("AUTHENTICATE OAUTHBEARER " + oauthBearer);
        assertThat(client.getReplyString()).contains("OK AUTHENTICATE completed.");
    }

    @Test
    void oauthShouldFailWhenCheckTokenByUserInfoIsFailed() throws Exception {
        imapServer.destroy();

        authServer
            .when(HttpRequest.request().withPath(USERINFO_URI_PATH))
            .respond(HttpResponse.response().withStatusCode(401)
                .withHeader("Content-Type", "application/json"));

        HierarchicalConfiguration<ImmutableNode> config = ConfigLoader.getConfig(ClassLoaderUtils.getSystemResourceAsSharedStream("oauth.xml"));
        config.addProperty("auth.oidc.jwksURL", String.format("http://127.0.0.1:%s%s", authServer.getLocalPort(), JWKS_URI_PATH));
        config.addProperty("auth.oidc.claim", OidcTokenFixture.CLAIM);
        config.addProperty("auth.oidc.oidcConfigurationURL", "https://example.com/jwks");
        config.addProperty("auth.oidc.scope", "email");
        config.addProperty("auth.oidc.userinfo.url", String.format("http://127.0.0.1:%s%s", authServer.getLocalPort(), USERINFO_URI_PATH));

        imapServer = createImapServer(config);

        int port = imapServer.getListenAddresses().get(0).getPort();

        String oauthBearer = OIDCSASLHelper.generateEncodedOauthbearerInitialClientResponse(USER.asString(), OidcTokenFixture.VALID_TOKEN);
        IMAPSClient client = imapsClient(port);
        client.sendCommand("AUTHENTICATE OAUTHBEARER " + oauthBearer);
        assertThat(client.getReplyString()).contains("NO AUTHENTICATE processing failed.");
    }

    @Test
    void oauthShouldImpersonateFailWhenNOTDelegated() throws Exception {
        String oauthBearer = OIDCSASLHelper.generateEncodedOauthbearerInitialClientResponse(USER3.asString(), OidcTokenFixture.VALID_TOKEN);
        IMAPSClient client = imapsClient(port);
        client.sendCommand("AUTHENTICATE OAUTHBEARER " + oauthBearer);
        assertThat(client.getReplyString()).contains("NO AUTHENTICATE");
    }

    @Test
    void oauthShouldImpersonateSuccessWhenDelegated() throws Exception {
        String oauthBearer = OIDCSASLHelper.generateEncodedOauthbearerInitialClientResponse(USER2.asString(), OidcTokenFixture.VALID_TOKEN);
        IMAPSClient client = imapsClient(port);
        client.sendCommand("AUTHENTICATE OAUTHBEARER " + oauthBearer);
        assertThat(client.getReplyString()).contains("OK AUTHENTICATE completed.");
    }

    @Test
    void impersonationShouldWorkWhenDelegated() throws Exception {
        // USER2: append a message
        try (TestIMAPClient client = new TestIMAPClient(imapsClient(port))) {
            client.login(USER2.asString(), USER_PASS)
                .append("INBOX", SMALL_MESSAGE);
        }

        // USER1 authenticate and impersonate as USER2
        try (TestIMAPClient client = new TestIMAPClient(imapsClient(port))) {
            String oauthBearer = OIDCSASLHelper.generateEncodedOauthbearerInitialClientResponse(USER2.asString(), OidcTokenFixture.VALID_TOKEN);
            String authenticateResponse = client.sendCommand("AUTHENTICATE OAUTHBEARER " + oauthBearer);
            assertThat(authenticateResponse).contains("OK AUTHENTICATE completed.");

            assertThat(client.select("INBOX")
                .readFirstMessage()).contains(SMALL_MESSAGE);
        }
    }
}
